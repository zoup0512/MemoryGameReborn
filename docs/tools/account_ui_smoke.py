"""Black-box GUI check for the account screens on a real device.

The package under test defaults to the isolated .accounttest variant; pass
--package com.memory.brain.training.games for a normal debug install.
Loopback runs use the in-process SMTP fixture; remote runs pass
--mail-command to read verification codes from the deployment mail sink.
It never installs, clears data, or writes credentials to test output.
"""
import argparse
import json
import pathlib
import re
import secrets
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
from account_smoke import CommandMailbox, MAILBOX, SmtpHandler, SmtpServer


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--serial", required=True)
    parser.add_argument("--evidence", required=True)
    parser.add_argument("--package", default="com.memory.brain.training.games.accounttest")
    parser.add_argument("--mail-command",
                        help="Shell command printing the deployment mail-sink log (remote runs)")
    options = parser.parse_args()
    package = options.package
    destination = pathlib.Path(options.evidence)
    destination.mkdir(parents=True, exist_ok=True)

    def adb(*args):
        return subprocess.run([options.adb, "-s", options.serial, *args], capture_output=True, check=True).stdout

    def tree():
        for _ in range(3):
            adb("shell", "rm", "-f", "/sdcard/account-test-ui.xml")
            out = adb("shell", "uiautomator", "dump", "/sdcard/account-test-ui.xml")
            if b"dumped" in out:
                break
            time.sleep(1)
        raw = adb("exec-out", "cat", "/sdcard/account-test-ui.xml")
        root = ET.fromstring(raw)
        if not any(n.get("package") == package for n in root.iter("node")):
            raise AssertionError("Foreground UI does not belong to the package under test")
        return root

    def center(node):
        coordinates = list(map(int, re.findall(r"\d+", node.get("bounds"))))
        return ((coordinates[0] + coordinates[2]) // 2, (coordinates[1] + coordinates[3]) // 2)

    def tap_node(node):
        x, y = center(node)
        adb("shell", "input", "tap", str(x), str(y))
        time.sleep(.35)

    screen_size = None

    def size():
        nonlocal screen_size
        if screen_size is None:
            tail = adb("shell", "wm", "size").decode().split()[-1]
            screen_size = list(map(int, re.findall(r"\d+", tail)))
        return screen_size

    def reveal_lower_content():
        # finger moves up -> page content scrolls down
        width, height = size()
        adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.80)),
            str(width // 2), str(int(height * 0.30)), "300")
        time.sleep(.35)

    def back_to_top():
        width, height = size()
        for _ in range(3):
            adb("shell", "input", "swipe", str(width // 2), str(int(height * 0.30)),
                str(width // 2), str(int(height * 0.80)), "300")
            time.sleep(.3)

    def visible_node(root, text):
        """On small screens off-viewport ScrollView children report zero bounds."""
        best = None
        for node in root.iter("node"):
            if node.get("text") != text:
                continue
            bounds = list(map(int, re.findall(r"\d+", node.get("bounds") or "")))
            if len(bounds) != 4:
                continue
            area = (bounds[2] - bounds[0]) * (bounds[3] - bounds[1])
            if area > 0 and (best is None or area > best[0]):
                best = (area, node)
        return best[1] if best else None

    def click(text):
        back_to_top()
        for _ in range(6):
            node = visible_node(tree(), text)
            if node is not None:
                tap_node(node)
                return
            reveal_lower_content()
        raise AssertionError("Missing UI control: " + text)

    def text():
        return "\n".join(n.get("text", "") for n in tree().iter("node"))

    def wait_contains(value, seconds=20):
        deadline = time.monotonic() + seconds
        while time.monotonic() < deadline:
            back_to_top()
            current = text()
            if value in current:
                return current
            time.sleep(.5)
        raise AssertionError("UI did not display expected completion state: " + value)

    def ime_shown():
        out = adb("shell", "dumpsys", "input_method").decode("utf-8", "replace")
        return "mInputShown=true" in out

    def fill(index, value):
        nodes = [n for n in tree().iter("node") if n.get("class") == "android.widget.EditText"]
        tap_node(nodes[index])
        adb("shell", "input", "text", value)
        if ime_shown():
            adb("shell", "input", "keyevent", "4")  # close the keyboard only when it is up
        time.sleep(.3)

    def clear_field(index):
        nodes = [n for n in tree().iter("node") if n.get("class") == "android.widget.EditText"]
        tap_node(nodes[index])
        adb("shell", "input", "keyevent", "KEYCODE_MOVE_END")
        adb("shell", "input", "keyevent", *(["KEYCODE_DEL"] * 80))

    def screenshot(label):
        (destination / (label + ".png")).write_bytes(adb("exec-out", "screencap", "-p"))

    smtp = None
    mailbox = MAILBOX
    if options.mail_command:
        mailbox = CommandMailbox(options.mail_command)
    else:
        smtp = SmtpServer(("127.0.0.1", 2526), SmtpHandler)
        threading.Thread(target=smtp.serve_forever, daemon=True).start()
    checks = []
    address = "ui-" + secrets.token_hex(8) + "@example.com"
    password = "UiA9" + secrets.token_hex(12)
    try:
        current = wait_contains("游客账号")
        old_id = re.search(r"游客账号 #(\d+)", current).group(1)
        screenshot("01-guest")
        click("绑定邮箱")
        fill(0, address)
        fill(1, password)
        fields = [n for n in tree().iter("node") if n.get("class") == "android.widget.EditText"]
        assert fields[0].get("text") == address, "Device keyboard changed the test email input"
        before = len(MAILBOX.messages)
        click("发送验证邮件")
        code = mailbox.code(address, before)
        wait_contains("操作成功")
        fill(2, code)
        click("确认")
        wait_contains("邮箱账号 #" + old_id)
        checks.append("verified-email registration preserves guest userId")
        screenshot("02-registered")

        click("修改昵称 / 内置头像")
        clear_field(0)
        fill(0, "UiAccount")
        click("确认")
        wait_contains("邮箱账号 #" + old_id)
        assert "UiAccount" in text(), "Profile update did not reach account summary"
        checks.append("Android HTTP PATCH profile update")
        screenshot("03-profile")

        click("设备与会话")
        wait_contains("当前设备")
        screenshot("04-devices")
        adb("shell", "input", "keyevent", "4")
        checks.append("device session list rendered")

        click("登录已有账号")
        click("确认")
        fill(0, address)
        fill(1, "incorrect-password")
        click("确认")
        wait_contains("INVALID_CREDENTIALS")
        screenshot("05-login-error")
        checks.append("failed login keeps form interactive and identity intact")
        clear_field(1)
        fill(1, password)
        click("确认")
        wait_contains("邮箱账号 #" + old_id)
        checks.append("existing-account login succeeds after failed attempt")
        screenshot("06-login")

        click("退出并创建新游客")
        click("确认")
        after = wait_contains("游客账号 #")
        new_id = re.search(r"游客账号 #(\d+)", after).group(1)
        assert new_id != old_id
        screenshot("07-logout-guest")
        checks.append("logout revokes session and explicitly creates distinct guest")

        adb("shell", "am", "force-stop", package)
        adb("shell", "am", "start", "-n", package + "/com.cube.memorygames.api.online.AccountActivity")
        wait_contains("游客账号 #" + new_id)
        checks.append("Keystore identity survives process restart")
        screenshot("08-restarted")
        (destination / "result.json").write_text(json.dumps({"ok": True, "checks": checks}, ensure_ascii=False, indent=2), encoding="utf-8")
        print(json.dumps({"ok": True, "checks": checks}, ensure_ascii=False, indent=2))
    finally:
        if smtp is not None:
            smtp.shutdown()
            smtp.server_close()


if __name__ == "__main__":
    main()
