#!/usr/bin/env python3
"""Dice Familiar LAN desktop: a stand-in "second phone" for testing LAN play from a Linux PC.

It speaks the app's LAN protocol (v1: one JSON message per line over TCP, `_dicefamiliar._tcp` on mDNS) and can
host a table or join one, exactly like the Android app:

  * Host:   phones join it (Tables nearby, or Join by address); pick a player, type dice, Send; see their rolls.
  * Player: join a phone's table; requests appear, Roll or Decline (or tick "Roll automatically").

The UI is a small local web page (Python standard library only; no pip packages): it opens in your browser at
http://127.0.0.1:8765. Discovery and advertising use the avahi tools when present (`avahi-browse`,
`avahi-publish-service`); without them use "Join by address" / the address shown on the page.

Usage:
  python3 tools/lan_desktop/dicefamiliar_lan.py [--name "Desk"] [--ui-port 8765] [--no-browser]
  python3 tools/lan_desktop/dicefamiliar_lan.py --self-test          # host + player on localhost, then exit
  python3 tools/lan_desktop/dicefamiliar_lan.py --write-fixtures DIR # sample messages for the app's tests
  python3 tools/lan_desktop/dicefamiliar_lan.py --auto-player HOST:PORT                       # headless player, rolls every request
  python3 tools/lan_desktop/dicefamiliar_lan.py --auto-host PORT --requests "2d6+1;10d6:pool"  # headless host, asks the first player

Rolls use the system's secure random generator; notation and limits follow the app (NdM, kh/kl, ±K, pools).
"""
import argparse
import json
import queue
import random
import re
import shutil
import socket
import subprocess
import sys
import threading
import time
import uuid
import webbrowser
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer

PROTOCOL_VERSION = 1
SERVICE_TYPE = "_dicefamiliar._tcp"
DEFAULT_PORT = 47321
MAX_LINE = 64 * 1024
PING_S = 15
TIMEOUT_S = 45
MAX_LOG = 100

RNG = random.SystemRandom()


def now_ms():
    return int(time.time() * 1000)


# --- Dice (mirrors domain/dice: DiceNotationParser, RollMode, DiceRoller) ---------------------------------------

LIMITS = dict(max_input=100, min_count=1, max_count=100, min_sides=2, max_sides=1000, max_terms=20, max_constant=10000, max_total_dice=200)


class NotationError(ValueError):
    pass


def parse_notation(text):
    """`2d6+1d4kh1-3` → list of terms; dice terms are dicts like the app's DiceTerm, constants like ConstantTerm."""
    if len(text) > LIMITS["max_input"]:
        raise NotationError("too long")
    s = re.sub(r"\s+", "", text).lower()
    if not s:
        raise NotationError("empty")
    terms, pos = [], 0

    def number():
        nonlocal pos
        m = re.match(r"\d+", s[pos:])
        if not m:
            return None
        pos += len(m.group())
        return int(m.group())

    def sign():
        nonlocal pos
        if pos < len(s) and s[pos] in "+-":
            pos += 1
            return "PLUS" if s[pos - 1] == "+" else "MINUS"
        return None

    current = sign() or "PLUS"
    while True:
        if pos == len(s):
            raise NotationError("incomplete")
        n = number()
        if pos < len(s) and s[pos] == "d":
            pos += 1
            count = 1 if n is None else n
            if not LIMITS["min_count"] <= count <= LIMITS["max_count"]:
                raise NotationError("count out of range")
            sides = number()
            if sides is None or not LIMITS["min_sides"] <= sides <= LIMITS["max_sides"]:
                raise NotationError("sides out of range")
            keep = None
            if pos < len(s) and s[pos] == "k":
                pos += 1
                if pos >= len(s) or s[pos] not in "hl":
                    raise NotationError("bad keep")
                kind = "HIGHEST" if s[pos] == "h" else "LOWEST"
                pos += 1
                k = number()
                k = 1 if k is None else k
                if not 1 <= k <= count:
                    raise NotationError("keep out of range")
                keep = {"type": kind, "count": k}
            terms.append({"type": "dice", "count": count, "sides": sides, "keep": keep, "sign": current})
        else:
            if n is None:
                raise NotationError("unexpected character")
            if n > LIMITS["max_constant"]:
                raise NotationError("constant out of range")
            terms.append({"type": "const", "value": n, "sign": current})
        if len(terms) > LIMITS["max_terms"]:
            raise NotationError("too many terms")
        if pos == len(s):
            break
        current = sign()
        if current is None:
            raise NotationError("unexpected character")
    if sum(t["count"] for t in terms if t["type"] == "dice") > LIMITS["max_total_dice"]:
        raise NotationError("too many dice")
    return terms


def apply_mode(terms, mode):
    """Advantage/disadvantage turn a single plain `1dN` into `2dNkh1` / `2dNkl1` (RollMode.applyTo)."""
    if mode not in ("ADVANTAGE", "DISADVANTAGE"):
        return terms
    dice = [t for t in terms if t["type"] == "dice"]
    if len(dice) != 1 or dice[0]["count"] != 1 or dice[0]["keep"] is not None or dice[0]["sign"] != "PLUS":
        return terms
    kind = "HIGHEST" if mode == "ADVANTAGE" else "LOWEST"
    return [dict(t, count=2, keep={"type": kind, "count": 1}) if t["type"] == "dice" else t for t in terms]


