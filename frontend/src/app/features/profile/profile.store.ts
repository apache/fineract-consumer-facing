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
  ForgotPasswordCommandRequest,
  InitiatePasswordChangeCommandRequest,
  ResetPasswordCommandRequest,
  UserChargeQueryData,
  UserCommandControllerService,
  UserImageQueryData,
  UserObligeeQueryData,
  UserPasswordChangeChallengeCommandData,
  UserProfileQueryData,
  UserQueryControllerService,
} from '@bff/client';
import { deviceFingerprint } from '../../core/auth/device-fingerprint';

const IMAGE_MAX_WIDTH = 256;
const IMAGE_MAX_HEIGHT = 256;

export interface ChargesFilter {
  status: string;
  page: number;
  size: number;
}

@Injectable({ providedIn: 'root' })
export class ProfileStore {
  private readonly query = inject(UserQueryControllerService);
  private readonly command = inject(UserCommandControllerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly profile = signal<UserProfileQueryData | null>(null);
  readonly charges = signal<UserChargeQueryData[]>([]);
  readonly totalFilteredRecords = signal(0);
  readonly obligees = signal<UserObligeeQueryData[]>([]);
  readonly image = signal<UserImageQueryData | null>(null);
  readonly passwordChangeChallenge = signal<UserPasswordChangeChallengeCommandData | null>(null);
  readonly loading = signal(false);

  loadProfile(): void {
    this.loading.set(true);
    this.query
      .getUserProfile()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: profile => {
          this.profile.set(profile);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  loadCharges(filter: ChargesFilter): void {
    this.query
      .getUserCharges(filter.status, filter.page, filter.size)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: response => {
          this.charges.set(response.charges ?? []);
          this.totalFilteredRecords.set(response.totalFilteredRecords ?? 0);
        },
        error: () => {
          this.charges.set([]);
          this.totalFilteredRecords.set(0);
        },
      });
  }

  loadImage(): void {
    this.query
      .getUserImage(IMAGE_MAX_WIDTH, IMAGE_MAX_HEIGHT)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: image => this.image.set(image),
        error: () => this.image.set(null),
      });
  }

  loadObligees(): void {
    this.query
      .getUserObligees()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: rows => this.obligees.set(rows),
        error: () => this.obligees.set([]),
      });
  }

  initiatePasswordChange(
    request: InitiatePasswordChangeCommandRequest,
  ): Observable<UserPasswordChangeChallengeCommandData> {
    return this.command
      .initiatePasswordChange(deviceFingerprint(), request)
      .pipe(tap(challenge => this.passwordChangeChallenge.set(challenge)));
  }

  confirmPasswordChange(request: ConfirmPasswordChangeCommandRequest): Observable<unknown> {
    return this.command
      .confirmPasswordChange(deviceFingerprint(), request)
      .pipe(tap(() => this.passwordChangeChallenge.set(null)));
  }

  forgotPassword(request: ForgotPasswordCommandRequest): Observable<unknown> {
    return this.command.forgotPassword(deviceFingerprint(), request);
  }

  resetPassword(request: ResetPasswordCommandRequest): Observable<unknown> {
    return this.command.resetPassword(deviceFingerprint(), request);
  }
}
