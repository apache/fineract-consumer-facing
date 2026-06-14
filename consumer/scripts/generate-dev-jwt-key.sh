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

# Generates the dev/CI JWT signing keypair (EC P-256, for ES256) as a PEM file.
# Dev-only convenience — prod injects the PEM from a secret manager instead.

set -euo pipefail

cd "$(dirname "$0")/.."

KEY_FILE="dev-jwt-key.pem"

if [ -f "$KEY_FILE" ]; then
  echo "Dev JWT key already exists at consumer/$KEY_FILE — leaving it untouched."
  exit 0
fi

umask 077

openssl ecparam -name prime256v1 -genkey -noout \
  | openssl pkcs8 -topk8 -nocrypt -out "$KEY_FILE"
openssl ec -in "$KEY_FILE" -pubout >> "$KEY_FILE" 2>/dev/null

echo "Generated dev JWT signing key at consumer/$KEY_FILE"
