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
import { Router } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { SavingsStore } from './savings.store';

@Component({
  selector: 'app-savings-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    MatButtonModule,
    MatCardModule,
    MatProgressBarModule,
    MatTableModule,
    TranslatePipe,
    PageHeaderComponent,
    StatusBadgeComponent,
  ],
  template: `
    <app-page-header [title]="'savings.list.title' | translate" />

    <mat-card>
      @if (store.loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <mat-card-content>
        <table mat-table [dataSource]="store.accounts()">
          <ng-container matColumnDef="accountNo">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.account' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.accountNo }}</td>
          </ng-container>
          <ng-container matColumnDef="productName">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.product' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.productName }}</td>
          </ng-container>
          <ng-container matColumnDef="status">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.status' | translate }}</th>
            <td mat-cell *matCellDef="let row">
              @if (row.status) {
                <app-status-badge [status]="row.status" />
              }
            </td>
          </ng-container>
          <ng-container matColumnDef="currency">
            <th mat-header-cell *matHeaderCellDef>{{ 'common.table.currency' | translate }}</th>
            <td mat-cell *matCellDef="let row">{{ row.currency }}</td>
          </ng-container>

          <tr mat-header-row *matHeaderRowDef="columns"></tr>
          <tr mat-row *matRowDef="let row; columns: columns" class="clickable" (click)="open(row.id)"></tr>
          <tr class="empty-row" *matNoDataRow>
            <td [attr.colspan]="columns.length">{{ 'savings.list.empty' | translate }}</td>
          </tr>
        </table>

        <div class="actions">
          <!--
            A6 (ABAC denial): deep-link to an account this client does not own. The BFF is the policy
            enforcement point and rejects it with a 403-class ConsumerApiError, which errorInterceptor
            surfaces as a snackbar — never a raw 403 page. 999999 is a deliberately non-owned id.
          -->
          <button mat-stroked-button color="warn" (click)="tryForbiddenAccount()">
            {{ 'savings.list.tryForbidden' | translate }}
          </button>
        </div>
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    table {
      width: 100%;
    }
    .clickable {
      cursor: pointer;
    }
    .actions {
      display: flex;
      gap: 0.75rem;
      margin-top: 1rem;
    }
    .actions button {
      background-color: #ffd6d6;
      color: #b00020;
    }
  `,
})
export class SavingsListComponent {
  protected readonly store = inject(SavingsStore);
  private readonly router = inject(Router);

  protected readonly columns = ['accountNo', 'productName', 'status', 'currency'];

  constructor() {
    this.store.loadAccounts();
  }

  protected open(savingsId: number | undefined): void {
    if (savingsId != null) {
      this.router.navigate(['/savings', savingsId]);
    }
  }

  protected tryForbiddenAccount(): void {
    this.router.navigate(['/savings', 999999]);
  }
}
