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

BFF_DB_CONTAINER="${BFF_DB_CONTAINER:-bff-db}"
BFF_DB_NAME="${POSTGRES_DB:-consumerapp}"      # defaults mirror compose.yaml
BFF_DB_USER="${POSTGRES_USER:-consumerapp}"

# --- Demo data --------
SAVINGS_PRODUCT_NAME="Demo Savings"
SAVINGS_PRODUCT_SHORT="DSAV"
LOAN_PRODUCT_NAME="Demo Loan"
LOAN_PRODUCT_SHORT="DLN"
IDENTIFIER_CODE_NAME="Customer Identifier"
PAYMENT_TYPE_NAME="Cash"

CLIENT_CHARGE_NAME="Demo Client Fee"
SAVINGS_CHARGE_NAME="Demo Savings Fee"
LOAN_CHARGE_NAME="Demo Loan Fee"

DEMO_PASSWORD="DemoPassw0rd!23"

OB_TPP_CLIENT_ID="${OB_TPP_CLIENT_ID:-demo-tpp}"
OB_TPP_CLIENT_REDIRECT_URI="${OB_TPP_CLIENT_REDIRECT_URI:-http://localhost:9999/tpp/callback}"
OB_TPP_CLIENT_NAME="Demo TPP"
OB_TPP_CLIENT_SCOPES="openbanking:consents,openbanking:accounts.read"
OB_TPP_CLIENT_SECRET_HASH='{bcrypt}$2y$10$piiChDy1OaMQggEym1p4J.0npIQfqpgeUChoB8j8oOt/oIX0P19Q.'

POSTMAN_TPP_CLIENT_ID="${POSTMAN_TPP_CLIENT_ID:-postman-tpp}"
POSTMAN_TPP_CLIENT_REDIRECT_URI="${POSTMAN_TPP_CLIENT_REDIRECT_URI:-https://oauth.pstmn.io/v1/callback}"
POSTMAN_TPP_CLIENT_NAME="Postman TPP"
POSTMAN_TPP_CLIENT_SCOPES="openbanking:consents,openbanking:accounts.read"
POSTMAN_TPP_CLIENT_SECRET_HASH="$OB_TPP_CLIENT_SECRET_HASH"

FIRST=("John" "Alan" "Grace" "Katherine")
LAST=("Doe" "Turing" "Hopper" "Johnson")
EXTID=("demo-client-1" "demo-client-2" "demo-client-3" "demo-client-4")
DOCTYPE=("SSN" "Aadhaar" "SSN" "Aadhaar")
DOCKEY=("123-45-6789" "2234 5678 9012" "321-54-9876" "3234 5678 9013")
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

SCALED_OPENING_DEPOSITS=(500 1000 1500 2000 2500 3000 3500 4000 4500 5000)
SCALED_TXN_TYPES=(deposit withdrawal deposit withdrawal deposit withdrawal deposit withdrawal deposit withdrawal)
SCALED_TXN_AMOUNTS=(200 100 300 120 400 80 250 60 350 150)
SCALED_TXN_DATES=("10 February 2024" "10 February 2024" "15 March 2024" "15 March 2024" "20 April 2024" "20 April 2024" "25 May 2024" "25 May 2024" "18 June 2024" "22 July 2024")
SCALED_REPAY_AMOUNTS=(100 150 200 125 175)
SCALED_REPAY_DATES=("15 January 2024" "29 January 2024" "12 February 2024" "26 February 2024" "11 March 2024" "25 March 2024" "08 April 2024" "22 April 2024" "06 May 2024" "20 May 2024" "03 June 2024" "17 June 2024" "01 July 2024" "15 July 2024" "29 July 2024" "12 August 2024" "26 August 2024" "09 September 2024" "23 September 2024" "07 October 2024" "21 October 2024" "04 November 2024" "18 November 2024" "02 December 2024" "16 December 2024")
SCALED_TRANSFER_AMOUNTS=(100 150 200 120 180 90 160 110 140 130)
SCALED_TRANSFER_DATES=("05 August 2024" "05 August 2024" "20 August 2024" "20 August 2024" "10 September 2024" "10 September 2024" "01 October 2024" "01 October 2024" "15 November 2024" "15 November 2024")
PAYOFF_DATE="01 February 2024"   # exact-payoff repayment date that closes loan #10
CLIENT_CHARGE_AMOUNTS=(25 35 45)
CLIENT_CHARGE_DATES=("15 January 2024" "15 April 2024" "15 July 2024")
SAVINGS_CHARGE_AMOUNTS=(10 15 20)
SAVINGS_CHARGE_DATES=("10 February 2024" "10 March 2024" "10 April 2024")
LOAN_CHARGE_AMOUNTS=(50 75 100)
LOAN_CHARGE_DATES=("10 February 2024" "10 March 2024" "10 April 2024")
CHARGE_SLOTS=3

