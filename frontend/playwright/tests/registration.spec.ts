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

import { expect, test } from '@playwright/test';

import { JSON_CONTENT_TYPE } from './constants';

const OTP_CODE = 'ABC123';
const DOCUMENT_KEY = 'SECRET-DOC-9999';

test('registration walks identity -> OTP -> success and never persists the OTP or document key', async ({
  page,
}) => {
  await page.route('**/api/v1/registration/submit', route =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ registrationId: 'reg-1', status: 'PENDING_OTP', maskedLastFour: '6789' }),
    }),
  );
  await page.route('**/api/v1/registration/otp/send', route =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ sentTo: 'j***@example.com', tokenLiveTimeInSec: 300 }),
    }),
  );
  await page.route('**/api/v1/registration/otp/verify', route =>
    route.fulfill({
      status: 200,
      contentType: JSON_CONTENT_TYPE,
      body: JSON.stringify({ status: 'BOUND' }),
    }),
  );

  await page.goto('/register');

  await page.locator('input[formControlName="fineractClientId"]').fill('42');
  await page.locator('input[formControlName="email"]').fill('jane@example.com');
  await page.locator('input[formControlName="password"]').fill('Sup3rSecret!Passw0rd');
  await page.locator('mat-select[formControlName="documentTypeName"]').click();
  await page.getByRole('option', { name: 'SSN' }).click();
  await page.locator('input[formControlName="documentKey"]').fill(DOCUMENT_KEY);
  await page.getByRole('button', { name: 'Continue' }).click();

  const otpField = page.locator('input[formControlName="otp"]');
  await expect(otpField).toBeVisible();
  await otpField.fill(OTP_CODE);
  await page.getByRole('button', { name: 'Verify' }).click();

  await expect(page.getByText(/account is created/i)).toBeVisible();

  const storage = await page.evaluate(() =>
    JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }),
  );
  expect(storage).not.toContain(OTP_CODE);
  expect(storage).not.toContain(DOCUMENT_KEY);
});
