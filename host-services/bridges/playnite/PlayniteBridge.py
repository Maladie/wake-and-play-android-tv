#!/usr/bin/env python3
"""Loopback HTTP bridge for Playnite's existing Sunshine connector pipe."""

from __future__ import annotations

import argparse
import ctypes
import json
import os
import re
import threading
import time
import urllib.parse
from collections import deque
from ctypes import wintypes
from http import HTTPStatus
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path
from typing import Any, Callable


GAME_ID_PATTERN = re.compile(
    r"^[0-9A-Fa-f]{8}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{4}-[0-9A-Fa-f]{12}$")
MAX_BODY_BYTES = 16 * 1024


def compact_json(value: Any) -> bytes:
    return json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode("utf-8")


class BridgeState:
    def __init__(self) -> None:
        self.lock = threading.RLock()
        self.events_changed = threading.Condition(self.lock)
        self.connected = False
        self.last_error = "Playnite connector is not connected."
        self.library: dict[str, dict[str, Any]] = {}
        self.library_staging: dict[str, dict[str, Any]] = {}
        self.categories: list[dict[str, Any]] = []
        self.plugins: list[dict[str, Any]] = []
        self.current: dict[str, Any] = {"state": "idle"}
        self.readiness: dict[str, Any] = {
            "ready": False,
            "reason": "window_probe_pending",
            "target_kind": "playnite",
            "stable_samples": 0,
        }
        self.events: deque[dict[str, Any]] = deque(maxlen=200)
        self.next_sequence = 1
        self.command_sender: Callable[[dict[str, Any]], None] | None = None

    @staticmethod
    def game_id(value: Any) -> str:
        result = str(value or "").strip().lower()
        if not GAME_ID_PATTERN.fullmatch(result):
            raise ValueError("Invalid Playnite game ID.")
        return result

    def set_transport(self, connected: bool,
                      sender: Callable[[dict[str, Any]], None] | None,
                      error: str = "") -> None:
        with self.lock:
            changed = self.connected != connected
            self.connected = connected
            self.command_sender = sender
            self.last_error = error[:500]
            if changed:
                self._publish_locked("bridge-connected" if connected else "bridge-disconnected", {})

    def _publish_locked(self, name: str, payload: dict[str, Any]) -> None:
        event = {
            "sequence": self.next_sequence,
            "event": name,
            "timestamp": int(time.time()),
            "payload": payload,
        }
        self.next_sequence += 1
        self.events.append(event)
        self.events_changed.notify_all()

    def handle_message(self, message: dict[str, Any]) -> None:
        kind = str(message.get("type", ""))
        with self.lock:
            if kind == "plugins":
                self.plugins = list(message.get("payload") or [])
                self.library_staging = {}
            elif kind == "categories":
                self.categories = list(message.get("payload") or [])
            elif kind == "games":
                for game in message.get("payload") or []:
                    if not isinstance(game, dict):
                        continue
                    try:
                        game_id = self.game_id(game.get("id"))
                    except ValueError:
                        continue
                    normalized = dict(game)
                    normalized["id"] = game_id
                    self.library_staging[game_id] = normalized
                self.library = dict(self.library_staging)
                self._publish_locked("library-updated", {"count": len(self.library)})
            elif kind == "status" and isinstance(message.get("status"), dict):
                status = dict(message["status"])
                name = str(status.pop("name", ""))
                game_id = ""
                try:
                    game_id = self.game_id(status.get("id"))
                except ValueError:
                    pass
                if game_id:
                    status["id"] = game_id
                if name == "gameStarted":
                    self.current = {"state": "running", **status}
                    self.readiness = {
                        "ready": False,
                        "reason": "waiting_for_game_window",
                        "target_kind": "game",
                        "game_id": game_id,
                        "stable_samples": 0,
                    }
                    self._publish_locked("game-running", dict(self.current))
                elif name == "gameStopped":
                    previous = dict(self.current)
                    self.current = {"state": "idle"}
                    self.readiness = {
                        "ready": False,
                        "reason": "waiting_for_playnite_window",
                        "target_kind": "playnite",
                        "stable_samples": 0,
                    }
                    self._publish_locked("game-stopped", previous)
                else:
                    self._publish_locked(name or "playnite-status", status)

    def send_command(self, command: str, **values: Any) -> dict[str, Any]:
        with self.lock:
            sender = self.command_sender
            if not self.connected or sender is None:
                raise ConnectionError(self.last_error or "Playnite connector is offline.")
            message = {"type": "command", "command": command, **values}
            sender(message)
            return {"accepted": True, "command": command}

    def start_game(self, game_id: Any) -> dict[str, Any]:
        normalized = self.game_id(game_id)
        with self.lock:
            self.readiness = {
                "ready": False,
                "reason": "game_starting",
                "target_kind": "game",
                "game_id": normalized,
                "stable_samples": 0,
            }
            self._publish_locked("game-starting", {"id": normalized})
        return self.send_command("launch", id=normalized)

    def stop_game(self, game_id: Any = "") -> dict[str, Any]:
        normalized = self.game_id(game_id) if game_id else ""
        return self.send_command("stop", id=normalized, force=False)

    def show_fullscreen(self) -> dict[str, Any]:
        with self.lock:
            self.readiness = {
                "ready": False,
                "reason": "waiting_for_playnite_window",
                "target_kind": "playnite",
                "stable_samples": 0,
            }
        return self.send_command("show-fullscreen")

    def library_page(self, cursor: str, limit: int) -> dict[str, Any]:
        offset = int(cursor or "0")
        if offset < 0 or limit < 1 or limit > 100:
            raise ValueError("Invalid library page.")
        with self.lock:
            games = sorted(self.library.values(), key=lambda game: str(game.get("name", "")).casefold())
            page = games[offset:offset + limit]
            next_offset = offset + len(page)
            return {
                "games": page,
                "next_cursor": str(next_offset) if next_offset < len(games) else "",
                "total": len(games),
                "categories": list(self.categories),
                "plugins": list(self.plugins),
            }

    def events_after(self, sequence: int, timeout: float) -> list[dict[str, Any]]:
        deadline = time.monotonic() + timeout
        with self.events_changed:
            while not any(item["sequence"] > sequence for item in self.events):
                remaining = deadline - time.monotonic()
                if remaining <= 0:
                    return []
                self.events_changed.wait(remaining)
            return [item for item in self.events if item["sequence"] > sequence]


