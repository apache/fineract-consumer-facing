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

import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Router } from '@angular/router';
import { DatePipe } from '@angular/common';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonProgressBar,
} from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { DemoStore } from './demo.store';

const PAGE_SIZE = 10;

@Component({
  selector: 'app-demo',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTableModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    IonProgressBar,
    DatePipe,
    TranslatePipe,
    PageHeaderComponent,
  ],
  template: `
    <app-page-header [title]="'demo.title' | translate" />

    <p class="demo-note">{{ 'demo.note' | translate }}</p>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'demo.audit.title' | translate }}</ion-card-title>
      </ion-card-header>

      @if (store.loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        <div class="table-scroll">
          <table cdk-table [dataSource]="store.events()">
            <ng-container cdkColumnDef="eventType">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'demo.audit.columns.eventType' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">{{ row.eventType }}</td>
            </ng-container>
            <ng-container cdkColumnDef="severity">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'demo.audit.columns.severity' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">{{ row.severity }}</td>
            </ng-container>
            <ng-container cdkColumnDef="source">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'demo.audit.columns.source' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">{{ row.source }}</td>
            </ng-container>
            <ng-container cdkColumnDef="receivedAt">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'demo.audit.columns.receivedAt' | translate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">{{ row.receivedAt | date: 'medium' }}</td>
            </ng-container>

            <tr cdk-header-row *cdkHeaderRowDef="columns"></tr>
            <tr cdk-row *cdkRowDef="let row; columns: columns"></tr>
            <tr class="empty-row" *cdkNoDataRow>
              <td [attr.colspan]="columns.length">{{ 'demo.audit.empty' | translate }}</td>
            </tr>
          </table>
        </div>

        <div class="pager">
          <ion-button
            size="small"
            fill="outline"
            [disabled]="page() === 0 || store.loading()"
            (click)="prev()"
          >
            {{ 'common.action.prev' | translate }}
          </ion-button>
          <ion-button size="small" fill="outline" [disabled]="nextDisabled()" (click)="next()">
            {{ 'common.action.next' | translate }}
          </ion-button>
        </div>
      </ion-card-content>
    </ion-card>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'demo.abac.title' | translate }}</ion-card-title>
      </ion-card-header>

      <ion-card-content>
        <p>{{ 'demo.abac.explanation' | translate }}</p>

        <div class="actions-row">
          <ion-button class="btn-danger" (click)="tryForbiddenSavings()">
            {{ 'demo.abac.tryForbiddenSavings' | translate }}
          </ion-button>
          <ion-button class="btn-danger" (click)="tryForbiddenLoan()">
            {{ 'demo.abac.tryForbiddenLoan' | translate }}
          </ion-button>
        </div>
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: [
    '../../shared/css/detail-page.scss',
    '../../shared/css/table.scss',
    '../../shared/css/pager.scss',
    '../../shared/css/actions.scss',
  ],
  styles: `
    .demo-note {
      margin: 0 0 1rem;
      color: var(--slate-600);
      font-style: italic;
    }
  `,
})
export class DemoComponent {
  protected readonly store = inject(DemoStore);
  private readonly router = inject(Router);

  protected readonly columns = ['eventType', 'severity', 'source', 'receivedAt'];
  protected readonly page = signal(0);
  protected readonly nextDisabled = computed(
    () => this.store.loading() || this.store.events().length < PAGE_SIZE,
  );

  constructor() {
    this.store.loadEvents(0, PAGE_SIZE);
  }

  protected prev(): void {
    const page = this.page() - 1;
    if (page < 0) {
      return;
    }
    this.page.set(page);
    this.store.loadEvents(page, PAGE_SIZE);
  }

  protected next(): void {
    const page = this.page() + 1;
    this.page.set(page);
    this.store.loadEvents(page, PAGE_SIZE);
  }

  protected tryForbiddenSavings(): void {
    this.router.navigate(['/savings', 999999]);
  }

  protected tryForbiddenLoan(): void {
    this.router.navigate(['/loans', 999999]);
  }
}