def to_notation(terms):
    out = []
    for i, t in enumerate(terms):
        if i > 0 or t["sign"] == "MINUS":
            out.append("+" if t["sign"] == "PLUS" else "-")
        if t["type"] == "dice":
            out.append(f"{t['count']}d{t['sides']}")
            if t["keep"]:
                out.append(("kh" if t["keep"]["type"] == "HIGHEST" else "kl") + str(t["keep"]["count"]))
        else:
            out.append(str(t["value"]))
    return "".join(out)


def roll(terms, rng=RNG):
    """A RollOutcome as the app serialises it (DiceRollResult.outcome)."""
    groups, constant = [], 0
    for t in terms:
        factor = 1 if t["sign"] == "PLUS" else -1
        if t["type"] == "const":
            constant += factor * t["value"]
            continue
        values = [rng.randint(1, t["sides"]) for _ in range(t["count"])]
        kept = set(range(len(values)))
        if t["keep"]:
            highest = t["keep"]["type"] == "HIGHEST"
            order = sorted(range(len(values)), key=lambda i: ((-values[i] if highest else values[i]), i))
            kept = set(order[: t["keep"]["count"]])
        dice = [{"value": v, "kept": i in kept} for i, v in enumerate(values)]
        subtotal = sum(d["value"] for d in dice if d["kept"]) * factor
        term = {k: t[k] for k in ("count", "sides", "keep", "sign")}
        groups.append({"term": term, "dice": dice, "subtotal": subtotal})
    total = sum(g["subtotal"] for g in groups) + constant
    return {"expression": {"terms": terms}, "groups": groups, "constant": constant, "total": total}


def outcome_text(outcome, pool=False):
    if outcome is None:
        return ""
    values = [d["value"] for g in outcome["groups"] for d in g["dice"]]
    if pool:
        return " ".join(str(v) for v in sorted(values, reverse=True))
    dice = " ".join("[" + " ".join(str(d["value"]) if d["kept"] else f"({d['value']})" for d in g["dice"]) + "]" for g in outcome["groups"])
    return f"{to_notation(outcome['expression']['terms'])} = {outcome['total']}  {dice}"


# --- Protocol (mirrors domain/lan/LanProtocol) ------------------------------------------------------------------------

def encode(message):
    return json.dumps(message, separators=(",", ":"), ensure_ascii=False)


def decode(line):
    if not line or len(line) > MAX_LINE:
        return None
    try:
        message = json.loads(line)
    except ValueError:
        return None
    return message if isinstance(message, dict) and isinstance(message.get("t"), str) else None


class Connection:
    """One TCP socket carrying lines. `on_line(conn, line)` runs on the reader thread; `on_close(conn)` once."""

    def __init__(self, sock, on_line, on_close):
        self.sock = sock
        self.on_line = on_line
        self.on_close = on_close
        self.lock = threading.Lock()
        self.closed = False
        self.last_seen = time.monotonic()
        sock.setsockopt(socket.IPPROTO_TCP, socket.TCP_NODELAY, 1)
        threading.Thread(target=self._read, daemon=True).start()

    def _read(self):
        buffer = b""
        try:
            while True:
                chunk = self.sock.recv(65536)
                if not chunk:
                    break
                buffer += chunk
                while b"\n" in buffer:
                    raw, buffer = buffer.split(b"\n", 1)
                    self.last_seen = time.monotonic()
                    self.on_line(self, raw.decode("utf-8", "replace").rstrip("\r"))
                if len(buffer) > MAX_LINE:
                    buffer = b""  # an over-long line is dropped, like the app does
        except OSError:
            pass
        finally:
            self.close()

    def send(self, message):
        data = (encode(message) + "\n").encode("utf-8")
        try:
            with self.lock:
                self.sock.sendall(data)
            return True
        except OSError:
            self.close()
            return False

    def close(self):
        with self.lock:
            if self.closed:
                return
            self.closed = True
        try:
            self.sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self.sock.close()
        self.on_close(self)


# --- Session (mirrors data/lan/LanSessionManager) ---------------------------------------------------------------------

