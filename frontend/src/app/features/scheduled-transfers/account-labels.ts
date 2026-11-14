/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

export interface LabelledAccount {
  id?: number;
  accountNo?: string;
  productName?: string;
}

export interface LabelledBeneficiary {
  name?: string;
  fineractAccountId?: number;
}

/** "Product · account number", the same label the transfer form shows. */
export function accountLabel(account: LabelledAccount): string {
  return [account.productName, account.accountNo].filter(Boolean).join(' · ') || String(account.id);
}

/**
 * Display labels for the account ids a scheduled transfer carries: the customer's own savings
 * accounts, then beneficiary names. Own accounts win if a beneficiary points at one.
 */
export function accountLabels(
  accounts: readonly LabelledAccount[],
  beneficiaries: readonly LabelledBeneficiary[],
): ReadonlyMap<number, string> {
  const labels = new Map<number, string>();
  for (const beneficiary of beneficiaries) {
    if (beneficiary.fineractAccountId != null && beneficiary.name) {
      labels.set(beneficiary.fineractAccountId, beneficiary.name);
    }
  }
  for (const account of accounts) {
    if (account.id != null) {
      labels.set(account.id, accountLabel(account));
    }
  }
  return labels;
}

/** The label for an account id, or `#id` when it is neither an own account nor a beneficiary. */
export function labelFor(labels: ReadonlyMap<number, string>, id: number | undefined): string {
  return id == null ? '' : (labels.get(id) ?? `#${id}`);
}
