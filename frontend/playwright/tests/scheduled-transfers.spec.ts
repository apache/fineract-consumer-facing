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

import { type Page, type Request } from '@playwright/test';

import { expect, test } from '../support/fixtures';
import { JSON_CONTENT_TYPE } from './constants';

const PUBLIC_ID = '7b0c6f1e-2f3a-4c5d-8e9f-0a1b2c3d4e5f';
const LIST_PATH = '/api/v1/scheduled-transfers';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function tomorrow(): string {
  const date = new Date();
  date.setDate(date.getDate() + 1);
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-');
}

function json(body: unknown, status = 200) {
  return { status, contentType: JSON_CONTENT_TYPE, body: JSON.stringify(body) };
}

async function mockSession(page: Page): Promise<void> {
  await page.route('**/api/v1/authentication/refresh', route =>
    route.fulfill(json({ expiresAt: new Date(Date.now() + 15 * 60_000).toISOString() })),
  );
  await page.route('**/api/v1/savings', route =>
    route.fulfill(
      json([
        { id: 1, accountNo: 'S-001', productName: 'Basic Savings', status: 'ACTIVE', currency: 'USD' },
      ]),
    ),
  );
  await page.route('**/api/v1/beneficiaries', route =>
    route.fulfill(json([{ publicId: 'b1', name: 'Asha', fineractAccountId: 2, transferLimit: 100 }])),
  );
}

/** Serves the active list from `rows()`, so a test can change it between requests. */
async function mockList(page: Page, rows: () => unknown[]): Promise<void> {
  await page.route(
    url => url.pathname === LIST_PATH,
    route => route.fulfill(json(rows())),
  );
}

async function fillForm(page: Page, amount: string): Promise<void> {
  await page.locator('ion-select[formControlName="fromAccountId"]').click();
  await page.getByRole('radio', { name: 'Basic Savings · S-001' }).click();
  await page.locator('ion-select[formControlName="toDestination"]').click();
  await page.getByRole('radio', { name: 'Asha' }).click();
  await page.locator('ion-input[formControlName="amount"] input').fill(amount);
  await page.getByRole('button', { name: 'Set up transfer' }).click();
}

async function submitOtp(page: Page): Promise<void> {
  const otpField = page.locator('ion-input[formControlName="otp"] input');
  await expect(otpField).toBeVisible();
  await otpField.fill('ABC123');
  await page.getByRole('button', { name: 'Verify' }).click();
}

test('the transfers page links to scheduled transfers', async ({ page }) => {
  await mockSession(page);
  await mockList(page, () => []);
  await page.route(
    url => url.pathname === '/api/v1/transfers',
    route => route.fulfill(json({ content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 })),
  );

  await page.goto('/transfers');
  await page.getByRole('link', { name: 'Scheduled transfers' }).click();

  await expect(page).toHaveURL(/\/transfers\/scheduled$/);
  await expect(page.getByText('No scheduled transfers yet.')).toBeVisible();
});

test('set up: form -> OTP step-up -> confirm, with a monthly transfer starting tomorrow', async ({
  page,
}) => {
  await mockSession(page);
  let initiate: Request | undefined;
  let confirm: Request | undefined;
  await page.route('**/api/v1/scheduled-transfers/initiate', route => {
    initiate = route.request();
    return route.fulfill(json({ stepUpToken: 'tok', sentTo: 'a***@example.com' }));
  });
  await page.route('**/api/v1/scheduled-transfers/confirm', route => {
    confirm = route.request();
    return route.fulfill(json({ publicId: PUBLIC_ID }));
  });

  await page.goto('/transfers/scheduled/new');
  await fillForm(page, '50');
  await submitOtp(page);

  await expect(page.getByText('Your scheduled transfer is set up.')).toBeVisible();
  const body = {
    fromAccountId: 1,
    toAccountId: 2,
    amount: 50,
    frequency: 'MONTHLY',
    startDate: tomorrow(),
  };
  expect(initiate?.postDataJSON()).toEqual(body);
  expect(confirm?.postDataJSON()).toEqual({ ...body, stepUpToken: 'tok', otp: 'ABC123' });
  expect(confirm?.headers()['idempotency-key']).toMatch(UUID);
});

