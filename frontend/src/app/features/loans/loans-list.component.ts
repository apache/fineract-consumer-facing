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
import { Router, RouterLink } from '@angular/router';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonIcon,
  IonProgressBar,
  IonRouterLink,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { add } from 'ionicons/icons';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { LoansStore } from './loans.store';

@Component({
  selector: 'app-loans-list',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTableModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonIcon,
    IonProgressBar,
    IonRouterLink,
    RouterLink,
    PageHeaderComponent,
    StatusBadgeComponent,
    TranslatePipe,
  ],
  template: `
    <app-page-header [title]="'loans.list.title' | translate">
      <ion-button [routerLink]="['/loans', 'apply']">
        <ion-icon slot="start" name="add" aria-hidden="true" />
        {{ 'loans.list.applyCta' | translate }}
      </ion-button>
    </app-page-header>

    <ion-card>
      @if (store.loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        <div class="table-scroll">
        <table cdk-table [dataSource]="store.loans()">
          <ng-container cdkColumnDef="accountNo">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.account' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.accountNo }}</td>
          </ng-container>
          <ng-container cdkColumnDef="productName">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.product' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.productName }}</td>
          </ng-container>
          <ng-container cdkColumnDef="status">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.status' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">
              @if (row.status) {
                <app-status-badge [status]="row.status" />
              }
            </td>
          </ng-container>
          <ng-container cdkColumnDef="currency">
            <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.currency' | translate }}</th>
            <td cdk-cell *cdkCellDef="let row">{{ row.currency }}</td>
          </ng-container>

          <tr cdk-header-row *cdkHeaderRowDef="columns"></tr>
          <tr cdk-row *cdkRowDef="let row; columns: columns" class="clickable" (click)="open(row.id)"></tr>
          <tr class="empty-row" *cdkNoDataRow>
            <td [attr.colspan]="columns.length">{{ 'loans.list.empty' | translate }}</td>
          </tr>
        </table>
        </div>

        <div class="actions-row">
          <ion-button class="btn-danger" (click)="tryForbiddenLoan()">
            {{ 'loans.list.tryForbidden' | translate }}
          </ion-button>
        </div>
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: ['../../shared/css/table.scss', '../../shared/css/actions.scss'],
})
export class LoansListComponent {
  protected readonly store = inject(LoansStore);
  private readonly router = inject(Router);

  protected readonly columns = ['accountNo', 'productName', 'status', 'currency'];

  constructor() {
    addIcons({ add });
    this.store.loadLoans();
  }

  protected open(loanId: number | undefined): void {
    if (loanId != null) {
      this.router.navigate(['/loans', loanId]);
    }
  }

  protected tryForbiddenLoan(): void {
    this.router.navigate(['/loans', 999999]);
  }
}
