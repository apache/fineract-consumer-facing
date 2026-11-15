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

import { HttpContext } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  ScheduledTransfersCommandControllerService,
  ScheduledTransfersQueryControllerService,
} from '@bff/client';
import { DeviceFingerprint } from '../../auth/device-fingerprint';
import { QUIET_ERROR_CODES } from '../../interceptors/quiet-error-codes';
import { mapPage } from './bff-page';
import type { Page } from './page';
import { SCHEDULED_TRANSFERS_DISABLED_CODE } from './scheduled-transfer-codes';
import type {
  ConfirmCancelScheduledTransferCommandRequest,
  ConfirmScheduledTransferCommandRequest,
  InitiateScheduledTransferCommandRequest,
  ScheduledTransferApi,
  ScheduledTransferChallengeCommandData,
  ScheduledTransferCommandData,
  ScheduledTransferQueryData,
  ScheduledTransferRunQueryData,
} from './scheduled-transfer.api';

@Injectable({ providedIn: 'root' })
export class BffScheduledTransferApi implements ScheduledTransferApi {
  private readonly command = inject(ScheduledTransfersCommandControllerService);
  private readonly query = inject(ScheduledTransfersQueryControllerService);
  private readonly fingerprint = inject(DeviceFingerprint);

  list(): Observable<ScheduledTransferQueryData[]> {
    const context = new HttpContext().set(QUIET_ERROR_CODES, [SCHEDULED_TRANSFERS_DISABLED_CODE]);
    return this.query.listScheduledTransfers('body', false, { context });
  }

  runs(
    publicId: string,
    page: number,
    size: number,
  ): Observable<Page<ScheduledTransferRunQueryData>> {
    return this.query
      .listScheduledTransferRuns(publicId, page, size)
      .pipe(map((response) => mapPage(response)));
  }

  initiate(
    request: InitiateScheduledTransferCommandRequest,
  ): Observable<ScheduledTransferChallengeCommandData> {
    return this.command.initiateScheduledTransfer(this.fingerprint.get(), request);
  }

  confirm(
    idempotencyKey: string,
    request: ConfirmScheduledTransferCommandRequest,
  ): Observable<ScheduledTransferCommandData> {
    return this.command.confirmScheduledTransfer(this.fingerprint.get(), idempotencyKey, request);
  }

  initiateCancel(publicId: string): Observable<ScheduledTransferChallengeCommandData> {
    return this.command.initiateCancelScheduledTransfer(this.fingerprint.get(), publicId);
  }

  confirmCancel(
    publicId: string,
    idempotencyKey: string,
    request: ConfirmCancelScheduledTransferCommandRequest,
  ): Observable<unknown> {
    return this.command.confirmCancelScheduledTransfer(
      this.fingerprint.get(),
      idempotencyKey,
      publicId,
      request,
    );
  }
}
