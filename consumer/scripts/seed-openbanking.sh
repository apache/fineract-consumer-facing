#!/usr/bin/env bash
# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Dev-only open banking token minter. Stands in for a third-party provider and a
# browser by driving the real OAuth2 authorization-code + PKCE flow headlessly
# (TPP token -> consent -> customer login -> consent approval -> code exchange),
# then prints the tokens ready to paste into a Postman environment.

set -euo pipefail

# --- Connections -----------------------------------
BFF_BASE="${BFF_BASE:-http://localhost:8080/api/v1}"
MAILPIT_BASE="${MAILPIT_BASE:-http://localhost:8025/api/v1}"

# --- Third-party provider ---------------------------------------------------
TPP_CLIENT_ID="${TPP_CLIENT_ID:-postman-tpp}"
TPP_CLIENT_SECRET="${TPP_CLIENT_SECRET:-demo-tpp-secret}"
TPP_REDIRECT_URI="${TPP_REDIRECT_URI:-https://oauth.pstmn.io/v1/callback}"

# --- Demo customer ---------------------------------
DEMO_EMAIL="${DEMO_EMAIL:-demo3@example.com}"
DEMO_PASSWORD="${DEMO_PASSWORD:-DemoPassw0rd!23}"
DEVICE_FINGERPRINT="${DEVICE_FINGERPRINT:-seed-openbanking-device}"

# --- PKCE -------------------------------------------------------------------
PKCE_CODE_VERIFIER="dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"
PKCE_CODE_CHALLENGE="E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM"
PKCE_CHALLENGE_METHOD="S256"

# --- Wire contract ----------------------------------------------------------
TOKEN_PATH="/oauth2/token"
AUTHORIZE_PATH="/oauth2/authorize"
ACCOUNT_ACCESS_CONSENTS_PATH="/openbanking/account-access-consents"
LOGIN_PATH="/authentication/login"
TWO_FACTOR_PATH="/authentication/2fa"
HEALTH_PROBE_PATH="/registration"

DEVICE_FINGERPRINT_HEADER="X-Device-Fingerprint"
ACCESS_TOKEN_COOKIE_NAME="access_token"
XSRF_COOKIE_NAME="XSRF-TOKEN"
CSRF_FORM_FIELD="_csrf"

JSON_CONTENT_TYPE="application/json"
FORM_CONTENT_TYPE="application/x-www-form-urlencoded"

PARAM_RESPONSE_TYPE="response_type"
PARAM_CLIENT_ID="client_id"
PARAM_REDIRECT_URI="redirect_uri"
PARAM_SCOPE="scope"
PARAM_STATE="state"
PARAM_CODE="code"
PARAM_ERROR="error"
PARAM_ERROR_DESCRIPTION="error_description"
PARAM_GRANT_TYPE="grant_type"
PARAM_CODE_CHALLENGE="code_challenge"
PARAM_CODE_CHALLENGE_METHOD="code_challenge_method"
PARAM_CODE_VERIFIER="code_verifier"
PARAM_CONSENT_ID="consent_id"

RESPONSE_TYPE_CODE="code"
GRANT_TYPE_CLIENT_CREDENTIALS="client_credentials"
GRANT_TYPE_AUTHORIZATION_CODE="authorization_code"
SCOPE_OPENBANKING_CONSENTS="openbanking:consents"
SCOPE_OPENBANKING_ACCOUNTS_READ="openbanking:accounts.read"

CONSENT_PERMISSIONS='["ReadAccountsBasic","ReadBalances"]'
CONSENT_STATUS_AWAITING="AWAITING_AUTHORISATION"
CONSENT_PAGE_MARKER="/consent?"

# Captured at runtime.
CONSENT_PAGE_URL=""
CONSENT_PAGE_XSRF_TOKEN=""
OB_ACCESS_TOKEN=""
OB_REFRESH_TOKEN=""
OB_TOKEN_SCOPE=""
OB_TOKEN_EXPIRES_IN=""
CONSENT_ID=""
TPP_STATE=""