GUARANTOR_TYPE_CUSTOMER=1
GUARANTOR_TYPE_EXTERNAL=3
EXTERNAL_GUARANTOR_FIRST=("Marcus" "Ada")
EXTERNAL_GUARANTOR_LAST=("Aurelius" "Lovelace")
GUARANTOR_FUNDED_AMOUNT=100
GUARANTOR_PENDING_LOAN_SLOT=7
GUARANTOR_PLAIN_LOAN_SLOTS="0 1"

# Captured at runtime.
SSN_ID=""
AADHAAR_ID=""
SAVINGS_PRODUCT_ID=""
LOAN_PRODUCT_ID=""
PAYMENT_TYPE_ID=""
CLIENT_CHARGE_ID=""
SAVINGS_CHARGE_ID=""
LOAN_CHARGE_ID=""
CLIENT_IDS=("" "" "" "")
SAV_IDS=("" "" "" "")
LOAN_IDS=("" "" "" "")
CREATED=("0" "0" "0" "0")
TPP_STATUS_UNSEEDED="not seeded"
TPP_STATUS_ACTIVE="ACTIVE"
OB_TPP_STATUS="$TPP_STATUS_UNSEEDED"
POSTMAN_TPP_STATUS="$TPP_STATUS_UNSEEDED"

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

# --- Open banking TPP registry ----------------------------------------------

seed_registered_tpp() { # clientId name secretHash redirectUri scopes -> RESULT = status
  local client_id="$1" name="$2" secret_hash="$3" redirect_uri="$4" scopes="$5"
  echo "Ensuring registered TPP '$client_id'..."

  docker exec -i "$BFF_DB_CONTAINER" \
    psql -q -v ON_ERROR_STOP=1 -U "$BFF_DB_USER" -d "$BFF_DB_NAME" \
    -v client_id="$client_id" \
    -v client_name="$name" \
    -v client_secret_hash="$secret_hash" \
    -v redirect_uri="$redirect_uri" \
    -v scopes="$scopes" <<'SQL'
INSERT INTO registered_tpps (
    id, client_id, client_secret_hash, client_name, redirect_uri, scopes,
    status, onboarded_at, status_updated_at)
VALUES (
    gen_random_uuid(), :'client_id', :'client_secret_hash', :'client_name',
    :'redirect_uri', :'scopes', 'ACTIVE', now(), now())
ON CONFLICT (client_id) DO UPDATE SET
    client_secret_hash = EXCLUDED.client_secret_hash,
    client_name        = EXCLUDED.client_name,
    redirect_uri       = EXCLUDED.redirect_uri,
    scopes             = EXCLUDED.scopes,
    status             = EXCLUDED.status,
    status_updated_at  = now();
SQL

  RESULT="$TPP_STATUS_ACTIVE"
  echo "  registered TPP '$client_id' is $RESULT."
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

ensure_charge() { # name jsonBody -> RESULT = chargeId
  local name="$1" body="$2" id
  fineract GET "/charges"
  id="$(echo "$HTTP_BODY" | jq -r --arg n "$name" '.[] | select(.name==$n) | .id // empty' | head -1)"
  if [ -z "$id" ]; then
    fineract POST "/charges" "$body"
    id="$(echo "$HTTP_BODY" | jq -r '.resourceId')"
    echo "  created charge '$name' (id $id)"
  else
    echo "  charge '$name' already present (id $id)"
  fi
  RESULT="$id"
}

ensure_charges() {
  echo "Ensuring client / savings / loan charge definitions..."
  ensure_charge "$CLIENT_CHARGE_NAME" "$(jq -nc --arg n "$CLIENT_CHARGE_NAME" \
    '{name:$n, chargeAppliesTo:3, chargeTimeType:2, chargeCalculationType:1,
      currencyCode:"USD", amount:25, active:true, penalty:false, locale:"en"}')"
  CLIENT_CHARGE_ID="$RESULT"

  ensure_charge "$SAVINGS_CHARGE_NAME" "$(jq -nc --arg n "$SAVINGS_CHARGE_NAME" \
    '{name:$n, chargeAppliesTo:2, chargeTimeType:2, chargeCalculationType:1,
      currencyCode:"USD", amount:10, active:true, penalty:false, locale:"en"}')"
  SAVINGS_CHARGE_ID="$RESULT"

  ensure_charge "$LOAN_CHARGE_NAME" "$(jq -nc --arg n "$LOAN_CHARGE_NAME" \
    '{name:$n, chargeAppliesTo:1, chargeTimeType:2, chargeCalculationType:1,
      chargePaymentMode:0, currencyCode:"USD", amount:50, active:true,
      penalty:false, locale:"en"}')"
  LOAN_CHARGE_ID="$RESULT"
}

