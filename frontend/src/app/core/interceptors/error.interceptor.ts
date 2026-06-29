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
import { MatSnackBar } from '@angular/material/snack-bar';
import { catchError, switchMap, throwError } from 'rxjs';
import { ConsumerApiError } from '../../api/consumer-api-error';
import { AuthService } from '../auth/auth.service';

const REFRESH_URL = '/api/v1/authentication/refresh';

export const errorInterceptor: HttpInterceptorFn = (req, next) => {
  const snackBar = inject(MatSnackBar);
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      if (req.url.includes(REFRESH_URL)) {
        return throwError(() => error);
      }

      if (error.status === 401) {
        return auth.refresh().pipe(
          switchMap(() => next(req)),
          catchError(() => {
            router.navigate(['/login']);
            return throwError(() => error);
          }),
        );
      }

      notify(snackBar, error);
      return throwError(() => error);
    }),
  );
};

function notify(snackBar: MatSnackBar, error: HttpErrorResponse): void {
  const body = error.error as ConsumerApiError | null;
  const message = body?.defaultMessage ?? 'Something went wrong. Please try again.';
  snackBar.open(message, 'Dismiss', { duration: 5000 });
}
