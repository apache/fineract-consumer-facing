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
import { RegistrationService } from './registration.service';

describe('RegistrationService', () => {
  let service: RegistrationService;
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
    service = TestBed.inject(RegistrationService);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('submitIdentity POSTs the binding request and returns the registrationId', () => {
    let registrationId: string | undefined;
    service
      .submitIdentity({
        fineractClientId: 42,
        email: 'jane@example.com',
        password: 'Sup3rSecret!Passw0rd',
        documentTypeName: 'SSN',
        documentKey: '123-45-6789',
      })
      .subscribe(data => (registrationId = data.registrationId));

    const req = controller.expectOne('/api/v1/registration/submit');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.fineractClientId).toBe(42);
    req.flush({ registrationId: 'reg-1', status: 'PENDING_OTP', maskedLastFour: '6789' });

    expect(registrationId).toBe('reg-1');
  });

  it('verifyOtp POSTs the token and returns the BOUND status', () => {
    let status: string | undefined;
    service
      .verifyOtp({ registrationId: 'reg-1', token: 'ABC123' })
      .subscribe(data => (status = data.status));

    const req = controller.expectOne('/api/v1/registration/otp/verify');
    expect(req.request.method).toBe('POST');
    expect(req.request.body.token).toBe('ABC123');
    req.flush({ status: 'BOUND' });

    expect(status).toBe('BOUND');
  });
});
