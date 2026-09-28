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
import { correlationIdInterceptor } from './correlation-id.interceptor';

describe('correlationIdInterceptor', () => {
  let http: HttpClient;
  let controller: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideZonelessChangeDetection(),
        provideHttpClient(withInterceptors([correlationIdInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    controller.verify();
  });

  it('should add X-Correlation-ID header to /api requests', () => {
    http.get('/api/v1/savings').subscribe();

    const req = controller.expectOne('/api/v1/savings');
    expect(req.request.headers.has('X-Correlation-ID')).toBe(true);
    const correlationId = req.request.headers.get('X-Correlation-ID');
    expect(correlationId).toBeTruthy();
    expect(correlationId?.length).toBeGreaterThan(0);
    req.flush({});
  });

  it('should preserve existing X-Correlation-ID header if already present', () => {
    const existingId = 'existing-test-correlation-id';
    http.get('/api/v1/loans', { headers: { 'X-Correlation-ID': existingId } }).subscribe();

    const req = controller.expectOne('/api/v1/loans');
    expect(req.request.headers.get('X-Correlation-ID')).toBe(existingId);
    req.flush({});
  });

  it('should not add X-Correlation-ID header to non-api requests', () => {
    http.get('/i18n/en.json').subscribe();

    const req = controller.expectOne('/i18n/en.json');
    expect(req.request.headers.has('X-Correlation-ID')).toBe(false);
    req.flush({});
  });
});