class Session:
    """Host a table or play at one. All public methods are thread-safe; `snapshot()` is what the UI shows."""

    def __init__(self, name, player_id=None, publish=True):
        self.me = {"id": player_id or str(uuid.uuid4()), "name": name}
        self.publish = publish
        self.lock = threading.RLock()
        self.role = "NONE"
        self.server = None
        self.port = None
        self.avahi = None
        self.conns = {}  # host: player id -> Connection
        self.players = {}  # host: player id -> {"participant", "connected"}
        self.logs = {}  # player id -> [entry], newest first
        self.host_conn = None
        self.host_info = None
        self.incoming = None
        self.auto_roll = False
        self.events = []
        self.hosts = []
        self.changed = threading.Condition(self.lock)
        threading.Thread(target=self._heartbeat, daemon=True).start()

    # -- helpers --

    def event(self, text):
        with self.lock:
            self.events.insert(0, time.strftime("%H:%M:%S ") + text)
            del self.events[50:]
            self.changed.notify_all()

    def _add_entry(self, player_id, entry):
        log = self.logs.setdefault(player_id, [])
        log.insert(0, entry)
        del log[MAX_LOG:]

    def _entry(self, player_id, request_id):
        return next((e for e in self.logs.get(player_id, []) if e["requestId"] == request_id), None)

    def snapshot(self):
        with self.lock:
            return {
                "role": self.role,
                "me": self.me,
                "port": self.port,
                "addresses": local_addresses(self.port) if self.role == "HOST" else [],
                "host": self.host_info,
                "players": list(self.players.values()),
                "logs": self.logs,
                "incoming": self.incoming,
                "autoRoll": self.auto_roll,
                "hosts": self.hosts,
                "events": self.events,
                "avahi": bool(shutil.which("avahi-browse")),
            }

    # -- host --

    def host(self, port=DEFAULT_PORT):
        with self.lock:
            if self.role != "NONE":
                return
            server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
            server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
            try:
                server.bind(("0.0.0.0", port))
            except OSError:
                server.bind(("0.0.0.0", 0))
            server.listen()
            self.server, self.port, self.role = server, server.getsockname()[1], "HOST"
            self.players, self.logs, self.conns = {}, {}, {}
        threading.Thread(target=self._accept, args=(server,), daemon=True).start()
        if self.publish and shutil.which("avahi-publish-service"):
            name = self.me["name"][:40] or "Dice Familiar"
            self.avahi = subprocess.Popen(
                ["avahi-publish-service", name, SERVICE_TYPE, str(self.port), "name=" + name],
                stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
            )
        self.event(f"Hosting on port {self.port}" + (" (advertised with avahi)" if self.avahi else " (not advertised: use Join by address)"))

    def _accept(self, server):
        while True:
            try:
                sock, address = server.accept()
            except OSError:
                return
            state = {"player": None}
            Connection(sock, lambda c, line, s=state: self._host_line(c, line, s), lambda c, s=state: self._host_closed(c, s))

    def _host_line(self, conn, line, state):
        message = decode(line)
        if message is None:
            return
        t = message["t"]
        with self.lock:
            player = state["player"]
            if player is None:
                if t != "hello":
                    return
                if message.get("version") != PROTOCOL_VERSION:
                    conn.send({"t": "bye", "reason": "VERSION_MISMATCH"})
                    conn.close()
                    return
                player = message["participant"]
                state["player"] = player
                old = self.conns.get(player["id"])
                self.conns[player["id"]] = conn
                self.players[player["id"]] = {"participant": player, "connected": True}
                if old and old is not conn:
                    old.close()
                conn.send({"t": "welcome", "host": self.me, "players": self._connected()})
                self._broadcast_players()
                self.event(f"{player['name']} joined")
                if self.role == "HOST":
                    self.changed.notify_all()
                return
            if t == "ping":
                conn.send({"t": "pong"})
            elif t == "response":
                self._on_response(player, message["response"])
            elif t == "bye":
                conn.close()

    def _host_closed(self, conn, state):
        player = state["player"]
        if not player:
            return
        with self.lock:
            if self.conns.get(player["id"]) is not conn:
                return
            del self.conns[player["id"]]
            if player["id"] in self.players:
                self.players[player["id"]]["connected"] = False
            for e in self.logs.get(player["id"], []):
                if e["state"] == "REQUESTED":
                    e["state"] = "DISCONNECTED"
            self._broadcast_players()
        self.event(f"{player['name']} left")

    def _connected(self):
        return [p["participant"] for p in self.players.values() if p["connected"]]

    def _broadcast_players(self):
        for c in list(self.conns.values()):
            c.send({"t": "players", "players": self._connected()})

    def send_roll(self, player_id, notation, mode="NORMAL", pool=False):
        with self.lock:
            conn = self.conns.get(player_id)
            player = self.players.get(player_id, {}).get("participant")
            if self.role != "HOST" or not conn or not player:
                return "Player not connected"
            try:
                parse_notation(notation)
            except NotationError as e:
                return f"Bad dice: {e}"
            if pool and not re.fullmatch(r"\d*d\d+", notation.strip().lower()):
                return "A pool is one die type, like 10d6"
            for e in self.logs.get(player_id, []):
                if e["state"] == "REQUESTED":
                    conn.send({"t": "cancel", "requestId": e["requestId"]})
                    e["state"] = "CANCELLED"
            request = {
                "id": str(uuid.uuid4()), "type": "dice.roll", "version": 1,
                "payload": {"notation": notation.strip(), "mode": mode, "pool": bool(pool)},
                "label": None, "from": self.me, "to": player, "createdAt": now_ms(),
            }
            self._add_entry(player_id, {"requestId": request["id"], "notation": notation.strip(), "pool": bool(pool), "state": "REQUESTED", "outcome": None, "at": now_ms()})
            sent = conn.send({"t": "request", "request": request})
            self.changed.notify_all()
        self.event(f"Asked {player['name']} to roll {notation}" + (" (pool)" if pool else ""))
        return None if sent else "Send failed"

    def _on_response(self, player, response):
        entry = self._entry(player["id"], response.get("requestId"))
        if not entry or entry["state"] != "REQUESTED":
            return
        status = response.get("status")
        if status == "COMPLETED" and response.get("payload"):
            entry["state"], entry["outcome"] = "ROLLED", response["payload"]["outcome"]
            self.event(f"{player['name']} rolled {outcome_text(entry['outcome'], entry['pool'])}")
        elif status == "DECLINED":
            entry["state"] = "DECLINED"
            self.event(f"{player['name']} declined")
        else:
            entry["state"], entry["error"] = "FAILED", response.get("errorCode")
            self.event(f"{player['name']} could not roll ({response.get('errorCode')})")
        self.changed.notify_all()

    def kick(self, player_id):
        with self.lock:
            conn = self.conns.pop(player_id, None)
            self.players.pop(player_id, None)
            self.logs.pop(player_id, None)
            if conn:
                conn.send({"t": "bye", "reason": "KICKED"})
                conn.close()
            self._broadcast_players()

    # -- player --

    def join(self, address, port=DEFAULT_PORT, name=None):
        with self.lock:
            if self.role != "NONE":
                return "Already in a session"
        try:
            sock = socket.create_connection((address, int(port)), timeout=5)
            sock.settimeout(None)
        except (OSError, ValueError) as e:
            self.event(f"Can't reach {address}:{port} ({e})")
            return f"Can't reach {address}:{port}"
        with self.lock:
            self.host_conn = Connection(sock, self._player_line, self._player_closed)
            self.role = "JOINING"
            self.logs = {}
            self.host_conn.send({"t": "hello", "participant": self.me, "version": PROTOCOL_VERSION})
        self.event(f"Connecting to {name or address}…")
        return None

    def _player_line(self, conn, line):
        message = decode(line)
        if message is None:
            return
        t = message["t"]
        auto = None
        with self.lock:
            if t == "welcome":
                self.role, self.host_info = "PLAYER", message["host"]
                self.event(f"Joined {message['host']['name']}'s table")
            elif t == "ping":
                conn.send({"t": "pong"})
            elif t == "request":
                request = message["request"]
                if request.get("type") != "dice.roll" or (request.get("to") or {}).get("id") != self.me["id"]:
                    return
                payload = request.get("payload") or {}
                self.incoming = request
                self._add_entry(self.me["id"], {"requestId": request["id"], "notation": payload.get("notation", ""), "pool": bool(payload.get("pool")), "state": "REQUESTED", "outcome": None, "at": now_ms()})
                self.event(f"{request['from']['name']} asks you to roll {payload.get('notation')}" + (" (pool)" if payload.get("pool") else ""))
                if self.auto_roll:
                    auto = request["id"]
            elif t == "cancel":
                if self.incoming and self.incoming["id"] == message.get("requestId"):
                    self.incoming = None
                entry = self._entry(self.me["id"], message.get("requestId"))
                if entry and entry["state"] == "REQUESTED":
                    entry["state"] = "CANCELLED"
                self.event("The host cancelled the request")
            elif t == "bye":
                self.event({"KICKED": "The host removed you", "VERSION_MISMATCH": "Different app version"}.get(message.get("reason"), "The host closed the table"))
                conn.close()
            self.changed.notify_all()
        if auto:
            threading.Timer(0.3, self.respond, args=(auto,)).start()

    def _player_closed(self, conn):
        with self.lock:
            if self.host_conn is not conn:
                return
            self._reset()
        self.event("Disconnected from the table")

    def respond(self, request_id):
        with self.lock:
            request = self.incoming
            if not request or request["id"] != request_id or not self.host_conn:
                return "Nothing to roll"
            self.incoming = None
            payload = request.get("payload") or {}
            entry = self._entry(self.me["id"], request_id)
            try:
                terms = apply_mode(parse_notation(str(payload.get("notation", ""))), payload.get("mode", "NORMAL"))
                outcome = roll(terms)
                response = {"requestId": request_id, "status": "COMPLETED", "payload": {"outcome": outcome}, "errorCode": None, "respondedBy": self.me, "respondedAt": now_ms()}
                if entry:
                    entry["state"], entry["outcome"] = "ROLLED", outcome
                self.event("You rolled " + outcome_text(outcome, bool(payload.get("pool"))))
            except NotationError:
                response = {"requestId": request_id, "status": "REJECTED", "payload": None, "errorCode": "dice.invalid_notation", "respondedBy": self.me, "respondedAt": now_ms()}
                if entry:
                    entry["state"] = "FAILED"
            self.host_conn.send({"t": "response", "response": response})
            self.changed.notify_all()
        return None

    def decline(self, request_id):
        with self.lock:
            if not self.incoming or self.incoming["id"] != request_id or not self.host_conn:
                return "Nothing to decline"
            self.incoming = None
            entry = self._entry(self.me["id"], request_id)
            if entry:
                entry["state"] = "DECLINED"
            self.host_conn.send({"t": "response", "response": {"requestId": request_id, "status": "DECLINED", "payload": None, "errorCode": None, "respondedBy": self.me, "respondedAt": now_ms()}})
            self.changed.notify_all()
        return None

    # -- both --

    def leave(self):
        with self.lock:
            if self.role == "HOST":
                for c in list(self.conns.values()):
                    c.send({"t": "bye", "reason": "HOST_STOPPED"})
                    c.close()
            elif self.host_conn:
                self.host_conn.send({"t": "bye", "reason": "LEFT"})
                conn, self.host_conn = self.host_conn, None
                conn.close()
            self._reset()
        self.event("Session ended")

    def _reset(self):
        if self.server:
            try:
                self.server.close()
            except OSError:
                pass
        if self.avahi:
            self.avahi.terminate()
        self.server = self.avahi = self.host_conn = self.host_info = self.incoming = self.port = None
        self.conns, self.players, self.role = {}, {}, "NONE"
        self.changed.notify_all()

    def _heartbeat(self):
        while True:
            time.sleep(PING_S)
            with self.lock:
                conns = list(self.conns.values()) + ([self.host_conn] if self.host_conn else [])
            for c in conns:
                if time.monotonic() - c.last_seen > TIMEOUT_S:
                    c.close()
                else:
                    c.send({"t": "ping"})

    def discover(self):
        """Tables advertised on the network (needs avahi-browse)."""
        found = discover_hosts()
        with self.lock:
            self.hosts = found
        return found


