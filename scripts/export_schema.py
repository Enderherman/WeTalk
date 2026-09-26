"""Export only the application's table definitions; never export private rows."""
import argparse
import re
import subprocess
from pathlib import Path

TABLES = (
    "app_update", "chat_message", "chat_session", "chat_session_user",
    "group_info", "user_contact", "user_contact_apply", "user_info", "user_info_beauty",
)
def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--defaults-file", required=True, type=Path)
    parser.add_argument("--database", default="wetalk")
    parser.add_argument("--mysqldump", default="mysqldump")
    parser.add_argument("--output", type=Path, default=Path("sql/001-schema.sql"))
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z0-9_]+", args.database):
        parser.error("Invalid database identifier")
    command = [
        args.mysqldump, "--defaults-extra-file=" + str(args.defaults_file.resolve()),
        "--no-data", "--skip-comments", "--skip-dump-date", "--skip-add-drop-table",
        "--set-gtid-purged=OFF", "--no-tablespaces", "--skip-triggers",
        "--default-character-set=utf8mb4", args.database, *TABLES,
    ]
    result = subprocess.run(command, capture_output=True)
    if result.returncode:
        raise SystemExit(result.stderr.decode("utf-8", errors="replace"))
    sql = result.stdout.decode("utf-8").replace("\r\n", "\n")
    sql = re.sub(r"\bAUTO_INCREMENT=\d+\b", "AUTO_INCREMENT=1", sql)
    if re.search(r"(?im)^\s*(INSERT|REPLACE|DROP TABLE|CREATE USER|GRANT)\b", sql):
        raise SystemExit("Unexpected data/destructive/credential statement")
    created = re.findall(r"CREATE TABLE `([^`]+)`", sql)
    if set(created) != set(TABLES):
        raise SystemExit(f"Expected all 9 application tables; exported: {created}")
    header = (
        "-- WeTalk empty-database initialization; exported from actual local MySQL.\n"
        f"-- Selected target schema: {args.database}.\n"
        "-- Table definitions only: no accounts, password hashes, messages or local data.\n"
        "-- Execute only against a new empty database. This is not an upgrade script.\n\n"
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(header + sql.rstrip() + "\n", encoding="utf-8")
    print(f"Exported {len(created)} empty table definitions to {args.output}")

if __name__ == "__main__":
    main()
