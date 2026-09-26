"""Safely move the known application's tables between MySQL schemas."""
import argparse
import hashlib
import json
import os
import re
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from export_schema import TABLES

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--defaults-file", required=True, type=Path)
    parser.add_argument("--source", default="easychat")
    parser.add_argument("--target", default="wetalk")
    parser.add_argument("--mysql", default="mysql")
    parser.add_argument("--mysqldump", default="mysqldump")
    parser.add_argument("--apply", action="store_true")
    args = parser.parse_args()
    for identifier in (args.source, args.target):
        if not re.fullmatch(r"[A-Za-z0-9_]+", identifier):
            parser.error("Invalid schema identifier")
    if args.source == args.target:
        parser.error("Source and target must differ")
    connection = "--defaults-extra-file=" + str(args.defaults_file.resolve())

    def query(sql):
        result = subprocess.run(
            [args.mysql, connection, "--default-character-set=utf8mb4",
             "--batch", "--raw", "--skip-column-names", "--execute", sql],
            capture_output=True,
        )
        if result.returncode:
            raise RuntimeError(result.stderr.decode("utf-8", errors="replace"))
        return [line.split("\t") for line in result.stdout.decode("utf-8").splitlines() if line]

    def objects(schema):
        return query("SELECT TABLE_NAME, ENGINE, TABLE_TYPE FROM information_schema.TABLES "
                     f"WHERE TABLE_SCHEMA='{schema}' ORDER BY TABLE_NAME")

    def fingerprint(schema):
        counts = {name: int(query(f"SELECT COUNT(*) FROM `{schema}`.`{name}`")[0][0])
                  for name in TABLES}
        qualified = ", ".join(f"`{schema}`.`{name}`" for name in TABLES)
        checksums = {row[0].split(".", 1)[1]: row[1]
                     for row in query(f"CHECKSUM TABLE {qualified} EXTENDED")}
        if set(checksums) != set(TABLES) or "NULL" in checksums.values():
            raise RuntimeError("Cannot verify all table checksums")
        return {"rows": counts, "checksums": checksums}

    version = query("SELECT VERSION()")[0][0]
    if not re.match(r"^8[.]", version) or "MariaDB" in version:
        raise RuntimeError("This migration is restricted to MySQL 8")
    tables = objects(args.source)
    if {row[0] for row in tables} != set(TABLES):
        raise RuntimeError("Source must contain exactly the 9 known application tables")
    if any(row[1] != "InnoDB" or row[2] != "BASE TABLE" for row in tables):
        raise RuntimeError("Only InnoDB base tables can be migrated")
    if objects(args.target):
        raise RuntimeError("Target contains objects; refusing to merge or overwrite")
    for table, column in (("TRIGGERS", "TRIGGER_SCHEMA"),
                          ("ROUTINES", "ROUTINE_SCHEMA"),
                          ("EVENTS", "EVENT_SCHEMA")):
        count = int(query(f"SELECT COUNT(*) FROM information_schema.{table} "
                          f"WHERE {column} IN ('{args.source}','{args.target}')")[0][0])
        if count:
            raise RuntimeError("Triggers/routines/events require a dedicated migration")
    sessions = int(query("SELECT COUNT(*) FROM information_schema.PROCESSLIST "
                         f"WHERE DB='{args.source}' AND ID<>CONNECTION_ID()")[0][0])
    if sessions:
        raise RuntimeError("Close source database connections and stop the backend first")
    schema = query("SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME "
                   f"FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{args.source}'")[0]
    if not all(re.fullmatch(r"[A-Za-z0-9_]+", value) for value in schema):
        raise RuntimeError("Invalid source charset/collation")
    target_schema = query("SELECT DEFAULT_CHARACTER_SET_NAME,DEFAULT_COLLATION_NAME "
                          f"FROM information_schema.SCHEMATA WHERE SCHEMA_NAME='{args.target}'")
    if target_schema and target_schema[0] != schema:
        raise RuntimeError("Existing empty target has different charset/collation")

    before = fingerprint(args.source)
    print(f"Plan: {args.source} -> {args.target}, {len(tables)} tables, "
          f"{sum(before['rows'].values())} records, MySQL {version}")
    if not args.apply:
        print("Dry run only. Pass --apply to back up and move tables.")
        return

    backup_dir = Path(__file__).resolve().parents[1] / ".private/db-backups"
    backup_dir.mkdir(parents=True, exist_ok=True)
    if os.name == "posix":
        backup_dir.chmod(0o700)
    stamp = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
    backup = backup_dir / f"{stamp}-{args.source}.sql"
    command = [args.mysqldump, connection, "--single-transaction", "--hex-blob",
               "--skip-comments", "--set-gtid-purged=OFF", "--no-tablespaces",
               "--default-character-set=utf8mb4", args.source, *TABLES]
    with backup.open("xb") as output:
        result = subprocess.run(command, stdout=output, stderr=subprocess.PIPE)
    if result.returncode or backup.stat().st_size == 0:
        raise RuntimeError("Backup failed; no tables were moved")
    if os.name == "posix":
        backup.chmod(0o600)
    if fingerprint(args.source) != before or objects(args.target):
        raise RuntimeError("Database changed during backup; no tables were moved")

    pairs = ", ".join(f"`{args.source}`.`{name}` TO `{args.target}`.`{name}`"
                      for name in TABLES)
    query("SET SESSION lock_wait_timeout=10; "
          f"CREATE DATABASE IF NOT EXISTS `{args.target}` "
          f"CHARACTER SET {schema[0]} COLLATE {schema[1]}; RENAME TABLE {pairs};")
    after = fingerprint(args.target)
    verified = before == after and not objects(args.source)
    rollback = "RENAME TABLE " + ", ".join(
        f"`{args.target}`.`{name}` TO `{args.source}`.`{name}`" for name in TABLES) + ";\n"
    (backup_dir / f"{stamp}-rollback.sql").write_text(rollback, encoding="utf-8")
    report = {"source": args.source, "target": args.target, "mysqlVersion": version,
              "backupFile": backup.name, "backupSha256": hashlib.sha256(backup.read_bytes()).hexdigest(),
              "before": before, "after": after, "verified": verified}
    (backup_dir / f"{stamp}-rename.json").write_text(
        json.dumps(report, indent=2), encoding="utf-8")
    if not verified:
        raise RuntimeError("Verification failed; review the private backup/rollback before continuing")
    print("Migration verified: all table row counts and data checksums match.")
    print(f"Private backup: {backup}")
    print("The old empty schema was retained. No accounts or grants were changed.")

if __name__ == "__main__":
    main()
