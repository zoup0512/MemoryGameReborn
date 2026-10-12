#!/usr/bin/env python3
"""Ad-hoc remote command runner for the LAN deployment (env credentials)."""
import os
import sys

import paramiko

HOST = os.environ["PK_HOST"]
USER = os.environ["PK_USER"]
PASSWORD = os.environ["PK_PASSWORD"]

client = paramiko.SSHClient()
client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
client.connect(HOST, username=USER, password=PASSWORD, timeout=10)
cmd = sys.argv[1]
_, stdout, stderr = client.exec_command(cmd, timeout=30)
out = stdout.read().decode("utf-8", "replace").strip()
err = stderr.read().decode("utf-8", "replace").strip()
print(out)
if err:
    print("[stderr]", err, file=sys.stderr)
client.close()
