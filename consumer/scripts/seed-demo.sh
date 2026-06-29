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

# Dev-only demo seeder. Stands in for the upstream back-office service by
# calling Fineract's REST API directly, then binds two BFF users by driving the
# real registration endpoints. NOT part of the BFF or Angular app.

set -euo pipefail

# --- Connections -----------------------------------
FINERACT_BASE="http://localhost:8888/fineract-provider/api/v1"
FINERACT_AUTH="mifos:password"
TENANT="default"
BFF_BASE="http://localhost:8080/api/v1"
MAILPIT_BASE="http://localhost:8025/api/v1"

# --- Demo data --------
SAVINGS_PRODUCT_NAME="Demo Savings"
SAVINGS_PRODUCT_SHORT="DSAV"
LOAN_PRODUCT_NAME="Demo Loan"
LOAN_PRODUCT_SHORT="DLN"
IDENTIFIER_CODE_NAME="Customer Identifier"
PAYMENT_TYPE_NAME="Cash"

DEMO_PASSWORD="DemoPassw0rd!23"   # >=15, lower+upper+digit+special

# Per-client config (index 0..3 == clients #1..#4).
FIRST=("John" "Alan" "Grace" "Katherine")
LAST=("Doe" "Turing" "Hopper" "Johnson")
EXTID=("demo-client-1" "demo-client-2" "demo-client-3" "demo-client-4")
DOCTYPE=("SSN" "Aadhaar" "SSN" "Aadhaar")
DOCKEY=("123-45-6789" "2234 5678 9012" "321-54-9876" "3234 5678 9013")
# Which clients get a pre-bound BFF user, and with what login email.
BFF_EMAIL=("" "" "demo3@example.com" "demo4@example.com")

# Fineract date inputs (in the past, after office opening 2009-01-01).
DATE_FMT="dd MMMM yyyy"
CLIENT_DATE="01 January 2024"
SAVINGS_DATE="02 January 2024"
DEPOSIT_DATE="03 January 2024"
LOAN_SUBMIT_DATE="04 January 2024"
LOAN_DISBURSE_DATE="05 January 2024"
REPAY_DATE="10 January 2024"
DEPOSIT_AMOUNT=1000
LOAN_PRINCIPAL=10000
REPAY_AMOUNT=900

# Captured at runtime.
SSN_ID=""
AADHAAR_ID=""
SAVINGS_PRODUCT_ID=""
LOAN_PRODUCT_ID=""
PAYMENT_TYPE_ID=""
CLIENT_IDS=("" "" "" "")
SAV_IDS=("" "" "" "")
LOAN_IDS=("" "" "" "")
CREATED=("0" "0" "0" "0")

# HTTP_CODE / HTTP_BODY are set by request(); RESULT carries function "returns".
HTTP_CODE=""
HTTP_BODY=""
RESULT=""

# --- HTTP plumbing ----------------------------------------------------------

request() { # method url [extra curl args...]
  local method="$1" url="$2"
  shift 2
  local tmp
  tmp="$(mktemp)"
  HTTP_CODE="$(curl -sS -o "$tmp" -w '%{http_code}' -X "$method" "$url" "$@")"
  HTTP_BODY="$(cat "$tmp")"
  rm -f "$tmp"
}

fineract() { # method path [json-body]
  local method="$1" path="$2" data="${3:-}"
  local args=(-u "$FINERACT_AUTH" -H "Fineract-Platform-TenantId: $TENANT")
  if [ -n "$data" ]; then
    args+=(-H 'Content-Type: application/json' --data "$data")
  fi
  request "$method" "$FINERACT_BASE$path" "${args[@]}"
  if [[ "$HTTP_CODE" != 2* ]]; then
    echo "ERROR: Fineract $method $path -> HTTP $HTTP_CODE" >&2
    echo "$HTTP_BODY" >&2
    exit 1
  fi
}

bff() { # method path [json-body]
  local method="$1" path="$2" data="${3:-}"
  local args=()
  if [ -n "$data" ]; then
    args+=(-H 'Content-Type: application/json' --data "$data")
  fi
  request "$method" "$BFF_BASE$path" "${args[@]}"
  if [[ "$HTTP_CODE" != 2* ]]; then
    echo "ERROR: BFF $method $path -> HTTP $HTTP_CODE" >&2
    echo "$HTTP_BODY" >&2
    exit 1
  fi
}