# --- Per-client Fineract seed ----------------------------------------------

seed_client() { # index
  local i="$1"
  local ext="${EXTID[$i]}" first="${FIRST[$i]}" last="${LAST[$i]}"
  local client_id

  fineract GET "/clients?externalId=$ext"
  client_id="$(echo "$HTTP_BODY" | jq -r '.pageItems[0].id // empty')"

  if [ -n "$client_id" ]; then
    # Client exists from a prior run: capture ALL its account IDs, skip creation.
    CLIENT_IDS[$i]="$client_id"
    fineract GET "/clients/$client_id/accounts"
    SAV_IDS[$i]="$(echo "$HTTP_BODY" | jq -r '[.savingsAccounts[]?.id | tostring] | join(" ")')"
    LOAN_IDS[$i]="$(echo "$HTTP_BODY" | jq -r '[.loanAccounts[]?.id | tostring] | join(" ")')"
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

  if [ "$i" -ge 2 ]; then
    # Clients #3/#4 (pre-bound BFF users) get the scaled data set.
    seed_client_charges "$client_id"
    seed_scaled_savings "$i" "$client_id"
    seed_scaled_savings_charges "$i"
    seed_scaled_loans "$i" "$client_id"
    seed_scaled_loan_charges "$i"
    seed_scaled_savings_activity "$i"
    seed_scaled_loan_repayments "$i"
    seed_scaled_transfers "$i" "$client_id"
  else
    seed_savings "$i" "$client_id"
    seed_loan "$i" "$client_id"
  fi
  echo "  client #$((i + 1)) -> id $client_id, savings [${SAV_IDS[$i]}], loans [${LOAN_IDS[$i]}]"
}

open_savings_account() { # clientId openingDeposit -> RESULT = savingsId
  local client_id="$1" amount="$2" sid
  fineract POST "/savingsaccounts" "$(jq -nc \
    --argjson clientId "$client_id" --argjson productId "$SAVINGS_PRODUCT_ID" \
    --arg date "$SAVINGS_DATE" --arg fmt "$DATE_FMT" \
    '{clientId:$clientId, productId:$productId, submittedOnDate:$date,
      dateFormat:$fmt, locale:"en"}')"
  sid="$(echo "$HTTP_BODY" | jq -r '.savingsId // .resourceId')"

  fineract POST "/savingsaccounts/$sid?command=approve" "$(jq -nc \
    --arg date "$SAVINGS_DATE" --arg fmt "$DATE_FMT" \
    '{approvedOnDate:$date, dateFormat:$fmt, locale:"en"}')"
  fineract POST "/savingsaccounts/$sid?command=activate" "$(jq -nc \
    --arg date "$SAVINGS_DATE" --arg fmt "$DATE_FMT" \
    '{activatedOnDate:$date, dateFormat:$fmt, locale:"en"}')"
  savings_transaction "$sid" deposit "$DEPOSIT_DATE" "$amount"
  RESULT="$sid"
}

