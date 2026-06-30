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

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import { MatMenuModule } from '@angular/material/menu';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';

const STORAGE_KEY = 'app.lang';

interface LanguageOption {
  readonly code: string;
  readonly endonym: string;
  readonly nameKey: string;
}

@Component({
  selector: 'app-language-switcher',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [MatButtonModule, MatIconModule, MatMenuModule, TranslatePipe],
  template: `
    <button mat-icon-button class="lang-trigger" [matMenuTriggerFor]="menu" [attr.aria-label]="'common.action.changeLanguage' | translate">
      <mat-icon>language</mat-icon>
    </button>
    <mat-menu #menu="matMenu">
      @for (lang of languages; track lang.code) {
        <button mat-menu-item (click)="use(lang.code)">
          {{ lang.endonym
          }}{{ (lang.nameKey | translate) === lang.endonym ? '' : ' (' + (lang.nameKey | translate) + ')' }}
        </button>
      }
    </mat-menu>
  `,
  styles: `
    .lang-trigger {
      width: 3rem;
      height: 3rem;
    }
    .lang-trigger mat-icon {
      font-size: 1.75rem;
      width: 1.75rem;
      height: 1.75rem;
    }
  `,
})
export class LanguageSwitcherComponent {
  private readonly translate = inject(TranslateService);

  protected readonly languages: readonly LanguageOption[] = [
    { code: 'en', endonym: 'English', nameKey: 'common.language.en' },
    { code: 'hi', endonym: 'हिन्दी', nameKey: 'common.language.hi' },
  ];

  constructor() {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      this.translate.use(stored);
    }
  }

  protected use(code: string): void {
    this.translate.use(code);
    localStorage.setItem(STORAGE_KEY, code);
  }
}
