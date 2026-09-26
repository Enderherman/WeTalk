"""Create dedicated infrastructure credentials without displaying them."""
import argparse
import ipaddress
import os
import secrets
from pathlib import Path

parser = argparse.ArgumentParser()
parser.add_argument("--directory", type=Path, default=Path("."))
parser.add_argument("--bind-ip", default="127.0.0.1")
args = parser.parse_args()
ipaddress.ip_address(args.bind_ip)
root = args.directory.resolve()
targets = [root / ".env", root / "config/redis.conf", root / "secrets"]
if any(p.exists() for p in targets):
    raise SystemExit("Existing environment/secrets/config detected; refusing to overwrite")
root.mkdir(parents=True, exist_ok=True)
mysql_root, mysql_app, redis_password = (secrets.token_urlsafe(32) for _ in range(3))
secret_dir = root / "secrets"
secret_dir.mkdir(mode=0o700)
values = {
    "mysql-root-password": mysql_root + "\n",
    "mysql-password": mysql_app + "\n",
    "redis-password": redis_password + "\n",
    "mysql-client.cnf": f"[client]\nuser=wetalk\npassword={mysql_app}\nhost=127.0.0.1\n",
}
for name, value in values.items():
    p = secret_dir / name
    p.write_text(value, encoding="utf-8")
    if os.name == "posix":
        p.chmod(0o444)  # Parent is 0700; container MySQL/Redis can read bind-mounted files.
config_dir = root / "config"
config_dir.mkdir(mode=0o700)
redis_config = config_dir / "redis.conf"
redis_config.write_text(
    "bind 0.0.0.0\nprotected-mode yes\ndir /data\nappendonly yes\n"
    f"appendfsync everysec\nsave 60 1000\nrequirepass {redis_password}\n",
    encoding="utf-8",
)
env = root / ".env"
env.write_text(
    f"NAS_BIND_IP={args.bind_ip}\nMYSQL_PUBLISHED_PORT=13306\nREDIS_PUBLISHED_PORT=16379\n"
    "DB_URL=jdbc:mysql://wetalk-mysql:3306/wetalk?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai\n"
    f"DB_USERNAME=wetalk\nDB_PASSWORD={mysql_app}\n"
    f"REDIS_HOST=wetalk-redis\nREDIS_PORT=6379\nREDIS_PASSWORD={redis_password}\n"
    "REDIS_DATABASE=0\nADMIN_EMAILS=\nWETALK_AI_ENABLED=false\nWETALK_AI_MODEL=none\n"
    "WETALK_DATA_DIR=./data/backend\nHTTP_PUBLISHED_PORT=15050\nWS_PUBLISHED_PORT=15051\n",
    encoding="utf-8",
)
if os.name == "posix":
    env.chmod(0o600)
    redis_config.chmod(0o600)
for name in ("data/mysql", "data/redis", "data/backend"):
    (root / name).mkdir(parents=True, exist_ok=True)
print(f"Prepared dedicated credentials and directories in {root}; secrets were not displayed.")
print("On Linux, set config/redis.conf owner to UID/GID 999 before starting Redis.")
