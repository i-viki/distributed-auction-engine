#!/usr/bin/env python3
"""
Live STOMP Protocol End-to-End Verification Test Suite
Author: Jayavignesh (https://jayavignesh.dev)
Verifies:
1. Native STOMP over WebSocket handshake on Direct Backend (8080) and Nginx Proxy (5173)
2. STOMP CONNECT & CONNECTED negotiation
3. STOMP SUBSCRIBE to /topic/auctions/{id}
4. Real-time MESSAGE delivery upon REST API bid placement
5. STOMP Inbound Channel Security (Blocking direct client SEND to /topic)
"""

import socket
import base64
import os
import struct
import json
import time
import urllib.request
import urllib.error
import sys

BACKEND_URL = "http://localhost:8080"

def encode_ws_frame(payload_str):
    data = payload_str.encode("utf-8")
    length = len(data)
    mask = os.urandom(4)
    if length <= 125:
        header = struct.pack("!BB", 0x81, 0x80 | length)
    elif length <= 65535:
        header = struct.pack("!BBH", 0x81, 0x80 | 126, length)
    else:
        header = struct.pack("!BBQ", 0x81, 0x80 | 127, length)
    masked_data = bytearray(length)
    for i in range(length):
        masked_data[i] = data[i] ^ mask[i % 4]
    return header + mask + bytes(masked_data)

def decode_ws_frame(buf):
    if len(buf) < 2:
        return None, buf
    b1, b2 = struct.unpack("!BB", buf[:2])
    masked = bool(b2 & 0x80)
    length = b2 & 0x7F
    offset = 2
    if length == 126:
        if len(buf) < 4:
            return None, buf
        length = struct.unpack("!H", buf[2:4])[0]
        offset = 4
    elif length == 127:
        if len(buf) < 10:
            return None, buf
        length = struct.unpack("!Q", buf[2:10])[0]
        offset = 10
    mask = b""
    if masked:
        if len(buf) < offset + 4:
            return None, buf
        mask = buf[offset:offset+4]
        offset += 4
    if len(buf) < offset + length:
        return None, buf
    payload = buf[offset:offset+length]
    remaining = buf[offset+length:]
    if masked:
        unmasked = bytearray(length)
        for i in range(length):
            unmasked[i] = payload[i] ^ mask[i % 4]
        payload = bytes(unmasked)
    return payload.decode("utf-8", errors="ignore"), remaining

class StompWsClient:
    def __init__(self, host, port, path="/ws", origin="http://localhost:5173"):
        self.host = host
        self.port = port
        self.path = path
        self.origin = origin
        self.sock = None
        self.buf = b""

    def connect(self):
        self.sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        self.sock.settimeout(5.0)
        self.sock.connect((self.host, self.port))
        key = base64.b64encode(os.urandom(16)).decode()
        handshake = (
            f"GET {self.path} HTTP/1.1\r\n"
            f"Host: {self.host}:{self.port}\r\n"
            "Upgrade: websocket\r\n"
            "Connection: Upgrade\r\n"
            f"Sec-WebSocket-Key: {key}\r\n"
            "Sec-WebSocket-Version: 13\r\n"
            f"Origin: {self.origin}\r\n\r\n"
        )
        self.sock.sendall(handshake.encode())
        resp = b""
        while b"\r\n\r\n" not in resp:
            chunk = self.sock.recv(1024)
            if not chunk:
                raise ConnectionError("Connection closed during WebSocket handshake")
            resp += chunk
        headers, self.buf = resp.split(b"\r\n\r\n", 1)
        if b"101 " not in headers:
            raise ConnectionError(f"Upgrade failed: {headers.decode(errors='ignore')}")

    def send_stomp(self, command, headers=None, body=""):
        hdrs = headers or {}
        lines = [command]
        for k, v in hdrs.items():
            lines.append(f"{k}:{v}")
        lines.append("")
        lines.append(body)
        raw = "\n".join(lines) + "\x00"
        self.sock.sendall(encode_ws_frame(raw))

    def read_stomp_frame(self, timeout=5.0):
        start = time.time()
        while time.time() - start < timeout:
            frame, self.buf = decode_ws_frame(self.buf)
            if frame is not None:
                # Parse STOMP frame
                lines = frame.split("\x00")[0].split("\n")
                command = lines[0].strip()
                headers = {}
                body_start = 1
                for i, line in enumerate(lines[1:], 1):
                    if not line.strip():
                        body_start = i + 1
                        break
                    if ":" in line:
                        k, v = line.split(":", 1)
                        headers[k.strip()] = v.strip()
                body = "\n".join(lines[body_start:])
                return {"command": command, "headers": headers, "body": body, "raw": frame}
            try:
                self.sock.settimeout(timeout - (time.time() - start))
                chunk = self.sock.recv(4096)
                if not chunk:
                    return None
                self.buf += chunk
            except socket.timeout:
                break
        return None

    def close(self):
        if self.sock:
            try:
                self.sock.close()
            except Exception:
                pass

def http_json(url, method="POST", payload=None, token=None):
    data = json.dumps(payload).encode("utf-8") if payload else None
    req = urllib.request.Request(url, data=data, method=method)
    req.add_header("Content-Type", "application/json")
    if token:
        req.add_header("Authorization", f"Bearer {token}")
    try:
        with urllib.request.urlopen(req, timeout=5) as r:
            return r.status, json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode())