HTTP_CODE=""
HTTP_BODY=""
HTTP_HEADERS=""
RESULT=""
FORM=""

# --- HTTP plumbing ----------------------------------------------------------

fail() { # message...
  echo "ERROR: $*" >&2
  exit 1
}

request() {
  local method="$1" url="$2"
  shift 2
  local body_tmp header_tmp
  body_tmp="$(mktemp)"
  header_tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -o "$body_tmp" -D "$header_tmp" -w '%{http_code}' -X "$method" "$url" "$@")"
  HTTP_BODY="$(cat "$body_tmp")"
  HTTP_HEADERS="$(cat "$header_tmp")"
  rm -f "$body_tmp" "$header_tmp"
}

rawurlencode() {
  local s="$1" out="" c i
  for (( i = 0; i < ${#s}; i++ )); do
    c="${s:$i:1}"
    case "$c" in
      [a-zA-Z0-9.~_-]) out+="$c" ;;
      *) out+="$(printf '%%%02X' "'$c")" ;;
    esac
  done
  printf '%s' "$out"
}

urldecode() {
  printf '%b' "${1//%/\\x}"
}

form_reset() {
  FORM=""
}

form_field() {
  FORM="${FORM:+$FORM&}$(rawurlencode "$1")=$(rawurlencode "$2")"
}

header_value() {
  local line
  line="$(printf '%s\n' "$HTTP_HEADERS" | tr -d '\r' | grep -i "^$1:" | head -1)" || line=""
  RESULT="$(printf '%s' "${line#*:}" | sed 's/^[[:space:]]*//')"
}

cookie_value() {
  local name="$1" line
  line="$(printf '%s\n' "$HTTP_HEADERS" | tr -d '\r' | grep -i "^set-cookie:[[:space:]]*$name=" | head -1)" || line=""
  if [ -z "$line" ]; then RESULT=""; return; fi
  line="${line#*"$name"=}"
  RESULT="$(urldecode "${line%%;*}")"
}

query_param() {
  local url="$1" name="$2" query rest pair
  RESULT=""
  query="${url#*\?}"
  if [ "$query" = "$url" ]; then return; fi
  rest="$query"
  while [ -n "$rest" ]; do
    pair="${rest%%&*}"
    if [ "$pair" = "$rest" ]; then rest=""; else rest="${rest#*&}"; fi
    case "$pair" in
      "$name"=*) RESULT="$(urldecode "${pair#*=}")"; return ;;
    esac
  done
}

redirect_location() {
  local context="$1"
  [ "$HTTP_CODE" = "302" ] || fail "$context returned HTTP $HTTP_CODE (expected a 302 redirect): $HTTP_BODY"
  header_value "Location"
  [ -n "$RESULT" ] || fail "$context returned a 302 with no Location header"
}

# --- Preflight --------------------------------------------------------------

preflight() {
  command -v jq >/dev/null 2>&1 || fail "jq is required"

  request GET "$BFF_BASE$HEALTH_PROBE_PATH" || true
  [ "$HTTP_CODE" != "000" ] || fail "BFF not reachable at $BFF_BASE - start the compose stack first"

  request GET "$MAILPIT_BASE/messages" || true
  [[ "$HTTP_CODE" == 2* ]] || fail "Mailpit not reachable at $MAILPIT_BASE - the login 2FA code is read from it"
}

# --- Step 1: TPP client-credentials token -----------------------------------

tpp_token() {
  echo "Requesting the TPP client-credentials token ($TPP_CLIENT_ID)..."
  form_reset
  form_field "$PARAM_GRANT_TYPE" "$GRANT_TYPE_CLIENT_CREDENTIALS"
  form_field "$PARAM_SCOPE" "$SCOPE_OPENBANKING_CONSENTS"
  request POST "$BFF_BASE$TOKEN_PATH" \
    -u "$TPP_CLIENT_ID:$TPP_CLIENT_SECRET" \
    -H "Content-Type: $FORM_CONTENT_TYPE" \
    --data-raw "$FORM"

  if [ "$HTTP_CODE" = "401" ]; then
    fail "TPP '$TPP_CLIENT_ID' was rejected (HTTP 401) - run seed-demo.sh first to register it"
  fi
  [ "$HTTP_CODE" = "200" ] || fail "token endpoint returned HTTP $HTTP_CODE: $HTTP_BODY"

  RESULT="$(echo "$HTTP_BODY" | jq -r '.access_token // empty')"
  [ -n "$RESULT" ] || fail "no access_token in the client-credentials response: $HTTP_BODY"
  echo "  TPP token acquired."
}

# --- Step 2: account access consent -----------------------------------------

create_consent() {
  local cc_token="$1" status
  echo "Creating an account access consent..."
  request POST "$BFF_BASE$ACCOUNT_ACCESS_CONSENTS_PATH" \
    -H "Authorization: Bearer $cc_token" \
    -H "Content-Type: $JSON_CONTENT_TYPE" \
    --data "$(jq -nc --argjson perms "$CONSENT_PERMISSIONS" '{permissions:$perms}')"

  [ "$HTTP_CODE" = "201" ] || fail "consent creation returned HTTP $HTTP_CODE: $HTTP_BODY"
  RESULT="$(echo "$HTTP_BODY" | jq -r '.consentId // empty')"
  [ -n "$RESULT" ] || fail "no consentId in the consent response: $HTTP_BODY"

  status="$(echo "$HTTP_BODY" | jq -r '.status // empty')"
  [ "$status" = "$CONSENT_STATUS_AWAITING" ] \
    || fail "consent $RESULT is '$status', expected '$CONSENT_STATUS_AWAITING'"
  echo "  consent $RESULT created ($status)."
}

# --- Step 3: headless customer login ----------------------------------------

mailpit_clear() {
  local email="$1" ids
  request GET "$MAILPIT_BASE/search?query=$(rawurlencode "to:$email")&limit=200"
  ids="$(echo "$HTTP_BODY" | jq -c '[.messages[].ID]')"
  if [ "$ids" != "[]" ] && [ -n "$ids" ]; then
    request DELETE "$MAILPIT_BASE/messages" -H "Content-Type: $JSON_CONTENT_TYPE" \
      --data "$(jq -nc --argjson ids "$ids" '{IDs:$ids}')"
  fi
}

mailpit_latest_code() {
  local email="$1" i id text code
  for i in $(seq 1 20); do
    request GET "$MAILPIT_BASE/search?query=$(rawurlencode "to:$email")&limit=1"
    id="$(echo "$HTTP_BODY" | jq -r '.messages[0].ID // empty')"
    if [ -n "$id" ]; then
      request GET "$MAILPIT_BASE/message/$id"
      text="$(echo "$HTTP_BODY" | jq -r '.Text')"
      code="$(printf '%s' "$text" | sed -n 's/.*verification code is:[[:space:]]*\([A-Za-z0-9]\{4,\}\).*/\1/p' | head -1)"
      if [ -n "$code" ]; then RESULT="$code"; return; fi
    fi
    sleep 1
  done
  fail "no 2FA email arrived for $email"
}

login_customer() {
  local challenge_token
  echo "Logging in $DEMO_EMAIL (login + emailed 2FA code)..."

  mailpit_clear "$DEMO_EMAIL"
  request POST "$BFF_BASE$LOGIN_PATH" \
    -H "Content-Type: $JSON_CONTENT_TYPE" \
    -H "$DEVICE_FINGERPRINT_HEADER: $DEVICE_FINGERPRINT" \
    --data "$(jq -nc --arg email "$DEMO_EMAIL" --arg pw "$DEMO_PASSWORD" '{email:$email, password:$pw}')"
  [ "$HTTP_CODE" = "200" ] \
    || fail "login returned HTTP $HTTP_CODE for $DEMO_EMAIL - is the user seeded? $HTTP_BODY"
  challenge_token="$(echo "$HTTP_BODY" | jq -r '.challengeToken // empty')"
  [ -n "$challenge_token" ] || fail "no challengeToken in the login response: $HTTP_BODY"

  mailpit_latest_code "$DEMO_EMAIL"   # sets RESULT
  request POST "$BFF_BASE$TWO_FACTOR_PATH" \
    -H "Content-Type: $JSON_CONTENT_TYPE" \
    -H "$DEVICE_FINGERPRINT_HEADER: $DEVICE_FINGERPRINT" \
    --data "$(jq -nc --arg challenge "$challenge_token" --arg token "$RESULT" \
      '{challengeToken:$challenge, token:$token}')"
  [ "$HTTP_CODE" = "200" ] || fail "2FA verification returned HTTP $HTTP_CODE: $HTTP_BODY"

  cookie_value "$ACCESS_TOKEN_COOKIE_NAME"
  [ -n "$RESULT" ] || fail "no $ACCESS_TOKEN_COOKIE_NAME cookie on the 2FA response"
  echo "  session established."
}

# --- Step 4: user authorization ---------------------------------------------

request_authorization() {
  local access_token="$1" consent_id="$2"
  echo "Opening the authorize link for consent $consent_id..."

  TPP_STATE="seed-openbanking-$(date +%s)-$$"
  form_reset
  form_field "$PARAM_RESPONSE_TYPE" "$RESPONSE_TYPE_CODE"
  form_field "$PARAM_CLIENT_ID" "$TPP_CLIENT_ID"
  form_field "$PARAM_REDIRECT_URI" "$TPP_REDIRECT_URI"
  form_field "$PARAM_SCOPE" "$SCOPE_OPENBANKING_ACCOUNTS_READ"
  form_field "$PARAM_STATE" "$TPP_STATE"
  form_field "$PARAM_CODE_CHALLENGE" "$PKCE_CODE_CHALLENGE"
  form_field "$PARAM_CODE_CHALLENGE_METHOD" "$PKCE_CHALLENGE_METHOD"
  form_field "$PARAM_CONSENT_ID" "$consent_id"

  request GET "$BFF_BASE$AUTHORIZE_PATH?$FORM" \
    -H "Cookie: $ACCESS_TOKEN_COOKIE_NAME=$access_token"
  redirect_location "the authorize request"
  CONSENT_PAGE_URL="$RESULT"

  case "$CONSENT_PAGE_URL" in
    *"$CONSENT_PAGE_MARKER"*) ;;
    *) fail "the authorize request did not reach the consent page (redirected to $CONSENT_PAGE_URL)" ;;
  esac

  cookie_value "$XSRF_COOKIE_NAME"
  CONSENT_PAGE_XSRF_TOKEN="$RESULT"
  [ -n "$CONSENT_PAGE_XSRF_TOKEN" ] || fail "no $XSRF_COOKIE_NAME cookie on the consent-page redirect"
  echo "  consent page reached."
}

