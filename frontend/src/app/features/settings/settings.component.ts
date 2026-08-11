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

import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe } from '@angular/common';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { OpenBankingUserConsentQueryData } from '@bff/client';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { ChangePasswordComponent } from './change-password.component';
import { SettingsStore } from './settings.store';

@Component({
  selector: 'app-settings',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTableModule,
    DatePipe,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    TranslatePipe,
    PageHeaderComponent,
    StatusBadgeComponent,
    ChangePasswordComponent,
  ],
  template: `
    <app-page-header [title]="'settings.title' | translate" />

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'settings.changePassword.title' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <app-change-password />
      </ion-card-content>
    </ion-card>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'settings.connectedApps.title' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <div class="table-scroll">
          <table cdk-table [dataSource]="store.consents()">
            <ng-container cdkColumnDef="tppClientId">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'settings.connectedApps.app' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">{{ row.tppClientId }}</td>
            </ng-container>
            <ng-container cdkColumnDef="permissions">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'settings.connectedApps.permissions' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">{{ formatPermissions(row.permissions) }}</td>
            </ng-container>
            <ng-container cdkColumnDef="status">
              <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.status' | translate }}</th>
              <td cdk-cell *cdkCellDef="let row">
                <app-status-badge [status]="'settings.connectedApps.status.' + row.status" />
              </td>
            </ng-container>
            <ng-container cdkColumnDef="creationDateTime">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'settings.connectedApps.granted' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">
                {{ row.creationDateTime | date: 'mediumDate' }}
              </td>
            </ng-container>
            <ng-container cdkColumnDef="expirationDateTime">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'settings.connectedApps.expires' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">
                {{ row.expirationDateTime | date: 'mediumDate' }}
              </td>
            </ng-container>
            <ng-container cdkColumnDef="actions">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'settings.connectedApps.actions' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row" class="row-actions">
                @if (row.status === statusAuthorised) {
                  <ion-button
                    class="btn-danger"
                    [disabled]="revokingId() === row.consentId"
                    (click)="confirmRevoke(row.consentId)"
                  >
                    {{ 'settings.connectedApps.revoke' | translate }}
                  </ion-button>
                }
              </td>
            </ng-container>

            <tr cdk-header-row *cdkHeaderRowDef="consentColumns"></tr>
            <tr cdk-row *cdkRowDef="let row; columns: consentColumns"></tr>
            <tr class="empty-row" *cdkNoDataRow>
              <td [attr.colspan]="consentColumns.length">
                {{ 'settings.connectedApps.empty' | translate }}
              </td>
            </tr>
          </table>
        </div>
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: [
    '../../shared/css/detail-page.scss',
    '../../shared/css/table.scss',
    '../../shared/css/actions.scss',
  ],
  styles: `
    .row-actions {
      white-space: nowrap;
    }
  `,
})
export class SettingsComponent {
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(SettingsStore);

  protected readonly consentColumns = [
    'tppClientId',
    'permissions',
    'status',
    'creationDateTime',
    'expirationDateTime',
    'actions',
  ];
  protected readonly statusAuthorised = OpenBankingUserConsentQueryData.StatusEnum.Authorised;
  protected readonly revokingId = signal<string | null>(null);

  constructor() {
    this.store.loadConsents();
  }

  protected formatPermissions(
    permissions: Set<OpenBankingUserConsentQueryData.PermissionsEnum> | undefined,
  ): string {
    return [...(permissions ?? [])].join(', ');
  }

  protected confirmRevoke(consentId: string): void {
    this.revokingId.set(consentId);
    this.store
      .revokeConsent(consentId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.revokingId.set(null);
          this.store.loadConsents();
        },
        error: () => this.revokingId.set(null),
      });
  }
}
