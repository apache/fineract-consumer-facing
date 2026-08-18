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

import { ChangeDetectionStrategy, Component, effect, inject } from '@angular/core';
import { CurrencyPipe, DatePipe } from '@angular/common';
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
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
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
    CurrencyPipe,
    PageHeaderComponent,
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
            <ion-select-option value="all">{{
              'profile.charges.statusAll' | translate
            }}</ion-select-option>
            <ion-select-option value="active">{{
              'profile.charges.stateOpen' | translate
            }}</ion-select-option>
            <ion-select-option value="inactive">{{
              'profile.charges.stateClosed' | translate
            }}</ion-select-option>
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
              <th cdk-header-cell *cdkHeaderCellDef class="num">
                {{ 'common.table.amount' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row" class="num">
                {{ row.amount | currency: row.currency }}
              </td>
            </ng-container>
            <ng-container cdkColumnDef="amountOutstanding">
              <th cdk-header-cell *cdkHeaderCellDef class="num">
                {{ 'common.table.outstanding' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row" class="num">
                {{ row.amountOutstanding | currency: row.currency }}
              </td>
            </ng-container>
            <ng-container cdkColumnDef="status">
              <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.status' | translate }}</th>
              <td cdk-cell *cdkCellDef="let row">
                {{
                  (row.active ? 'profile.charges.stateOpen' : 'profile.charges.stateClosed')
                    | translate
                }}
              </td>
            </ng-container>

            <tr cdk-header-row *cdkHeaderRowDef="chargeColumns"></tr>
            <tr cdk-row *cdkRowDef="let row; columns: chargeColumns"></tr>
            <tr class="empty-row" *cdkNoDataRow>
              <td [attr.colspan]="chargeColumns.length">
                {{ 'common.table.noCharges' | translate }}
              </td>
            </tr>
          </table>
        </div>
        <p class="total">
          {{ 'profile.charges.total' | translate: { total: store.totalFilteredRecords() } }}
        </p>
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: [
    '../../shared/css/detail-page.scss',
    '../../shared/css/table.scss',
    '../../shared/css/filter-bar.scss',
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
  `,
})
export class ProfileComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly store = inject(ProfileStore);

  protected readonly chargeColumns = ['name', 'dueDate', 'amount', 'amountOutstanding', 'status'];

  protected readonly chargesForm = this.fb.group({
    status: this.fb.control<'all' | 'active' | 'inactive'>('all'),
    page: this.fb.control<number>(0),
    size: this.fb.control<number>(20),
  });

  constructor() {
    addIcons({ person });
    this.store.loadProfile();
    this.store.loadCharges({ status: 'all', page: 0, size: 20 });
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
}
