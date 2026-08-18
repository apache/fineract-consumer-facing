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
import { DemoStore } from './demo.store';

const AUDIT_EVENTS_URL = '/api/v1/audit/events';

describe('DemoStore', () => {
  let store: DemoStore;
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
    store = TestBed.inject(DemoStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('loadEvents forwards paging as query params and stores the rows', () => {
    store.loadEvents(2, 10);

    const req = controller.expectOne((r) => r.url === AUDIT_EVENTS_URL);
    expect(req.request.params.get('page')).toBe('2');
    expect(req.request.params.get('size')).toBe('10');
    req.flush([
      {
        eventType: 'SENSITIVE_VIEW',
        severity: 'INFO',
        source: 'FRONTEND',
        receivedAt: '2026-08-01T12:00:00Z',
      },
    ]);

    expect(store.events().length).toBe(1);
    expect(store.loading()).toBe(false);
  });

  it('loadEvents clears previous rows and stops loading on error', () => {
    store.events.set([{ eventType: 'NAVIGATION' }]);

    store.loadEvents(0, 10);
    expect(store.events()).toEqual([]);

    controller
      .expectOne((r) => r.url === AUDIT_EVENTS_URL)
      .flush(null, { status: 500, statusText: 'Server Error' });

    expect(store.loading()).toBe(false);
  });
});