class WindowsPipeClient:
    CONTROL_PIPE = r"\\.\pipe\Sunshine.PlayniteExtension"
    GENERIC_READ = 0x80000000
    GENERIC_WRITE = 0x40000000
    OPEN_EXISTING = 3
    ERROR_PIPE_BUSY = 231
    INVALID_HANDLE_VALUE = ctypes.c_void_p(-1).value

    def __init__(self, state: BridgeState) -> None:
        self.state = state
        self.handle: int | None = None
        self.write_lock = threading.Lock()
        self.stopping = threading.Event()

    @staticmethod
    def _kernel32():
        if os.name != "nt":
            raise OSError("Playnite named pipes require Windows.")
        kernel32 = ctypes.WinDLL("kernel32", use_last_error=True)
        kernel32.CreateFileW.argtypes = [wintypes.LPCWSTR, wintypes.DWORD, wintypes.DWORD,
                                         wintypes.LPVOID, wintypes.DWORD, wintypes.DWORD,
                                         wintypes.HANDLE]
        kernel32.CreateFileW.restype = wintypes.HANDLE
        return kernel32

    def _open(self, path: str, timeout_ms: int = 3000) -> int:
        kernel32 = self._kernel32()
        deadline = time.monotonic() + timeout_ms / 1000.0
        while True:
            handle = kernel32.CreateFileW(
                path, self.GENERIC_READ | self.GENERIC_WRITE, 0, None,
                self.OPEN_EXISTING, 0, None)
            value = int(handle) if handle else 0
            if value and value != self.INVALID_HANDLE_VALUE:
                return value
            error = ctypes.get_last_error()
            if error != self.ERROR_PIPE_BUSY or time.monotonic() >= deadline:
                raise OSError(error, f"Unable to open Playnite pipe {path}")
            kernel32.WaitNamedPipeW(path, 250)

    def _read(self, handle: int, size: int) -> bytes:
        kernel32 = self._kernel32()
        buffer = ctypes.create_string_buffer(size)
        read = wintypes.DWORD()
        if not kernel32.ReadFile(handle, buffer, size, ctypes.byref(read), None):
            raise OSError(ctypes.get_last_error(), "Playnite pipe read failed")
        return buffer.raw[:read.value]

    def _write(self, handle: int, value: bytes) -> None:
        kernel32 = self._kernel32()
        written = wintypes.DWORD()
        if not kernel32.WriteFile(handle, value, len(value), ctypes.byref(written), None):
            raise OSError(ctypes.get_last_error(), "Playnite pipe write failed")

    def _close(self, handle: int | None) -> None:
        if handle:
            try:
                self._kernel32().CloseHandle(handle)
            except OSError:
                pass

    def _connect(self) -> int:
        control = self._open(self.CONTROL_PIPE)
        try:
            handshake = b""
            while len(handshake) < 80:
                chunk = self._read(control, 80 - len(handshake))
                if not chunk:
                    raise ConnectionError("Playnite handshake ended early.")
                handshake += chunk
            pipe_name = handshake.decode("utf-16-le", errors="ignore").split("\0", 1)[0].strip()
            if not pipe_name:
                raise ConnectionError("Playnite returned an empty data pipe name.")
            self._write(control, b"\x02")
        finally:
            self._close(control)
        prefix = "\\\\.\\pipe\\"
        path = pipe_name if pipe_name.startswith(prefix) else prefix + pipe_name
        data = self._open(path)
        self.handle = data
        self._send({"role": "launcher", "pid": os.getpid(), "client": "WakePlayBridge"})
        return data

    def _send(self, message: dict[str, Any]) -> None:
        encoded = compact_json(message) + b"\n"
        with self.write_lock:
            if not self.handle:
                raise ConnectionError("Playnite pipe is not connected.")
            self._write(self.handle, encoded)

    def run(self) -> None:
        while not self.stopping.is_set():
            handle = None
            try:
                handle = self._connect()
                self.state.set_transport(True, self._send)
                pending = b""
                while not self.stopping.is_set():
                    chunk = self._read(handle, 8192)
                    if not chunk:
                        raise ConnectionError("Playnite pipe closed.")
                    pending += chunk
                    while b"\n" in pending:
                        raw, pending = pending.split(b"\n", 1)
                        if not raw.strip():
                            continue
                        value = json.loads(raw.decode("utf-8-sig"))
                        if isinstance(value, dict):
                            self.state.handle_message(value)
            except Exception as error:
                self.state.set_transport(False, None, str(error))
                self.stopping.wait(1.0)
            finally:
                self.handle = None
                self._close(handle)


