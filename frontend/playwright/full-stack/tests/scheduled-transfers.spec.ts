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

import { addBeneficiaryViaUi, expect, seedPayee, test } from '../support/consumer';
import { action, expectToast, fill, pickDate, select, submitOtp } from '../support/ionic';

const ACTIVE = 'standingInstructionStatus.active';
const DELETED = 'standingInstructionStatus.deleted';
const SET_UP = /Your scheduled transfer is set up\./;
const START_DATE_PICKER = 'scheduledTransferStartDate';

/**
 * A week from today. The form suggests tomorrow in the browser's time zone, but the BFF wants a
 * start after Fineract's today, which is in the tenant's zone and can already be tomorrow. A week
 * out leaves room for any zone, as the Cucumber scenarios do.
 */
function aWeekFromToday(): string {
  const date = new Date();
  date.setDate(date.getDate() + 7);
  return [
    date.getFullYear(),
    String(date.getMonth() + 1).padStart(2, '0'),
    String(date.getDate()).padStart(2, '0'),
  ].join('-');
}

test.describe('scheduled transfers', () => {
  test('a consumer sets up a weekly transfer and cancels it, each with an emailed OTP', async ({
    page,
    fineract,
    mailpit,
    signedIn,
  }) => {
    const instructions = () =>
      fineract.standingInstructionStatuses(signedIn.clientId, signedIn.savings.id);

    await page.goto('/transfers');
    await action(page, 'Scheduled transfers').click();
    await expect(page).toHaveURL(/\/transfers\/scheduled$/);
    await expect(page.getByText('No scheduled transfers yet.')).toBeVisible();

    await action(page, 'Set up a scheduled transfer').click();
    await expect(page).toHaveURL(/\/transfers\/scheduled\/new$/);
    await select(page, 'fromAccountId', new RegExp(signedIn.savings.accountNo));
    await select(page, 'toDestination', new RegExp(signedIn.secondSavings.accountNo));
    await fill(page, 'amount', 75);
    await select(page, 'frequency', 'Weekly');
    await pickDate(page, START_DATE_PICKER, aWeekFromToday());
    await mailpit.clear(signedIn.email);
    await page.getByRole('button', { name: 'Set up transfer' }).click();
    await submitOtp(page, await mailpit.otpFor(signedIn.email));

    await expect(page.getByText(SET_UP)).toBeVisible();
    await expect.poll(instructions).toEqual([ACTIVE]);

    await action(page, 'Back to scheduled transfers').click();
    const row = page.locator('tr', { hasText: '75.00' });
    await expect(row).toContainText('Weekly');
    await expect(row).toContainText(signedIn.secondSavings.accountNo);
    await action(row, 'View').click();
    await expect(page).toHaveURL(/\/transfers\/scheduled\/[0-9a-f-]{36}$/);
    await expect(page.getByText('No runs yet.')).toBeVisible();

    await mailpit.clear(signedIn.email);
    await action(page, 'Cancel this transfer').click();
    await submitOtp(page, await mailpit.otpFor(signedIn.email));

    await expectToast(page, 'Scheduled transfer cancelled.');
    await expect(page).toHaveURL(/\/transfers\/scheduled$/);
    await expect(page.getByText('No scheduled transfers yet.')).toBeVisible();
    await expect.poll(instructions).toEqual([DELETED]);
  });

  test('a beneficiary with an active scheduled transfer to it cannot be deleted', async ({
    page,
    fineract,
    mailpit,
    signedIn,
  }) => {
    const payee = await seedPayee(fineract);
    await addBeneficiaryViaUi(page, mailpit, signedIn, {
      name: 'Rent',
      officeName: payee.officeName,
      accountNumber: payee.savings.accountNo,
      transferLimit: 500,
    });

    await page.goto('/transfers/scheduled/new');
    await select(page, 'fromAccountId', new RegExp(signedIn.savings.accountNo));
    await select(page, 'toDestination', 'Rent');
    await fill(page, 'amount', 400);
    await pickDate(page, START_DATE_PICKER, aWeekFromToday());
    await mailpit.clear(signedIn.email);
    await page.getByRole('button', { name: 'Set up transfer' }).click();
    await submitOtp(page, await mailpit.otpFor(signedIn.email));
    await expect(page.getByText(SET_UP)).toBeVisible();

    await page.goto('/beneficiaries');
    const row = page.locator('tr', { hasText: 'Rent' });
    await row.getByRole('button', { name: 'Delete' }).click();

    await expectToast(
      page,
      'This beneficiary has an active scheduled transfer. Cancel the scheduled transfer first.',
    );
    await expect(row).toBeVisible();
  });
});
