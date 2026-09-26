"""Build a deployable NAS ZIP from an already tested executable JAR."""
import hashlib
import json
import subprocess
import xml.etree.ElementTree as ET
import zipfile
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
VERSION = ET.parse(ROOT / "pom.xml").find(
    "{http://maven.apache.org/POM/4.0.0}version"
).text
JAR = ROOT / "target/wetalk.jar"
assert JAR.is_file(), "Run mvn clean verify first"
with zipfile.ZipFile(JAR) as jar:
    assert "BOOT-INF/classes/top/enderherman/wetalk/WeTalkApplication.class" in jar.namelist()
    settings = jar.read("BOOT-INF/classes/application.yml").decode()
    assert "${DB_PASSWORD:}" in settings and "${OPENAI_API_KEY:}" in settings

reports = list((ROOT / "target/surefire-reports").glob("TEST-*.xml"))
assert reports, "No test reports found"
totals = {key: 0 for key in ("tests", "failures", "errors", "skipped")}
for report in reports:
    suite = ET.parse(report).getroot()
    for key in totals:
        totals[key] += int(suite.attrib.get(key, 0))
assert totals["failures"] == totals["errors"] == 0, totals
assert totals["tests"] > 0, totals

def sha(data):
    return hashlib.sha256(data).hexdigest()

files = {
    name: (ROOT / name).read_bytes()
    for name in ("target/wetalk.jar", "Dockerfile", "compose.yaml",
                 ".env.example", "README.md", "CHANGELOG.md")
}
commit = subprocess.check_output(
    ["git", "rev-parse", "HEAD"], cwd=ROOT, text=True
).strip()
dirty = bool(subprocess.check_output(
    ["git", "status", "--porcelain"], cwd=ROOT, text=True
).strip())
info = {
    "version": VERSION, "sourceCommit": commit,
    "sourceDirty": dirty,
    "packagedAt": datetime.now(timezone.utc).isoformat(),
    "tests": totals, "jarSha256": sha(files["target/wetalk.jar"]),
    "baseImage": "eclipse-temurin:17-jre-jammy",
    "dockerImageIncluded": False, "databaseSchemaIncluded": False,
}
files["RELEASE.json"] = json.dumps(info, ensure_ascii=False, indent=2).encode()
files["SHA256SUMS"] = "".join(
    f"{sha(data)}  {name}\n" for name, data in files.items()
).encode()
dist = ROOT / "dist"
dist.mkdir(exist_ok=True)
archive = dist / f"wetalk-backend-{VERSION}-nas.zip"
with zipfile.ZipFile(archive, "w", zipfile.ZIP_DEFLATED, compresslevel=6) as package:
    for name, data in files.items():
        package.writestr(name, data)
digest = sha(archive.read_bytes())
archive.with_suffix(".zip.sha256").write_text(
    f"{digest}  {archive.name}\n", encoding="utf-8"
)
print(f"Release: {archive}")
print(f"SHA256: {digest}")
print(f"Tests: {totals}")