#: Container and VM bridges: not reachable from phones, so neither listed nor searched.
VIRTUAL_IFACES = ("docker", "br-", "virbr", "veth")


def discover_hosts(timeout=4):
    if not shutil.which("avahi-browse"):
        return []
    try:
        out = subprocess.run(["avahi-browse", "-rpt", SERVICE_TYPE], capture_output=True, text=True, timeout=timeout).stdout
    except (subprocess.TimeoutExpired, OSError):
        return []
    hosts = {}
    for line in out.splitlines():
        parts = line.split(";")
        if len(parts) < 10 or parts[0] != "=" or parts[2] != "IPv4" or parts[1].startswith(VIRTUAL_IFACES):
            continue
        txt = re.findall(r'"name=([^"]*)"', parts[9])
        name = (txt[0] if txt else parts[3]).replace("\\032", " ")
        hosts[(parts[7], parts[8])] = {"name": name, "address": parts[7], "port": int(parts[8])}
    return sorted(hosts.values(), key=lambda h: h["name"].lower())


def local_addresses(port):
    """IPv4 addresses others can type (from `ip`), VPN-looking interfaces marked."""
    result = []
    try:
        out = subprocess.run(["ip", "-4", "-o", "addr", "show"], capture_output=True, text=True, timeout=2).stdout
        for line in out.splitlines():
            parts = line.split()
            iface, address = parts[1], parts[3].split("/")[0]
            if iface == "lo" or iface.startswith(VIRTUAL_IFACES):
                continue
            vpn = iface.startswith(("tun", "wg", "ppp", "tailscale", "zt", "ipsec"))
            result.append({"address": f"{address}:{port}", "vpn": vpn, "iface": iface})
    except (OSError, IndexError, subprocess.TimeoutExpired):
        pass
    return sorted(result, key=lambda a: a["vpn"])