savings_transaction() { # savingsId deposit|withdrawal date amount
  local sid="$1" command="$2" date="$3" amount="$4"
  fineract POST "/savingsaccounts/$sid/transactions?command=$command" "$(jq -nc \
    --arg date "$date" --arg fmt "$DATE_FMT" --argjson amt "$amount" \
    --argjson ptid "$PAYMENT_TYPE_ID" \
    '{transactionDate:$date, transactionAmount:$amt, paymentTypeId:$ptid, dateFormat:$fmt, locale:"en"}')"
}

seed_savings() { # index clientId  (clients #1/#2: one account, one deposit)
  local i="$1" client_id="$2"
  open_savings_account "$client_id" "$DEPOSIT_AMOUNT"
  SAV_IDS[$i]="$RESULT"
}

submit_loan() { # clientId -> RESULT = loanId (submitted.and.pending.approval)
  local client_id="$1" lid
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
  RESULT="$lid"
}

approve_and_disburse_loan() { # loanId
  local lid="$1"
  fineract POST "/loans/$lid?command=approve" "$(jq -nc \
    --arg date "$LOAN_SUBMIT_DATE" --arg fmt "$DATE_FMT" \
    '{approvedOnDate:$date, dateFormat:$fmt, locale:"en"}')"
  fineract POST "/loans/$lid?command=disburse" "$(jq -nc \
    --arg date "$LOAN_DISBURSE_DATE" --arg fmt "$DATE_FMT" --argjson amt "$LOAN_PRINCIPAL" \
    '{actualDisbursementDate:$date, transactionAmount:$amt, dateFormat:$fmt, locale:"en"}')"
}

repay_loan() { # loanId date amount
  local lid="$1" date="$2" amount="$3"
  fineract POST "/loans/$lid/transactions?command=repayment" "$(jq -nc \
    --arg date "$date" --arg fmt "$DATE_FMT" --argjson amt "$amount" \
    '{transactionDate:$date, transactionAmount:$amt, dateFormat:$fmt, locale:"en"}')"
}

seed_loan() { # index clientId  (clients #1/#2: one active loan, one repayment)
  local i="$1" client_id="$2"
  submit_loan "$client_id"
  LOAN_IDS[$i]="$RESULT"
  approve_and_disburse_loan "${LOAN_IDS[$i]}"
  repay_loan "${LOAN_IDS[$i]}" "$REPAY_DATE" "$REPAY_AMOUNT"
}

# --- Scaled seed for clients #3/#4 ------------------------------------------

attach_charge() { # path chargeId amount dueDate
  fineract POST "$1/charges" "$(jq -nc \
    --argjson chargeId "$2" --argjson amt "$3" --arg due "$4" --arg fmt "$DATE_FMT" \
    '{chargeId:$chargeId, amount:$amt, dueDate:$due, dateFormat:$fmt, locale:"en"}')"
}

seed_client_charges() { # clientId — 3 client charges, due dates spread over 2024
  local client_id="$1" n
  echo "  attaching $CHARGE_SLOTS client charges..."
  for n in 0 1 2; do
    attach_charge "/clients/$client_id" "$CLIENT_CHARGE_ID" \
      "${CLIENT_CHARGE_AMOUNTS[$n]}" "${CLIENT_CHARGE_DATES[$n]}"
  done
}

seed_scaled_savings_charges() { # index — charges on the first 3 (ACTIVE) accounts
  local i="$1" n sav
  sav=(${SAV_IDS[$i]})
  echo "  attaching $CHARGE_SLOTS savings charges..."
  for n in 0 1 2; do
    attach_charge "/savingsaccounts/${sav[$n]}" "$SAVINGS_CHARGE_ID" \
      "${SAVINGS_CHARGE_AMOUNTS[$n]}" "${SAVINGS_CHARGE_DATES[$n]}"
  done
}

