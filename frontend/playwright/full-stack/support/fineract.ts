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

import { APIRequestContext, expect, request } from '@playwright/test';
import { FINERACT_API_URL, FINERACT_PASSWORD, FINERACT_TENANT, FINERACT_USERNAME } from './env';

/**
 * The back-office side of the e2e journeys: what staff do in Fineract before a consumer can
 * self-register. It creates clients, identifiers, products and accounts over Fineract's REST API
 * with the default `mifos` user, mirroring the Cucumber suite's FineractSeeder.
 */

const HEAD_OFFICE_ID = 1;
const LEGAL_FORM_PERSON = 1;
const LOCALE = 'en';
const DATE_FORMAT = 'dd MMMM yyyy';
const START_DATE = '01 January 2023';
const USD = 'USD';
const PAYMENT_TYPE_ID = 2;
const CUSTOMER_IDENTIFIER_CODE = 'Customer Identifier';
/** The registration form offers SSN and Aadhaar; the default Fineract tenant has neither. */
export const DOCUMENT_TYPE = 'SSN';

export interface SeededAccount {
  id: number;
  accountNo: string;
}

export interface SeededClient {
  clientId: number;
  displayName: string;
  documentKey: string;
  officeName: string;
}

const uid = () => Math.random().toString(36).slice(2, 10).toUpperCase();

export class Fineract {
  private savingsProductId?: number;
  private loanProductId?: number;

  private constructor(private readonly api: APIRequestContext) {}

  static async connect(): Promise<Fineract> {
    const credentials = Buffer.from(`${FINERACT_USERNAME}:${FINERACT_PASSWORD}`).toString('base64');
    return new Fineract(
      await request.newContext({
        baseURL: FINERACT_API_URL,
        extraHTTPHeaders: {
          Authorization: `Basic ${credentials}`,
          'Fineract-Platform-TenantId': FINERACT_TENANT,
        },
      }),
    );
  }

  async dispose(): Promise<void> {
    await this.api.dispose();
  }

  /** An active client with an SSN identifier, ready to self-register. */
  async createClient(): Promise<SeededClient> {
    const lastname = `E2E-${uid()}`;
    const { clientId } = await this.post('clients', {
      officeId: HEAD_OFFICE_ID,
      firstname: 'Playwright',
      lastname,
      active: true,
      activationDate: START_DATE,
      legalFormId: LEGAL_FORM_PERSON,
      locale: LOCALE,
      dateFormat: DATE_FORMAT,
    });
    const documentKey = `${uid()}${uid()}`.slice(0, 9);
    await this.post(`clients/${clientId}/identifiers`, {
      documentTypeId: await this.documentTypeId(),
      documentKey,
      status: 'ACTIVE',
    });
    const client = await this.get(`clients/${clientId}`);
    return {
      clientId,
      displayName: client.displayName,
      documentKey,
      officeName: client.officeName,
    };
  }

  /** An approved and activated USD savings account, optionally funded. */
  async openSavings(clientId: number, deposit?: number): Promise<SeededAccount> {
    await this.ensureUsd();
    const { savingsId } = await this.post('savingsaccounts', {
      clientId,
      productId: await this.savingsProduct(),
      submittedOnDate: START_DATE,
      locale: LOCALE,
      dateFormat: DATE_FORMAT,
    });
    await this.post(`savingsaccounts/${savingsId}?command=approve`, {
      approvedOnDate: START_DATE,
      locale: LOCALE,
      dateFormat: DATE_FORMAT,
    });
    await this.post(`savingsaccounts/${savingsId}?command=activate`, {
      activatedOnDate: START_DATE,
      locale: LOCALE,
      dateFormat: DATE_FORMAT,
    });
    if (deposit) {
      await this.post(`savingsaccounts/${savingsId}/transactions?command=deposit`, {
        transactionAmount: deposit,
        transactionDate: START_DATE,
        paymentTypeId: PAYMENT_TYPE_ID,
        locale: LOCALE,
        dateFormat: DATE_FORMAT,
      });
    }
    const account = await this.get(`savingsaccounts/${savingsId}`);
    return { id: savingsId, accountNo: account.accountNo };
  }

  /** An approved and disbursed 10,000 USD loan. */
  async disburseLoan(clientId: number): Promise<SeededAccount> {
    await this.ensureUsd();
    const { loanId } = await this.post('loans', {
      clientId,
      productId: await this.loanProduct(),
      principal: 10000,
      loanTermFrequency: 5,
      loanTermFrequencyType: 2,
      numberOfRepayments: 5,
      repaymentEvery: 1,
      repaymentFrequencyType: 2,
      interestRatePerPeriod: 2,
      amortizationType: 1,
      interestType: 1,
      interestCalculationPeriodType: 1,
      transactionProcessingStrategyCode: 'mifos-standard-strategy',
      expectedDisbursementDate: START_DATE,
      submittedOnDate: START_DATE,
      loanType: 'individual',
      locale: LOCALE,
      dateFormat: DATE_FORMAT,
    });
    await this.post(`loans/${loanId}?command=approve`, {
      approvedOnDate: START_DATE,
      locale: LOCALE,
      dateFormat: DATE_FORMAT,
    });
    await this.post(`loans/${loanId}?command=disburse`, {
      actualDisbursementDate: START_DATE,
      locale: LOCALE,
      dateFormat: DATE_FORMAT,
    });
    const loan = await this.get(`loans/${loanId}`);
    return { id: loanId, accountNo: loan.accountNo };
  }

