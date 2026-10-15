"""HTTP/SMTP account acceptance test; never prints credentials.

Default (loopback only): start the server with its development HTTP override
and SMTP at 127.0.0.1:2526, then run:
  python docs/tools/account_smoke.py --base-url http://127.0.0.1:18081

Remote mode (own deployment only): with --allow-remote-account-tests the run
creates real throwaway accounts on that server; pair it with --mail-command
that prints the deployment's mail-sink log, e.g.
  --mail-command "ssh root@host tail -n 80 /root/memorygames-pk/mail-sink.log"
"""
import argparse
from contextlib import closing
import email
import json
import re
import secrets
import socketserver
import subprocess
import threading
import time
import urllib.error
import urllib.parse
import urllib.request


class Mailbox:
    def __init__(self):
        self.condition = threading.Condition()
        self.messages = []

    def add(self, recipient, raw):
        message = email.message_from_bytes(raw)
        bodies = []
        for part in message.walk():
            if part.get_content_type() in ("text/plain", "text/html"):
                body = part.get_payload(decode=True)
                if body:
                    bodies.append(body.decode(part.get_content_charset() or "utf-8"))
        with self.condition:
            self.messages.append((recipient, "\n".join(bodies)))
            self.condition.notify_all()

    def code(self, recipient, after):
        deadline = time.monotonic() + 15
        with self.condition:
            while time.monotonic() < deadline:
                for target, body in self.messages[after:]:
                    if recipient.lower() in target.lower():
                        match = re.search(r"(?<!\d)(\d{6})(?!\d)", body)
                        if match:
                            return match.group(1)
                self.condition.wait(max(0, deadline - time.monotonic()))
        raise AssertionError("Verification email was not delivered to the local SMTP fixture")


MAILBOX = Mailbox()


class CommandMailbox:
    """Reads verification codes from a deployment's mail-sink log over SSH."""

    def __init__(self, command):
        self.command = command

    def code(self, recipient, after):
        deadline = time.monotonic() + 20
        while True:
            output = subprocess.run(self.command, shell=True, capture_output=True,
                                    text=True, timeout=30).stdout
            lines = [line for line in output.splitlines()
                     if recipient.lower() in line.lower() and "CODE=" in line]
            if lines:
                return lines[-1].rsplit("CODE=", 1)[1].strip()
            if time.monotonic() >= deadline:
                raise AssertionError("No verification email for the test recipient in the mail-sink log")
            time.sleep(1)


