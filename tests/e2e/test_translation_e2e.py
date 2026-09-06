import urllib.request
import json
import time
import sys

# Ensure UTF-8 output on Windows console
if hasattr(sys.stdout, 'reconfigure'):
    sys.stdout.reconfigure(encoding='utf-8')

API_URL = "http://127.0.0.1:8000"

def post_json(endpoint: str, data: dict):
    req = urllib.request.Request(
        f"{API_URL}{endpoint}",
        data=json.dumps(data).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req, timeout=10) as res:
        return res.status, json.loads(res.read().decode("utf-8"))

def get_json(endpoint: str):
    req = urllib.request.Request(f"{API_URL}{endpoint}")
    with urllib.request.urlopen(req, timeout=10) as res:
        return res.status, json.loads(res.read().decode("utf-8"))

def run_e2e_tests():
    print("=" * 65)
    print(" VYNCE TRANSLATION SERVICE LIVE END-TO-END VERIFICATION")
    print("=" * 65)
    
    passed = 0
    failed = 0

    def assert_test(condition: bool, name: str, extra_info: str = ""):
        nonlocal passed, failed
        if condition:
            passed += 1
            print(f"[PASS] {name} {extra_info}")
        else:
            failed += 1
            print(f"[FAIL] {name} {extra_info}")

    # TEST 1: Service Health & Startup Readiness
    try:
        status, health = get_json("/health")
        assert_test(status == 200 and health.get("process_alive") and health.get("model_ready"),
                    "Test 22: /health endpoint ready and warmed up", f"({health.get('provider')} on {health.get('device')})")
    except Exception as e:
        assert_test(False, "Test 22: /health endpoint", str(e))

    # TEST 2: Indic -> English Translation (IndicTrans2 Indic-EN Route)
    try:
        t0 = time.time()
        payload = {
            "lines": ["तू ही मेरी दुनिया है", "तेरे बिना मैं अधूरा हूँ"],
            "sourceLanguage": "hin_Deva",
            "targetLanguage": "eng_Latn",
            "mode": "literal"
        }
        status, res = post_json("/translate", payload)
        duration_ms = (time.time() - t0) * 1000
        
        translated = res.get("translatedLines", [])
        assert_test(status == 200 and len(translated) == 2 and "world" in translated[0].lower(),
                    "Test 23: Indic -> English (Hindi -> English) live translation",
                    f"-> '{translated[0]}' in {duration_ms:.1f}ms (Route: {res.get('provenance', {}).get('route')})")
    except Exception as e:
        assert_test(False, "Test 23: Indic -> English", str(e))

    # TEST 3: Indic -> Indic Translation (IndicTrans2 Indic-Indic Route)
    try:
        t0 = time.time()
        payload = {
            "lines": ["तू ही मेरी दुनिया है"],
            "sourceLanguage": "hin_Deva",
            "targetLanguage": "tel_Telu",
            "mode": "literal"
        }
        status, res = post_json("/translate", payload)
        duration_ms = (time.time() - t0) * 1000
        
        translated = res.get("translatedLines", [])
        assert_test(status == 200 and len(translated) == 1 and len(translated[0]) > 0,
                    "Test 24: Indic -> Indic (Hindi -> Telugu) live translation",
                    f"-> '{translated[0]}' in {duration_ms:.1f}ms (Route: {res.get('provenance', {}).get('route')})")
    except Exception as e:
        assert_test(False, "Test 24: Indic -> Indic", str(e))

    # TEST 4: English -> Indic Translation (IndicTrans2 EN-Indic Route)
    try:
        t0 = time.time()
        payload = {
            "lines": ["You are my whole world"],
            "sourceLanguage": "eng_Latn",
            "targetLanguage": "hin_Deva",
            "mode": "literal"
        }
        status, res = post_json("/translate", payload)
        duration_ms = (time.time() - t0) * 1000
        
        translated = res.get("translatedLines", [])
        assert_test(status == 200 and len(translated) == 1 and ("दुनिया" in translated[0] or "तुम" in translated[0]),
                    "Test 25: English -> Indic (English -> Hindi) live translation",
                    f"-> '{translated[0]}' in {duration_ms:.1f}ms (Route: {res.get('provenance', {}).get('route')})")
    except Exception as e:
        assert_test(False, "Test 25: English -> Indic", str(e))

    # TEST 5: Persistent Server SQLite Cache Hit Verification
    try:
        t0 = time.time()
        # Send identical request to verify instant cache recall
        payload = {
            "lines": ["तू ही मेरी दुनिया है", "तेरे बिना मैं अधूरा हूँ"],
            "sourceLanguage": "hin_Deva",
            "targetLanguage": "eng_Latn",
            "mode": "literal"
        }
        status, res = post_json("/translate", payload)
        cache_duration_ms = (time.time() - t0) * 1000
        
        assert_test(res.get("cached") == True and cache_duration_ms < 50,
                    "Test 26: Tier-2 Server SQLite Persistent Cache HIT",
                    f"(cached=True in {cache_duration_ms:.2f}ms — ZERO model inference)")
    except Exception as e:
        assert_test(False, "Test 26: Persistent Cache HIT", str(e))

    # TEST 6: Multi-line Full Song Stanza Translation
    try:
        stanza = [
            "तू ही मेरी दुनिया है",
            "तेरे बिना मैं अधूरा हूँ",
            "मेरा दिल है",
            "तुम ही हो, अब तुम ही हो",
            "जिंदगी अब तुम ही हो"
        ]
        payload = {
            "lines": stanza,
            "sourceLanguage": "hin_Deva",
            "targetLanguage": "eng_Latn",
            "mode": "literal"
        }
        status, res = post_json("/translate", payload)
        translated = res.get("translatedLines", [])
        assert_test(status == 200 and len(translated) == 5,
                    "Test 27: Full 5-Line Stanza Structure & Line-Count Preservation",
                    f"(Output lines: {len(translated)}/5 matched)")
    except Exception as e:
        assert_test(False, "Test 27: Stanza preservation", str(e))

    print("\n" + "=" * 65)
    print(f" END-TO-END SUMMARY: {passed} PASSED, {failed} FAILED")
    print("=" * 65)
    return failed == 0

if __name__ == "__main__":
    time.sleep(1) # wait for server loop
    success = run_e2e_tests()
    sys.exit(0 if success else 1)