# --- Web UI ----------------------------------------------------------------------------------------------------------

PAGE = r"""<!doctype html><html><head><meta charset="utf-8"><title>Dice Familiar LAN desktop</title>
<style>
body{font:15px system-ui,sans-serif;margin:0;background:#f4f1ea;color:#222}
header{background:#3b5b8c;color:#fff;padding:10px 16px;display:flex;gap:12px;align-items:center}
header h1{font-size:18px;margin:0;flex:1}
main{display:grid;grid-template-columns:1fr 1fr;gap:16px;padding:16px}
section{background:#fff;border-radius:12px;padding:12px 16px;box-shadow:0 1px 3px #0002}
h2{font-size:15px;margin:4px 0 10px;color:#3b5b8c}
button{font:inherit;padding:6px 12px;border-radius:8px;border:1px solid #3b5b8c;background:#3b5b8c;color:#fff;cursor:pointer}
button.ghost{background:#fff;color:#3b5b8c}
button:disabled{opacity:.4;cursor:default}
input,select{font:inherit;padding:5px 8px;border-radius:6px;border:1px solid #aaa}
.row{display:flex;gap:8px;align-items:center;margin:6px 0;flex-wrap:wrap}
.item{padding:6px 8px;border-radius:8px;cursor:pointer}
.item:hover{background:#eef}
.item.sel{background:#dbe4f5}
.dot{display:inline-block;width:10px;height:10px;border-radius:50%;margin-right:6px;background:#bbb}
.dot.on{background:#2e8b57}
.mono{font-family:ui-monospace,monospace}
.muted{color:#777;font-size:13px}
.req{background:#f6e7c8;border-radius:10px;padding:10px;margin:8px 0}
ul{margin:0;padding-left:18px} li{margin:2px 0}
</style></head><body>
<header><h1>🎲 Dice Familiar · LAN desktop</h1><span id="who"></span></header>
<main>
<section id="left"></section>
<section><h2>Activity</h2><div id="events" class="muted"></div></section>
</main>
<script>
let S=null, sel=null;
const $=id=>document.getElementById(id), esc=s=>String(s??'').replace(/[&<>"]/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;'}[c]));
async function api(path,body){const r=await fetch('/api/'+path,{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify(body||{})});const j=await r.json();if(j.error)alert(j.error);refresh();return j}
function summary(e){const d=e.pool?e.notation+' (pool)':e.notation;if(e.state==='ROLLED'){const o=e.outcome;const v=o.groups.flatMap(g=>g.dice.map(x=>x.kept?x.value:'('+x.value+')'));return e.pool?d+': '+v.map(Number).filter(x=>!isNaN(x)).sort((a,b)=>b-a).join(' '):d+' = '+o.total+'  ['+v.join(' ')+']'}return d+' · '+e.state.toLowerCase()}
function logList(entries){return entries&&entries.length?'<ul>'+entries.map(e=>'<li class="mono">'+esc(summary(e))+' <span class="muted">'+new Date(e.at).toLocaleTimeString()+'</span></li>').join('')+'</ul>':'<p class="muted">No rolls yet.</p>'}
function render(){
 const s=S; $('who').textContent=s.me.name+(s.role!=='NONE'?' · '+s.role.toLowerCase():'');
 $('events').innerHTML=s.events.map(esc).join('<br>');
 let h='';
 if(s.role==='NONE'||s.role==='JOINING'){
  h+='<h2>Your name</h2><div class="row"><input id="name" value="'+esc(s.me.name)+'"><button class="ghost" onclick="api(\'rename\',{name:$(\'name\').value})">Save</button></div>';
  h+='<h2>Host</h2><div class="row"><button onclick="api(\'host\')">Host a table</button><span class="muted">Phones join it from LAN → Tables nearby, or Join by address.</span></div>';
  h+='<h2>Tables nearby</h2><div class="row"><button class="ghost" onclick="api(\'discover\')">Search</button>'+(s.avahi?'':'<span class="muted">avahi-browse not found: use the address</span>')+'</div>';
  h+=s.hosts.length?s.hosts.map(x=>'<div class="item" onclick="api(\'join\',{address:\''+x.address+'\',port:'+x.port+',name:\''+esc(x.name)+'\'})">📶 '+esc(x.name)+' <span class="muted mono">'+x.address+':'+x.port+'</span></div>').join(''):'<p class="muted">None found yet (press Search).</p>';
  h+='<h2>Join by address</h2><div class="row"><input id="addr" placeholder="192.168.1.20 or 100.101.2.3:47321" size="30"><button onclick="joinAddr()">Join</button></div>';
 } else if(s.role==='HOST'){
  h+='<h2>Your table: '+esc(s.me.name)+'</h2><div class="row"><button class="ghost" onclick="api(\'leave\')">Stop</button></div>';
  h+='<p class="muted">Addresses for Join by address:<br>'+s.addresses.map(a=>'<span class="mono">'+a.address+'</span> '+(a.vpn?'VPN':esc(a.iface))).join('<br>')+'</p>';
  h+='<h2>Players</h2>'+(s.players.length?s.players.map(p=>'<div class="item'+(sel===p.participant.id?' sel':'')+'" onclick="sel=\''+p.participant.id+'\';render()"><span class="dot'+(p.connected?' on':'')+'"></span>'+esc(p.participant.name)+'</div>').join(''):'<p class="muted">Waiting for players…</p>');
  const p=s.players.find(x=>x.participant.id===sel);
  if(p){h+='<h2>Ask '+esc(p.participant.name)+' to roll</h2><div class="row"><input id="dice" value="'+esc(window.lastDice||'3d6+2')+'" size="12"><select id="mode"><option>NORMAL</option><option>ADVANTAGE</option><option>DISADVANTAGE</option></select><label><input type="checkbox" id="pool"> pool</label><button onclick="send()">Send</button><button class="ghost" onclick="if(confirm(\'Remove '+esc(p.participant.name)+'?\'))api(\'kick\',{player:sel})">Remove</button></div>';
   h+='<h2>'+esc(p.participant.name)+'\'s rolls</h2>'+logList(s.logs[p.participant.id]);}
 } else {
  h+='<h2>At '+esc(s.host?s.host.name:'')+'\'s table</h2><div class="row"><button class="ghost" onclick="api(\'leave\')">Leave</button><label><input type="checkbox" '+(s.autoRoll?'checked':'')+' onchange="api(\'auto\',{on:this.checked})"> Roll requests automatically</label></div>';
  if(s.incoming){const pl=s.incoming.payload||{};h+='<div class="req"><b>'+esc(s.incoming.from.name)+'</b> asks you to roll <b class="mono">'+esc(pl.notation)+'</b>'+(pl.pool?' (pool)':'')+(pl.mode&&pl.mode!=='NORMAL'?' · '+pl.mode.toLowerCase():'')+'<div class="row"><button onclick="api(\'roll\',{id:\''+s.incoming.id+'\'})">Roll</button><button class="ghost" onclick="api(\'decline\',{id:\''+s.incoming.id+'\'})">Decline</button></div></div>';}
  h+='<h2>My rolls</h2>'+logList(s.logs[s.me.id]);
 }
 const focus=document.activeElement&&document.activeElement.id, val=focus&&$(focus)&&$(focus).value;
 $('left').innerHTML=h; if(focus&&$(focus)){$(focus).focus();if(val!==undefined&&$(focus).value!==undefined)$(focus).value=val}
}
function joinAddr(){const t=$('addr').value.trim();const m=t.match(/^\[?([^\]]+?)\]?(?::(\d+))?$/);if(!m)return alert('Type an address like 192.168.1.20');api('join',{address:m[1],port:m[2]?+m[2]:47321})}
function send(){window.lastDice=$('dice').value;api('send',{player:sel,notation:$('dice').value,mode:$('mode').value,pool:$('pool').checked})}
async function refresh(){try{S=await (await fetch('/api/state')).json();if(!['INPUT','SELECT'].includes((document.activeElement||{}).tagName))render();else{$('events').innerHTML=S.events.map(esc).join('<br>')}}catch(e){}}
setInterval(refresh,800);refresh();
</script></body></html>"""