# --- Health gate ------------------------------------------------------------

wait_for_health() {
  echo "Waiting for the stack to come up (first Fineract boot can take ~2 min)..."

  local i
  # Fineract: need a real 2xx (DB ready), not just a reachable socket.
  for i in $(seq 1 120); do
    request GET "$FINERACT_BASE/clients?limit=1" \
      -u "$FINERACT_AUTH" -H "Fineract-Platform-TenantId: $TENANT" || true
    if [[ "$HTTP_CODE" == 2* ]]; then break; fi
    if [ "$i" -eq 120 ]; then echo "ERROR: Fineract not ready at $FINERACT_BASE" >&2; exit 1; fi
    sleep 2
  done
  echo "  Fineract is up."

  # Mailpit: messages endpoint returns 200 when ready.
  for i in $(seq 1 60); do
    request GET "$MAILPIT_BASE/messages" || true
    if [[ "$HTTP_CODE" == 2* ]]; then break; fi
    if [ "$i" -eq 60 ]; then echo "ERROR: Mailpit not ready at $MAILPIT_BASE" >&2; exit 1; fi
    sleep 2
  done
  echo "  Mailpit is up."

  # BFF: any HTTP response means the app is serving (actuator is on a separate port).
  for i in $(seq 1 90); do
    request GET "$BFF_BASE/registration" || true
    if [ "$HTTP_CODE" != "000" ]; then break; fi
    if [ "$i" -eq 90 ]; then echo "ERROR: BFF not reachable at $BFF_BASE" >&2; exit 1; fi
    sleep 2
  done
  echo "  BFF is up."
}

# --- Fineract org prerequisites --------------------------------------------

ensure_codevalue() { # codeId name  -> RESULT = codeValueId
  local code_id="$1" name="$2" id
  fineract GET "/codes/$code_id/codevalues"
  id="$(echo "$HTTP_BODY" | jq -r --arg n "$name" '.[] | select(.name==$n) | .id // empty')"
  if [ -z "$id" ]; then
    fineract POST "/codes/$code_id/codevalues" "$(jq -nc --arg n "$name" '{name:$n}')"
    fineract GET "/codes/$code_id/codevalues"
    id="$(echo "$HTTP_BODY" | jq -r --arg n "$name" '.[] | select(.name==$n) | .id // empty')"
    echo "  created code value '$name' (id $id)"
  else
    echo "  code value '$name' already present (id $id)"
  fi
  RESULT="$id"
}

ensure_identifier_codevalues() {
  echo "Ensuring SSN / Aadhaar identifier types..."
  local code_id
  fineract GET "/codes"
  code_id="$(echo "$HTTP_BODY" | jq -r --arg n "$IDENTIFIER_CODE_NAME" '.[] | select(.name==$n) | .id // empty')"
  if [ -z "$code_id" ]; then
    echo "ERROR: Fineract code '$IDENTIFIER_CODE_NAME' not found" >&2
    exit 1
  fi
  ensure_codevalue "$code_id" "SSN"; SSN_ID="$RESULT"
  ensure_codevalue "$code_id" "Aadhaar"; AADHAAR_ID="$RESULT"
}

doctype_id() { # name -> stdout
  case "$1" in
    SSN) echo "$SSN_ID" ;;
    Aadhaar) echo "$AADHAAR_ID" ;;
    *) echo "ERROR: unknown document type '$1'" >&2; exit 1 ;;
  esac
}

