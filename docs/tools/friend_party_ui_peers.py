"""Two loopback-only peers for manual Android multiplayer GUI acceptance.

Run against a disposable local server, join the printed room from Android,
press ready, then play. Peers load/respond automatically. No credentials are
printed or written. Uses the shared loopback smoke client and websocket-client.
"""
import argparse
from contextlib import ExitStack
import pathlib
import time
import urllib.parse
import urllib.request

from friend_party_smoke import Client


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--base-url", default="http://127.0.0.1:18091")
    parser.add_argument("--code-file", help="Write only the room code to a local evidence file")
    parser.add_argument("--seconds", type=int, default=600)
    options = parser.parse_args()
    parsed = urllib.parse.urlparse(options.base_url)
    if parsed.scheme != "http" or parsed.hostname not in ("127.0.0.1", "localhost", "::1"):
        parser.error("Only a disposable local HTTP server is permitted")
    urllib.request.install_opener(urllib.request.build_opener(urllib.request.ProxyHandler({})))
    with ExitStack() as stack:
        clients = []
        for _ in range(2):
            client = Client(options.base_url.rstrip("/"))
            stack.callback(client.close)
            clients.append(client)
        created = clients[0].command("party_create", {"roundCount": 3})
        assert created["t"] == "party_state", created["t"]
        lobby = created["d"]
        joined = clients[1].command("party_join", {"code": lobby["code"]})
        assert joined["t"] == "party_state", joined["t"]
        print("ROOM " + lobby["code"], flush=True)
        if options.code_file:
            pathlib.Path(options.code_file).write_text(lobby["code"], encoding="utf-8")
        states = [lobby, joined["d"]]
        loaded = set()
        ready_sent = set()
        last_level_at = {}
        levels = {}
        starting = set()
        last_phase = None
        deadline = time.monotonic() + options.seconds
        while time.monotonic() < deadline:
            for index, client in enumerate(clients):
                with client.condition:
                    if client.failure:
                        raise ConnectionError("Peer connection failed: " + client.failure)
                    frames = list(client.frames)
                    client.frames.clear()
                for frame in frames:
                    if frame["t"] == "party_state":
                        states[index] = frame["d"]
                    elif frame["t"] == "error":
                        print("PEER_ERROR " + str(frame["d"].get("message", "unknown")), flush=True)
                state = states[index]
                phase = state["phase"]
                match_id = state.get("matchId")
                round_index = state["roundIndex"]
                identity = {"lobbyId": state["lobbyId"], "matchId": match_id, "roundIndex": round_index}
                key = (index, match_id, round_index)
                if phase == "LOBBY":
                    self_member = next((member for member in state["members"]
                                        if member["playerId"] == client.user_id), None)
                    ready_key = (index, state["stateVersion"])
                    if client.user_id != state["hostId"] and self_member and not self_member["ready"] and ready_key not in ready_sent:
                        client.send("party_ready", {"lobbyId": state["lobbyId"], "ready": True})
                        ready_sent.add(ready_key)
                    if client.user_id == state["hostId"] and len(state["members"]) >= 3 and all(
                            member["connected"] and (member["playerId"] == state["hostId"] or member["ready"])
                            for member in state["members"]):
                        if state["stateVersion"] not in starting:
                            client.send("party_start", {"lobbyId": state["lobbyId"]})
                            starting.add(state["stateVersion"])
                elif phase == "LOADING" and key not in loaded:
                    client.send("party_round_ready", identity)
                    loaded.add(key)
                elif phase == "PLAYING" and time.monotonic() - last_level_at.get(key, 0) >= 3:
                    level = levels.get(key, 0) + 1
                    if level <= 48:
                        client.send("party_level", dict(identity, level=level, success=True))
                        levels[key] = level
                        last_level_at[key] = time.monotonic()
            phase_key = (states[0].get("matchId"), states[0]["phase"], states[0]["roundIndex"])
            if phase_key != last_phase:
                print("PHASE " + states[0]["phase"] + " ROUND " + str(states[0]["roundIndex"] + 1), flush=True)
                last_phase = phase_key
            time.sleep(.1)
        for client in clients:
            client.close()
        print("PEERS STOPPED", flush=True)


if __name__ == "__main__":
    main()
