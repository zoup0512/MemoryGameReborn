#!/usr/bin/env python3
"""Build and launch the interactive administrator password hashing utility."""

import os
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    if len(sys.argv) != 1:
        print("Usage: python tools/admin-password-hash.py (no password arguments)", file=sys.stderr)
        return 2
    if not sys.stdin.isatty() or not sys.stdout.isatty():
        print("Run this utility in an interactive terminal; redirected password input is not accepted.", file=sys.stderr)
        return 2

    project = Path(__file__).resolve().parent.parent
    java_home = os.environ.get("JAVA_HOME")
    java = str(Path(java_home) / "bin" / ("java.exe" if os.name == "nt" else "java")) if java_home else shutil.which("java")
    if not java or not Path(java).is_file():
        print("Set JAVA_HOME to a Java 17 installation before running this utility.", file=sys.stderr)
        return 2

    if os.name == "nt":
        command = ["cmd.exe", "/d", "/c", "gradlew.bat", "-q", "--console=plain", "printAdminPasswordHashClasspath"]
    else:
        command = [str(project / "gradlew"), "-q", "--console=plain", "printAdminPasswordHashClasspath"]
    build = subprocess.run(command, cwd=project, stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
    if build.returncode:
        print("Could not build the password hashing utility:", file=sys.stderr)
        print(build.stderr or build.stdout, file=sys.stderr)
        return build.returncode
    classpaths = [line.strip() for line in build.stdout.splitlines() if "build" in line and "spring-security-crypto" in line]
    if len(classpaths) != 1:
        print("Gradle did not return one valid runtime classpath; no password was requested.", file=sys.stderr)
        return 2

    # Inherit the terminal so Java Console can read the password without echoing it.
    return subprocess.run([java, "-cp", classpaths[0], "com.memorygames.pk.admin.AdminPasswordHash"], cwd=project).returncode


if __name__ == "__main__":
    raise SystemExit(main())