def make_handler(session):
    class Handler(BaseHTTPRequestHandler):
        def log_message(self, *args):
            pass

        def _json(self, data, code=200):
            body = json.dumps(data).encode()
            self.send_response(code)
            self.send_header("Content-Type", "application/json")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_GET(self):
            if self.path == "/api/state":
                return self._json(session.snapshot())
            body = PAGE.encode()
            self.send_response(200)
            self.send_header("Content-Type", "text/html; charset=utf-8")
            self.send_header("Content-Length", str(len(body)))
            self.end_headers()
            self.wfile.write(body)

        def do_POST(self):
            # The page is served to this machine only (127.0.0.1); reject other origins anyway.
            if self.headers.get("Origin") not in (None, f"http://{self.headers.get('Host')}"):
                return self._json({"error": "forbidden"}, 403)
            length = int(self.headers.get("Content-Length") or 0)
            body = json.loads(self.rfile.read(length) or b"{}")
            action = self.path.removeprefix("/api/")
            error = None
            if action == "host":
                session.host()
            elif action == "discover":
                session.discover()
            elif action == "join":
                error = session.join(body.get("address", ""), body.get("port", DEFAULT_PORT), body.get("name"))
            elif action == "send":
                error = session.send_roll(body.get("player"), body.get("notation", ""), body.get("mode", "NORMAL"), body.get("pool", False))
            elif action == "kick":
                session.kick(body.get("player"))
            elif action == "roll":
                error = session.respond(body.get("id"))
            elif action == "decline":
                error = session.decline(body.get("id"))
            elif action == "auto":
                session.auto_roll = bool(body.get("on"))
            elif action == "leave":
                session.leave()
            elif action == "rename":
                if session.role == "NONE" and body.get("name", "").strip():
                    session.me["name"] = body["name"].strip()[:40]
            else:
                return self._json({"error": "unknown action"}, 404)
            return self._json({"error": error})

    return Handler


