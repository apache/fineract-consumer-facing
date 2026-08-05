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

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { ToastController } from '@ionic/angular/standalone';
import { TranslateService } from '@ngx-translate/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { ConsumerApiError } from '../../api/consumer-api-error';
import { AUDIT_EVENTS_PATH, AuditService } from '../audit/audit.service';
import { buildDetails } from '../audit/pii-scrub';
import { AuthService } from '../auth/auth.service';

const REFRESH_URL = '/api/v1/authentication/refresh';
const GENERIC_ERROR_KEY = 'common.error.generic';
const DISMISS_KEY = 'common.action.dismiss';
const DEVICE_MISMATCH_CODE = 'error.msg.consumer.auth.device.fingerprint.forbidden';
const RATE_LIMITED_KEY = 'common.error.rateLimited';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const toast = inject(ToastController);
  const auth = inject(AuthService);
  const audit = inject(AuditService);
  const router = inject(Router);
  const translate = inject(TranslateService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (req.url.includes(AUDIT_EVENTS_PATH)) {
        return throwError(() => error);
      }

      if (req.url.includes(REFRESH_URL)) {
        return throwError(() => error);
      }

      if (error.status === 403 && (error.error as ConsumerApiError | null)?.code === DEVICE_MISMATCH_CODE) {
        recordApiFailure(audit, req.url, error.status);
        auth.clearSession();
        router.navigate(['/login']);
        return throwError(() => error);
      }

      if (error.status === 401) {
        return auth.refresh().pipe(
          switchMap(() => next(req)),
          catchError((retryError: HttpErrorResponse) => {
            recordApiFailure(audit, req.url, error.status);
            if (retryError.status === 401 || retryError.status === 403) {
              auth.clearSession();
              router.navigate(['/login']);
              return throwError(() => error);
            }
            if (retryError.status === 429) {
              showToast(toast, translate, translate.instant(RATE_LIMITED_KEY));
            }
            return throwError(() => retryError);
          }),
        );
      }

      if (error.status === 429) {
        recordApiFailure(audit, req.url, error.status);
        showToast(toast, translate, translate.instant(RATE_LIMITED_KEY));
        return throwError(() => error);
      }

      recordApiFailure(audit, req.url, error.status);
      notify(toast, translate, error);
      return throwError(() => error);
    }),
  );
};

function recordApiFailure(audit: AuditService, url: string, status: number): void {
  audit.record('API_FAILURE', buildDetails({ endpoint: endpointTemplate(url), status }));
}

const UUID_SEGMENT = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function endpointTemplate(url: string): string {
  const path = url.split('?')[0].replace(/^https?:\/\/[^/]+/, '');
  return path
    .split('/')
    .map(segment => (/^\d+$/.test(segment) || UUID_SEGMENT.test(segment) ? ':id' : segment))
    .join('/');
}

function notify(toast: ToastController, translate: TranslateService, error: HttpErrorResponse): void {
  const body = error.error as ConsumerApiError | null;
  showToast(toast, translate, resolveMessage(translate, body));
}

function showToast(toast: ToastController, translate: TranslateService, message: string): void {
  void toast
    .create({
      message,
      duration: 5000,
      position: 'bottom',
      buttons: [{ text: translate.instant(DISMISS_KEY), role: 'cancel' }],
    })
    .then((t) => t.present());
}

function resolveMessage(translate: TranslateService, body: ConsumerApiError | null): string {
  if (body?.code) {
    const translated = translate.instant(body.code);
    if (translated !== body.code) {
      return translated;
    }
  }
  if (body?.defaultMessage) {
    return body.defaultMessage;
  }
  return translate.instant(GENERIC_ERROR_KEY);
}
