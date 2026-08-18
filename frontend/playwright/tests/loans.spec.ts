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

import { type Page, expect, test } from '@playwright/test';

import { JSON_CONTENT_TYPE } from './constants';

const LOAN_ID = 1;

async function mockResumedSession(page: Page): Promise<void> {
  await page.route('**/api/v1/authentication/refresh', (route) =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ expiresAt: new Date(Date.now() + 15 * 60_000).toISOString() }),
    }),
  );
}

async function mockLoanDetail(page: Page, status: string): Promise<void> {
  await page.route(
    (url) => url.pathname === `/api/v1/loans/${LOAN_ID}`,
    (route) =>
      route.fulfill({
        status: 200,
        contentType: JSON_CONTENT_TYPE,
        body: JSON.stringify({
          id: LOAN_ID,
          accountNo: 'L-001',
          productName: 'Personal Loan',
          status,
          active: status === 'loanStatusType.active',
          currency: 'USD',
          principalDisbursed: 1000,
          totalOutstanding: 800,
          interestOutstanding: 50,
          annualInterestRate: 12,
          nextDueAmount: 100,
          nextDueDate: '2026-09-01',
        }),
      }),
  );
  await page.route(
    (url) => url.pathname === `/api/v1/loans/${LOAN_ID}/charges`,
    (route) =>
      route.fulfill({ status: 200, contentType: JSON_CONTENT_TYPE, body: JSON.stringify([]) }),
  );
  await page.route(
    (url) => url.pathname === `/api/v1/loans/${LOAN_ID}/guarantors`,
    (route) =>
      route.fulfill({ status: 200, contentType: JSON_CONTENT_TYPE, body: JSON.stringify([]) }),
  );
  await page.route(
    (url) => url.pathname === `/api/v1/loans/${LOAN_ID}/transactions`,
    (route) =>
      route.fulfill({
        status: 200,
        contentType: JSON_CONTENT_TYPE,
        body: JSON.stringify({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }),
      }),
  );
}

async function mockSavingsAccounts(page: Page): Promise<void> {
  await page.route(
    (url) => url.pathname === '/api/v1/savings',
    (route) =>
      route.fulfill({
        status: 200,
        contentType: JSON_CONTENT_TYPE,
        body: JSON.stringify([
          {
            id: 1,
            accountNo: 'S-001',
            productName: 'Basic Savings',
            status: 'ACTIVE',
            currency: 'USD',
          },
        ]),
      }),
  );
}

async function mockInitiate(page: Page): Promise<void> {
  await page.route('**/api/v1/transfers/initiate', (route) =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ stepUpToken: 'tok', sentTo: 'a***@example.com' }),
    }),
  );
}

async function fillRepayForm(page: Page): Promise<void> {
  await page.locator('ion-select[formControlName="fromAccountId"]').click();
  await page.getByRole('radio', { name: 'Basic Savings · S-001' }).click();
  await page.locator('ion-input[formControlName="amount"] input').fill('100');
  await page.getByRole('button', { name: 'Make payment' }).click();
}

test('loans route is guarded: redirects to /login when unauthenticated', async ({ page }) => {
  await page.goto('/loans');
  await expect(page).toHaveURL(/\/login$/);
});

test('loan detail: the make-a-payment button is hidden unless the loan is active', async ({
  page,
}) => {
  await mockResumedSession(page);
  await mockLoanDetail(page, 'loanStatusType.closed.obligations.met');

  await page.goto(`/loans/${LOAN_ID}`);

  await expect(page.getByText('L-001')).toBeVisible();
  await expect(page.getByRole('link', { name: 'Make a payment' })).toHaveCount(0);
});

test('logged-in: loan detail -> repay form -> OTP step-up -> confirm -> success', async ({
  page,
}) => {
  await mockResumedSession(page);
  await mockLoanDetail(page, 'loanStatusType.active');
  await mockSavingsAccounts(page);
  await mockInitiate(page);
  await page.route('**/api/v1/transfers/confirm', (route) =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({
        transferId: 200,
        fromAccountId: 1,
        toAccountId: LOAN_ID,
        amount: 100,
      }),
    }),
  );

  await page.goto(`/loans/${LOAN_ID}`);
  await page.getByRole('link', { name: 'Make a payment' }).click();

  await expect(page).toHaveURL(new RegExp(`/loans/${LOAN_ID}/repay$`));
  await fillRepayForm(page);

  const otpField = page.locator('ion-input[formControlName="otp"] input');
  await expect(otpField).toBeVisible();
  await otpField.fill('ABC123');
  await page.getByRole('button', { name: 'Verify' }).click();

  await expect(page.getByText(/payment complete/i)).toBeVisible();
});

test('wrong OTP: surfaces the ConsumerApiError snackbar and stays on the OTP step', async ({
  page,
}) => {
  await mockResumedSession(page);
  await mockLoanDetail(page, 'loanStatusType.active');
  await mockSavingsAccounts(page);
  await mockInitiate(page);
  await page.route('**/api/v1/transfers/confirm', (route) =>
    route.fulfill({
      status: 400,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ code: 'otp.invalid', defaultMessage: 'Invalid verification code' }),
    }),
  );

  await page.goto(`/loans/${LOAN_ID}/repay`);
  await fillRepayForm(page);

  const otpField = page.locator('ion-input[formControlName="otp"] input');
  await expect(otpField).toBeVisible();
  await otpField.fill('WRONG1');
  await page.getByRole('button', { name: 'Verify' }).click();

  await expect(page.getByText('Invalid verification code')).toBeVisible();
  await expect(otpField).toBeVisible();
});