approve_consent() {
  local access_token="$1" client_id state granted_scopes callback error_code error_text scope_value
  local -a scopes
  echo "Approving the consent..."

  query_param "$CONSENT_PAGE_URL" "$PARAM_CLIENT_ID"; client_id="$RESULT"
  query_param "$CONSENT_PAGE_URL" "$PARAM_STATE"; state="$RESULT"
  query_param "$CONSENT_PAGE_URL" "$PARAM_SCOPE"; granted_scopes="$RESULT"
  [ -n "$client_id" ] && [ -n "$state" ] && [ -n "$granted_scopes" ] \
    || fail "consent page URL is missing client_id/state/scope: $CONSENT_PAGE_URL"

  form_reset
  form_field "$PARAM_CLIENT_ID" "$client_id"
  form_field "$PARAM_STATE" "$state"
  form_field "$CSRF_FORM_FIELD" "$CONSENT_PAGE_XSRF_TOKEN"
  read -ra scopes <<<"$granted_scopes"
  for scope_value in "${scopes[@]}"; do
    form_field "$PARAM_SCOPE" "$scope_value"
  done

  request POST "$BFF_BASE$AUTHORIZE_PATH" \
    -H "Cookie: $ACCESS_TOKEN_COOKIE_NAME=$access_token; $XSRF_COOKIE_NAME=$CONSENT_PAGE_XSRF_TOKEN" \
    -H "Content-Type: $FORM_CONTENT_TYPE" \
    --data-raw "$FORM"
  redirect_location "the consent decision"
  callback="$RESULT"

  query_param "$callback" "$PARAM_ERROR"; error_code="$RESULT"
  if [ -n "$error_code" ]; then
    query_param "$callback" "$PARAM_ERROR_DESCRIPTION"; error_text="$RESULT"
    fail "the authorization was refused: $error_code ${error_text:-}"
  fi

  query_param "$callback" "$PARAM_CODE"
  [ -n "$RESULT" ] || fail "no $PARAM_CODE in the TPP callback redirect: $callback"
  local code="$RESULT"

  query_param "$callback" "$PARAM_STATE"
  [ "$RESULT" = "$TPP_STATE" ] || fail "callback state '$RESULT' does not match the sent state '$TPP_STATE'"
  RESULT="$code"
  echo "  authorization code received."
}