test('a BFF error code is shown in translation, not as the raw default message', async ({
  page,
}) => {
  await mockSession(page);
  await page.route('**/api/v1/scheduled-transfers/initiate', route =>
    route.fulfill(
      json(
        {
          code: 'error.msg.consumer.scheduledtransfers.command.beneficiary.limit.exceeded',
          defaultMessage: 'This transfer exceeds the limit set for this beneficiary.',
        },
        403,
      ),
    ),
  );

  await page.goto('/transfers/scheduled/new');
  await fillForm(page, '500');

  await expect(
    page.locator('ion-toast').filter({
      hasText: 'That amount is over the transfer limit set for this beneficiary.',
    }),
  ).toBeVisible();
  await expect(page.getByRole('button', { name: 'Set up transfer' })).toBeVisible();
});

test('details: run history with a failed run, then cancel with an OTP', async ({ page }) => {
  await mockSession(page);
  let rows: unknown[] = [
    {
      publicId: PUBLIC_ID,
      fromAccountId: 1,
      toAccountId: 2,
      amount: 50,
      frequency: 'WEEKLY',
      startDate: '2026-12-07',
    },
  ];
  await mockList(page, () => rows);
  await page.route(`**/api/v1/scheduled-transfers/${PUBLIC_ID}/runs**`, route =>
    route.fulfill(
      json({
        content: [
          { date: '2026-12-07', status: 'SUCCEEDED', amount: 50 },
          { date: '2026-12-14', status: 'FAILED' },
        ],
        page: 0,
        size: 10,
        totalElements: 2,
        totalPages: 1,
      }),
    ),
  );
  await page.route(`**/api/v1/scheduled-transfers/${PUBLIC_ID}/cancel/initiate`, route =>
    route.fulfill(json({ stepUpToken: 'cancel-tok', sentTo: 'a***@example.com' })),
  );
  let cancel: Request | undefined;
  await page.route(`**/api/v1/scheduled-transfers/${PUBLIC_ID}/cancel/confirm`, route => {
    cancel = route.request();
    rows = [];
    return route.fulfill({ status: 204 });
  });

  await page.goto('/transfers/scheduled');
  const row = page.locator('tr', { hasText: 'Asha' });
  await expect(row).toContainText('Basic Savings · S-001');
  await expect(row).toContainText('Weekly');
  await row.getByRole('link', { name: 'View' }).click();

  await expect(page).toHaveURL(new RegExp(`/transfers/scheduled/${PUBLIC_ID}$`));
  await expect(page.getByText('Succeeded')).toBeVisible();
  await expect(page.locator('tr', { hasText: 'Failed' })).toContainText('—');

  await page.getByRole('button', { name: 'Cancel this transfer' }).click();
  await submitOtp(page);

  await expect(
    page.locator('ion-toast').filter({ hasText: 'Scheduled transfer cancelled.' }),
  ).toBeVisible();
  await expect(page).toHaveURL(/\/transfers\/scheduled$/);
  await expect(page.getByText('No scheduled transfers yet.')).toBeVisible();
  expect(cancel?.postDataJSON()).toEqual({ stepUpToken: 'cancel-tok', otp: 'ABC123' });
  expect(cancel?.headers()['idempotency-key']).toMatch(UUID);
});

test('feature off: the list says so in place, with no toast and nothing to set up', async ({
  page,
}) => {
  await mockSession(page);
  await page.route(
    url => url.pathname === LIST_PATH,
    route =>
      route.fulfill(
        json(
          {
            code: 'error.msg.consumer.scheduledtransfers.disabled',
            defaultMessage: 'scheduled transfers are not enabled on this deployment',
          },
          404,
        ),
      ),
  );

  await page.goto('/transfers/scheduled');

  await expect(
    page.getByText("Scheduled transfers aren't available on this service."),
  ).toBeVisible();
  await expect(page.getByRole('link', { name: 'Set up a scheduled transfer' })).toHaveCount(0);
  await expect(page.locator('ion-toast')).toHaveCount(0);
});
