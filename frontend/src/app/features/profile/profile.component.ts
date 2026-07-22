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
import { DatePipe, DecimalPipe } from '@angular/common';
import { NonNullableFormBuilder, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { ChangePasswordComponent } from './change-password.component';
import { ProfileStore } from './profile.store';

@Component({
  selector: 'app-profile',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    TranslatePipe,
    DatePipe,
    DecimalPipe,
    PageHeaderComponent,
    ChangePasswordComponent,
  ],
  template: `
    <app-page-header [title]="'profile.title' | translate" />

    @if (store.loading()) {
      <mat-progress-bar mode="indeterminate" />
    }

    @if (store.profile(); as profile) {
      <mat-card>
        <mat-card-header>
          @if (profile.hasImage && store.image(); as image) {
            <img
              mat-card-avatar
              [src]="image.imageDataUri"
              [alt]="'profile.card.photoAlt' | translate"
            />
          }
          <mat-card-title>{{ profile.displayName }}</mat-card-title>
          <mat-card-subtitle>
            {{ 'profile.card.accountNo' | translate }} {{ profile.accountNo }}
          </mat-card-subtitle>
        </mat-card-header>
        <mat-card-content>
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
        </mat-card-content>
      </mat-card>
    }

    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'common.section.charges' | translate }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <form class="filter" [formGroup]="chargesForm" (ngSubmit)="applyChargesFilter()">
          <mat-form-field appearance="fill">
            <mat-label>{{ 'common.table.status' | translate }}</mat-label>
            <mat-select formControlName="status">
              <mat-option value="all">{{ 'profile.charges.statusAll' | translate }}</mat-option>
              <mat-option value="active">{{ 'profile.charges.statusActive' | translate }}</mat-option>
              <mat-option value="inactive">{{ 'profile.charges.statusInactive' | translate }}</mat-option>
            </mat-select>
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'common.filter.page' | translate }}</mat-label>
            <input matInput type="number" min="0" formControlName="page" />
          </mat-form-field>
          <mat-form-field appearance="fill">
            <mat-label>{{ 'common.filter.size' | translate }}</mat-label>
            <input matInput type="number" min="1" formControlName="size" />
          </mat-form-field>
          <button mat-flat-button color="primary" type="submit">
            {{ 'common.action.applyFilter' | translate }}
          </button>
        </form>

        <table mat-table [dataSource]="store.charges()">
          <ng-container matColumnDef="name">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.charge' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.name }}</td>
          </ng-container>
          <ng-container matColumnDef="dueDate">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.date' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.dueDate | date: 'mediumDate' }}</td>
          </ng-container>
          <ng-container matColumnDef="amount">
            <th mat-header-cell *matHeaderCellDef class="num">{{ 'common.table.amount' | translate }}</th>
            <td mat-cell *matCellDef="let row" class="num">{{ row.amount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="amountOutstanding">
            <th mat-header-cell *matHeaderCellDef class="num">
              {{ 'common.table.outstanding' | translate }}
            </th>
            <td mat-cell *matCellDef="let row" class="num">
              {{ row.amountOutstanding | number: '1.2-2' }}
            </td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.status' | translate }}</th>
            <td mat-cell *matCellDef="let row">
              {{
                (row.active ? 'profile.charges.statusActive' : 'profile.charges.statusInactive')
                  | translate
              }}
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="chargeColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: chargeColumns"></tr>
          <tr class="empty-row" *matNoDataRow>
            <td [attr.colspan]="chargeColumns.length">{{ 'common.table.noCharges' | translate }}</td>
          </tr>
        </table>
        <p class="total">
          {{ 'profile.charges.total' | translate: { total: store.totalFilteredRecords() } }}
        </p>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'profile.obligees.title' | translate }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <table mat-table [dataSource]="store.obligees()">
          <ng-container matColumnDef="displayName">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.name' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.displayName }}</td>
          </ng-container>
          <ng-container matColumnDef="accountNumber">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.account' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.accountNumber }}</td>
          </ng-container>
          <ng-container matColumnDef="loanAmount">
            <th mat-header-cell *matHeaderCellDef class="num">
              {{ 'profile.obligees.loanAmount' | translate }}
            </th>
            <td mat-cell *matCellDef="let row" class="num">{{ row.loanAmount | number: '1.2-2' }}</td>
          </ng-container>
          <ng-container matColumnDef="guaranteeAmount">
            <th mat-header-cell *matHeaderCellDef class="num">
              {{ 'profile.obligees.guaranteeAmount' | translate }}
            </th>
            <td mat-cell *matCellDef="let row" class="num">
              {{ row.guaranteeAmount | number: '1.2-2' }}
            </td>
          </ng-container>
          <ng-container matColumnDef="amountReleased">
            <th mat-header-cell *matHeaderCellDef class="num">
              {{ 'profile.obligees.amountReleased' | translate }}
            </th>
            <td mat-cell *matCellDef="let row" class="num">
              {{ row.amountReleased | number: '1.2-2' }}
            </td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="obligeeColumns"></tr>
          <tr mat-row *matRowDef="let row; columns: obligeeColumns"></tr>
          <tr class="empty-row" *matNoDataRow>
            <td [attr.colspan]="obligeeColumns.length">{{ 'profile.obligees.empty' | translate }}</td>
          </tr>
        </table>
      </mat-card-content>
    </mat-card>

    <mat-card>
      <mat-card-header>
        <mat-card-title>{{ 'profile.changePassword.title' | translate }}</mat-card-title>
      </mat-card-header>
      <mat-card-content>
        <app-change-password />
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 1.5rem;
      padding: 2rem;
    }
    table {
      width: 100%;
    }
    .filter {
      display: flex;
      flex-wrap: wrap;
      gap: 0.75rem;
      align-items: center;
      margin-bottom: 1rem;
    }
    .total {
      margin: 0.75rem 0 0;
      color: rgba(0, 0, 0, 0.6);
    }
  `,
})
export class ProfileComponent {
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly store = inject(ProfileStore);

  protected readonly chargeColumns = ['name', 'dueDate', 'amount', 'amountOutstanding', 'status'];
  protected readonly obligeeColumns = [
    'displayName',
    'accountNumber',
    'loanAmount',
    'guaranteeAmount',
    'amountReleased',
  ];

  protected readonly chargesForm = this.fb.group({
    status: this.fb.control<'all' | 'active' | 'inactive'>('all'),
    page: this.fb.control<number>(0),
    size: this.fb.control<number>(20),
  });

  constructor() {
    this.store.loadProfile();
    this.store.loadCharges({ status: 'all', page: 0, size: 20 });
    this.store.loadObligees();
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
