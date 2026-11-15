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

import { InjectionToken, inject } from '@angular/core';
import { Observable } from 'rxjs';
import type {
  ConfirmCancelScheduledTransferCommandRequest,
  ConfirmScheduledTransferCommandRequest,
  InitiateScheduledTransferCommandRequest,
  ScheduledTransferChallengeCommandData,
  ScheduledTransferCommandData,
  ScheduledTransferQueryData,
  ScheduledTransferRunQueryData,
} from '@bff/client';
import { BffScheduledTransferApi } from './bff-scheduled-transfer.api';
import type { Page } from './page';

export type {
  ConfirmCancelScheduledTransferCommandRequest,
  ConfirmScheduledTransferCommandRequest,
  InitiateScheduledTransferCommandRequest,
  ScheduledTransferChallengeCommandData,
  ScheduledTransferCommandData,
  ScheduledTransferQueryData,
  ScheduledTransferRunQueryData,
};

export type ScheduledTransferFrequency = InitiateScheduledTransferCommandRequest.FrequencyEnum;

/** The frequencies the BFF accepts, in the order the form offers them. */
export const SCHEDULED_TRANSFER_FREQUENCIES: readonly ScheduledTransferFrequency[] = [
  'WEEKLY',
  'MONTHLY',
];

export { SCHEDULED_TRANSFERS_DISABLED_CODE } from './scheduled-transfer-codes';

/**
 * Weekly or monthly transfers that Fineract runs as standing instructions. Setting one up and
 * cancelling one are two-step, OTP-confirmed commands.
 */
export interface ScheduledTransferApi {
  list(): Observable<ScheduledTransferQueryData[]>;
  runs(
    publicId: string,
    page: number,
    size: number,
  ): Observable<Page<ScheduledTransferRunQueryData>>;
  initiate(
    request: InitiateScheduledTransferCommandRequest,
  ): Observable<ScheduledTransferChallengeCommandData>;
  confirm(
    idempotencyKey: string,
    request: ConfirmScheduledTransferCommandRequest,
  ): Observable<ScheduledTransferCommandData>;
  initiateCancel(publicId: string): Observable<ScheduledTransferChallengeCommandData>;
  confirmCancel(
    publicId: string,
    idempotencyKey: string,
    request: ConfirmCancelScheduledTransferCommandRequest,
  ): Observable<unknown>;
}

export const SCHEDULED_TRANSFER_API = new InjectionToken<ScheduledTransferApi>(
  'ScheduledTransferApi',
  {
    providedIn: 'root',
    factory: () => inject(BffScheduledTransferApi),
  },
);
