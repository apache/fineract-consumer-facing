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

import { type Page } from '@playwright/test';

import { expect, test } from '../support/fixtures';
import { JSON_CONTENT_TYPE } from './constants';

async function mockResumedSession(page: Page): Promise<void> {
  await page.route('**/api/v1/authentication/refresh', route =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ expiresAt: new Date(Date.now() + 15 * 60_000).toISOString() }),
    }),
  );
}

async function mockAccountData(page: Page): Promise<void> {
  await page.route('**/api/v1/savings', route =>
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
  await page.route('**/api/v1/beneficiaries', route =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify([
        { publicId: 'b1', name: 'Asha', fineractAccountId: 2, transferLimit: 100 },
      ]),
    }),
  );
}

async function fillTransferForm(page: Page): Promise<void> {
  await page.locator('ion-select[formControlName="fromAccountId"]').click();
  await page.getByRole('radio', { name: 'Basic Savings · S-001' }).click();
  await page.locator('ion-select[formControlName="toDestination"]').click();
  await page.getByRole('radio', { name: 'Asha' }).click();
  await page.locator('ion-input[formControlName="amount"] input').fill('50');
  await page.getByRole('button', { name: 'Send transfer' }).click();
}

test('transfers route is guarded: redirects to /login when unauthenticated', async ({ page }) => {
  await page.route('**/api/v1/authentication/refresh', route => route.fulfill({ status: 401 }));

  await page.goto('/transfers');
  await expect(page).toHaveURL(/\/login$/);
});

test('history page: the new-transfer button navigates to the form', async ({ page }) => {
  await mockResumedSession(page);
  await mockAccountData(page);
  await page.route(
    url => url.pathname === '/api/v1/transfers',
    route =>
      route.fulfill({ status: 200, contentType: JSON_CONTENT_TYPE, body: JSON.stringify([]) }),
  );

  await page.goto('/transfers');
  await page.getByRole('link', { name: /transfer money/i }).click();

  await expect(page).toHaveURL(/\/transfers\/new$/);
  await expect(page.locator('ion-select[formControlName="fromAccountId"]')).toBeVisible();
});

test('logged-in: form -> OTP step-up -> confirm -> success', async ({ page }) => {
  await mockResumedSession(page);
  await mockAccountData(page);
  await page.route('**/api/v1/transfers/initiate', route =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ stepUpToken: 'tok', sentTo: 'a***@example.com' }),
    }),
  );
  await page.route('**/api/v1/transfers/confirm', route =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ transferId: 100, fromAccountId: 1, toAccountId: 2, amount: 50 }),
    }),
  );

  await page.goto('/transfers/new');
  await expect(page).toHaveURL(/\/transfers\/new$/);

  await fillTransferForm(page);

  const otpField = page.locator('ion-input[formControlName="otp"] input');
  await expect(otpField).toBeVisible();
  await otpField.fill('ABC123');
  await page.getByRole('button', { name: 'Verify' }).click();

  await expect(page.getByText(/transfer complete/i)).toBeVisible();
});

test('wrong OTP: surfaces the ConsumerApiError snackbar and stays on the OTP step', async ({
  page,
}) => {
  await mockResumedSession(page);
  await mockAccountData(page);
  await page.route('**/api/v1/transfers/initiate', route =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ stepUpToken: 'tok', sentTo: 'a***@example.com' }),
    }),
  );
  await page.route('**/api/v1/transfers/confirm', route =>
    route.fulfill({
      status: 400,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ code: 'otp.invalid', defaultMessage: 'Invalid verification code' }),
    }),
  );

  await page.goto('/transfers/new');
  await fillTransferForm(page);

  const otpField = page.locator('ion-input[formControlName="otp"] input');
  await expect(otpField).toBeVisible();
  await otpField.fill('WRONG1');
  await page.getByRole('button', { name: 'Verify' }).click();

  await expect(page.getByText('Invalid verification code')).toBeVisible();
  await expect(otpField).toBeVisible();
});
