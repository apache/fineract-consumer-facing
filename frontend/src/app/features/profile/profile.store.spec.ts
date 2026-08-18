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
import { ProfileStore } from './profile.store';

const PROFILE_URL = '/api/v1/user/profile';
const CHARGES_URL = '/api/v1/user/charges';
const IMAGE_URL = '/api/v1/user/image';
const DISPLAY_NAME = 'Asha Kumar';
const MASKED_EMAIL = 'a***@example.com';
const CHARGE_NAME = 'Annual Fee';
const IMAGE_DATA_URI = 'data:image/png;base64,AAAA';

describe('ProfileStore', () => {
  let store: ProfileStore;
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
    store = TestBed.inject(ProfileStore);
    controller = TestBed.inject(HttpTestingController);
  });

  afterEach(() => controller.verify());

  it('loadProfile sets the profile signal', () => {
    store.loadProfile();

    controller.expectOne(PROFILE_URL).flush({
      displayName: DISPLAY_NAME,
      accountNo: '000000001',
      active: true,
      memberSince: '2025-01-15',
      maskedEmail: MASKED_EMAIL,
      maskedMobile: '***-***-1234',
      kycVerified: true,
      hasImage: false,
    });

    expect(store.profile()?.displayName).toBe(DISPLAY_NAME);
    expect(store.profile()?.maskedEmail).toBe(MASKED_EMAIL);
    expect(store.loading()).toBe(false);
  });

  it('loadCharges passes status/page/size params and maps charges plus total', () => {
    store.loadCharges({ status: 'active', page: 2, size: 5 });

    const req = controller.expectOne(
      (r) =>
        r.url === CHARGES_URL &&
        r.params.get('status') === 'active' &&
        r.params.get('page') === '2' &&
        r.params.get('size') === '5',
    );
    req.flush({
      charges: [
        {
          id: 11,
          name: CHARGE_NAME,
          currencyCode: 'USD',
          amount: 25,
          amountOutstanding: 25,
          active: true,
        },
      ],
      totalFilteredRecords: 9,
    });

    expect(store.charges().length).toBe(1);
    expect(store.charges()[0].name).toBe(CHARGE_NAME);
    expect(store.totalFilteredRecords()).toBe(9);
  });

  it('loadImage passes maxWidth/maxHeight params and sets the image signal', () => {
    store.loadImage();

    const req = controller.expectOne(
      (r) =>
        r.url === IMAGE_URL &&
        r.params.get('maxWidth') === '256' &&
        r.params.get('maxHeight') === '256',
    );
    req.flush({ imageDataUri: IMAGE_DATA_URI });

    expect(store.image()?.imageDataUri).toBe(IMAGE_DATA_URI);
  });
});
