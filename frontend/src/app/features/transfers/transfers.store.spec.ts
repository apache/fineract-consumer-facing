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
import { TransfersStore } from './transfers.store';

describe('TransfersStore', () => {
  let store: TransfersStore;
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
    store = TestBed.inject(TransfersStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('initiate stores the challenge', () => {
    store
      .initiate({ fromAccountId: 1, toAccountId: 2, toAccountType: 'SAVINGS', amount: 50 })
      .subscribe();
    controller
      .expectOne('/api/v1/transfers/initiate')
      .flush({ stepUpToken: 'tok', sentTo: 'a***@example.com' });

    expect(store.challenge()?.stepUpToken).toBe('tok');
  });

  it('confirm stores the result and clears the challenge', () => {
    store.challenge.set({ stepUpToken: 'tok' });

    store
      .confirm({
        stepUpToken: 'tok',
        otp: 'ABC123',
        fromAccountId: 1,
        toAccountId: 2,
        toAccountType: 'SAVINGS',
        amount: 50,
      })
      .subscribe();
    controller
      .expectOne('/api/v1/transfers/confirm')
      .flush({ transferId: 9, fromAccountId: 1, toAccountId: 2, amount: 50 });

    expect(store.result()?.transferId).toBe(9);
    expect(store.challenge()).toBeNull();
  });
});