seed_scaled_loan_charges() { # index — charges on the first 3 DISBURSED loans only
  local i="$1" n loans
  loans=(${LOAN_IDS[$i]})
  echo "  attaching $CHARGE_SLOTS loan charges..."
  for n in 0 1 2; do
    attach_charge "/loans/${loans[$n]}" "$LOAN_CHARGE_ID" \
      "${LOAN_CHARGE_AMOUNTS[$n]}" "${LOAN_CHARGE_DATES[$n]}"
  done
}

seed_scaled_savings() { # index clientId — 10 accounts, varied opening deposits
  local i="$1" client_id="$2" n ids=""
  echo "  seeding 10 savings accounts (opening deposits 500-5000)..."
  for n in 0 1 2 3 4 5 6 7 8 9; do
    open_savings_account "$client_id" "${SCALED_OPENING_DEPOSITS[$n]}"
    ids="${ids:+$ids }$RESULT"
  done
  SAV_IDS[$i]="$ids"
}

seed_scaled_loans() { # index clientId — loans 1-7 active, 8-9 pending, 10 closed
  local i="$1" client_id="$2" n lid outstanding ids=""
  echo "  seeding 10 loans (7 active, 2 pending approval, 1 closed)..."
  for n in 0 1 2 3 4 5 6 7 8 9; do
    submit_loan "$client_id"
    lid="$RESULT"
    ids="${ids:+$ids }$lid"
    if [ "$n" -le 6 ]; then
      approve_and_disburse_loan "$lid"
    elif [ "$n" -eq 9 ]; then
      approve_and_disburse_loan "$lid"
      fineract GET "/loans/$lid"
      outstanding="$(echo "$HTTP_BODY" | jq -r '.summary.totalOutstanding')"
      repay_loan "$lid" "$PAYOFF_DATE" "$outstanding"
    fi
  done
  LOAN_IDS[$i]="$ids"
}

seed_scaled_savings_activity() { # index — 10 extra txns, one per account
  local i="$1" n sav
  sav=(${SAV_IDS[$i]})
  echo "  seeding 10 extra savings transactions (Feb-Jul 2024)..."
  for n in 0 1 2 3 4 5 6 7 8 9; do
    savings_transaction "${sav[$n]}" "${SCALED_TXN_TYPES[$n]}" \
      "${SCALED_TXN_DATES[$n]}" "${SCALED_TXN_AMOUNTS[$n]}"
  done
}

seed_scaled_loan_repayments() { # index — 25 repayments on EACH of the 7 active loans
  local i="$1" l n loans
  loans=(${LOAN_IDS[$i]})
  echo "  seeding 25 loan repayments on each of the 7 active loans (175 total)..."
  for l in 0 1 2 3 4 5 6; do
    for n in $(seq 0 24); do
      repay_loan "${loans[$l]}" "${SCALED_REPAY_DATES[$n]}" "${SCALED_REPAY_AMOUNTS[$((n % 5))]}"
    done
  done
}

seed_scaled_transfers() { # index clientId — 10 transfers between own accounts
  local i="$1" client_id="$2" n sav from to
  sav=(${SAV_IDS[$i]})
  echo "  seeding 10 savings-to-savings transfers (Aug-Nov 2024)..."
  for n in 0 1 2 3 4 5 6 7 8 9; do
    from="${sav[$n]}"
    to="${sav[$(((n + 1) % 10))]}"
    fineract POST "/accounttransfers" "$(jq -nc \
      --argjson cid "$client_id" --argjson from "$from" --argjson to "$to" \
      --argjson amt "${SCALED_TRANSFER_AMOUNTS[$n]}" \
      --arg date "${SCALED_TRANSFER_DATES[$n]}" --arg fmt "$DATE_FMT" \
      '{fromOfficeId:1, fromClientId:$cid, fromAccountType:2, fromAccountId:$from,
        toOfficeId:1, toClientId:$cid, toAccountType:2, toAccountId:$to,
        transferAmount:$amt, transferDate:$date,
        transferDescription:"Demo transfer between own accounts",
        dateFormat:$fmt, locale:"en"}')"
  done
}