ensure_products() {
  echo "Ensuring savings / loan products..."

  fineract GET "/savingsproducts"
  SAVINGS_PRODUCT_ID="$(echo "$HTTP_BODY" | jq -r --arg s "$SAVINGS_PRODUCT_SHORT" '.[] | select(.shortName==$s) | .id // empty')"
  if [ -z "$SAVINGS_PRODUCT_ID" ]; then
    fineract POST "/savingsproducts" "$(jq -nc \
      --arg name "$SAVINGS_PRODUCT_NAME" --arg short "$SAVINGS_PRODUCT_SHORT" \
      '{name:$name, shortName:$short, currencyCode:"USD", digitsAfterDecimal:2,
        inMultiplesOf:0, nominalAnnualInterestRate:5, interestCompoundingPeriodType:1,
        interestPostingPeriodType:4, interestCalculationType:1,
        interestCalculationDaysInYearType:365, accountingRule:1, locale:"en"}')"
    SAVINGS_PRODUCT_ID="$(echo "$HTTP_BODY" | jq -r '.resourceId')"
    echo "  created '$SAVINGS_PRODUCT_NAME' (id $SAVINGS_PRODUCT_ID)"
  else
    echo "  '$SAVINGS_PRODUCT_NAME' already present (id $SAVINGS_PRODUCT_ID)"
  fi

  fineract GET "/loanproducts"
  LOAN_PRODUCT_ID="$(echo "$HTTP_BODY" | jq -r --arg s "$LOAN_PRODUCT_SHORT" '.[] | select(.shortName==$s) | .id // empty')"
  if [ -z "$LOAN_PRODUCT_ID" ]; then
    fineract POST "/loanproducts" "$(jq -nc \
      --arg name "$LOAN_PRODUCT_NAME" --arg short "$LOAN_PRODUCT_SHORT" \
      --argjson principal "$LOAN_PRINCIPAL" \
      '{name:$name, shortName:$short, currencyCode:"USD", digitsAfterDecimal:2,
        inMultiplesOf:0, principal:$principal, numberOfRepayments:12, repaymentEvery:1,
        repaymentFrequencyType:2, interestRatePerPeriod:2, interestRateFrequencyType:2,
        amortizationType:1, interestType:0, interestCalculationPeriodType:1,
        transactionProcessingStrategyCode:"mifos-standard-strategy", accountingRule:1,
        daysInYearType:1, daysInMonthType:1, isInterestRecalculationEnabled:false,
        locale:"en"}')"
    LOAN_PRODUCT_ID="$(echo "$HTTP_BODY" | jq -r '.resourceId')"
    echo "  created '$LOAN_PRODUCT_NAME' (id $LOAN_PRODUCT_ID)"
  else
    echo "  '$LOAN_PRODUCT_NAME' already present (id $LOAN_PRODUCT_ID)"
  fi
}

ensure_payment_type() {
  echo "Ensuring payment type..."
  fineract GET "/paymenttypes"
  PAYMENT_TYPE_ID="$(echo "$HTTP_BODY" | jq -r --arg n "$PAYMENT_TYPE_NAME" '.[] | select(.name==$n) | .id // empty' | head -1)"
  if [ -z "$PAYMENT_TYPE_ID" ]; then
    fineract POST "/paymenttypes" "$(jq -nc --arg n "$PAYMENT_TYPE_NAME" \
      '{name:$n, description:"Demo cash payment type", isCashPayment:true, position:1}')"
    PAYMENT_TYPE_ID="$(echo "$HTTP_BODY" | jq -r '.resourceId')"
    echo "  created payment type '$PAYMENT_TYPE_NAME' (id $PAYMENT_TYPE_ID)"
  else
    echo "  payment type '$PAYMENT_TYPE_NAME' already present (id $PAYMENT_TYPE_ID)"
  fi
}

# --- Per-client Fineract seed ----------------------------------------------

seed_client() { # index
  local i="$1"
  local ext="${EXTID[$i]}" first="${FIRST[$i]}" last="${LAST[$i]}"
  local client_id

  fineract GET "/clients?externalId=$ext"
  client_id="$(echo "$HTTP_BODY" | jq -r '.pageItems[0].id // empty')"

  if [ -n "$client_id" ]; then
    # Client exists from a prior run: capture its account IDs, skip creation.
    CLIENT_IDS[$i]="$client_id"
    fineract GET "/clients/$client_id/accounts"
    SAV_IDS[$i]="$(echo "$HTTP_BODY" | jq -r '.savingsAccounts[0].id // empty')"
    LOAN_IDS[$i]="$(echo "$HTTP_BODY" | jq -r '.loanAccounts[0].id // empty')"
    echo "Client #$((i + 1)) ($ext) already seeded (id $client_id) - skipping."
    return
  fi

  echo "Seeding client #$((i + 1)) ($first $last, $ext)..."
  fineract POST "/clients" "$(jq -nc \
    --arg first "$first" --arg last "$last" --arg ext "$ext" \
    --arg date "$CLIENT_DATE" --arg fmt "$DATE_FMT" \
    '{officeId:1, legalFormId:1, firstname:$first, lastname:$last, externalId:$ext, active:true,
      activationDate:$date, dateFormat:$fmt, locale:"en"}')"
  client_id="$(echo "$HTTP_BODY" | jq -r '.clientId // .resourceId')"
  CLIENT_IDS[$i]="$client_id"
  CREATED[$i]="1"

  # Identifier (needed for registration binding + masked last-4).
  local type_id
  type_id="$(doctype_id "${DOCTYPE[$i]}")"
  fineract POST "/clients/$client_id/identifiers" "$(jq -nc \
    --argjson typeId "$type_id" --arg key "${DOCKEY[$i]}" \
    '{documentTypeId:$typeId, documentKey:$key, status:"Active"}')"

  seed_savings "$i" "$client_id"
  seed_loan "$i" "$client_id"
  echo "  client #$((i + 1)) -> id $client_id, savings ${SAV_IDS[$i]}, loan ${LOAN_IDS[$i]}"
}