# --- Self-test and fixtures -------------------------------------------------------------------------------------------

def wait_for(predicate, timeout=5.0):
    end = time.monotonic() + timeout
    while time.monotonic() < end:
        if predicate():
            return True
        time.sleep(0.02)
    raise AssertionError("timed out")


def self_test():
    """Host and player in one process over localhost: join, sum and pool requests, advantage, decline, leave."""
    terms = parse_notation("2d20kh1+1d4-3")
    assert to_notation(terms) == "2d20kh1+1d4-3", to_notation(terms)
    assert to_notation(apply_mode(parse_notation("1d20+5"), "ADVANTAGE")) == "2d20kh1+5"
    assert to_notation(apply_mode(parse_notation("2d6"), "ADVANTAGE")) == "2d6"
    for bad in ["", "d", "2d1", "101d6", "1d6k", "4d6kh5", "3d6+", "abc"]:
        try:
            parse_notation(bad)
            raise AssertionError("accepted " + bad)
        except NotationError:
            pass
    fixed = random.Random(1)
    outcome = roll(parse_notation("4d6kh3+2"), fixed)
    kept = [d for d in outcome["groups"][0]["dice"] if d["kept"]]
    assert len(kept) == 3 and outcome["total"] == sum(d["value"] for d in kept) + 2

    host = Session("Desk host", publish=False)
    player = Session("Desk player", publish=False)
    host.host(port=0)
    assert player.join("127.0.0.1", host.port) is None
    wait_for(lambda: player.role == "PLAYER")
    pid = player.me["id"]
    for notation, mode, pool in [("2d6+3", "NORMAL", False), ("10d6", "NORMAL", True), ("1d20", "ADVANTAGE", False)]:
        assert host.send_roll(pid, notation, mode, pool) is None
        wait_for(lambda: player.incoming is not None)
        player.respond(player.incoming["id"])
        wait_for(lambda: host.logs[pid][0]["state"] == "ROLLED")
        rolled = host.logs[pid][0]["outcome"]
        if pool:
            assert len(rolled["groups"][0]["dice"]) == 10
        if mode == "ADVANTAGE":
            assert to_notation(rolled["expression"]["terms"]) == "2d20kh1"
    host.send_roll(pid, "1d8")
    wait_for(lambda: player.incoming is not None)
    player.decline(player.incoming["id"])
    wait_for(lambda: host.logs[pid][0]["state"] == "DECLINED")
    player.leave()
    wait_for(lambda: not host.players[pid]["connected"])
    host.leave()
    print("self-test passed")