# --- Guarantors and obligees (cross-client) ---------------------------------

loan_guarantor_count() { # loanId -> RESULT = number of guarantors
  fineract GET "/loans/$1/guarantors"
  RESULT="$(echo "$HTTP_BODY" | jq -r 'length')"
}

add_guarantor() { # loanId jsonBody  (no-op when the loan already has one)
  local lid="$1"
  loan_guarantor_count "$lid"
  if [ "$RESULT" != "0" ]; then return; fi
  fineract POST "/loans/$lid/guarantors" "$2"
}

seed_guarantors() {
  local a b n loans sav
  if [ -z "${CLIENT_IDS[2]}" ] || [ -z "${CLIENT_IDS[3]}" ]; then return; fi
  echo "Wiring guarantors and obligees between clients #3 and #4..."

  for a in 2 3; do
    b=$((5 - a))   # the other scaled client; entityId == own client is rejected
    loans=(${LOAN_IDS[$a]})
    sav=(${SAV_IDS[$b]})
    n=$((a - 2))

    add_guarantor "${loans[$GUARANTOR_PENDING_LOAN_SLOT]}" "$(jq -nc \
      --argjson type "$GUARANTOR_TYPE_CUSTOMER" --argjson entity "${CLIENT_IDS[$b]}" \
      --argjson savings "${sav[0]}" --argjson amt "$GUARANTOR_FUNDED_AMOUNT" \
      --arg fmt "$DATE_FMT" \
      '{guarantorTypeId:$type, entityId:$entity, savingsId:$savings, amount:$amt,
        dateFormat:$fmt, locale:"en"}')"

    set -- $GUARANTOR_PLAIN_LOAN_SLOTS
    add_guarantor "${loans[$1]}" "$(jq -nc \
      --argjson type "$GUARANTOR_TYPE_CUSTOMER" --argjson entity "${CLIENT_IDS[$b]}" \
      '{guarantorTypeId:$type, entityId:$entity, locale:"en"}')"
    add_guarantor "${loans[$2]}" "$(jq -nc \
      --argjson type "$GUARANTOR_TYPE_EXTERNAL" \
      --arg first "${EXTERNAL_GUARANTOR_FIRST[$n]}" --arg last "${EXTERNAL_GUARANTOR_LAST[$n]}" \
      '{guarantorTypeId:$type, firstname:$first, lastname:$last, locale:"en"}')"

    echo "  client #$((a + 1)): guaranteed by client #$((b + 1)) (funded, on the pending loan) + 2 plain guarantors."
  done
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

count_and_first() {
  local ids="$1"
  if [ -z "$ids" ]; then RESULT="-"; return; fi
  set -- $ids
  RESULT="$# (#$1)"
}

count_endpoints() {
  local total=0 p n
  for p in "$@"; do
    fineract GET "$p"
    n="$(echo "$HTTP_BODY" | jq -r 'if type=="array" then length else (.pageItems // [] | length) end')"
    total=$((total + n))
  done
  RESULT="$total"
}

count_charge_slots() {
  local i="$1" base="$2" n ids paths=()
  if [ "$base" = "/loans" ]; then ids=(${LOAN_IDS[$i]}); else ids=(${SAV_IDS[$i]}); fi
  for n in 0 1 2; do paths+=("$base/${ids[$n]}/charges"); done
  count_endpoints "${paths[@]}"
}

count_guarantor_slots() {
  local i="$1" loans paths=()
  loans=(${LOAN_IDS[$i]})
  set -- $GUARANTOR_PLAIN_LOAN_SLOTS
  paths=("/loans/${loans[$1]}/guarantors" "/loans/${loans[$2]}/guarantors"
         "/loans/${loans[$GUARANTOR_PENDING_LOAN_SLOT]}/guarantors")
  count_endpoints "${paths[@]}"
}

