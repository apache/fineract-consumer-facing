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
import { BeneficiariesStore } from './beneficiaries.store';

describe('BeneficiariesStore', () => {
  let store: BeneficiariesStore;
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
    store = TestBed.inject(BeneficiariesStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('load populates beneficiaries', () => {
    store.load().subscribe();
    controller
      .expectOne('/api/v1/beneficiaries')
      .flush([{ publicId: 'b1', name: 'Asha', accountType: 'SAVINGS', transferLimit: 100 }]);

    expect(store.beneficiaries().length).toBe(1);
    expect(store.beneficiaries()[0].name).toBe('Asha');
  });

  it('loadTemplate populates account type options', () => {
    store.loadTemplate().subscribe();
    controller
      .expectOne('/api/v1/beneficiaries/template')
      .flush({ accountTypeOptions: ['SAVINGS', 'LOAN'] });

    expect(store.accountTypeOptions()).toEqual(['SAVINGS', 'LOAN']);
  });

  it('initiateAdd stores the challenge', () => {
    store
      .initiateAdd({
        name: 'Asha',
        officeName: 'Head Office',
        accountNumber: '000000001',
        accountType: 'SAVINGS',
      })
      .subscribe();
    controller
      .expectOne('/api/v1/beneficiaries/initiate')
      .flush({ stepUpToken: 'tok', sentTo: 'a***@example.com' });

    expect(store.challenge()?.stepUpToken).toBe('tok');
  });

  it('confirmAdd clears the challenge', () => {
    store.challenge.set({ stepUpToken: 'tok' });

    store
      .confirmAdd({
        stepUpToken: 'tok',
        otp: 'ABC123',
        name: 'Asha',
        officeName: 'Head Office',
        accountNumber: '000000001',
        accountType: 'SAVINGS',
      })
      .subscribe();
    controller
      .expectOne('/api/v1/beneficiaries/confirm')
      .flush({ publicId: 'b1', name: 'Asha', accountType: 'SAVINGS' });

    expect(store.challenge()).toBeNull();
  });

  it('delete issues DELETE for the beneficiary', () => {
    store.delete('b1').subscribe();
    const req = controller.expectOne('/api/v1/beneficiaries/b1');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });
  });
});
