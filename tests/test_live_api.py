#!/usr/bin/env python3
"""
Comprehensive Live API Verification & Security Test Suite
Author: Jayavignesh (https://jayavignesh.dev)
"""

import urllib.request
import urllib.error
import json
import time
import sys

BACKEND_URL = "http://localhost:8080"
FRONTEND_URL = "http://localhost:5173"

def request(url, method="GET", payload=None, headers=None):
    hdrs = headers or {}
    data = None
    if payload is not None:
        data = json.dumps(payload).encode("utf-8")
        hdrs["Content-Type"] = "application/json"
    req = urllib.request.Request(url, data=data, headers=hdrs, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            body = resp.read().decode("utf-8")
            try:
                parsed = json.loads(body)
            except Exception:
                parsed = body
            return resp.status, resp.headers, parsed
    except urllib.error.HTTPError as e:
        body = e.read().decode("utf-8")
        try:
            parsed = json.loads(body)
        except Exception:
            parsed = body
        return e.code, e.headers, parsed
    except Exception as e:
        return 0, {}, {"error": str(e)}

def run_tests():
    print("=" * 70)
    print("RUNNING LIVE API VERIFICATION & SECURITY SUITE")
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

    nonce = int(time.time() * 1000)

    # 1. Frontend Check (Nginx on 5173)
    status, _, body = request(FRONTEND_URL)
    assert_test("1. Frontend Nginx Serving HTML", status == 200 and "<html" in str(body).lower())

    # 2. Frontend Nginx Proxy to Backend (/api/auctions)
    status, _, body = request(f"{FRONTEND_URL}/api/auctions")
    assert_test("2. Frontend Nginx Proxy /api/auctions", status == 200 and isinstance(body, list))

    # 3. Password Policy Validation (Minimum 8 characters)
    status, _, body = request(f"{BACKEND_URL}/api/auth/register", method="POST", payload={
        "name": "Weak User",
        "email": f"weak_{nonce}@example.com",
        "password": "short"
    })
    assert_test("3. OWASP Password Strength (< 8 chars rejected)", 
                status == 400 and "at least 8 characters" in str(body), f"Status: {status}, Body: {body}")

    # 4. Legitimate User Registration
    seller_email = f"seller_{nonce}@example.com"
    status, _, seller_reg = request(f"{BACKEND_URL}/api/auth/register", method="POST", payload={
        "name": "Seller Test",
        "email": seller_email,
        "password": "StrongPassword123!"
    })
    assert_test("4. Seller Registration (201 Created)", status == 201 and "id" in seller_reg, f"Status: {status}")

    # 5. Seller Login to obtain JWT
    status, _, seller_auth = request(f"{BACKEND_URL}/api/auth/login", method="POST", payload={
        "email": seller_email,
        "password": "StrongPassword123!"
    })
    seller_token = seller_auth.get("token")
    assert_test("5. Seller Authentication Login", status == 200 and seller_token is not None)

    # 6. Buyer Registration & Login
    buyer_email = f"buyer_{nonce}@example.com"
    status, _, buyer_reg = request(f"{BACKEND_URL}/api/auth/register", method="POST", payload={
        "name": "Buyer Test",
        "email": buyer_email,
        "password": "StrongPassword123!"
    })
    assert_test("6. Buyer Registration (201 Created)", status == 201 and "id" in buyer_reg)

    status, _, buyer_auth = request(f"{BACKEND_URL}/api/auth/login", method="POST", payload={
        "email": buyer_email,
        "password": "StrongPassword123!"
    })
    buyer_token = buyer_auth.get("token")
    assert_test("7. Buyer Authentication Login", status == 200 and buyer_token is not None)

    # 7. Auction Creation with durationMinutes
    status, _, auction = request(f"{BACKEND_URL}/api/auctions", method="POST", headers={
        "Authorization": f"Bearer {seller_token}"
    }, payload={
        "title": f"Rolex Submariner {nonce} Listing",
        "description": "Mint condition with original box and papers",
        "startingPrice": 5000.00,
        "durationMinutes": 60
    })
    auction_id = auction.get("id") if (status in [200, 201] and isinstance(auction, dict)) else None
    assert_test("8. Server-Authoritative Auction Creation", 
                status == 201 and auction_id is not None and auction.get("status") == "LIVE",
                f"Auction ID: {auction_id}, Status: {status}")

    # 8. Anti-Shill Bidding: Seller bidding on own auction MUST fail
    status, _, err = request(f"{BACKEND_URL}/api/auctions/{auction_id}/bids", method="POST", headers={
        "Authorization": f"Bearer {seller_token}"
    }, payload={"amount": 5500.00})
    assert_test("9. Anti-Shill Security: Seller blocked from manual bidding",
                status == 400 and "Sellers are not permitted to bid" in str(err),
                f"Status: {status}, Detail: {err}")

    # 9. Anti-Shill Bidding: Seller configuring auto-bid on own auction MUST fail
    status, _, err = request(f"{BACKEND_URL}/api/auctions/{auction_id}/auto-bid", method="POST", headers={
        "Authorization": f"Bearer {seller_token}"
    }, payload={"maxAmount": 10000.00})
    assert_test("10. Anti-Shill Security: Seller blocked from auto-bidding bots",
                status == 400 and "Sellers are not permitted to configure auto-bid" in str(err),
                f"Status: {status}, Detail: {err}")

    # 10. Legitimate Buyer Bid
    status, _, bid_res = request(f"{BACKEND_URL}/api/auctions/{auction_id}/bids", method="POST", headers={
        "Authorization": f"Bearer {buyer_token}"
    }, payload={"amount": 5100.00})
    assert_test("11. Legitimate Buyer Bid Accepted",
                status == 200 and bid_res.get("amount") == 5100.00,
                f"Status: {status}, Bid Amount: {bid_res.get('amount') if isinstance(bid_res, dict) else bid_res}")

    # 11. Underbid Rejected (Amount must be strictly greater than current highest bid)
    status, _, underbid_err = request(f"{BACKEND_URL}/api/auctions/{auction_id}/bids", method="POST", headers={
        "Authorization": f"Bearer {buyer_token}"
    }, payload={"amount": 5050.00})
    assert_test("12. Underbid Rejected (Amount <= Highest Bid)",
                status == 400, f"Status: {status}")

    # 12. CORS Preflight: Allowed Origin
    status, headers, _ = request(f"{BACKEND_URL}/api/auctions", method="OPTIONS", headers={
        "Origin": "http://localhost:5173",
        "Access-Control-Request-Method": "POST"
    })
    allow_origin = headers.get("Access-Control-Allow-Origin")
    assert_test("13. CORS Preflight Allowed Origin",
                status == 200 and allow_origin == "http://localhost:5173",
                f"Status: {status}, Access-Control-Allow-Origin: {allow_origin}")

    # 13. CORS Preflight: Malicious Origin Rejected
    status, _, _ = request(f"{BACKEND_URL}/api/auctions", method="OPTIONS", headers={
        "Origin": "http://malicious-attacker.com",
        "Access-Control-Request-Method": "POST"
    })
    assert_test("14. CORS Preflight Malicious Origin Rejected",
                status == 403, f"Status: {status}")

    # 14. SockJS Fallback Endpoint Info Check
    status, _, ws_info = request(f"{BACKEND_URL}/ws-sockjs/info")
    assert_test("15. WebSocket SockJS Handshake Endpoint",
                status == 200 and "entropy" in str(ws_info),
                f"Status: {status}")

    # 15. Clean 404 for Non-Existent Resource (authenticated)
    status, _, not_found = request(f"{BACKEND_URL}/api/nonexistent-route", headers={
        "Authorization": f"Bearer {buyer_token}"
    })
    assert_test("16. Clean 404 for Unknown Endpoints",
                status == 404, f"Status: {status}")

    print("=" * 70)
    print(f"TEST SUMMARY: {passed} / {total} tests passed ({(passed / total) * 100:.1f}%)")
    print("=" * 70)
    return passed == total

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)
