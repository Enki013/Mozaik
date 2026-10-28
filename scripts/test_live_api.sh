#!/bin/bash
BASE_URL="${BASE_URL:-https://personal-records-api.enessalman1300.workers.dev}"
# The real key is never stored here: export API_KEY before running.
VALID_KEY="${API_KEY:?Set API_KEY to the server API key, e.g. API_KEY=... ./scripts/test_live_api.sh}"
INVALID_KEY="invalid-key-for-tests"

passed=0
failed=0

run_test() {
  local name="$1"
  local expected_status="$2"
  shift 2
  
  status=$(curl -s -o /tmp/last_body.txt -w "%{http_code}" "$@")
  
  if [ "$status" = "$expected_status" ]; then
    echo "✅ [PASS] $name (Status: $status)"
    ((passed++))
  else
    echo "❌ [FAIL] $name (Expected: $expected_status, Got: $status)"
    echo "   Response preview: $(head -c 150 /tmp/last_body.txt)"
    ((failed++))
  fi
}

echo "=============================================="
echo "🚀 STARTING COMPREHENSIVE LIVE API TEST SUITE"
echo "Target: $BASE_URL"
echo "=============================================="
echo ""

# 1. Health & Public Endpoints
echo "--- 1. Health & Public Endpoints ---"
run_test "GET /api/health (public)" "200" "$BASE_URL/api/health"
run_test "GET / (Dashboard HTML)" "200" "$BASE_URL/"

# 2. Authentication & Verification
echo ""
echo "--- 2. Auth Endpoints ---"
run_test "GET /api/auth/verify without key" "401" "$BASE_URL/api/auth/verify"
run_test "GET /api/auth/verify with WRONG key" "401" -H "X-API-Key: $INVALID_KEY" "$BASE_URL/api/auth/verify"
run_test "GET /api/auth/verify with VALID X-API-Key" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/auth/verify"
run_test "GET /api/auth/verify with VALID Bearer token" "200" -H "Authorization: Bearer $VALID_KEY" "$BASE_URL/api/auth/verify"

# Test Login endpoint
run_test "POST /api/auth/login with wrong key" "401" -X POST -H 'Content-Type: application/json' -d "{\"apiKey\":\"$INVALID_KEY\"}" "$BASE_URL/api/auth/login"
run_test "POST /api/auth/login with valid key" "200" -X POST -H 'Content-Type: application/json' -d "{\"apiKey\":\"$VALID_KEY\"}" "$BASE_URL/api/auth/login"

# Cookie login test
LOGIN_COOKIE=$(curl -i -s -X POST -H 'Content-Type: application/json' -d "{\"apiKey\":\"$VALID_KEY\"}" "$BASE_URL/api/auth/login" | grep -i "set-cookie: pr_auth=" | awk '{print $2}' | tr -d '\r;')
run_test "GET /api/dashboard with Session Cookie" "200" -H "Cookie: $LOGIN_COOKIE" "$BASE_URL/api/dashboard"

# Logout test
run_test "POST /api/auth/logout" "200" -X POST "$BASE_URL/api/auth/logout"

