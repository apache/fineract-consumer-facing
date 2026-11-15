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

import { provideZonelessChangeDetection, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import { ScheduledTransferQueryData } from '../../core/adapters';
import { provideI18nTesting } from '../../testing/i18n-testing';
import { BeneficiariesStore } from '../beneficiaries/beneficiaries.store';
import { SavingsStore } from '../savings/savings.store';
import { ScheduledTransfersComponent } from './scheduled-transfers.component';
import { ScheduledTransfersStore } from './scheduled-transfers.store';

const transfers = signal<ScheduledTransferQueryData[]>([]);
const disabled = signal(false);

async function render(): Promise<ComponentFixture<ScheduledTransfersComponent>> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      ...provideI18nTesting(),
      provideRouter([]),
      {
        provide: SavingsStore,
        useValue: {
          accounts: signal([{ id: 7, accountNo: '000000007', productName: 'Savings' }]),
          loadAccounts: vi.fn(),
        },
      },
      {
        provide: BeneficiariesStore,
        useValue: {
          beneficiaries: signal([{ publicId: 'b1', name: 'Asha', fineractAccountId: 42 }]),
          load: () => of([]),
        },
      },
      {
        provide: ScheduledTransfersStore,
        useValue: { transfers, disabled, load: () => of(transfers()) },
      },
    ],
  });
  const fixture = TestBed.createComponent(ScheduledTransfersComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  return fixture;
}

function text(fixture: ComponentFixture<ScheduledTransfersComponent>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

describe('ScheduledTransfersComponent', () => {
  beforeEach(() => {
    transfers.set([]);
    disabled.set(false);
  });

  it('labels each row with the account and beneficiary names', async () => {
    transfers.set([
      {
        publicId: 'p-1',
        fromAccountId: 7,
        toAccountId: 42,
        amount: 50,
        frequency: 'WEEKLY',
        startDate: '2026-12-07',
      },
    ]);

    const fixture = await render();

    expect(text(fixture)).toContain('Savings · 000000007');
    expect(text(fixture)).toContain('Asha');
    expect(text(fixture)).toContain('scheduledTransfers.frequency.WEEKLY');
    expect(text(fixture)).toContain('scheduledTransfers.noEndDate');
    expect(text(fixture)).toContain('scheduledTransfers.newCta');
  });

  it('says so in place, and offers no set-up, when the deployment has the feature off', async () => {
    disabled.set(true);

    const fixture = await render();

    expect(text(fixture)).toContain('scheduledTransfers.disabled');
    expect(text(fixture)).not.toContain('scheduledTransfers.newCta');
    expect(text(fixture)).not.toContain('scheduledTransfers.list.empty');
  });
});
