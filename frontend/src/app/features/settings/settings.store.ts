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

import { DestroyRef, Injectable, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Observable, tap } from 'rxjs';
import {
  ConfirmPasswordChangeCommandRequest,
  InitiatePasswordChangeCommandRequest,
  OpenBankingConsentCommandData,
  OpenBankingUserConsentCommandControllerService,
  OpenBankingUserConsentQueryControllerService,
  OpenBankingUserConsentQueryData,
  UserCommandControllerService,
  UserPasswordChangeChallengeCommandData,
} from '@bff/client';
import { deviceFingerprint } from '../../core/auth/device-fingerprint';

@Injectable({ providedIn: 'root' })
export class SettingsStore {
  private readonly command = inject(UserCommandControllerService);
  private readonly consentQuery = inject(OpenBankingUserConsentQueryControllerService);
  private readonly consentCommand = inject(OpenBankingUserConsentCommandControllerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly passwordChangeChallenge = signal<UserPasswordChangeChallengeCommandData | null>(null);
  readonly consents = signal<OpenBankingUserConsentQueryData[]>([]);

  initiatePasswordChange(
    request: InitiatePasswordChangeCommandRequest,
  ): Observable<UserPasswordChangeChallengeCommandData> {
    return this.command
      .initiatePasswordChange(deviceFingerprint(), request)
      .pipe(tap((challenge) => this.passwordChangeChallenge.set(challenge)));
  }

  confirmPasswordChange(request: ConfirmPasswordChangeCommandRequest): Observable<unknown> {
    return this.command
      .confirmPasswordChange(deviceFingerprint(), request)
      .pipe(tap(() => this.passwordChangeChallenge.set(null)));
  }

  loadConsents(): void {
    this.consents.set([]);
    this.consentQuery
      .listConsents()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => this.consents.set(rows),
        error: () => {},
      });
  }

  revokeConsent(consentId: string): Observable<OpenBankingConsentCommandData> {
    return this.consentCommand.revokeConsent(consentId);
  }
}
