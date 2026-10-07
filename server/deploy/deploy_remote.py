#!/usr/bin/env python3
"""Deploy the PK server to the LAN test box over SSH (password auth).

Credentials come from the environment, never from this file:
    PK_HOST PK_USER PK_PASSWORD python deploy_remote.py [--check-java-only]

Steps: probe OS/Java -> upload jar via SFTP -> stop old instance ->
start on port 8090 with an H2 file store -> tail the log -> health check.
"""
import os
import sys
import time

import paramiko

HOST = os.environ["PK_HOST"]
USER = os.environ["PK_USER"]
PASSWORD = os.environ["PK_PASSWORD"]
PORT = 8090
REMOTE_DIR = "/root/memorygames-pk"
JAR_LOCAL = os.path.join(os.path.dirname(os.path.abspath(__file__)),
                         "memorygames-pk-server-0.1.0.jar")


def connect():
    client = paramiko.SSHClient()
    client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
    client.connect(HOST, username=USER, password=PASSWORD, timeout=10)
    return client


def run(client, cmd, timeout=60):
    _, stdout, stderr = client.exec_command(cmd, timeout=timeout)
    out = stdout.read().decode("utf-8", "replace").strip()
    err = stderr.read().decode("utf-8", "replace").strip()
    code = stdout.channel.recv_exit_status()
    return code, out, err


def main():
    check_only = "--check-java-only" in sys.argv
    client = connect()
    try:
        code, out, _ = run(client, "uname -a")
        print("[remote]", out)
        code, out, _ = run(client, "java -version 2>&1 | head -1")
        print("[remote java]", out or "(no java)")
        if check_only:
            return
        if "version \"17" not in out and "version \"21" not in out and "version \"25" not in out:
            print("!! Java 17+ not found on remote; install it first (apt/yum openjdk-17-jre)")
            sys.exit(2)

        import hashlib
        local_md5 = hashlib.md5(open(JAR_LOCAL, "rb").read()).hexdigest()
        print(f"==> uploading jar (49 MB, local md5 {local_md5[:12]}...) ...")
        sftp = client.open_sftp()
        try:
            run(client, f"mkdir -p {REMOTE_DIR}")
            sftp.put(JAR_LOCAL, f"{REMOTE_DIR}/app.jar")
        finally:
            sftp.close()
        # The NAS has already been burned by "upload finished != upload
        # correct": verify the remote md5 matches before going further.
        code, remote_md5, _ = run(client, f"md5sum {REMOTE_DIR}/app.jar | cut -d' ' -f1")
        remote_md5 = remote_md5.strip()
        if remote_md5 != local_md5:
            print(f"!! md5 mismatch: remote={remote_md5} local={local_md5}")
            sys.exit(4)
        print("[md5] verified")

        print("==> stopping previous instance")
        # SIGTERM first so the Spring shutdown hook closes H2 cleanly;
        # kill -9 loses unflushed commits (registrations lost on the LAN
        # box once). Escalate only if the process survives.
        run(client, "pkill -f 'java.*app\\.jar'; sleep 5; pkill -9 -f 'java.*app\\.jar'; sleep 1", timeout=30)

        print(f"==> starting PK server on 0.0.0.0:{PORT} (H2 file: {REMOTE_DIR}/data/pk)")
        # Double-detach: the subshell exits immediately so the SSH channel
        # gets EOF even though java keeps running (a plain "cmd &" holds the
        # channel's fds open and times out paramiko).
        # The admin API is hidden (404) unless PK_ADMIN_TOKEN is provided to
        # this script; on a publicly exposed box use a strong random token.
        token = os.environ.get("PK_ADMIN_TOKEN", "")
        token_prefix = f"PK_ADMIN_TOKEN='{token}' " if token else ""
        start = (
            f"cd {REMOTE_DIR} && ({token_prefix}nohup java -Xms128m -Xmx512m"
            f" -Dserver.address=0.0.0.0 -Dserver.port={PORT}"
            " -Dspring.datasource.url='jdbc:h2:file:./data/pk;DB_CLOSE_DELAY=-1'"
            " -jar app.jar > pk-server.log 2>&1 < /dev/null &) && echo launched"
        )
        run(client, start, timeout=20)

        print("==> waiting for boot")
        deadline = time.time() + 60
        started = False
        while time.time() < deadline:
            code, out, _ = run(client, f"grep -c 'Started PkServerApplication' {REMOTE_DIR}/pk-server.log 2>/dev/null")
            if out.strip() == "1":
                started = True
                break
            code, out, _ = run(client, f"grep -m1 'APPLICATION FAILED\\|ERROR' {REMOTE_DIR}/pk-server.log 2>/dev/null | head -2")
            if out:
                print("[boot error]", out)
                break
            time.sleep(3)
        _, log_tail, _ = run(client, f"tail -n 8 {REMOTE_DIR}/pk-server.log")
        print("[log tail]\n" + log_tail)
        if not started:
            print("!! server did not report successful boot")
            sys.exit(3)
        print("OK: PkServerApplication started")
    finally:
        client.close()


if __name__ == "__main__":
    main()