def write_fixtures(directory):
    """Messages as this tool sends them, for the app's LanDesktopCompatTest (fixed values, stable output)."""
    import os
    os.makedirs(directory, exist_ok=True)
    me = {"id": "desk-1", "name": "Desk"}
    host = {"id": "phone-1", "name": "Phone"}
    terms = parse_notation("2d6kh1+1d4-3")
    outcome = roll(terms, random.Random(7))
    pool_outcome = roll(parse_notation("6d6"), random.Random(8))
    request = {"id": "req-1", "type": "dice.roll", "version": 1, "payload": {"notation": "3d6+2", "mode": "NORMAL", "pool": False}, "label": None, "from": me, "to": host, "createdAt": 1000}
    lines = {
        "hello": {"t": "hello", "participant": me, "version": PROTOCOL_VERSION},
        "welcome": {"t": "welcome", "host": me, "players": [host]},
        "players": {"t": "players", "players": [host]},
        "ping": {"t": "ping"},
        "pong": {"t": "pong"},
        "request": {"t": "request", "request": request},
        "cancel": {"t": "cancel", "requestId": "req-1"},
        "bye": {"t": "bye", "reason": "LEFT"},
        "response_rolled": {"t": "response", "response": {"requestId": "req-2", "status": "COMPLETED", "payload": {"outcome": outcome}, "errorCode": None, "respondedBy": me, "respondedAt": 2000}},
        "response_pool": {"t": "response", "response": {"requestId": "req-3", "status": "COMPLETED", "payload": {"outcome": pool_outcome}, "errorCode": None, "respondedBy": me, "respondedAt": 3000}},
        "response_declined": {"t": "response", "response": {"requestId": "req-4", "status": "DECLINED", "payload": None, "errorCode": None, "respondedBy": me, "respondedAt": 4000}},
    }
    with open(os.path.join(directory, "desktop-messages.jsonl"), "w", encoding="utf-8", newline="\n") as f:
        for key, message in lines.items():
            f.write(key + "\t" + encode(message) + "\n")
    expected = {"response_rolled_total": outcome["total"], "response_pool_values": [d["value"] for d in pool_outcome["groups"][0]["dice"]]}
    with open(os.path.join(directory, "desktop-expected.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(expected, f, indent=2)
        f.write("\n")
    print("wrote", directory)


def auto_player(target, name, seconds=30):
    """Joins HOST:PORT, rolls every request, prints one JSON line per roll; exits when the table closes."""
    address, _, port = target.rpartition(":")
    session = Session(name, publish=False)
    session.auto_roll = True
    if session.join(address or target, int(port or DEFAULT_PORT)) is not None:
        return 1
    reported = set()
    end = time.monotonic() + seconds
    while time.monotonic() < end:
        with session.lock:
            for e in session.logs.get(session.me["id"], []):
                if e["state"] == "ROLLED" and e["requestId"] not in reported:
                    reported.add(e["requestId"])
                    print(json.dumps({"notation": e["notation"], "pool": e["pool"], "total": e["outcome"]["total"]}), flush=True)
            if session.role == "NONE" and time.monotonic() > end - seconds + 2:
                return 0
        time.sleep(0.05)
    session.leave()
    return 0


def auto_host(port, requests, seconds=30):
    """Hosts on PORT, waits for one player, asks each `notation[:pool][:MODE]`, prints the outcomes as JSON, stops."""
    session = Session("Desk host", publish=False)
    session.host(port=port)
    print(json.dumps({"port": session.port}), flush=True)
    wait_for(lambda: any(p["connected"] for p in session.players.values()), seconds)
    player = next(iter(session.players))
    for spec in [r for r in requests.split(";") if r]:
        parts = spec.split(":")
        notation, pool = parts[0], "pool" in parts[1:]
        mode = next((p for p in parts[1:] if p in ("ADVANTAGE", "DISADVANTAGE")), "NORMAL")
        error = session.send_roll(player, notation, mode, pool)
        if error:
            print(json.dumps({"error": error}), flush=True)
            return 1
        wait_for(lambda: session.logs[player][0]["state"] != "REQUESTED", seconds)
        e = session.logs[player][0]
        o = e["outcome"]
        print(json.dumps({"notation": notation, "state": e["state"], "rolled": to_notation(o["expression"]["terms"]) if o else None,
                          "values": [d["value"] for g in o["groups"] for d in g["dice"]] if o else None, "total": o["total"] if o else None}), flush=True)
    session.leave()
    return 0


def main():
    parser = argparse.ArgumentParser(description=__doc__.split("\n\n")[0])
    parser.add_argument("--name", default=socket.gethostname()[:40] or "Desktop")
    parser.add_argument("--ui-port", type=int, default=8765)
    parser.add_argument("--no-browser", action="store_true")
    parser.add_argument("--self-test", action="store_true")
    parser.add_argument("--write-fixtures", metavar="DIR")
    parser.add_argument("--auto-player", metavar="HOST:PORT")
    parser.add_argument("--auto-host", metavar="PORT", type=int)
    parser.add_argument("--requests", default="2d6+1;10d6:pool;1d20:ADVANTAGE")
    args = parser.parse_args()
    if args.auto_player:
        return auto_player(args.auto_player, args.name)
    if args.auto_host is not None:
        return auto_host(args.auto_host, args.requests)
    if args.self_test:
        return self_test()
    if args.write_fixtures:
        return write_fixtures(args.write_fixtures)
    session = Session(args.name)
    server = ThreadingHTTPServer(("127.0.0.1", args.ui_port), make_handler(session))
    url = f"http://127.0.0.1:{server.server_address[1]}/"
    print(f"Dice Familiar LAN desktop: {url}  (Ctrl+C to quit)")
    if not args.no_browser:
        threading.Timer(0.5, webbrowser.open, args=(url,)).start()
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        session.leave()


if __name__ == "__main__":
    sys.exit(main())