seed_savings() { # index clientId
  local i="$1" client_id="$2" sid
  fineract POST "/savingsaccounts" "$(jq -nc \
    --argjson clientId "$client_id" --argjson productId "$SAVINGS_PRODUCT_ID" \
    --arg date "$SAVINGS_DATE" --arg fmt "$DATE_FMT" \
    '{clientId:$clientId, productId:$productId, submittedOnDate:$date,
      dateFormat:$fmt, locale:"en"}')"
  sid="$(echo "$HTTP_BODY" | jq -r '.savingsId // .resourceId')"
  SAV_IDS[$i]="$sid"

  fineract POST "/savingsaccounts/$sid?command=approve" "$(jq -nc \
    --arg date "$SAVINGS_DATE" --arg fmt "$DATE_FMT" \
    '{approvedOnDate:$date, dateFormat:$fmt, locale:"en"}')"
  fineract POST "/savingsaccounts/$sid?command=activate" "$(jq -nc \
    --arg date "$SAVINGS_DATE" --arg fmt "$DATE_FMT" \
    '{activatedOnDate:$date, dateFormat:$fmt, locale:"en"}')"
  fineract POST "/savingsaccounts/$sid/transactions?command=deposit" "$(jq -nc \
    --arg date "$DEPOSIT_DATE" --arg fmt "$DATE_FMT" --argjson amt "$DEPOSIT_AMOUNT" \
    --argjson ptid "$PAYMENT_TYPE_ID" \
    '{transactionDate:$date, transactionAmount:$amt, paymentTypeId:$ptid, dateFormat:$fmt, locale:"en"}')"
}

seed_loan() { # index clientId
  local i="$1" client_id="$2" lid
  fineract POST "/loans" "$(jq -nc \
    --argjson clientId "$client_id" --argjson productId "$LOAN_PRODUCT_ID" \
    --argjson principal "$LOAN_PRINCIPAL" \
    --arg disburse "$LOAN_DISBURSE_DATE" --arg submit "$LOAN_SUBMIT_DATE" --arg fmt "$DATE_FMT" \
    '{clientId:$clientId, productId:$productId, loanType:"individual", principal:$principal,
      loanTermFrequency:12, loanTermFrequencyType:2, numberOfRepayments:12, repaymentEvery:1,
      repaymentFrequencyType:2, interestRatePerPeriod:2, amortizationType:1, interestType:0,
      interestCalculationPeriodType:1, transactionProcessingStrategyCode:"mifos-standard-strategy",
      expectedDisbursementDate:$disburse, submittedOnDate:$submit, dateFormat:$fmt, locale:"en"}')"
  lid="$(echo "$HTTP_BODY" | jq -r '.loanId // .resourceId')"
  LOAN_IDS[$i]="$lid"

  fineract POST "/loans/$lid?command=approve" "$(jq -nc \
    --arg date "$LOAN_SUBMIT_DATE" --arg fmt "$DATE_FMT" \
    '{approvedOnDate:$date, dateFormat:$fmt, locale:"en"}')"
  fineract POST "/loans/$lid?command=disburse" "$(jq -nc \
    --arg date "$LOAN_DISBURSE_DATE" --arg fmt "$DATE_FMT" --argjson amt "$LOAN_PRINCIPAL" \
    '{actualDisbursementDate:$date, transactionAmount:$amt, dateFormat:$fmt, locale:"en"}')"
  fineract POST "/loans/$lid/transactions?command=repayment" "$(jq -nc \
    --arg date "$REPAY_DATE" --arg fmt "$DATE_FMT" --argjson amt "$REPAY_AMOUNT" \
    '{transactionDate:$date, transactionAmount:$amt, dateFormat:$fmt, locale:"en"}')"
}

