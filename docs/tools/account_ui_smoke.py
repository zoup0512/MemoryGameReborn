"""Black-box GUI check for the account screens on a real device.

The package under test defaults to the isolated .accounttest variant; pass
--package com.memory.brain.training.games for a normal debug install.
Loopback runs use the in-process SMTP fixture; remote runs pass
--mail-command to read verification codes from the deployment mail sink.
It never installs, clears data, or writes credentials to test output.
Running it does register a disposable account and update its profile: use only
an isolated test deployment. Nickname and avatar editors are checked separately
from both the account/security and profile-only summaries.
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
    parser.add_argument("--expect-nickname-approval", action="store_true",
                        help="Expect configured machine moderation to approve the test nickname; "
                             "otherwise verify the default unavailable state and avatar-only save")
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

    def edit_fields(root):
        return [n for n in root.iter("node") if n.get("class") == "android.widget.EditText"]

    def avatar_choices(root):
        return [n for n in root.iter("node")
                if (n.get("content-desc") or "").startswith("选择头像")]

    def assert_split_actions(root):
        labels = {n.get("text") for n in root.iter("node")}
        assert {"修改昵称", "更换内置头像"} <= labels, "Independent profile actions are missing"
        assert "修改昵称 / 内置头像" not in labels, "Combined profile entry is still present"

    def assert_nickname_editor(expected=None):
        root = tree()
        fields = edit_fields(root)
        assert len(fields) == 1, "Nickname editor must contain exactly one name input"
        assert not avatar_choices(root), "Nickname editor contains avatar choices"
        assert not any(n.get("text") in {"修改昵称", "更换内置头像", "修改昵称 / 内置头像", "账户与安全", "我的资料"}
                       for n in root.iter("node")), "Nickname editor is not an independent full-screen page"
        title = visible_node(root, "编辑名字")
        save = visible_node(root, "保存")
        assert title is not None and save is not None, "Nickname page title or toolbar save is missing"
        assert center(title)[1] < center(fields[0])[1], "Nickname title is not above the input"
        assert center(save)[0] > center(title)[0] and center(save)[1] < center(fields[0])[1], \
            "Nickname save must be in the top-right toolbar, not below the form"
        draft = fields[0].get("text", "")
        if expected is not None:
            assert draft == expected, "Nickname editor changed or discarded the draft"
        # Python len counts Unicode code points, not UTF-16 units or UTF-8 bytes.
        counter = r"\s*" + str(len(draft)) + r"\s*/\s*16\s*"
        assert any(re.fullmatch(counter, n.get("text", "")) for n in root.iter("node")), \
            "Nickname Unicode counter does not match the current draft"
        return draft

    def wait_summary(nickname, heading):
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            root = tree()
            labels = [n.get("text", "") for n in root.iter("node")]
            # A dialog can expose its underlying summary; identity text alone is not completion.
            if (not edit_fields(root) and not avatar_choices(root) and "编辑名字" not in labels
                    and heading in "\n".join(labels)
                    and any(nickname in label.splitlines() for label in labels)):
                assert_split_actions(root)
                return
            time.sleep(.5)
        raise AssertionError("Editor did not close and refresh the original account summary")

    def back_from_editor():
        if ime_shown():
            adb("shell", "input", "keyevent", "4")
        adb("shell", "input", "keyevent", "4")
        time.sleep(.35)

    def discard_nickname(original, heading):
        back_from_editor()
        wait_contains("放弃修改")
        click("放弃修改")
        wait_summary(original, heading)
        click("修改昵称")
        assert_nickname_editor(original)
        # An unchanged editor should finish directly, without another discard prompt.
        back_from_editor()
        wait_summary(original, heading)

    def inspect_avatars():
        back_to_top()
        choices = {}
        selected = set()
        for _ in range(6):
            root = tree()
            assert not edit_fields(root), "Avatar-only dialog must not contain an EditText"
            assert visible_node(root, "编辑名字") is None, "Avatar dialog opened the nickname editor"
            for node in avatar_choices(root):
                label = node.get("content-desc")
                choices[label] = node
                if node.get("selected") == "true" or visible_node(node, "✓") is not None:
                    selected.add(label)
            reveal_lower_content()
        assert len(choices) == 12, "Avatar-only dialog must offer exactly 12 presets"
        assert len(selected) == 1, "Avatar dialog must identify exactly one selected preset"
        return choices, selected.pop()

    def change_avatar(nickname, heading, label):
        click("更换内置头像")
        choices, original_avatar = inspect_avatars()
        target = next(choice for choice in choices if choice != original_avatar)
        back_to_top()
        for _ in range(6):
            root = tree()
            node = next((n for n in avatar_choices(root)
                         if n.get("content-desc") == target and n.get("bounds") != "[0,0][0,0]"), None)
            if node is not None:
                tap_node(node)
                break
            reveal_lower_content()
        else:
            raise AssertionError("Cannot reach the alternate avatar preset")
        root = tree()
        assert not edit_fields(root), "Selecting an avatar exposed a nickname field"
        # inspect_avatars leaves the dialog scrolled to its bottom action.
        _, selected = inspect_avatars()
        assert selected == target, "Avatar selection did not update"
        save = visible_node(tree(), "保存")
        assert save is not None and save.get("enabled") == "true", "Avatar bottom save is unavailable"
        assert center(save)[1] > size()[1] // 2, "Avatar save is not at the bottom of the dialog"
        screenshot(label + "-avatar-dialog")
        tap_node(save)
        wait_summary(nickname, heading)
        click("更换内置头像")
        _, persisted = inspect_avatars()
        assert persisted == target, "Avatar-only save did not persist the new preset"
        adb("shell", "input", "keyevent", "4")
        wait_summary(nickname, heading)
        checks.append(label + ": avatar-only dialog has 12 presets, no EditText; save preserves nickname")
        return target

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
        click("刷新账号资料")
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

        heading = "邮箱账号 #" + old_id
        assert_split_actions(tree())
        click("修改昵称")
        original_nickname = assert_nickname_editor()
        # Count the original Unicode name as well as empty and ASCII drafts;
        # adb input text cannot reliably inject supplementary-plane characters.
        clear_field(0)
        if ime_shown():
            adb("shell", "input", "keyevent", "4")
        assert_nickname_editor("")
        fill(0, "UiAccountDraft16X")
        assert_nickname_editor("UiAccountDraft16X")
        screenshot("03-nickname-draft")
        discard_nickname(original_nickname, heading)
        checks.append("nickname-only full-screen editor, top-right save, Unicode counter; Back discards draft")

        click("修改昵称")
        assert_nickname_editor(original_nickname)
        clear_field(0)
        fill(0, "UiAccount")
        assert_nickname_editor("UiAccount")
        click("保存")
        if options.expect_nickname_approval:
            # Do not refresh or press Back: the child must finish and the parent's onResume
            # must expose the cached approved name on the original summary.
            wait_summary("UiAccount", heading)
            current_nickname = "UiAccount"
            checks.append("approved nickname save finishes editor and refreshes original account summary")
        else:
            wait_contains("昵称审核暂不可用")
            assert_nickname_editor("UiAccount")
            save = visible_node(tree(), "保存")
            assert save is not None and save.get("enabled") == "true", "Moderation failure left Save disabled"
            screenshot("03-moderation-unavailable")
            # Prove that a failed save leaves the field editable, not just visually present.
            clear_field(0)
            fill(0, "UiRetry")
            assert_nickname_editor("UiRetry")
            discard_nickname(original_nickname, heading)
            current_nickname = original_nickname
            checks.append("unavailable moderation retains editable nickname draft and re-enables Save")

        change_avatar(current_nickname, heading, "04-security")
        screenshot("04-account-summary")

        # Exercise the same independent actions from the profile-only summary. This
        # launches only the account UI, not an editor extra or a data-changing API.
        adb("shell", "am", "start", "-n", package + "/com.cube.memorygames.api.online.AccountActivity",
            "--ez", "profileOnly", "true")
        wait_summary(current_nickname, "我的资料")
        click("修改昵称")
        assert_nickname_editor(current_nickname)
        if options.expect_nickname_approval:
            # A successful save may have started the nickname cooldown; no second rename.
            back_from_editor()
            wait_summary(current_nickname, "我的资料")
        else:
            clear_field(0)
            fill(0, "UiProfileDraft")
            assert_nickname_editor("UiProfileDraft")
            discard_nickname(current_nickname, "我的资料")
        checks.append("profile summary exposes independent nickname and avatar actions")
        change_avatar(current_nickname, "我的资料", "04-profile")
        screenshot("04-profile-summary")
        adb("shell", "input", "keyevent", "4")
        wait_summary(current_nickname, heading)

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