# --- Step 5: code exchange --------------------------------------------------

exchange_code() { # authorizationCode
  local code="$1"
  echo "Exchanging the authorization code for an open banking token..."
  form_reset
  form_field "$PARAM_GRANT_TYPE" "$GRANT_TYPE_AUTHORIZATION_CODE"
  form_field "$PARAM_CODE" "$code"
  form_field "$PARAM_REDIRECT_URI" "$TPP_REDIRECT_URI"
  form_field "$PARAM_CODE_VERIFIER" "$PKCE_CODE_VERIFIER"

  request POST "$BFF_BASE$TOKEN_PATH" \
    -u "$TPP_CLIENT_ID:$TPP_CLIENT_SECRET" \
    -H "Content-Type: $FORM_CONTENT_TYPE" \
    --data-raw "$FORM"
  [ "$HTTP_CODE" = "200" ] || fail "code exchange returned HTTP $HTTP_CODE: $HTTP_BODY"

  OB_ACCESS_TOKEN="$(echo "$HTTP_BODY" | jq -r '.access_token // empty')"
  OB_REFRESH_TOKEN="$(echo "$HTTP_BODY" | jq -r '.refresh_token // empty')"
  OB_TOKEN_SCOPE="$(echo "$HTTP_BODY" | jq -r '.scope // empty')"
  OB_TOKEN_EXPIRES_IN="$(echo "$HTTP_BODY" | jq -r '.expires_in // empty')"
  [ -n "$OB_ACCESS_TOKEN" ] || fail "no access_token in the code-exchange response: $HTTP_BODY"
  [ -n "$OB_REFRESH_TOKEN" ] || fail "no refresh_token in the code-exchange response: $HTTP_BODY"
  echo "  open banking token minted."
}