  async savingsBalance(savingsId: number): Promise<number> {
    return (await this.get(`savingsaccounts/${savingsId}`)).summary.accountBalance;
  }

  async loanStatus(loanId: number): Promise<string> {
    return (await this.get(`loans/${loanId}`)).status.code;
  }

  async loanOutstanding(loanId: number): Promise<number> {
    return (await this.get(`loans/${loanId}`)).summary.totalOutstanding;
  }

  /**
   * Status codes of the client's standing instructions out of one savings account, deleted ones
   * included: `standingInstructionStatus.active`, `.disabled` or `.deleted`.
   */
  async standingInstructionStatuses(clientId: number, fromSavingsId: number): Promise<string[]> {
    const page = await this.get(`standinginstructions?clientId=${clientId}&limit=100`);
    return (page.pageItems as { status: { code: string }; fromAccount: { id: number } }[])
      .filter((instruction) => instruction.fromAccount.id === fromSavingsId)
      .map((instruction) => instruction.status.code);
  }

  private async documentTypeId(): Promise<number> {
    const codes = (await this.get('codes')) as { id: number; name: string }[];
    const code = codes.find((c) => c.name === CUSTOMER_IDENTIFIER_CODE);
    expect(code, `Fineract code "${CUSTOMER_IDENTIFIER_CODE}"`).toBeTruthy();
    const values = (await this.get(`codes/${code!.id}/codevalues`)) as {
      id: number;
      name: string;
    }[];
    const existing = values.find((v) => v.name === DOCUMENT_TYPE);
    if (existing) {
      return existing.id;
    }
    const created = await this.post(`codes/${code!.id}/codevalues`, {
      name: DOCUMENT_TYPE,
      position: values.length + 1,
      isActive: true,
    });
    return created.subResourceId;
  }

  private async ensureUsd(): Promise<void> {
    const config = await this.get('currencies');
    const selected = (config.selectedCurrencyOptions ?? []).map((c: { code: string }) => c.code);
    if (!selected.includes(USD)) {
      await this.put('currencies', { currencies: [...selected, USD] });
    }
  }

  private async savingsProduct(): Promise<number> {
    if (!this.savingsProductId) {
      const suffix = uid().slice(0, 4);
      this.savingsProductId = (
        await this.post('savingsproducts', {
          name: `E2E-SAV-${suffix}`,
          shortName: suffix,
          currencyCode: USD,
          digitsAfterDecimal: 2,
          inMultiplesOf: 0,
          nominalAnnualInterestRate: 5,
          interestCompoundingPeriodType: 4,
          interestPostingPeriodType: 4,
          interestCalculationType: 1,
          interestCalculationDaysInYearType: 365,
          accountingRule: 1,
          locale: LOCALE,
        })
      ).resourceId;
    }
    return this.savingsProductId!;
  }

  private async loanProduct(): Promise<number> {
    if (!this.loanProductId) {
      const suffix = uid().slice(0, 4);
      this.loanProductId = (
        await this.post('loanproducts', {
          name: `E2E-LOAN-${suffix}`,
          shortName: suffix,
          currencyCode: USD,
          digitsAfterDecimal: 2,
          inMultiplesOf: 0,
          principal: 10000,
          numberOfRepayments: 5,
          repaymentEvery: 1,
          repaymentFrequencyType: 2,
          interestRatePerPeriod: 2,
          interestRateFrequencyType: 2,
          amortizationType: 1,
          interestType: 1,
          interestCalculationPeriodType: 1,
          transactionProcessingStrategyCode: 'mifos-standard-strategy',
          accountingRule: 1,
          daysInYearType: 365,
          daysInMonthType: 30,
          isInterestRecalculationEnabled: false,
          locale: LOCALE,
        })
      ).resourceId;
    }
    return this.loanProductId!;
  }

  private async get(path: string) {
    const response = await this.api.get(path);
    expect(response.ok(), `GET ${path}: ${await response.text()}`).toBeTruthy();
    return response.json();
  }

  private async post(path: string, body: unknown) {
    const response = await this.api.post(path, { data: body });
    expect(response.ok(), `POST ${path}: ${await response.text()}`).toBeTruthy();
    return response.json();
  }

  private async put(path: string, body: unknown) {
    const response = await this.api.put(path, { data: body });
    expect(response.ok(), `PUT ${path}: ${await response.text()}`).toBeTruthy();
    return response.json();
  }
}
