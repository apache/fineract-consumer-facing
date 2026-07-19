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

import { Injectable, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';
import {
  BeneficiariesCommandControllerService,
  BeneficiariesQueryControllerService,
  BeneficiaryChallengeCommandData,
  BeneficiaryCommandData,
  BeneficiaryQueryData,
  BeneficiaryTemplateQueryData,
  ConfirmAddBeneficiaryCommandRequest,
  ConfirmUpdateBeneficiaryCommandRequest,
  InitiateAddBeneficiaryCommandRequest,
  InitiateUpdateBeneficiaryCommandRequest,
} from '@bff/client';
import { deviceFingerprint } from '../../core/auth/device-fingerprint';

@Injectable({ providedIn: 'root' })
export class BeneficiariesStore {
  private readonly query = inject(BeneficiariesQueryControllerService);
  private readonly command = inject(BeneficiariesCommandControllerService);

  readonly beneficiaries = signal<BeneficiaryQueryData[]>([]);
  readonly accountTypeOptions = signal<string[]>([]);
  readonly challenge = signal<BeneficiaryChallengeCommandData | null>(null);

  load(): Observable<BeneficiaryQueryData[]> {
    return this.query.listBeneficiaries().pipe(tap(rows => this.beneficiaries.set(rows)));
  }

  loadTemplate(): Observable<BeneficiaryTemplateQueryData> {
    return this.query
      .getBeneficiaryTemplate()
      .pipe(tap(template => this.accountTypeOptions.set(template.accountTypeOptions ?? [])));
  }

  initiateAdd(request: InitiateAddBeneficiaryCommandRequest): Observable<BeneficiaryChallengeCommandData> {
    return this.command
      .initiateAddBeneficiary(deviceFingerprint(), request)
      .pipe(tap(challenge => this.challenge.set(challenge)));
  }

  confirmAdd(request: ConfirmAddBeneficiaryCommandRequest): Observable<BeneficiaryCommandData> {
    return this.command
      .confirmAddBeneficiary(deviceFingerprint(), request)
      .pipe(tap(() => this.challenge.set(null)));
  }

  initiateUpdate(
    publicId: string,
    request: InitiateUpdateBeneficiaryCommandRequest,
  ): Observable<BeneficiaryChallengeCommandData> {
    return this.command
      .initiateUpdateBeneficiary(deviceFingerprint(), publicId, request)
      .pipe(tap(challenge => this.challenge.set(challenge)));
  }

  confirmUpdate(
    publicId: string,
    request: ConfirmUpdateBeneficiaryCommandRequest,
  ): Observable<BeneficiaryCommandData> {
    return this.command
      .confirmUpdateBeneficiary(deviceFingerprint(), publicId, request)
      .pipe(tap(() => this.challenge.set(null)));
  }

  delete(publicId: string): Observable<unknown> {
    return this.command.deleteBeneficiary(publicId);
  }
}