# 3. Data Endpoints (Auth checks)
echo ""
echo "--- 3. Protected Data Endpoints (No Auth => 401) ---"
run_test "GET /api/dashboard [NO AUTH]" "401" "$BASE_URL/api/dashboard"
run_test "GET /api/activity [NO AUTH]" "401" "$BASE_URL/api/activity"
run_test "GET /api/goodreads/books [NO AUTH]" "401" "$BASE_URL/api/goodreads/books"
run_test "GET /api/letterboxd/films [NO AUTH]" "401" "$BASE_URL/api/letterboxd/films"
run_test "GET /api/fatsecret/food-entries [NO AUTH]" "401" "$BASE_URL/api/fatsecret/food-entries"
run_test "GET /api/fatsecret/daily-summary [NO AUTH]" "401" "$BASE_URL/api/fatsecret/daily-summary"
run_test "GET /api/music [NO AUTH]" "401" "$BASE_URL/api/music"
run_test "GET /api/music/stats [NO AUTH]" "401" "$BASE_URL/api/music/stats"
run_test "GET /api/notifications [NO AUTH]" "401" "$BASE_URL/api/notifications"
run_test "GET /api/health-connect/records [NO AUTH]" "401" "$BASE_URL/api/health-connect/records"
run_test "GET /api/trakt/history [NO AUTH]" "401" "$BASE_URL/api/trakt/history"
run_test "GET /api/settings/trakt [NO AUTH]" "401" "$BASE_URL/api/settings/trakt"
run_test "GET /api/settings/mdblist [NO AUTH]" "401" "$BASE_URL/api/settings/mdblist"
run_test "GET /api/simkl/history [NO AUTH]" "401" "$BASE_URL/api/simkl/history"
run_test "GET /api/settings/simkl [NO AUTH]" "401" "$BASE_URL/api/settings/simkl"
run_test "GET /api/media/ratings [NO AUTH]" "401" "$BASE_URL/api/media/ratings"
run_test "GET /api/export/sql [NO AUTH]" "401" "$BASE_URL/api/export/sql"

echo ""
echo "--- 4. Protected Data Endpoints (Valid Key => 200) ---"
run_test "GET /api/dashboard [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/dashboard"
run_test "GET /api/activity [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/activity"
run_test "GET /api/goodreads/books [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/goodreads/books"
run_test "GET /api/letterboxd/films [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/letterboxd/films"
run_test "GET /api/fatsecret/food-entries [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/fatsecret/food-entries"
run_test "GET /api/fatsecret/daily-summary [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/fatsecret/daily-summary"
run_test "GET /api/music [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/music"
run_test "GET /api/music/stats [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/music/stats"
run_test "GET /api/notifications [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/notifications"
run_test "GET /api/health-connect/records [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/health-connect/records"
run_test "GET /api/trakt/history [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/trakt/history"
run_test "GET /api/simkl/history [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/simkl/history"
run_test "GET /api/export/sql [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/export/sql"

# 5. Settings Endpoints
echo ""
echo "--- 5. Settings Endpoints ---"
run_test "GET /api/settings/calorie-target [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/calorie-target"
run_test "GET /api/settings/fatsecret [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/fatsecret"
run_test "GET /api/settings/goodreads [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/goodreads"
run_test "GET /api/settings/letterboxd [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/letterboxd"
run_test "GET /api/settings/music [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/music"
run_test "GET /api/settings/plugins [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/plugins"
run_test "GET /api/settings/trakt [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/trakt"
run_test "POST /api/settings/trakt [AUTH]" "200" -X POST -H "X-API-Key: $VALID_KEY" -H 'Content-Type: application/json' -d '{"username":"enesben_","sync_movies":true,"sync_episodes":true,"sync_ratings":true}' "$BASE_URL/api/settings/trakt"
run_test "POST /api/sync/trakt?force=true [AUTH]" "200" -X POST -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/sync/trakt?force=true"
run_test "GET /api/settings/mdblist [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/mdblist"
run_test "POST /api/settings/mdblist [AUTH]" "200" -X POST -H "X-API-Key: $VALID_KEY" -H 'Content-Type: application/json' -d '{"api_key":"","sync_ratings_enabled":true,"sync_lists_enabled":false}' "$BASE_URL/api/settings/mdblist"
run_test "POST /api/sync/mdblist without key (Expect 400) [AUTH]" "400" -X POST -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/sync/mdblist?force=true"
run_test "GET /api/settings/simkl [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/settings/simkl"
run_test "POST /api/settings/simkl [AUTH]" "200" -X POST -H "X-API-Key: $VALID_KEY" -H 'Content-Type: application/json' -d '{"client_id":"test_id","access_token":"test_token","sync_movies":true,"sync_shows":true,"sync_anime":true,"sync_ratings":true,"enabled":true}' "$BASE_URL/api/settings/simkl"
run_test "POST /api/sync/simkl?force=true [AUTH]" "200" -X POST -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/sync/simkl?force=true"

