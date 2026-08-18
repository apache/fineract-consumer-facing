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
import { AuditEventQueryData, AuditQueryControllerService } from '@bff/client';

@Injectable({ providedIn: 'root' })
export class DemoStore {
  private readonly query = inject(AuditQueryControllerService);
  private readonly destroyRef = inject(DestroyRef);

  readonly events = signal<AuditEventQueryData[]>([]);
  readonly loading = signal(false);

  loadEvents(page: number, size: number): void {
    this.events.set([]);
    this.loading.set(true);
    this.query
      .listAuditEvents(page, size)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (rows) => {
          this.events.set(rows);
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }
}
