"""Loopback-only, real HTTP/WebSocket acceptance for multiplayer friend rooms.

Creates six disposable guest accounts, plays three rounds, checks independent
history/economy, then starts a rematch and verifies withdrawal isolation.
Requires websocket-client. Use a disposable local server with accelerated
round timing (at least 3000 ms) and a low min-level-interval override (50 ms).
Never points at production or prints credentials.
"""
import argparse
from contextlib import ExitStack
import json
import secrets
import threading
import time
import urllib.parse
import urllib.request

import websocket


class Client:
    def __init__(self, base):
        body = json.dumps({"installationId": "party-smoke-" + secrets.token_hex(12),
                           "guestSecret": secrets.token_hex(32),
                           "deviceName": "Local party acceptance"}).encode()
        request = urllib.request.Request(base + "/api/v2/auth/guest", body,
                                         {"Content-Type": "application/json"}, method="POST")
        with urllib.request.urlopen(request, timeout=15) as response:
            account = json.load(response)
        self.token = account["accessToken"]
        self.initial = account["user"]
        self.user_id = self.initial["userId"]
        self.base = base
        self.condition = threading.Condition()
        self.frames = []
        self.failure = None
        self.lobby_id = None
        self.closing = False
        self.ws = websocket.create_connection(base.replace("http://", "ws://", 1) + "/ws", timeout=30)
        self.ws.settimeout(None)
        self.thread = threading.Thread(target=self.read, daemon=True)
        self.thread.start()
        try:
            self.send("auth", {"token": self.token})
            auth = self.wait(lambda frame: frame["t"] == "auth_ok")["d"]
            for field in ("balance", "elo", "wins", "losses", "draws"):
                self.initial[field] = auth[field]
        except BaseException:
            self.close()
            raise

    def read(self):
        try:
            while True:
                raw = self.ws.recv()
                if not raw:
                    raise ConnectionError("socket closed")
                frame = json.loads(raw)
                with self.condition:
                    if frame["t"] == "party_state":
                        self.lobby_id = frame["d"]["lobbyId"]
                    elif frame["t"] == "party_left" and frame["d"].get("lobbyId") == self.lobby_id:
                        self.lobby_id = None
                    self.frames.append(frame)
                    self.condition.notify_all()
        except Exception as error:
            with self.condition:
                self.failure = type(error).__name__
                self.condition.notify_all()

    def send(self, kind, data=None):
        rid = secrets.token_hex(8)
        self.ws.send(json.dumps({"v": 1, "t": kind, "d": data or {}, "rid": rid}))
        return rid

    def wait(self, predicate, timeout=20):
        deadline = time.monotonic() + timeout
        with self.condition:
            while True:
                for index, frame in enumerate(self.frames):
                    if predicate(frame):
                        return self.frames.pop(index)
                remaining = deadline - time.monotonic()
                if remaining <= 0 or self.failure:
                    kinds = [frame["t"] for frame in self.frames[-8:]]
                    raise AssertionError("Missing expected frame; recent types=" + str(kinds)
                                         + "; connection=" + str(self.failure))
                self.condition.wait(remaining)

    def state(self, predicate=lambda state: True, timeout=20):
        return self.wait(lambda frame: frame["t"] == "party_state"
                         and predicate(frame["d"]), timeout)["d"]

    def command(self, kind, data):
        rid = self.send(kind, data)
        return self.wait(lambda frame: frame.get("rid") == rid)

    def close(self):
        if self.closing:
            return
        self.closing = True
        try:
            if self.lobby_id and not self.failure:
                rid = self.send("party_leave", {"lobbyId": self.lobby_id})
                self.wait(lambda frame: frame.get("rid") == rid and frame["t"] == "party_left", timeout=3)
        except Exception as error:
            print("CLEANUP_WARNING " + type(error).__name__, flush=True)
        finally:
            self.ws.close()
            self.thread.join(timeout=3)

    def profile(self):
        request = urllib.request.Request(self.base + "/api/v2/me",
                                         headers={"Authorization": "Bearer " + self.token})
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.load(response)