# 6. Webhooks
echo ""
echo "--- 6. Webhooks Endpoints ---"
run_test "POST /api/webhooks/notification [NO AUTH]" "401" -X POST -H 'Content-Type: application/json' -d '{"packageName":"com.test","title":"Test"}' "$BASE_URL/api/webhooks/notification"
run_test "POST /api/webhooks/notification [AUTH]" "200" -X POST -H "X-API-Key: $VALID_KEY" -H 'Content-Type: application/json' -d '{"packageName":"com.test.verifier","title":"Verification Ping","text":"Testing webhook security","postTime":1700000000000}' "$BASE_URL/api/webhooks/notification"
run_test "GET /api/webhooks/health [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/webhooks/health"
run_test "GET /api/webhooks/music [AUTH]" "200" -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/webhooks/music"

# 7. Security Headers & CORS Verification
echo ""
echo "--- 7. Security Headers & CORS ---"
HEAD_OUT=$(curl -s -I "$BASE_URL/")
if echo "$HEAD_OUT" | grep -iq "x-frame-options: DENY"; then
  echo "✅ [PASS] Security Header: X-Frame-Options: DENY present"
  ((passed++))
else
  echo "❌ [FAIL] Security Header: X-Frame-Options: DENY missing"
  ((failed++))
fi

if echo "$HEAD_OUT" | grep -iq "x-content-type-options: nosniff"; then
  echo "✅ [PASS] Security Header: X-Content-Type-Options: nosniff present"
  ((passed++))
else
  echo "❌ [FAIL] Security Header: X-Content-Type-Options: nosniff missing"
  ((failed++))
fi

if echo "$HEAD_OUT" | grep -iq "content-security-policy:"; then
  echo "✅ [PASS] Security Header: Content-Security-Policy present"
  ((passed++))
else
  echo "❌ [FAIL] Security Header: Content-Security-Policy missing"
  ((failed++))
fi

EVIL_CORS=$(curl -s -I -H "Origin: https://evil.attacker.com" "$BASE_URL/api/dashboard")
if echo "$EVIL_CORS" | grep -iq "access-control-allow-origin"; then
  echo "❌ [FAIL] Untrusted Origin allowed by CORS!"
  ((failed++))
else
  echo "✅ [PASS] Untrusted Origin correctly blocked by CORS"
  ((passed++))
fi

LOCAL_CORS=$(curl -s -I -H "Origin: http://localhost:8787" "$BASE_URL/api/dashboard")
if echo "$LOCAL_CORS" | grep -iq "access-control-allow-origin: http://localhost:8787"; then
  echo "✅ [PASS] Trusted Origin allowed by CORS (http://localhost:8787)"
  ((passed++))
else
  echo "❌ [FAIL] Trusted Origin not echoed in CORS!"
  ((failed++))
fi

# 8. Sync Cooldown & Rate Limiting
echo ""
echo "--- 8. Sync Cooldown & Rate Limiting ---"
# Trigger first sync with force to ensure fresh state
curl -s -X POST -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/sync/letterboxd?force=true" > /dev/null
# Immediate second sync without force should return 429
run_test "POST /api/sync/letterboxd immediate repeat (Expect 429)" "429" -X POST -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/sync/letterboxd"
# Third sync WITH ?force=true should bypass cooldown (Expect 200)
run_test "POST /api/sync/letterboxd with ?force=true (Expect 200)" "200" -X POST -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/sync/letterboxd?force=true"

# Simkl cooldown check
run_test "POST /api/sync/simkl immediate repeat (Expect 429)" "429" -X POST -H "X-API-Key: $VALID_KEY" "$BASE_URL/api/sync/simkl"

echo ""
echo "=============================================="
echo "📊 TEST RESULTS: Passed: $passed, Failed: $failed"
echo "=============================================="