print_summary() {
  echo ""
  echo "================ DEMO SEED COMPLETE ================"
  printf "%-8s %-18s %-9s %-10s %-10s %-22s\n" "Client" "Name" "ClientId" "Savings" "Loans" "LoginEmail"
  local i sav_col loan_col
  for i in 0 1 2 3; do
    count_and_first "${SAV_IDS[$i]}"; sav_col="$RESULT"
    count_and_first "${LOAN_IDS[$i]}"; loan_col="$RESULT"
    printf "%-8s %-18s %-9s %-10s %-10s %-22s\n" \
      "#$((i + 1))" "${FIRST[$i]} ${LAST[$i]}" "${CLIENT_IDS[$i]}" \
      "$sav_col" "$loan_col" "${BFF_EMAIL[$i]:-(unbound)}"
  done
  echo ""
  printf "%-8s %-15s %-16s %-13s %-12s %-10s\n" \
    "Client" "ClientCharges" "SavingsCharges" "LoanCharges" "Guarantors" "Obligees"
  local cc sc lc gc oc
  for i in 2 3; do
    if [ -z "${CLIENT_IDS[$i]}" ]; then continue; fi
    count_charge_slots "$i" "/savingsaccounts"; sc="$RESULT"
    count_charge_slots "$i" "/loans"; lc="$RESULT"
    count_endpoints "/clients/${CLIENT_IDS[$i]}/charges"; cc="$RESULT"
    count_guarantor_slots "$i"; gc="$RESULT"
    count_endpoints "/clients/${CLIENT_IDS[$i]}/obligeedetails"; oc="$RESULT"
    printf "%-8s %-15s %-16s %-13s %-12s %-10s\n" \
      "#$((i + 1))" "$cc" "$sc" "$lc" "$gc" "$oc"
  done
  echo "Clients #1/#2 carry no charges or guarantors by design."
  echo ""
  echo "Open banking TPP: $OB_TPP_CLIENT_ID ($OB_TPP_CLIENT_NAME) - $OB_TPP_STATUS"
  echo "  redirect URI: $OB_TPP_CLIENT_REDIRECT_URI"
  echo "  scopes:       $OB_TPP_CLIENT_SCOPES"
  echo "Open banking TPP: $POSTMAN_TPP_CLIENT_ID ($POSTMAN_TPP_CLIENT_NAME) - $POSTMAN_TPP_STATUS"
  echo "  redirect URI: $POSTMAN_TPP_CLIENT_REDIRECT_URI"
  echo "  scopes:       $POSTMAN_TPP_CLIENT_SCOPES"
  echo ""
  echo "Pre-bound login password (clients #3/#4): $DEMO_PASSWORD"
  echo "Clients #1/#2 have no BFF user - use them for the live registration demo."
  echo "==================================================="
}

# --- Main -------------------------------------------------------------------

main() {
  command -v jq >/dev/null 2>&1 || { echo "ERROR: jq is required" >&2; exit 1; }
  command -v docker >/dev/null 2>&1 || { echo "ERROR: docker is required" >&2; exit 1; }

  wait_for_health
  seed_registered_tpp "$OB_TPP_CLIENT_ID" "$OB_TPP_CLIENT_NAME" "$OB_TPP_CLIENT_SECRET_HASH" \
    "$OB_TPP_CLIENT_REDIRECT_URI" "$OB_TPP_CLIENT_SCOPES"; OB_TPP_STATUS="$RESULT"
  seed_registered_tpp "$POSTMAN_TPP_CLIENT_ID" "$POSTMAN_TPP_CLIENT_NAME" "$POSTMAN_TPP_CLIENT_SECRET_HASH" \
    "$POSTMAN_TPP_CLIENT_REDIRECT_URI" "$POSTMAN_TPP_CLIENT_SCOPES"; POSTMAN_TPP_STATUS="$RESULT"
  ensure_identifier_codevalues
  ensure_products
  ensure_payment_type
  ensure_charges

  local i
  for i in 0 1 2 3; do seed_client "$i"; done
  seed_guarantors
  for i in 0 1 2 3; do bind_bff_user "$i"; done

  print_summary
}

main "$@"
