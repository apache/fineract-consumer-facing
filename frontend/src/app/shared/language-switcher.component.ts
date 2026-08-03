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
import { IonButton, IonIcon, IonItem, IonLabel, IonList, IonPopover } from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { globeOutline } from 'ionicons/icons';
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
  imports: [IonButton, IonIcon, IonItem, IonLabel, IonList, IonPopover, TranslatePipe],
  template: `
    <ion-button
      id="language-trigger"
      fill="clear"
      class="icon-button-lg"
      [attr.aria-label]="'common.action.changeLanguage' | translate"
    >
      <ion-icon slot="icon-only" name="globe-outline" aria-hidden="true" />
    </ion-button>
    <ion-popover trigger="language-trigger" [dismissOnSelect]="true" [showBackdrop]="false">
      <ng-template>
        <ion-list lines="none">
          @for (lang of languages; track lang.code) {
            <ion-item [button]="true" [detail]="false" (click)="use(lang.code)">
              <ion-label>
                {{ lang.endonym
                }}{{ (lang.nameKey | translate) === lang.endonym ? '' : ' (' + (lang.nameKey | translate) + ')' }}
              </ion-label>
            </ion-item>
          }
        </ion-list>
      </ng-template>
    </ion-popover>
  `,
  styleUrls: ['./css/icon-button.scss'],
  styles: `
    ion-button {
      --color: var(--slate-600);
    }

    ion-popover {
      --background: var(--surface);
      --box-shadow: var(--shadow-md);
    }

    ion-list {
      background: var(--surface);
      padding: 0.25rem 0;
    }

    ion-item {
      --background: var(--surface);
      --background-hover: var(--page-bg);
      --background-hover-opacity: 1;
      --color: var(--ink);
      --min-height: 2.5rem;
      font-size: var(--text-base);
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
    addIcons({ globeOutline });
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