def play(clients, first_state):
    match_id = first_state["matchId"]
    lobby_id = first_state["lobbyId"]
    for round_index in range(3):
        for index, client in enumerate(clients):
            if not (round_index == 0 and index == 0 and first_state["phase"] == "LOADING"):
                client.state(lambda state: state.get("matchId") == match_id
                             and state["phase"] == "LOADING" and state["roundIndex"] == round_index)
            client.send("party_round_ready", {"lobbyId": lobby_id, "matchId": match_id,
                                              "roundIndex": round_index})
        for client in clients:
            client.state(lambda state: state.get("matchId") == match_id
                         and state["phase"] == "PLAYING" and state["roundIndex"] == round_index)
        # Equal positive scores must share the first two occupied places.
        time.sleep(.2)
        for client in clients[:2]:
            client.send("party_level", {"lobbyId": lobby_id, "matchId": match_id,
                                       "roundIndex": round_index, "level": 1, "success": True})
        clients[0].state(lambda state: state.get("matchId") == match_id
                         and state["roundIndex"] == round_index
                         and sum(player["score"] > 0 for player in state["participants"]) == 2)
        for client in clients:
            client.send("party_complete", {"lobbyId": lobby_id, "matchId": match_id,
                                          "roundIndex": round_index})
        result = clients[0].state(lambda state: state.get("matchId") == match_id
                                  and len(state.get("results", [])) > round_index)
        entries = result["results"][round_index]["entries"]
        assert {entry["playerId"] for entry in entries} == {client.user_id for client in clients}, entries
        positive = [entry for entry in entries if entry["playerId"] in
                    (clients[0].user_id, clients[1].user_id)]
        assert len(positive) == 2
        assert all(entry["score"] > 0 and entry["rank"] == 1 and entry["points2"] == 9
                   for entry in positive), positive
        assert all(entry["points2"] == 0 for entry in entries if entry["score"] == 0)
    end = result if result["phase"] == "FINISHED" else clients[0].state(
        lambda state: state.get("matchId") == match_id and state["phase"] == "FINISHED")
    assert end["status"] == "COMPLETED", end["status"]
    assert {player["playerId"] for player in end["participants"]} == {client.user_id for client in clients}
    for player in end["participants"]:
        if player["playerId"] in (clients[0].user_id, clients[1].user_id):
            assert player["points2"] == 27 and player["rank"] == 1, player
        else:
            assert player["points2"] == 0, player
    return end


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:18091")
    options = parser.parse_args()
    parsed = urllib.parse.urlparse(options.base_url)
    if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("Only a disposable local HTTP server is permitted")
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    base = options.base_url.rstrip("/")
    checks = []
    with ExitStack() as stack:
        clients = []
        for _ in range(6):
            client = Client(base)
            stack.callback(client.close)
            clients.append(client)
        host = clients[0]
        created = host.command("party_create", {"roundCount": 3})
        assert created["t"] == "party_state", created
        lobby = created["d"]
        assert len(lobby["code"]) == 6 and lobby["code"].isdigit()
        lobby_id = lobby["lobbyId"]
        for client in clients[1:]:
            joined = client.command("party_join", {"code": lobby["code"]})
            assert joined["t"] == "party_state", joined
        host.state(lambda state: len(state["members"]) == 6)
        checks.append("six-person persistent lobby with six-digit code")
        denied = clients[1].command("party_config", {"lobbyId": lobby_id, "roundCount": 5})
        assert denied["t"] == "error", denied
        premature = host.command("party_start", {"lobbyId": lobby_id})
        assert premature["t"] == "error", premature
        for client in clients[1:]:
            ready = client.command("party_ready", {"lobbyId": lobby_id, "ready": True})
            assert ready["t"] == "party_state", ready
        checks.append("host-only controls and all-member ready gate")
        started = host.command("party_start", {"lobbyId": lobby_id})
        assert started["t"] == "party_state", started
        end = play(clients, started["d"])
        checks.append("three synchronized rounds, tied ranks, zero-score rule and total points")
        for client in clients:
            rid = client.send("party_history")
            history = client.wait(lambda frame: frame["t"] == "party_history" and frame.get("rid") == rid)
            stored = next(entry for entry in history["d"]["matches"] if entry["matchId"] == end["matchId"])
            result = next(player for player in end["participants"] if player["playerId"] == client.user_id)
            assert stored["status"] == "COMPLETED" and stored["participantCount"] == 6
            assert stored["roundCount"] == 3
            for field in ("rank", "points2", "firstPlaces"):
                assert stored[field] == result[field], (field, stored[field], result[field])
            rid = client.send("history_request")
            duel_history = client.wait(lambda frame: frame["t"] == "history_response" and frame.get("rid") == rid)
            assert not duel_history["d"]["matches"], "party leaked into duel history"
            profile = client.profile()
            for field in ("balance", "elo"):
                assert profile[field] == client.initial[field], field
            # Socket auth contains the duel W/L/D snapshot even if REST omits it.
            if all(field in profile for field in ("wins", "losses", "draws")):
                for field in ("wins", "losses", "draws"):
                    assert profile[field] == client.initial[field], field
        checks.append("independent persisted history; balance/rating/duel statistics unchanged")
        returned = host.command("party_return", {"lobbyId": lobby_id, "matchId": end["matchId"]})
        assert returned["t"] == "party_state" and returned["d"]["phase"] == "LOBBY", returned
        assert returned["d"]["code"] == lobby["code"]
        assert not any(member["ready"] for member in returned["d"]["members"])
        for client in clients[1:]:
            client.command("party_ready", {"lobbyId": lobby_id, "ready": True})
        second = host.command("party_start", {"lobbyId": lobby_id})["d"]
        assert second["matchId"] != end["matchId"]
        stale = host.command("party_leave", {"lobbyId": lobby_id, "matchId": end["matchId"]})
        assert stale["t"] == "error", stale
        for client in clients[1:]:
            left = client.command("party_leave", {"lobbyId": lobby_id, "matchId": second["matchId"]})
            assert left["t"] == "party_left", left
        interrupted = host.state(lambda state: state.get("matchId") == second["matchId"]
                                  and state["phase"] == "FINISHED")
        assert interrupted["status"] == "INTERRUPTED", interrupted["status"]
        checks.append("same-lobby rematch, stale-match isolation and withdrawal interruption")
        for client in clients:
            client.command("party_leave", {"lobbyId": lobby_id})
        third = clients[1].command("party_create", {"roundCount": 5})
        assert third["t"] == "party_state", third
        clients[1].command("party_leave", {"lobbyId": third["d"]["lobbyId"]})
        checks.append("leaving immediately releases account participation")
    for check in checks:
        print("PASS " + check)
    print("PASS multiplayer acceptance: " + str(len(checks)) + " checks")


if __name__ == "__main__":
    main()