class SmtpHandler(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(20)
        self.wfile.write(b"220 local-test ESMTP\r\n")
        recipients = []
        while True:
            line = self.rfile.readline()
            if not line:
                return
            command = line.decode("ascii", "replace").strip()
            verb = command.split(" ", 1)[0].upper()
            if verb in ("EHLO", "HELO"):
                self.wfile.write(b"250-local-test\r\n250 8BITMIME\r\n")
            elif verb == "MAIL":
                recipients = []
                self.wfile.write(b"250 OK\r\n")
            elif verb == "RCPT":
                recipients.append(command.partition(":")[2])
                self.wfile.write(b"250 OK\r\n")
            elif verb == "DATA":
                self.wfile.write(b"354 End with dot\r\n")
                raw = bytearray()
                while True:
                    part = self.rfile.readline()
                    if not part or part == b".\r\n":
                        break
                    raw.extend(part[1:] if part.startswith(b"..") else part)
                for recipient in recipients:
                    MAILBOX.add(recipient, bytes(raw))
                self.wfile.write(b"250 Queued\r\n")
            elif verb == "QUIT":
                self.wfile.write(b"221 Bye\r\n")
                return
            else:
                self.wfile.write(b"250 OK\r\n")


class SmtpServer(socketserver.ThreadingTCPServer):
    allow_reuse_address = True
    daemon_threads = True


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--base-url", default="http://127.0.0.1:18081")
    parser.add_argument("--smtp-port", type=int, default=2526)
    parser.add_argument("--expect-nickname-approval", action="store_true",
                        help="Expect configured machine moderation to approve the test nickname; "
                             "otherwise verify the default fail-closed behavior")
    parser.add_argument("--allow-remote-account-tests", action="store_true",
                        help="Create throwaway accounts on a non-loopback deployment (own servers only)")
    parser.add_argument("--mail-command",
                        help="Shell command printing the deployment mail-sink log (required for remote runs)")
    options = parser.parse_args()
    parsed = urllib.parse.urlparse(options.base_url)
    loopback = parsed.hostname in ("127.0.0.1", "localhost", "::1")
    if not loopback and not options.allow_remote_account_tests:
        parser.error("This test creates accounts; only loopback is allowed without --allow-remote-account-tests")
    if not loopback and not options.mail_command:
        parser.error("Remote runs need --mail-command to read verification codes from the deployment's mail sink")
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    root = options.base_url.rstrip("/") + "/api/v2"
    checks = []
    import websocket
    ws_url = options.base_url.rstrip("/").replace("http://", "ws://", 1).replace("https://", "wss://", 1) + "/ws"

    def socket_auth(token, expected_user):
        connection = websocket.create_connection(ws_url, timeout=8)
        connection.send(json.dumps({"v": 1, "t": "auth", "d": {"token": token}}))
        response = json.loads(connection.recv())
        assert response["t"] == "auth_ok", "WebSocket authentication failed"
        assert response["d"]["userId"] == expected_user
        assert response["d"]["token"] == token, "WebSocket returned a legacy credential"
        return connection

    mailbox = CommandMailbox(options.mail_command) if options.mail_command else MAILBOX
    smtp = None
    if not options.mail_command:
        smtp = SmtpServer(("127.0.0.1", options.smtp_port), SmtpHandler)
        thread = threading.Thread(target=smtp.serve_forever, daemon=True)
        thread.start()

    def request(method, path, data=None, token=None, expected=200):
        body = None if data is None else json.dumps(data).encode("utf-8")
        headers = {"Content-Type": "application/json"}
        if token:
            headers["Authorization"] = "Bearer " + token
        req = urllib.request.Request(root + path, body, headers, method=method)
        try:
            with urllib.request.urlopen(req, timeout=15) as response:
                status = response.status
                content = response.read()
        except urllib.error.HTTPError as error:
            status = error.code
            content = error.read()
        result = json.loads(content) if content else {}
        allowed = (expected,) if isinstance(expected, int) else expected
        if status not in allowed:
            code = result.get("code", "unknown") if isinstance(result, dict) else "unknown"
            raise AssertionError(f"{method} {path}: expected {allowed}, got {status} ({code})")
        return result

    def passed(name):
        checks.append(name)

    installation = "smoke-" + secrets.token_hex(12)
    secret = secrets.token_hex(32)
    password = "A9!" + secrets.token_urlsafe(18)
    address = "smoke-" + secrets.token_hex(8) + "@example.com"
    try:
        guest_body = {"installationId": installation, "guestSecret": secret,
                      "deviceName": "Local acceptance test"}
        guest = request("POST", "/auth/guest", guest_body, expected=(200, 201))
        initial_nickname = guest["user"]["nickname"]
        assert 2 <= len(initial_nickname) <= 16
        assert not re.fullmatch(r"Player\d*", initial_nickname, re.IGNORECASE)
        user_id = guest["user"]["userId"]
        balance = guest["user"]["balance"]
        assert guest["user"]["accountType"] == "GUEST"
        request("GET", "/me", token=guest["accessToken"])
        with closing(socket_auth(guest["accessToken"], user_id)) as connection:
            connection.send(json.dumps({"v": 1, "t": "top_request", "d": {}}))
            top = json.loads(connection.recv())
            assert top["t"] == "top_response"
            entries = top["d"].get("entries", [])
            assert all("userId" in item for item in entries)
        wrong = dict(guest_body, guestSecret=secrets.token_hex(32))
        request("POST", "/auth/guest", wrong, expected=(401, 403, 409))
        passed("guest creation and secret ownership")

        after = len(MAILBOX.messages)
        request("POST", "/account/email-verification", {"email": address}, guest["accessToken"])
        code = mailbox.code(address, after)
        request("POST", "/auth/register", {"email": address, "password": password,
                "verificationCode": "invalid"}, guest["accessToken"], expected=(400, 401, 403))
        still_guest = request("GET", "/me", token=guest["accessToken"])
        assert still_guest["accountType"] == "GUEST"
        account = request("POST", "/auth/register", {"email": address, "password": password,
                          "verificationCode": code}, guest["accessToken"], expected=(200, 201))
        assert account["user"]["userId"] == user_id
        assert account["user"]["balance"] == balance
        assert account["user"]["accountType"] == "REGISTERED"
        assert account["user"]["emailVerified"] is True
        with closing(socket_auth(account["accessToken"], user_id)) as connection:
            connection.send(json.dumps({"v": 1, "t": "history_request", "d": {}}))
            history = json.loads(connection.recv())
            assert history["t"] == "history_response"
        request("POST", "/auth/guest", guest_body, expected=(401, 403, 409))
        passed("real SMTP verification and in-place guest upgrade")

        login_body = {"email": address, "password": password,
                      "installationId": "login-" + secrets.token_hex(12), "deviceName": "Second test device"}
        request("POST", "/auth/login", dict(login_body, password="wrong-password"), expected=(400, 401, 403))
        logged = request("POST", "/auth/login", login_body)
        assert logged["user"]["userId"] == user_id
        assert logged["user"]["balance"] == balance
        refreshed = request("POST", "/auth/refresh", {"refreshToken": logged["refreshToken"]})
        assert refreshed["refreshToken"] != logged["refreshToken"]
        request("POST", "/auth/refresh", {"refreshToken": logged["refreshToken"]}, expected=(401, 403))
        request("GET", "/me", token=refreshed["accessToken"], expected=(401, 403))
        passed("login and refresh replay revokes token family")

        logged = request("POST", "/auth/login", login_body)
        if options.expect_nickname_approval:
            request("PATCH", "/me/profile", {"nickname": "SmokeUpdated", "avatarValue": "avatar_2"},
                    logged["accessToken"])
            me = request("GET", "/me", token=logged["accessToken"])
            assert me["nickname"] == "SmokeUpdated" and me["avatarValue"] == "avatar_2"
            passed("configured machine moderation approves profile update")
        else:
            unavailable = request("PATCH", "/me/profile",
                                  {"nickname": "SmokeUpdated", "avatarValue": "avatar_2"},
                                  logged["accessToken"], expected=503)
            assert unavailable["code"] == "NICKNAME_MODERATION_UNAVAILABLE"
            me = request("GET", "/me", token=logged["accessToken"])
            assert me["nickname"] == initial_nickname and me["avatarValue"] == "avatar_1"
            passed("unconfigured moderation fails closed without partial profile update")
        request("PATCH", "/me/profile", {"avatarValue": "avatar_3"}, logged["accessToken"])
        me = request("GET", "/me", token=logged["accessToken"])
        assert me["avatarValue"] == "avatar_3"
        passed("avatar-only save does not require nickname moderation")
        devices = request("GET", "/me/devices", token=logged["accessToken"])["devices"]
        assert any(item["current"] for item in devices)
        passed("profile and device list")
        request("POST", "/auth/logout-all", {}, logged["accessToken"])
        request("GET", "/me", token=logged["accessToken"], expected=(401, 403))
        request("GET", "/me", token=account["accessToken"], expected=(401, 403))
        passed("logout-all revokes all device sessions")

        after = len(MAILBOX.messages)
        request("POST", "/auth/password-reset/request", {"email": address})
        reset_code = mailbox.code(address, after)
        new_password = "B8!" + secrets.token_urlsafe(18)
        request("POST", "/auth/password-reset/confirm", {"email": address,
                "verificationCode": reset_code, "newPassword": new_password})
        request("POST", "/auth/login", login_body, expected=(401, 403))
        login_body["password"] = new_password
        logged = request("POST", "/auth/login", login_body)
        request("POST", "/me/delete-request", {"password": new_password}, logged["accessToken"])
        request("GET", "/me", token=logged["accessToken"], expected=(401, 403))
        request("POST", "/auth/login", login_body, expected=(401, 403, 409))
        restored = request("POST", "/auth/delete-cancel", login_body)
        assert restored["user"]["userId"] == user_id
        assert restored["user"]["balance"] == balance
        passed("password reset and pending deletion cancellation")
        connection = socket_auth(restored["accessToken"], user_id)
        request("POST", "/auth/logout", {}, restored["accessToken"])
        request("GET", "/me", token=restored["accessToken"], expected=(401, 403))
        try:
            frame = connection.recv()
            if frame:
                revoked = json.loads(frame)
                assert revoked["t"] in ("session_revoked", "session_replaced", "error")
                try:
                    assert not connection.recv(), "Revoked WebSocket did not close"
                except websocket.WebSocketConnectionClosedException:
                    pass
        except websocket.WebSocketConnectionClosedException:
            pass
        finally:
            connection.close()
        passed("logout revocation closes WebSocket")
        print(json.dumps({"ok": True, "checks": checks}, ensure_ascii=False, indent=2))
    finally:
        if smtp is not None:
            smtp.shutdown()
            smtp.server_close()


if __name__ == "__main__":
    main()
