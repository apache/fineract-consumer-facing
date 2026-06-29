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

type Tone = 'success' | 'warning' | 'error' | 'neutral';

function classify(status: string): Tone {
  const value = status.toLowerCase();
  if (value.includes('active') || value.includes('approved')) {
    return 'success';
  }
  if (value.includes('pending') || value.includes('submitted')) {
    return 'warning';
  }
  if (
    value.includes('rejected') ||
    value.includes('closed') ||
    value.includes('overpaid') ||
    value.includes('withdrawn')
  ) {
    return 'error';
  }
  return 'neutral';
}

function humanize(status: string): string {
  const code = status.includes('.') ? status.slice(status.indexOf('.') + 1) : status;
  const text = code.replace(/\./g, ' ');
  return text.charAt(0).toUpperCase() + text.slice(1);
}

@Component({
  selector: 'app-status-badge',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<span class="badge" [class]="tone()">{{ label() }}</span>`,
  styles: `
    .badge {
      display: inline-block;
      padding: 0.15rem 0.6rem;
      border-radius: 999px;
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1.4;
      white-space: nowrap;
      color: #fff;
    }
    .success {
      background-color: var(--success-color);
    }
    .warning {
      background-color: var(--warning-color);
    }
    .error {
      background-color: var(--error-color);
    }
    .neutral {
      background-color: #e0e0e0;
      color: var(--text-color);
    }
  `,
})
export class StatusBadgeComponent {
  readonly status = input.required<string>();
  protected readonly tone = computed(() => classify(this.status()));
  protected readonly label = computed(() => humanize(this.status()));
}
