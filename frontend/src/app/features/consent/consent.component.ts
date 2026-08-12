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
import { ActivatedRoute } from '@angular/router';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';

const SCOPE_LABEL_KEYS: Record<string, string> = {
  'openbanking:accounts.read': 'consent.scope.accountsRead',
};

function csrfTokenFromCookie(): string {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : '';
}

@Component({
  selector: 'app-consent',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [IonButton, IonCard, IonCardContent, IonCardHeader, IonCardTitle, TranslatePipe],
  template: `
    <ion-card class="page-card">
      <ion-card-header>
        <ion-card-title>{{ 'consent.title' | translate }}</ion-card-title>
      </ion-card-header>

      <ion-card-content>
        @if (valid) {
          <p>{{ 'consent.intro' | translate: { tppClientId: tppClientId } }}</p>
          <p>{{ 'consent.permissionsTitle' | translate }}</p>
          <ul class="permissions">
            @for (scope of scopes; track scope) {
              <li>{{ scopeLabelKey(scope) | translate }}</li>
            }
          </ul>
          <div class="actions">
            <form method="post" action="/api/v1/oauth2/authorize">
              <input type="hidden" name="client_id" [value]="tppClientId" />
              <input type="hidden" name="state" [value]="state" />
              <input type="hidden" name="_csrf" [value]="csrfToken" />
              <ion-button type="submit" fill="outline" color="medium">
                {{ 'consent.denyCta' | translate }}
              </ion-button>
            </form>
            <form method="post" action="/api/v1/oauth2/authorize">
              <input type="hidden" name="client_id" [value]="tppClientId" />
              <input type="hidden" name="state" [value]="state" />
              <input type="hidden" name="_csrf" [value]="csrfToken" />
              @for (scope of scopes; track scope) {
                <input type="hidden" name="scope" [value]="scope" />
              }
              <ion-button type="submit">{{ 'consent.allowCta' | translate }}</ion-button>
            </form>
          </div>
        } @else {
          <p>{{ 'consent.invalid' | translate }}</p>
        }
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: ['../../shared/css/centered-page.scss'],
  styles: `
    .actions {
      display: flex;
      justify-content: flex-end;
      gap: 0.5rem;
      margin-top: 1rem;
    }
  `,
})
export class ConsentComponent {
  private readonly route = inject(ActivatedRoute);

  protected readonly tppClientId = this.route.snapshot.queryParamMap.get('client_id') ?? '';
  protected readonly state = this.route.snapshot.queryParamMap.get('state') ?? '';
  protected readonly scopes = (this.route.snapshot.queryParamMap.get('scope') ?? '')
    .split(' ')
    .filter(scope => scope.length > 0);
  protected readonly csrfToken = csrfTokenFromCookie();
  protected readonly valid = this.tppClientId !== '' && this.state !== '';

  protected scopeLabelKey(scope: string): string {
    return SCOPE_LABEL_KEYS[scope] ?? scope;
  }
}
