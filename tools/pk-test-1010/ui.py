"""Minimal uiautomator drive helper for the practice-match device test.

Usage:
  python ui.py dump                 -> full ui dump
  python ui.py find <text>          -> center coords of first node containing text
  python ui.py tap <text>           -> tap first node containing text
  python ui.py wait <text> [sec]    -> poll until text appears, print coords
"""
import re
import subprocess
import sys
import time

ADB = r"C:/MyProgram/Sdk/platform-tools/adb.exe"


def sh(*args):
    result = subprocess.run([ADB] + list(args), capture_output=True, text=True,
                            errors="replace")
    return result.stdout


def dump():
    sh("shell", "rm -f /data/local/tmp/ui.xml")
    sh("shell", "uiautomator", "dump", "/data/local/tmp/ui.xml")
    return sh("shell", "cat", "/data/local/tmp/ui.xml")


def find(xml, text):
    for m in re.finditer(
            r'<node[^>]*?text="([^"]*)"[^>]*?bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"', xml):
        if text.lower() in m.group(1).lower():
            x = (int(m.group(2)) + int(m.group(4))) // 2
            y = (int(m.group(3)) + int(m.group(5))) // 2
            return x, y, m.group(1)
    return None


def tap(x, y):
    sh("shell", "input", "tap", str(x), str(y))


def main():
    cmd = sys.argv[1]
    if cmd == "dump":
        print(dump())
    elif cmd == "find":
        hit = find(dump(), sys.argv[2])
        print(hit if hit else "NOT FOUND")
    elif cmd == "tap":
        hit = find(dump(), sys.argv[2])
        if hit:
            tap(hit[0], hit[1])
            print(f"tapped {hit}")
        else:
            print("NOT FOUND")
            sys.exit(1)
    elif cmd == "wait":
        text = sys.argv[2]
        deadline = time.time() + (int(sys.argv[3]) if len(sys.argv) > 3 else 20)
        while time.time() < deadline:
            xml = dump()
            hit = find(xml, text)
            if hit:
                print(f"FOUND {hit}")
                return
            time.sleep(1.5)
        print(f"TIMEOUT waiting for {text!r}")
        sys.exit(2)


if __name__ == "__main__":
    main()
