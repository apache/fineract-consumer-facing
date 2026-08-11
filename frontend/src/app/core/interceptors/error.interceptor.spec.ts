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
import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter, Router } from '@angular/router';
import { ToastController } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { ConsumerApiError } from '../../api/consumer-api-error';
import { AuthService } from '../auth/auth.service';
import { I18nService } from '../i18n/i18n.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  const present = vi.fn(() => Promise.resolve());
  const create = vi.fn(() => Promise.resolve({ present }));
  const refresh = vi.fn(() => of(void 0));
  const clearSession = vi.fn();
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    present.mockClear();
    create.mockClear();
    refresh.mockClear();
    clearSession.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: ToastController, useValue: { create } },
        { provide: AuthService, useValue: { refresh, clearSession } },
        {
          provide: I18nService,
          useValue: {
            translate: (key: string) => (key === 'common.action.dismiss' ? 'Dismiss' : key),
          },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('renders a ConsumerApiError envelope as a toast with defaultMessage', () => {
    const envelope: ConsumerApiError = {
      code: 'savings.account.not.owned',
      defaultMessage: 'You are not allowed to view this account.',
    };

    http.get('/api/v1/savings').subscribe({ error: () => undefined });

    controller.expectOne('/api/v1/savings').flush(envelope, {
      status: 403,
      statusText: 'Forbidden',
    });

    expect(create).toHaveBeenCalledWith({
      message: envelope.defaultMessage,
      duration: 5000,
      position: 'bottom',
      buttons: [{ text: 'Dismiss', role: 'cancel' }],
    });
  });

  it('does not refresh or redirect on a business 401 carrying a ConsumerApiError code', () => {
    const router = TestBed.inject(Router);
    const navigate = vi.spyOn(router, 'navigate');
    const envelope: ConsumerApiError = {
      code: 'error.msg.consumer.auth.two.factor.invalid',
      defaultMessage: 'invalid two-factor challenge',
    };
    let received: unknown;

    http
      .post('/api/v1/authentication/2fa', {})
      .subscribe({ error: (error: unknown) => (received = error) });

    controller.expectOne('/api/v1/authentication/2fa').flush(envelope, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(refresh).not.toHaveBeenCalled();
    expect(clearSession).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
    expect(received).toBeInstanceOf(HttpErrorResponse);
    expect(create).toHaveBeenCalledWith({
      message: envelope.defaultMessage,
      duration: 5000,
      position: 'bottom',
      buttons: [{ text: 'Dismiss', role: 'cancel' }],
    });
  });

  it('refreshes and retries on a session 401 with an empty body', () => {
    let result: unknown;

    http.get('/api/v1/savings').subscribe({ next: (value) => (result = value) });

    controller.expectOne('/api/v1/savings').flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });

    expect(refresh).toHaveBeenCalledTimes(1);
    controller.expectOne('/api/v1/savings').flush([{ id: 1 }]);
    expect(result).toEqual([{ id: 1 }]);
  });
});
