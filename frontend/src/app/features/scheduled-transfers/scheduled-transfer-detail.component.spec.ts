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
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { of } from 'rxjs';
import {
  OVERLAY,
  ScheduledTransferChallengeCommandData,
  ScheduledTransferQueryData,
  ScheduledTransferRunQueryData,
} from '../../core/adapters';
import { provideI18nTesting } from '../../testing/i18n-testing';
import { BeneficiariesStore } from '../beneficiaries/beneficiaries.store';
import { SavingsStore } from '../savings/savings.store';
import { ScheduledTransferDetailComponent } from './scheduled-transfer-detail.component';
import { ScheduledTransfersStore } from './scheduled-transfers.store';

const PUBLIC_ID = 'p-1';

interface DetailInternals {
  step(): string;
  cancel(): void;
  confirmCancel(otp: string): void;
}

const transfers = signal<ScheduledTransferQueryData[]>([]);
const loaded = signal(true);
const challenge = signal<ScheduledTransferChallengeCommandData | null>(null);
const runs = signal<ScheduledTransferRunQueryData[]>([]);
const loadRuns = vi.fn();
const initiateCancel = vi.fn();
const confirmCancel = vi.fn();
const toast = vi.fn(() => Promise.resolve());

async function render(): Promise<ComponentFixture<ScheduledTransferDetailComponent>> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      ...provideI18nTesting(),
      provideRouter([]),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { paramMap: convertToParamMap({ publicId: PUBLIC_ID }) } },
      },
      { provide: OVERLAY, useValue: { toast } },
      { provide: SavingsStore, useValue: { accounts: signal([]), loadAccounts: vi.fn() } },
      { provide: BeneficiariesStore, useValue: { beneficiaries: signal([]), load: () => of([]) } },
      {
        provide: ScheduledTransfersStore,
        useValue: {
          transfers,
          loaded,
          challenge,
          runs,
          runsPage: signal(0),
          runsSize: signal(10),
          runsTotalElements: signal(0),
          runsTotalPages: signal(0),
          runsLoading: signal(false),
          load: () => of(transfers()),
          loadRuns,
          initiateCancel,
          confirmCancel,
        },
      },
    ],
  });
  const fixture = TestBed.createComponent(ScheduledTransferDetailComponent);
  fixture.detectChanges();
  await fixture.whenStable();
  return fixture;
}

function text(fixture: ComponentFixture<ScheduledTransferDetailComponent>): string {
  return (fixture.nativeElement as HTMLElement).textContent ?? '';
}

describe('ScheduledTransferDetailComponent', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    transfers.set([
      {
        publicId: PUBLIC_ID,
        fromAccountId: 7,
        toAccountId: 9,
        amount: 50,
        frequency: 'MONTHLY',
        startDate: '2026-12-01',
      },
    ]);
    loaded.set(true);
    challenge.set(null);
    runs.set([]);
  });

  it('loads the first page of the run history', async () => {
    await render();

    expect(loadRuns).toHaveBeenCalledWith(PUBLIC_ID, 0, 10);
  });

  it('shows a failed run with its status and no amount', async () => {
    runs.set([{ date: '2027-01-01', status: 'FAILED' }]);

    const fixture = await render();

    expect(text(fixture)).toContain('scheduledTransfers.runs.status.FAILED');
    expect(text(fixture)).toContain('—');
  });

  it('keeps the run history but offers no cancel for a transfer that is no longer active', async () => {
    transfers.set([]);

    const fixture = await render();

    expect(text(fixture)).toContain('scheduledTransfers.detail.notActive');
    expect(text(fixture)).not.toContain('scheduledTransfers.detail.cancelCta');
    expect(loadRuns).toHaveBeenCalledWith(PUBLIC_ID, 0, 10);
  });

  it('cancels through the OTP step-up, then toasts and returns to the list', async () => {
    initiateCancel.mockReturnValue(of({ stepUpToken: 'cancel-tok' }));
    confirmCancel.mockReturnValue(of(null));
    const fixture = await render();
    const navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const component = fixture.componentInstance as unknown as DetailInternals;

    component.cancel();
    expect(initiateCancel).toHaveBeenCalledWith(PUBLIC_ID);
    expect(component.step()).toBe('otp');

    challenge.set({ stepUpToken: 'cancel-tok' });
    component.confirmCancel('ABC123');

    expect(confirmCancel).toHaveBeenCalledWith(PUBLIC_ID, expect.any(String), {
      stepUpToken: 'cancel-tok',
      otp: 'ABC123',
    });
    expect(toast).toHaveBeenCalledWith(
      expect.objectContaining({ message: 'scheduledTransfers.detail.cancelled' }),
    );
    expect(navigate).toHaveBeenCalledWith(['/transfers', 'scheduled']);
  });
});
