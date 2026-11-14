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

import { Locator, Page, expect } from '@playwright/test';

/**
 * Ionic renders form controls as web components with the real <input> inside, and opens
 * selects in an overlay attached to <body>. These helpers hide both details.
 */

/** The native input behind `<ion-input formControlName="…">`, optionally within a scope. */
export function ionInput(scope: Page | Locator, control: string): Locator {
  return scope.locator(`ion-input[formControlName="${control}"] input`);
}

export async function fill(scope: Page | Locator, control: string, value: string | number) {
  const input = ionInput(scope, control);
  await input.fill(String(value));
  await expect(input).toHaveValue(String(value));
}

/**
 * Chooses an option of `<ion-select formControlName="…">` by its visible label. Works for both
 * the popover and the alert interface; the alert needs its OK button pressed.
 */
export async function select(page: Page, control: string, label: string | RegExp) {
  await page.locator(`ion-select[formControlName="${control}"]`).click();
  // `first()`: a backend-fed select can hold several matching options across runs.
  const option = page.getByRole('radio', { name: label }).first();
  await option.click();
  const ok = page.locator('ion-alert button', { hasText: /^OK$/ });
  if (await ok.isVisible()) {
    await ok.click();
  }
  // Wait for this overlay to close. Not "no ion-popover at all": the shell keeps the language
  // switcher's popover in the DOM.
  await expect(option).toBeHidden();
}

/**
 * An action by its label, whether it renders as a button or a link: an `<ion-button>` with
 * `routerLink` renders as an anchor, so its role is link, not button.
 */
export function action(page: Page | Locator, name: string): Locator {
  return page.getByRole('button', { name }).or(page.getByRole('link', { name }));
}

/** Enters a code into the shared OTP step and submits it. */
export async function submitOtp(page: Page, code: string) {
  const otp = page.locator('app-otp');
  await expect(otp).toBeVisible();
  await fill(otp, 'otp', code);
  await otp.getByRole('button', { name: 'Verify' }).click();
}

/** Waits for a toast containing `text`. */
export async function expectToast(page: Page, text: string | RegExp) {
  await expect(page.locator('ion-toast').filter({ hasText: text }).last()).toBeVisible();
}

/**
 * Picks `isoDate` (`YYYY-MM-DD`, on or after the month the picker opens on) in the modal
 * `ion-datetime` that `ion-datetime-button[datetime=id]` opens, and closes it with Done.
 */
export async function pickDate(page: Page, datetimeId: string, isoDate: string) {
  const [year, month, day] = isoDate.split('-').map(Number);
  await page.locator(`ion-datetime-button[datetime="${datetimeId}"]`).click();
  const datetime = page.locator(`ion-datetime#${datetimeId}`);
  const shown = datetime.locator('.calendar-month-year');
  await expect(shown).toBeVisible();

  // The heading reads "November 2026". Step one month at a time, waiting for each to render.
  const monthIndex = (heading: string) => {
    const first = new Date(`1 ${heading.trim()}`);
    return first.getFullYear() * 12 + first.getMonth();
  };
  let steps = year * 12 + (month - 1) - monthIndex(await shown.innerText());
  for (; steps > 0; steps--) {
    const before = await shown.innerText();
    await datetime.getByRole('button', { name: 'Next month' }).click();
    await expect(shown).not.toHaveText(before);
  }

  await datetime
    .locator(`button.calendar-day[data-year="${year}"][data-month="${month}"][data-day="${day}"]`)
    .click();
  await datetime.getByRole('button', { name: 'Done' }).click();
  await expect(datetime).toBeHidden();
}
