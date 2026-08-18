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

import { provideZonelessChangeDetection } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Configuration } from '@bff/client';
import { deviceFingerprint } from '../../core/auth/device-fingerprint';
import { SettingsStore } from './settings.store';

const PASSWORD_CHANGE_INITIATE_URL = '/api/v1/user/password/change/initiate';
const PASSWORD_CHANGE_CONFIRM_URL = '/api/v1/user/password/change/confirm';
const CONSENTS_URL = '/api/v1/openbanking/consents';
const CONSENT_ID = '4f9a2c1e-8b3d-4e6f-9a0b-1c2d3e4f5a6b';
const CONSENT_TPP_CLIENT_ID = 'demo-tpp';
const MASKED_EMAIL = 'a***@example.com';
const STEP_UP_TOKEN = 'tok';

describe('SettingsStore', () => {
  let store: SettingsStore;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: Configuration, useValue: new Configuration({ basePath: '' }) },
      ],
    });
    store = TestBed.inject(SettingsStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('initiatePasswordChange sends the device fingerprint and stores the challenge', () => {
    store.initiatePasswordChange({ currentPassword: 'Current-password-1!' }).subscribe();

    const req = controller.expectOne(PASSWORD_CHANGE_INITIATE_URL);
    expect(req.request.headers.get('X-Device-Fingerprint')).toBe(deviceFingerprint());
    req.flush({ stepUpToken: STEP_UP_TOKEN, sentTo: MASKED_EMAIL });

    expect(store.passwordChangeChallenge()?.stepUpToken).toBe(STEP_UP_TOKEN);
  });

  it('confirmPasswordChange sends the device fingerprint and clears the challenge', () => {
    store.passwordChangeChallenge.set({ stepUpToken: STEP_UP_TOKEN, sentTo: MASKED_EMAIL });

    store
      .confirmPasswordChange({
        stepUpToken: STEP_UP_TOKEN,
        otp: '123456',
        newPassword: 'New-password-12345!',
      })
      .subscribe();

    const req = controller.expectOne(PASSWORD_CHANGE_CONFIRM_URL);
    expect(req.request.headers.get('X-Device-Fingerprint')).toBe(deviceFingerprint());
    req.flush({});

    expect(store.passwordChangeChallenge()).toBeNull();
  });

  it('loadConsents sets the consents signal', () => {
    store.loadConsents();

    controller.expectOne(CONSENTS_URL).flush([
      {
        consentId: CONSENT_ID,
        tppClientId: CONSENT_TPP_CLIENT_ID,
        permissions: ['ReadAccountsBasic', 'ReadBalances'],
        status: 'AUTHORISED',
        creationDateTime: '2026-08-08T10:00:00Z',
        expirationDateTime: '2026-11-06T10:00:00Z',
      },
    ]);

    expect(store.consents().length).toBe(1);
    expect(store.consents()[0].consentId).toBe(CONSENT_ID);
    expect(store.consents()[0].status).toBe('AUTHORISED');
  });

  it('loadConsents leaves the consents signal empty on error', () => {
    store.loadConsents();

    controller
      .expectOne(CONSENTS_URL)
      .flush(
        { code: 'error.msg.consumer.unexpected', defaultMessage: 'unexpected error' },
        { status: 500, statusText: 'Internal Server Error' },
      );

    expect(store.consents()).toEqual([]);
  });

  it('revokeConsent posts to the revoke endpoint and emits the updated status', () => {
    let revoked: string | undefined;
    store.revokeConsent(CONSENT_ID).subscribe((result) => (revoked = result.status));

    const req = controller.expectOne(`${CONSENTS_URL}/${CONSENT_ID}/revoke`);
    expect(req.request.method).toBe('POST');
    req.flush({ consentId: CONSENT_ID, status: 'REVOKED' });

    expect(revoked).toBe('REVOKED');
    expect(store.consents()).toEqual([]);
  });

  it('revokeConsent propagates errors to the subscriber', () => {
    let status: number | undefined;
    store.revokeConsent(CONSENT_ID).subscribe({ error: (err) => (status = err.status) });

    controller.expectOne(`${CONSENTS_URL}/${CONSENT_ID}/revoke`).flush(
      {
        code: 'error.msg.consumer.openbanking.consent.not.revocable',
        defaultMessage: 'not revocable',
      },
      { status: 409, statusText: 'Conflict' },
    );

    expect(status).toBe(409);
  });
});