# --- BFF user binding via the real registration endpoints (Option A) -------

mailpit_clear() { # email  (avoid reading a stale code from a prior run)
  local email="$1" ids
  request GET "$MAILPIT_BASE/search?query=$(rawurlencode "to:$email")&limit=200"
  ids="$(echo "$HTTP_BODY" | jq -c '[.messages[].ID]')"
  if [ "$ids" != "[]" ] && [ -n "$ids" ]; then
    request DELETE "$MAILPIT_BASE/messages" -H 'Content-Type: application/json' \
      --data "$(jq -nc --argjson ids "$ids" '{IDs:$ids}')"
  fi
}

mailpit_latest_code() { # email -> RESULT = otp code (never printed)
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
  echo "ERROR: no OTP email arrived for $email" >&2
  exit 1
}

rawurlencode() { # string -> stdout (percent-encode for query params)
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

bind_bff_user() { # index
  local i="$1" email="${BFF_EMAIL[$i]}" client_id="${CLIENT_IDS[$i]}"
  if [ -z "$email" ]; then return; fi

  if [ "${CREATED[$i]}" != "1" ]; then
    echo "BFF user for client #$((i + 1)) ($email) assumed already bound - skipping."
    return
  fi

  echo "Binding BFF user $email to client #$((i + 1)) (id $client_id)..."

  bff POST "/registration/submit" "$(jq -nc \
    --argjson clientId "$client_id" --arg email "$email" --arg pw "$DEMO_PASSWORD" \
    --arg type "${DOCTYPE[$i]}" --arg key "${DOCKEY[$i]}" \
    '{fineractClientId:$clientId, email:$email, password:$pw,
      documentTypeName:$type, documentKey:$key}')"
  local reg_id
  reg_id="$(echo "$HTTP_BODY" | jq -r '.registrationId')"

  mailpit_clear "$email"
  bff POST "/registration/otp/send" "$(jq -nc \
    --arg id "$reg_id" --arg method "email" \
    '{registrationId:$id, deliveryMethod:$method}')"

  mailpit_latest_code "$email"   # sets RESULT
  bff POST "/registration/otp/verify" "$(jq -nc \
    --arg id "$reg_id" --arg token "$RESULT" \
    '{registrationId:$id, token:$token}')"

  local status
  status="$(echo "$HTTP_BODY" | jq -r '.status // empty')"
  echo "  $email bound (status ${status:-unknown})."
}

# --- Summary ----------------------------------------------------------------

print_summary() {
  echo ""
  echo "================ DEMO SEED COMPLETE ================"
  printf "%-8s %-18s %-9s %-8s %-8s %-22s\n" "Client" "Name" "ClientId" "Savings" "Loan" "LoginEmail"
  local i
  for i in 0 1 2 3; do
    printf "%-8s %-18s %-9s %-8s %-8s %-22s\n" \
      "#$((i + 1))" "${FIRST[$i]} ${LAST[$i]}" "${CLIENT_IDS[$i]}" \
      "${SAV_IDS[$i]}" "${LOAN_IDS[$i]}" "${BFF_EMAIL[$i]:-(unbound)}"
  done
  echo ""
  echo "Pre-bound login password (clients #3/#4): $DEMO_PASSWORD"
  echo "Clients #1/#2 have no BFF user - use them for the live registration demo."
  echo "==================================================="
}

# --- Main -------------------------------------------------------------------

main() {
  command -v jq >/dev/null 2>&1 || { echo "ERROR: jq is required" >&2; exit 1; }

  wait_for_health
  ensure_identifier_codevalues
  ensure_products
  ensure_payment_type

  local i
  for i in 0 1 2 3; do seed_client "$i"; done
  for i in 0 1 2 3; do bind_bff_user "$i"; done

  print_summary
}

main "$@"
