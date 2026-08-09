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

import { ChangeDetectionStrategy, Component, DestroyRef, effect, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DatePipe, DecimalPipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardSubtitle,
  IonCardTitle,
  IonIcon,
  IonInput,
  IonProgressBar,
  IonSelect,
  IonSelectOption,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { person } from 'ionicons/icons';
import { TranslatePipe } from '@ngx-translate/core';
import { OpenBankingUserConsentQueryData } from '@bff/client';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { ChangePasswordComponent } from './change-password.component';
import { ProfileStore } from './profile.store';

@Component({
  selector: 'app-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CdkTableModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardSubtitle,
    IonCardTitle,
    IonIcon,
    IonInput,
    IonProgressBar,
    IonSelect,
    IonSelectOption,
    TranslatePipe,
    DatePipe,
    DecimalPipe,
    PageHeaderComponent,
    ChangePasswordComponent,
  ],
  template: `
    <app-page-header [title]="'profile.title' | translate" />

    @if (store.loading()) {
      <ion-progress-bar type="indeterminate" />
    }

    @if (store.profile(); as profile) {
      <ion-card>
        <ion-card-header class="profile-header">
          @if (profile.hasImage && store.image(); as image) {
            <img
              class="avatar"
              [src]="image.imageDataUri"
              [alt]="'profile.card.photoAlt' | translate"
            />
          } @else {
            <ion-icon name="person" class="avatar avatar-fallback" aria-hidden="true" />
          }
          <div class="profile-titles">
            <ion-card-title>{{ profile.displayName }}</ion-card-title>
            <ion-card-subtitle>
              {{ 'profile.card.accountNo' | translate }} {{ profile.accountNo }}
            </ion-card-subtitle>
          </div>
        </ion-card-header>
        <ion-card-content>
          <p>
            {{ 'profile.card.status' | translate }}
            {{
              (profile.active ? 'profile.card.statusActive' : 'profile.card.statusInactive')
                | translate
            }}
          </p>
          @if (profile.memberSince) {
            <p>
              {{ 'profile.card.memberSince' | translate }}
              {{ profile.memberSince | date: 'mediumDate' }}
            </p>
          }
          @if (profile.maskedEmail) {
            <p>{{ 'profile.card.email' | translate }} {{ profile.maskedEmail }}</p>
          }
          @if (profile.maskedMobile) {
            <p>{{ 'profile.card.mobile' | translate }} {{ profile.maskedMobile }}</p>
          }
          <p>
            {{ 'profile.card.kyc' | translate }}
            {{
              (profile.kycVerified ? 'profile.card.kycVerified' : 'profile.card.kycPending')
                | translate
            }}
          </p>
        </ion-card-content>
      </ion-card>
    }

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'common.section.charges' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <form class="filter" [formGroup]="chargesForm" (ngSubmit)="applyChargesFilter()">
          <ion-select
            formControlName="status"
            fill="outline"
            labelPlacement="stacked"
            interface="popover"
            [label]="'common.table.status' | translate"
          >
            <ion-select-option value="all">{{ 'profile.charges.statusAll' | translate }}</ion-select-option>
            <ion-select-option value="active">{{ 'profile.charges.statusActive' | translate }}</ion-select-option>
            <ion-select-option value="inactive">{{ 'profile.charges.statusInactive' | translate }}</ion-select-option>
          </ion-select>
          <ion-input
            type="number"
            min="0"
            formControlName="page"
            fill="outline"
            labelPlacement="stacked"
            [label]="'common.filter.page' | translate"
          />
          <ion-input
            type="number"
            min="1"
            formControlName="size"
            fill="outline"
            labelPlacement="stacked"
            [label]="'common.filter.size' | translate"
          />
          <ion-button type="submit">
            {{ 'common.action.applyFilter' | translate }}
          </ion-button>
        </form>

        <div class="table-scroll">
        <table cdk-table [dataSource]="store.charges()">
          <ng-container cdkColumnDef="name">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.charge' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.name }}</td>
          </ng-container>
          <ng-container cdkColumnDef="dueDate">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.date' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.dueDate | date: 'mediumDate' }}</td>
          </ng-container>
          <ng-container cdkColumnDef="amount">
            <th cdk-header-cell *cdkHeaderCellDef class="num">{{ 'common.table.amount' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container cdkColumnDef="amountOutstanding">
            <th cdk-header-cell *cdkHeaderCellDef class="num">
              {{ 'common.table.outstanding' | translate }}
            </th>
            <td cdk-cell *cdkCellDef="let row" class="num">
              {{ row.amountOutstanding | number: '1.2-2' }}
            </td>
          </ng-container>
          <ng-container cdkColumnDef="status">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.status' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">
              {{
                (row.active ? 'profile.charges.statusActive' : 'profile.charges.statusInactive')
                  | translate
              }}
            </td>
          </ng-container>

          <tr cdk-header-row *cdkHeaderRowDef="chargeColumns"></tr>
          <tr cdk-row *cdkRowDef="let row; columns: chargeColumns"></tr>
          <tr class="empty-row" *cdkNoDataRow>
            <td [attr.colspan]="chargeColumns.length">{{ 'common.table.noCharges' | translate }}</td>
          </tr>
        </table>
        </div>
        <p class="total">
          {{ 'profile.charges.total' | translate: { total: store.totalFilteredRecords() } }}
        </p>
      </ion-card-content>
    </ion-card>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'profile.obligees.title' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <div class="table-scroll">
        <table cdk-table [dataSource]="store.obligees()">
          <ng-container cdkColumnDef="displayName">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.name' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.displayName }}</td>
          </ng-container>
          <ng-container cdkColumnDef="accountNumber">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.account' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.accountNumber }}</td>
          </ng-container>
          <ng-container cdkColumnDef="loanAmount">
            <th cdk-header-cell *cdkHeaderCellDef class="num">
              {{ 'profile.obligees.loanAmount' | translate }}
            </th>
            <td cdk-cell *cdkCellDef="let row" class="num">{{ row.loanAmount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container cdkColumnDef="guaranteeAmount">
            <th cdk-header-cell *cdkHeaderCellDef class="num">
              {{ 'profile.obligees.guaranteeAmount' | translate }}
            </th>
            <td cdk-cell *cdkCellDef="let row" class="num">
              {{ row.guaranteeAmount | number: '1.2-2' }}
            </td>
          </ng-container>
          <ng-container cdkColumnDef="amountReleased">
            <th cdk-header-cell *cdkHeaderCellDef class="num">
              {{ 'profile.obligees.amountReleased' | translate }}
            </th>
            <td cdk-cell *cdkCellDef="let row" class="num">
              {{ row.amountReleased | number: '1.2-2' }}
            </td>
          </ng-container>

          <tr cdk-header-row *cdkHeaderRowDef="obligeeColumns"></tr>
          <tr cdk-row *cdkRowDef="let row; columns: obligeeColumns"></tr>
          <tr class="empty-row" *cdkNoDataRow>
            <td [attr.colspan]="obligeeColumns.length">{{ 'profile.obligees.empty' | translate }}</td>
          </tr>
        </table>
        </div>
      </ion-card-content>
    </ion-card>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'profile.connectedApps.title' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <div class="table-scroll">
        <table cdk-table [dataSource]="store.consents()">
          <ng-container cdkColumnDef="tppClientId">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'profile.connectedApps.app' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.tppClientId }}</td>
          </ng-container>
          <ng-container cdkColumnDef="permissions">
            <th cdk-header-cell *cdkHeaderCellDef>
              {{ 'profile.connectedApps.permissions' | translate }}
            </th>
            <td cdk-cell *cdkCellDef="let row">{{ formatPermissions(row.permissions) }}</td>
          </ng-container>
          <ng-container cdkColumnDef="status">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.status' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">
              <span class="badge" [class]="consentTone(row.status)">
                {{ 'profile.connectedApps.status.' + row.status | translate }}
              </span>
            </td>
          </ng-container>
          <ng-container cdkColumnDef="creationDateTime">
            <th cdk-header-cell *cdkHeaderCellDef>
              {{ 'profile.connectedApps.granted' | translate }}
            </th>
            <td cdk-cell *cdkCellDef="let row">{{ row.creationDateTime | date: 'mediumDate' }}</td>
          </ng-container>
          <ng-container cdkColumnDef="expirationDateTime">
            <th cdk-header-cell *cdkHeaderCellDef>
              {{ 'profile.connectedApps.expires' | translate }}
            </th>
            <td cdk-cell *cdkCellDef="let row">{{ row.expirationDateTime | date: 'mediumDate' }}</td>
          </ng-container>
          <ng-container cdkColumnDef="actions">
            <th cdk-header-cell *cdkHeaderCellDef>
              {{ 'profile.connectedApps.actions' | translate }}
            </th>
            <td cdk-cell *cdkCellDef="let row" class="row-actions">
              @if (row.status === statusAuthorised) {
                @if (pendingRevokeId() === row.consentId) {
                  <ion-button
                    class="btn-danger"
                    [disabled]="revoking()"
                    (click)="confirmRevoke(row.consentId)"
                  >
                    {{ 'profile.connectedApps.revokeConfirm' | translate }}
                  </ion-button>
                  <ion-button fill="clear" [disabled]="revoking()" (click)="cancelRevoke()">
                    {{ 'profile.connectedApps.revokeCancel' | translate }}
                  </ion-button>
                } @else {
                  <ion-button class="btn-danger" (click)="requestRevoke(row.consentId)">
                    {{ 'profile.connectedApps.revoke' | translate }}
                  </ion-button>
                }
              }
            </td>
          </ng-container>

          <tr cdk-header-row *cdkHeaderRowDef="consentColumns"></tr>
          <tr cdk-row *cdkRowDef="let row; columns: consentColumns"></tr>
          <tr class="empty-row" *cdkNoDataRow>
            <td [attr.colspan]="consentColumns.length">
              {{ 'profile.connectedApps.empty' | translate }}
            </td>
          </tr>
        </table>
        </div>
      </ion-card-content>
    </ion-card>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'profile.changePassword.title' | translate }}</ion-card-title>
      </ion-card-header>
      <ion-card-content>
        <app-change-password />
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: [
    '../../shared/css/detail-page.scss',
    '../../shared/css/table.scss',
    '../../shared/css/filter-bar.scss',
    '../../shared/css/actions.scss',
    '../../shared/css/badge.scss',
  ],
  styles: `
    .profile-header {
      display: flex;
      flex-direction: column;
      align-items: center;
      gap: 0.75rem;
    }
    .profile-titles {
      text-align: center;
    }
    .avatar {
      width: 6rem;
      height: 6rem;
      flex-shrink: 0;
      border-radius: var(--radius-full);
      object-fit: cover;
    }
    .avatar-fallback {
      padding: 1rem;
      box-sizing: border-box;
      color: var(--slate-400);
      background-color: var(--neutral-tint);
    }
    .total {
      margin: 0.75rem 0 0;
      color: var(--slate-500);
    }
    .row-actions {
      white-space: nowrap;
    }
  `,
})
export class ProfileComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly store = inject(ProfileStore);

  protected readonly chargeColumns = ['name', 'dueDate', 'amount', 'amountOutstanding', 'status'];
  protected readonly obligeeColumns = [
    'displayName',
    'accountNumber',
    'loanAmount',
    'guaranteeAmount',
    'amountReleased',
  ];
  protected readonly consentColumns = [
    'tppClientId',
    'permissions',
    'status',
    'creationDateTime',
    'expirationDateTime',
    'actions',
  ];
  protected readonly statusAuthorised = OpenBankingUserConsentQueryData.StatusEnum.Authorised;
  protected readonly pendingRevokeId = signal<string | null>(null);
  protected readonly revoking = signal(false);

  protected readonly chargesForm = this.fb.group({
    status: this.fb.control<'all' | 'active' | 'inactive'>('all'),
    page: this.fb.control<number>(0),
    size: this.fb.control<number>(20),
  });

  constructor() {
    addIcons({ person });
    this.store.loadProfile();
    this.store.loadCharges({ status: 'all', page: 0, size: 20 });
    this.store.loadObligees();
    this.store.loadConsents();
    effect(() => {
      if (this.store.profile()?.hasImage && !this.store.image()) {
        this.store.loadImage();
      }
    });
  }

  protected applyChargesFilter(): void {
    const { status, page, size } = this.chargesForm.getRawValue();
    this.store.loadCharges({ status, page, size });
  }

  protected formatPermissions(permissions: Set<OpenBankingUserConsentQueryData.PermissionsEnum> | undefined): string {
    return [...(permissions ?? [])].join(', ');
  }

  protected consentTone(status: OpenBankingUserConsentQueryData.StatusEnum | undefined): string {
    switch (status) {
      case OpenBankingUserConsentQueryData.StatusEnum.Authorised:
        return 'success';
      case OpenBankingUserConsentQueryData.StatusEnum.AwaitingAuthorisation:
        return 'warning';
      case OpenBankingUserConsentQueryData.StatusEnum.Rejected:
      case OpenBankingUserConsentQueryData.StatusEnum.Revoked:
        return 'error';
      default:
        return 'neutral';
    }
  }

  protected requestRevoke(consentId: string): void {
    this.pendingRevokeId.set(consentId);
  }

  protected cancelRevoke(): void {
    this.pendingRevokeId.set(null);
  }

  protected confirmRevoke(consentId: string): void {
    this.revoking.set(true);
    this.store
      .revokeConsent(consentId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pendingRevokeId.set(null);
          this.revoking.set(false);
          this.store.loadConsents();
        },
        error: () => this.revoking.set(false),
      });
  }
}
