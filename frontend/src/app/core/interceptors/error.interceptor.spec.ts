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
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { MatSnackBar } from '@angular/material/snack-bar';
import { TranslateService } from '@ngx-translate/core';
import { ConsumerApiError } from '../../api/consumer-api-error';
import { AuthService } from '../auth/auth.service';
import { errorInterceptor } from './error.interceptor';

describe('errorInterceptor', () => {
  const open = vi.fn();
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    open.mockClear();
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideRouter([]),
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: MatSnackBar, useValue: { open } },
        { provide: AuthService, useValue: {} },
        {
          provide: TranslateService,
          useValue: {
            instant: (key: string) => (key === 'common.action.dismiss' ? 'Dismiss' : key),
          },
        },
      ],
    });
    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('renders a ConsumerApiError envelope as a snackbar with defaultMessage', () => {
    const envelope: ConsumerApiError = {
      code: 'savings.account.not.owned',
      defaultMessage: 'You are not allowed to view this account.',
    };

    http.get('/api/v1/savings').subscribe({ error: () => undefined });

    controller.expectOne('/api/v1/savings').flush(envelope, {
      status: 403,
      statusText: 'Forbidden',
    });

    expect(open).toHaveBeenCalledWith(envelope.defaultMessage, 'Dismiss', { duration: 5000 });
  });
});
