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

import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  AuthenticationCommandControllerService,
  LoginChallengeCommandData,
  LoginCommandRequest,
  SessionCommandData,
  VerifyTwoFactorCommandRequest,
} from '@bff/client';
import { deviceFingerprint } from './device-fingerprint';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly api = inject(AuthenticationCommandControllerService);

  private readonly accessToken = signal<string | null>(null);
  readonly token = this.accessToken.asReadonly();
  readonly isAuthenticated = computed(() => this.accessToken() !== null);

  login(request: LoginCommandRequest): Observable<LoginChallengeCommandData> {
    return this.api.login(deviceFingerprint(), request);
  }

  verifyTwoFactor(request: VerifyTwoFactorCommandRequest): Observable<SessionCommandData> {
    return this.api
      .verifyTwoFactor(deviceFingerprint(), request)
      .pipe(tap(session => this.adoptSession(session)));
  }

  refresh(): Observable<SessionCommandData> {
    return this.api
      .refreshSession(deviceFingerprint())
      .pipe(tap(session => this.adoptSession(session)));
  }

  logout(): Observable<unknown> {
    return this.api.logout().pipe(tap(() => this.accessToken.set(null)));
  }

  private adoptSession(session: SessionCommandData): void {
    this.accessToken.set(session.accessToken ?? null);
  }
}
