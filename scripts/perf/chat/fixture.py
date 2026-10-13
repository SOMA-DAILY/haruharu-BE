#!/usr/bin/env python3
"""Offline local configuration / JWT fixture generation. Never reads production credentials."""

import argparse
import base64
import hashlib
import hmac
import json
import os
from pathlib import Path
import secrets
import subprocess
import time
import uuid
from datetime import datetime, timezone

ROOT = Path(__file__).resolve().parents[3]
STATE = ROOT / "build" / "chat-perf"
ENV = STATE / ".env"


def private_write(path, value):
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    os.fchmod(fd, 0o600)
    with os.fdopen(fd, "w") as output:
        output.write(value)


def read_env():
    if not ENV.exists():
        raise SystemExit("Run fixture.py init first")
    return dict(line.split("=", 1) for line in ENV.read_text().splitlines() if line and not line.startswith("#"))


def init():
    STATE.mkdir(parents=True, exist_ok=True, mode=0o700)
    STATE.chmod(0o700)
    if ENV.exists():
        raise SystemExit("Local configuration already exists; refusing to replace DB password / signing key")
    private_write(ENV, "CHAT_PERF_DB_PASSWORD=" + secrets.token_hex(24) + "\n"
                  + "CHAT_PERF_JWT_SECRET=" + secrets.token_hex(32) + "\n")
    private_write(STATE / "application-perf.properties", """# Generated local-only settings; no deployment profile is loaded.
server.address=127.0.0.1
server.port=18082
spring.datasource.url=jdbc:postgresql://127.0.0.1:15432/haruharu_chat_perf
spring.datasource.username=chat_perf
spring.datasource.password=${CHAT_PERF_DB_PASSWORD}
spring.datasource.hikari.maximum-pool-size=10
spring.jpa.hibernate.ddl-auto=validate
spring.jpa.show-sql=false
jwt.secret=${CHAT_PERF_JWT_SECRET}
jwt.access-token-expiry-ms=86400000
jwt.refresh-token-expiry-ms=86400000
revenuecat.webhook-secret=local-perf-unused
oauth.providers.kakao.client-id=local-perf-unused
oauth.providers.apple.client-id=local-perf-unused
aws.s3.bucket=local-perf-unused
fcm.service-account-json=
identity.encryption.key=${CHAT_PERF_JWT_SECRET}
haruharu.room.reconcile.enabled=false
haruharu.batch.notification-cleanup.scheduler.enabled=false
haruharu.notification.slot-start.enabled=false
haruharu.chat.admin-message.enabled=false
haruharu.chat.fanout.redis.enabled=false
haruharu.chat.subscription-reconcile-delay-ms=10000
haruharu.ticket.enabled=false
haruharu.payment.enabled=false
""")
    print(f"Local configuration created in {STATE}; signing key and password were not printed.")


def start_app(init_schema, pool_size=None):
    local = read_env()
    # Inherit toolchain paths, not a shell's DB/JWT/AWS/FCM settings.
    allowed = ("PATH", "HOME", "JAVA_HOME", "GRADLE_USER_HOME", "TMPDIR", "LANG", "LC_ALL")
    env = {key: os.environ[key] for key in allowed if key in os.environ}
    env.update(local)
    args = "--spring.profiles.active=perf --spring.config.location=classpath:/application.yaml," \
           + (STATE / "application-perf.properties").as_uri()
    if init_schema:
        args += " --spring.jpa.hibernate.ddl-auto=update"
    if pool_size is not None:
        if not 2 <= pool_size <= 80:
            raise SystemExit("Local pool size must be between 2 and 80")
        args += f" --spring.datasource.hikari.maximum-pool-size={pool_size}"
    raise SystemExit(subprocess.call([str(ROOT / "gradlew"), ":app:bootRun", "--args=" + args], cwd=ROOT, env=env))


def b64(data):
    return base64.urlsafe_b64encode(data).rstrip(b"=").decode("ascii")


def token(user_id, key, issued, expires):
    # Same claims as JwtProvider: numeric-string subject, ACCESS type, jti, iat, exp.
    # JJWT accepts HS256 with this local >=256-bit key.
    head = b64(json.dumps({"alg": "HS256", "typ": "JWT"}, separators=(",", ":")).encode())
    body = b64(json.dumps({"sub": str(user_id), "type": "ACCESS", "jti": str(uuid.uuid4()),
                           "iat": issued, "exp": expires}, separators=(",", ":")).encode())
    signed = head + "." + body
    return signed + "." + b64(hmac.new(key.encode(), signed.encode(), hashlib.sha256).digest())


def credentials():
    local = read_env()
    mapping = json.loads((STATE / "mapping.json").read_text())
    dataset = mapping["dataset"]
    users = mapping["users"]
    expected = (dataset["active_rooms"] + 1) * dataset["members_per_room"]
    if len(users) != expected or len({u["user_id"] for u in users}) != expected:
        raise SystemExit("Export does not contain the expected distinct active + archived participants")
    issued = int(time.time())
    expires = issued + 24 * 60 * 60
    for user in users:
        user["access_token"] = token(user["user_id"], local["CHAT_PERF_JWT_SECRET"], issued, expires)
    active = [u for u in users if u["status"] == "ACTIVE"]
    archived = [u for u in users if u["status"] == "ENDED"]
    result = {
        "base_url": "http://127.0.0.1:18082", "ws_url": "ws://127.0.0.1:18082/ws-chat",
        "issued_at": datetime.fromtimestamp(issued, timezone.utc).isoformat(),
        "expires_at": datetime.fromtimestamp(expires, timezone.utc).isoformat(),
        "dataset": dataset, "rooms": mapping["rooms"], "users": active, "archived_users": archived,
        "workloads": {str(n): {"users": n, "rooms": n // 6} for n in (6, 60, 180, 300) if n <= len(active)},
    }
    private_write(STATE / "fixtures.json", json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(f"Exported {len(active)} active / {len(archived)} archived test credentials to {STATE / 'fixtures.json'}")
    print(f"Tokens expire at {result['expires_at']}; rerun credentials to refresh them.")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("init", "start-app", "credentials"))
    parser.add_argument("--init-schema", action="store_true", help="Create missing schema on the dedicated local DB")
    parser.add_argument("--pool-size", type=int, help="Override only the local app DB pool for a labelled experiment")
    args = parser.parse_args()
    if args.command == "init":
        init()
    elif args.command == "start-app":
        start_app(args.init_schema, args.pool_size)
    else:
        credentials()


if __name__ == "__main__":
    main()
