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

import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TranslatePipe } from '@ngx-translate/core';

type Tone = 'success' | 'warning' | 'error' | 'neutral';

function classify(status: string): Tone {
  const value = status.toLowerCase();
  if (value.includes('awaiting')) {
    return 'warning';
  }
  if (value.includes('active') || value.includes('approved') || value.includes('authorised')) {
    return 'success';
  }
  if (value.includes('pending') || value.includes('submitted')) {
    return 'warning';
  }
  if (value.includes('rejected') || value.includes('revoked')) {
    return 'error';
  }
  return 'neutral';
}

@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [TranslatePipe],
  template: `<span class="badge" [class]="tone()">{{ status() | translate }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 0.125rem 0.5rem;
      border-radius: var(--radius-full);
      font-size: var(--text-xs);
      font-weight: 600;
      letter-spacing: 0.01em;
      line-height: 1.4;
      white-space: nowrap;
    }
    .success {
      background-color: var(--success-tint);
      color: var(--success);
    }
    .warning {
      background-color: var(--warning-tint);
      color: var(--warning);
    }
    .error {
      background-color: var(--danger-tint);
      color: var(--danger);
    }
    .neutral {
      background-color: var(--neutral-tint);
      color: var(--neutral);
    }
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  protected readonly tone = computed(() => classify(this.status()));
}