# --- Step 6: summary --------------------------------------------------------

print_summary() {
  echo ""
  echo "=============== OPEN BANKING TOKENS ==============="
  echo "Paste these into your Postman environment:"
  echo ""
  echo "obClientId     = $TPP_CLIENT_ID"
  echo "obConsentId    = $CONSENT_ID"
  echo "obAccessToken  = $OB_ACCESS_TOKEN"
  echo "obRefreshToken = $OB_REFRESH_TOKEN"
  echo ""
  echo "scope:      ${OB_TOKEN_SCOPE:-unknown}"
  echo "expires in: ${OB_TOKEN_EXPIRES_IN:-unknown}s (refresh with grant_type=refresh_token at $BFF_BASE$TOKEN_PATH)"
  echo "customer:   $DEMO_EMAIL"
  echo ""
  echo "NOT IDEMPOTENT: every run creates a NEW consent row and a NEW token pair."
  echo "Re-running does not update the previous consent - it leaves it behind."
  echo "==================================================="
}

# --- Main -------------------------------------------------------------------

main() {
  preflight

  tpp_token
  local cc_token="$RESULT"

  create_consent "$cc_token"
  CONSENT_ID="$RESULT"

  login_customer
  local access_token="$RESULT"

  request_authorization "$access_token" "$CONSENT_ID"
  approve_consent "$access_token"
  exchange_code "$RESULT"

  print_summary
}

main "$@"