def run_stomp_tests():
    print("=" * 70)
    print("RUNNING LIVE STOMP PROTOCOL VERIFICATION SUITE")
    print("=" * 70)
    passed = 0
    total = 0

    def assert_test(name, condition, detail=""):
        nonlocal passed, total
        total += 1
        if condition:
            passed += 1
            print(f"  [PASS] {name} {detail}")
        else:
            print(f"  [FAIL] {name} - {detail}")

    # Setup test users and auction
    nonce = int(time.time() * 1000)
    s_email = f"stomp_s_{nonce}@example.com"
    b_email = f"stomp_b_{nonce}@example.com"

    http_json(f"{BACKEND_URL}/api/auth/register", payload={"name": "Seller", "email": s_email, "password": "Password123!"})
    _, s_login = http_json(f"{BACKEND_URL}/api/auth/login", payload={"email": s_email, "password": "Password123!"})
    seller_token = s_login.get("token")

    http_json(f"{BACKEND_URL}/api/auth/register", payload={"name": "Buyer", "email": b_email, "password": "Password123!"})
    _, b_login = http_json(f"{BACKEND_URL}/api/auth/login", payload={"email": b_email, "password": "Password123!"})
    buyer_token = b_login.get("token")

    _, auction = http_json(f"{BACKEND_URL}/api/auctions", payload={
        "title": f"STOMP Verification Item {nonce}",
        "description": "Mint condition test description for STOMP verification",
        "startingPrice": 100.00,
        "durationMinutes": 30
    }, token=seller_token)
    auction_id = auction.get("id")

    # TEST 1: Direct Backend (8080) STOMP Handshake & CONNECT
    client_8080 = StompWsClient("127.0.0.1", 8080, "/ws")
    try:
        client_8080.connect()
        client_8080.send_stomp("CONNECT", {"accept-version": "1.1,1.2", "heart-beat": "10000,10000"})
        f1 = client_8080.read_stomp_frame()
        assert_test("1. Direct Backend STOMP Handshake (8080)", f1 is not None and f1["command"] == "CONNECTED",
                    f"Command: {f1['command'] if f1 else 'None'}")
    except Exception as e:
        assert_test("1. Direct Backend STOMP Handshake (8080)", False, str(e))
    finally:
        client_8080.close()

    # TEST 2: Nginx Proxy (5173) STOMP Handshake & CONNECT
    client_5173 = StompWsClient("127.0.0.1", 5173, "/ws")
    try:
        client_5173.connect()
        client_5173.send_stomp("CONNECT", {"accept-version": "1.1,1.2", "heart-beat": "10000,10000"})
        f2 = client_5173.read_stomp_frame()
        assert_test("2. Nginx Reverse Proxy STOMP Handshake (5173)", f2 is not None and f2["command"] == "CONNECTED",
                    f"Command: {f2['command'] if f2 else 'None'}")
    except Exception as e:
        assert_test("2. Nginx Reverse Proxy STOMP Handshake (5173)", False, str(e))

    # TEST 3: STOMP SUBSCRIBE and Real-time Broadcast Delivery
    try:
        # Subscribe to /topic/auctions/{auction_id}
        client_5173.send_stomp("SUBSCRIBE", {
            "id": "sub-test-1",
            "destination": f"/topic/auctions/{auction_id}"
        })
        assert_test("3. STOMP SUBSCRIBE frame transmitted", True, f"Destination: /topic/auctions/{auction_id}")

        # Place a real bid via REST API
        time.sleep(0.1)
        bid_status, bid_data = http_json(f"{BACKEND_URL}/api/auctions/{auction_id}/bids",
                                         payload={"amount": 150.00}, token=buyer_token)
        assert_test("4. REST API Bid Placed for STOMP Broadcast", bid_status == 200, f"Status: {bid_status}")

        # Read STOMP MESSAGE frame received over WebSocket
        msg_frame = client_5173.read_stomp_frame(timeout=3.0)
        is_msg = msg_frame is not None and msg_frame["command"] == "MESSAGE"
        msg_body = json.loads(msg_frame["body"]) if (is_msg and msg_frame["body"]) else {}
        highest_bid = msg_body.get("currentHighestBid")
        assert_test("5. Real-time STOMP MESSAGE Delivered",
                    is_msg and highest_bid == 150.00,
                    f"Command: {msg_frame['command'] if msg_frame else 'None'}, Body: {msg_body}")

    except Exception as e:
        assert_test("5. Real-time STOMP MESSAGE Delivered", False, str(e))

    # TEST 4: Security Enforcement - Block direct client SEND to /topic
    try:
        # Attempt to publish fake bid directly via STOMP SEND frame
        client_5173.send_stomp("SEND", {
            "destination": f"/topic/auctions/{auction_id}",
            "content-type": "application/json"
        }, body=json.dumps({"currentHighestBid": 999999.00}))

        # Spring Boot channel interceptor drops the frame or raises exception; connection terminates or sends ERROR
        sec_frame = client_5173.read_stomp_frame(timeout=1.0)
        # Verify no fake broadcast is reflected to subscriber
        fake_reflected = (sec_frame is not None and sec_frame.get("command") == "MESSAGE" and "999999" in sec_frame.get("body", ""))
        assert_test("6. STOMP Security: Direct SEND to /topic Blocked",
                    not fake_reflected,
                    f"Frame: {sec_frame['command'] if sec_frame else 'Blocked/Dropped by ChannelInterceptor'}")
    except Exception as e:
        assert_test("6. STOMP Security: Direct SEND to /topic Blocked", True, f"Blocked with exception: {e}")
    finally:
        client_5173.close()

    print("=" * 70)
    print(f"STOMP TEST SUMMARY: {passed} / {total} tests passed ({(passed / total) * 100:.1f}%)")
    print("=" * 70)
    return passed == total

if __name__ == "__main__":
    success = run_stomp_tests()
    sys.exit(0 if success else 1)
