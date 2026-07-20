#!/usr/bin/env python3
"""Minimal RCON client for The Glitch — stdlib only, localhost only.

Usage:
    mc-cmd.py '<console command>' ['<another command>' ...]

Reads the RCON port/password straight from the live server.properties, so
there is no second place for credentials to drift. RCON is bound to the
box and never opened in the firewall; this is an on-host admin bridge,
not a remote one.
"""
import os
import socket
import struct
import sys

PROPS_PATH = os.environ.get("GLITCH_PROPS", "/opt/theglitch/server/server.properties")

SERVERDATA_AUTH = 3
SERVERDATA_EXECCOMMAND = 2


def read_props(path):
    props = {}
    with open(path, encoding="utf-8") as fh:
        for line in fh:
            line = line.strip()
            if line and not line.startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                props[key] = value
    return props


def send_packet(sock, req_id, ptype, body):
    payload = struct.pack("<ii", req_id, ptype) + body.encode("utf-8") + b"\x00\x00"
    sock.sendall(struct.pack("<i", len(payload)) + payload)


def recv_exact(sock, n):
    data = b""
    while len(data) < n:
        chunk = sock.recv(n - len(data))
        if not chunk:
            raise ConnectionError("RCON connection closed by server")
        data += chunk
    return data


def recv_packet(sock):
    (length,) = struct.unpack("<i", recv_exact(sock, 4))
    data = recv_exact(sock, length)
    req_id, ptype = struct.unpack("<ii", data[:8])
    return req_id, ptype, data[8:-2].decode("utf-8", "replace")


def run_command(sock, command):
    send_packet(sock, 2, SERVERDATA_EXECCOMMAND, command)
    # Vanilla replies in a single packet; drain briefly in case of fragments.
    parts = []
    sock.settimeout(10)
    req_id, _, body = recv_packet(sock)
    if req_id == -1:
        raise ConnectionError("RCON rejected the command (auth lost?)")
    parts.append(body)
    sock.settimeout(0.3)
    try:
        while True:
            _, _, body = recv_packet(sock)
            parts.append(body)
    except (TimeoutError, socket.timeout):
        pass
    finally:
        sock.settimeout(10)
    return "".join(parts).strip()


def main():
    if len(sys.argv) < 2:
        print(__doc__.strip(), file=sys.stderr)
        return 2
    try:
        props = read_props(PROPS_PATH)
    except OSError as exc:
        print(f"cannot read {PROPS_PATH}: {exc}", file=sys.stderr)
        return 1
    if props.get("enable-rcon") != "true" or not props.get("rcon.password"):
        print("RCON is not enabled — run bootstrap.sh and restart the server first", file=sys.stderr)
        return 1
    port = int(props.get("rcon.port", "25575"))
    try:
        with socket.create_connection(("127.0.0.1", port), timeout=10) as sock:
            send_packet(sock, 1, SERVERDATA_AUTH, props["rcon.password"])
            req_id, _, _ = recv_packet(sock)
            if req_id == -1:
                print("RCON authentication failed", file=sys.stderr)
                return 1
            for command in sys.argv[1:]:
                output = run_command(sock, command.lstrip("/"))
                if output:
                    print(output)
    except (OSError, ConnectionError) as exc:
        print(f"RCON error: {exc} (is the server fully started?)", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main())