class PlayniteHandler(BaseHTTPRequestHandler):
    server_version = "WakePlayPlayniteBridge/0.1"

    @property
    def state(self) -> BridgeState:
        return self.server.state  # type: ignore[attr-defined]

    def log_message(self, fmt: str, *args: Any) -> None:
        print(f"{self.log_date_time_string()} {fmt % args}", flush=True)

    def send_json(self, status: int, value: Any) -> None:
        body = compact_json(value)
        self.send_response(int(status))
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.send_header("Cache-Control", "no-store")
        self.end_headers()
        self.wfile.write(body)

    def read_json(self) -> dict[str, Any]:
        length = int(self.headers.get("Content-Length", "0"))
        if length < 0 or length > MAX_BODY_BYTES:
            raise ValueError("Request body is too large.")
        value = json.loads(self.rfile.read(length).decode("utf-8")) if length else {}
        if not isinstance(value, dict):
            raise ValueError("JSON object expected.")
        return value

    def do_GET(self) -> None:  # noqa: N802
        try:
            target = urllib.parse.urlsplit(self.path)
            query = urllib.parse.parse_qs(target.query)
            if target.path == "/health":
                with self.state.lock:
                    self.send_json(HTTPStatus.OK, {
                        "ok": True,
                        "connector_connected": self.state.connected,
                        "library_count": len(self.state.library),
                        "error": self.state.last_error,
                    })
            elif target.path == "/library/list":
                cursor = query.get("cursor", ["0"])[0] or "0"
                limit = int(query.get("limit", ["50"])[0])
                self.send_json(HTTPStatus.OK, self.state.library_page(cursor, limit))
            elif target.path == "/game/current":
                with self.state.lock:
                    self.send_json(HTTPStatus.OK, dict(self.state.current))
            elif target.path == "/window/readiness":
                with self.state.lock:
                    self.send_json(HTTPStatus.OK, dict(self.state.readiness))
            elif target.path == "/events":
                after = int(query.get("after", ["0"])[0])
                events = self.state.events_after(after, 20.0)
                self.send_json(HTTPStatus.OK, {"events": events})
            else:
                self.send_json(HTTPStatus.NOT_FOUND, {"error": "Endpoint not found."})
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(HTTPStatus.BAD_REQUEST, {"error": str(error)})
        except Exception as error:
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"error": str(error)})

    def do_POST(self) -> None:  # noqa: N802
        try:
            body = self.read_json()
            path = urllib.parse.urlsplit(self.path).path
            if path == "/game/start":
                result = self.state.start_game(body.get("game_id"))
            elif path == "/game/stop":
                if bool(body.get("force", False)):
                    raise ValueError("Forced game termination is not exposed by this Bridge.")
                result = self.state.stop_game(body.get("game_id", ""))
            elif path == "/playnite/show-fullscreen":
                result = self.state.show_fullscreen()
            else:
                self.send_json(HTTPStatus.NOT_FOUND, {"error": "Endpoint not found."})
                return
            self.send_json(HTTPStatus.ACCEPTED, {"ok": True, **result})
        except ConnectionError as error:
            self.send_json(HTTPStatus.SERVICE_UNAVAILABLE, {"ok": False, "error": str(error)})
        except (ValueError, json.JSONDecodeError) as error:
            self.send_json(HTTPStatus.BAD_REQUEST, {"ok": False, "error": str(error)})
        except Exception as error:
            self.send_json(HTTPStatus.INTERNAL_SERVER_ERROR, {"ok": False, "error": str(error)})


class PlayniteServer(ThreadingHTTPServer):
    daemon_threads = True
    allow_reuse_address = True

    def __init__(self, address: tuple[str, int], state: BridgeState) -> None:
        super().__init__(address, PlayniteHandler)
        self.state = state


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--config", default="config.json")
    args = parser.parse_args()
    config_path = Path(args.config).resolve()
    config = json.loads(config_path.read_text(encoding="utf-8-sig"))
    listen_host = str(config.get("listen_host", "127.0.0.1"))
    if listen_host not in {"127.0.0.1", "localhost"}:
        raise ValueError("Playnite Bridge must remain on loopback.")
    state = BridgeState()
    pipe = WindowsPipeClient(state)
    threading.Thread(target=pipe.run, name="PlaynitePipe", daemon=True).start()
    server = PlayniteServer((listen_host, int(config.get("listen_port", 8780))), state)
    print(f"Playnite Bridge listening on http://{listen_host}:{server.server_port}", flush=True)
    try:
        server.serve_forever(poll_interval=0.25)
    except KeyboardInterrupt:
        pass
    finally:
        pipe.stopping.set()
        server.server_close()


if __name__ == "__main__":
    main()
