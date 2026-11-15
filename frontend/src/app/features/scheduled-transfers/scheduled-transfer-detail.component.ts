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

import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  computed,
  inject,
  signal,
} from '@angular/core';
import { DatePipe, DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonCardHeader,
  IonCardTitle,
  IonIcon,
  IonProgressBar,
  IonRouterLink,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { arrowBack, closeCircle } from 'ionicons/icons';
import { OVERLAY, TranslatePipe } from '../../core/adapters';
import { I18nService } from '../../core/i18n/i18n.service';
import { OtpComponent } from '../../shared/otp/otp.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { StatusBadgeComponent } from '../../shared/ui/status-badge.component';
import { BeneficiariesStore } from '../beneficiaries/beneficiaries.store';
import { SavingsStore } from '../savings/savings.store';
import { accountLabels, labelFor } from './account-labels';
import { ScheduledTransfersStore } from './scheduled-transfers.store';

const RUNS_PAGE_SIZE = 10;

@Component({
  selector: 'app-scheduled-transfer-detail',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    CdkTableModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonCardHeader,
    IonCardTitle,
    IonIcon,
    IonProgressBar,
    IonRouterLink,
    RouterLink,
    OtpComponent,
    PageHeaderComponent,
    StatusBadgeComponent,
    DatePipe,
    DecimalPipe,
    TranslatePipe,
  ],
  template: `
    <app-page-header [title]="'scheduledTransfers.detail.title' | appTranslate">
      <ion-button fill="outline" routerLink="/transfers/scheduled">
        <ion-icon slot="start" name="arrow-back" aria-hidden="true" />
        {{ 'scheduledTransfers.backCta' | appTranslate }}
      </ion-button>
    </app-page-header>

    <ion-card>
      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }
      <ion-card-content>
        @if (step() === 'otp') {
          <p>{{ 'scheduledTransfers.detail.cancelPrompt' | appTranslate }}</p>
          <app-otp
            [sentTo]="store.challenge()?.sentTo ?? null"
            [loading]="loading()"
            (submitted)="confirmCancel($event)"
            (cancelled)="step.set('view')"
          />
        } @else if (transfer(); as current) {
          <dl>
            <dt>{{ 'common.filter.from' | appTranslate }}</dt>
            <dd>{{ label(current.fromAccountId) }}</dd>
            <dt>{{ 'common.filter.to' | appTranslate }}</dt>
            <dd>{{ label(current.toAccountId) }}</dd>
            <dt>{{ 'common.table.amount' | appTranslate }}</dt>
            <dd>{{ current.amount | number: '1.2-2' }}</dd>
            <dt>{{ 'scheduledTransfers.frequency.label' | appTranslate }}</dt>
            <dd>{{ 'scheduledTransfers.frequency.' + current.frequency | appTranslate }}</dd>
            <dt>{{ 'scheduledTransfers.startDate' | appTranslate }}</dt>
            <dd>{{ current.startDate | date: 'mediumDate' }}</dd>
            <dt>{{ 'scheduledTransfers.endDate' | appTranslate }}</dt>
            <dd>
              @if (current.endDate) {
                {{ current.endDate | date: 'mediumDate' }}
              } @else {
                {{ 'scheduledTransfers.noEndDate' | appTranslate }}
              }
            </dd>
          </dl>
          <div class="actions-end">
            <ion-button color="danger" fill="outline" [disabled]="loading()" (click)="cancel()">
              <ion-icon slot="start" name="close-circle" aria-hidden="true" />
              {{ 'scheduledTransfers.detail.cancelCta' | appTranslate }}
            </ion-button>
          </div>
        } @else if (store.loaded()) {
          <p>{{ 'scheduledTransfers.detail.notActive' | appTranslate }}</p>
        }
      </ion-card-content>
    </ion-card>

    <ion-card>
      <ion-card-header>
        <ion-card-title>{{ 'scheduledTransfers.runs.title' | appTranslate }}</ion-card-title>
      </ion-card-header>
      @if (store.runsLoading()) {
        <ion-progress-bar type="indeterminate" />
      }
      <ion-card-content>
        <div class="table-scroll">
          <table cdk-table [dataSource]="store.runs()">
            <ng-container cdkColumnDef="date">
              <th cdk-header-cell *cdkHeaderCellDef>{{ 'common.table.date' | appTranslate }}</th>
              <td cdk-cell *cdkCellDef="let row">{{ row.date | date: 'mediumDate' }}</td>
            </ng-container>
            <ng-container cdkColumnDef="status">
              <th cdk-header-cell *cdkHeaderCellDef>
                {{ 'scheduledTransfers.runs.status.label' | appTranslate }}
              </th>
              <td cdk-cell *cdkCellDef="let row">
                <app-status-badge [status]="'scheduledTransfers.runs.status.' + row.status" />
              </td>
            </ng-container>
            <ng-container cdkColumnDef="amount">
              <th cdk-header-cell *cdkHeaderCellDef class="num">
                {{ 'common.table.amount' | appTranslate }}
              </th>
              <td cdk-cell *cdkCellDef="let row" class="num">
                @if (row.amount != null) {
                  {{ row.amount | number: '1.2-2' }}
                } @else {
                  —
                }
              </td>
            </ng-container>

            <tr cdk-header-row *cdkHeaderRowDef="runColumns"></tr>
            <tr cdk-row *cdkRowDef="let row; columns: runColumns"></tr>
            <tr class="empty-row" *cdkNoDataRow>
              <td [attr.colspan]="runColumns.length">
                {{ 'scheduledTransfers.runs.empty' | appTranslate }}
              </td>
            </tr>
          </table>
        </div>

        <div class="pager">
          <span class="showing">
            {{ 'common.pagination.showing' | appTranslate: showingParams() }}
          </span>
          <ion-button
            size="small"
            fill="outline"
            [disabled]="runsPrevDisabled()"
            (click)="loadRuns(store.runsPage() - 1)"
          >
            {{ 'common.action.prev' | appTranslate }}
          </ion-button>
          <ion-button
            size="small"
            fill="outline"
            [disabled]="runsNextDisabled()"
            (click)="loadRuns(store.runsPage() + 1)"
          >
            {{ 'common.action.next' | appTranslate }}
          </ion-button>
        </div>
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: [
    '../../shared/css/table.scss',
    '../../shared/css/pager.scss',
    '../../shared/css/actions.scss',
  ],
  styles: `
    :host {
      display: flex;
      flex-direction: column;
      gap: 1rem;
    }
    dl {
      display: grid;
      grid-template-columns: auto 1fr;
      gap: 0.25rem 1rem;
      margin: 0 0 1rem;
    }
    dt {
      font-weight: 600;
    }
    dd {
      margin: 0;
    }
  `,
})
export class ScheduledTransferDetailComponent {
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroyRef = inject(DestroyRef);
  private readonly overlay = inject(OVERLAY);
  private readonly i18n = inject(I18nService);
  protected readonly store = inject(ScheduledTransfersStore);
  private readonly savingsStore = inject(SavingsStore);
  private readonly beneficiariesStore = inject(BeneficiariesStore);

  protected readonly publicId = this.route.snapshot.paramMap.get('publicId') ?? '';
  protected readonly step = signal<'view' | 'otp'>('view');
  protected readonly loading = signal(false);
  protected readonly runColumns = ['date', 'status', 'amount'];

  /** The transfer when it is still active. Ended or cancelled ones keep only their run history. */
  protected readonly transfer = computed(() =>
    this.store.transfers().find((row) => row.publicId === this.publicId),
  );

  private readonly labels = computed(() =>
    accountLabels(this.savingsStore.accounts(), this.beneficiariesStore.beneficiaries()),
  );

  protected readonly runsPrevDisabled = computed(
    () => this.store.runsLoading() || this.store.runsPage() === 0,
  );
  protected readonly runsNextDisabled = computed(
    () =>
      this.store.runsLoading() ||
      this.store.runsTotalPages() === 0 ||
      this.store.runsPage() >= this.store.runsTotalPages() - 1,
  );
  protected readonly showingParams = computed(() => {
    const total = this.store.runsTotalElements();
    if (total === 0) {
      return { from: 0, to: 0, total: 0 };
    }
    const page = this.store.runsPage();
    const size = this.store.runsSize();
    return { from: page * size + 1, to: Math.min((page + 1) * size, total), total };
  });

  private idempotencyKey: string | null = null;

  constructor() {
    addIcons({ arrowBack, closeCircle });
    this.savingsStore.loadAccounts();
    this.beneficiariesStore.load().pipe(takeUntilDestroyed()).subscribe();
    this.store.load().pipe(takeUntilDestroyed()).subscribe();
    this.loadRuns(0);
  }

  protected label(accountId: number | undefined): string {
    return labelFor(this.labels(), accountId);
  }

  protected loadRuns(page: number): void {
    if (page < 0) {
      return;
    }
    this.store.loadRuns(this.publicId, page, RUNS_PAGE_SIZE);
  }

  protected cancel(): void {
    this.loading.set(true);
    this.store
      .initiateCancel(this.publicId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.step.set('otp');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected confirmCancel(otp: string): void {
    const stepUpToken = this.store.challenge()?.stepUpToken;
    if (!stepUpToken) {
      return;
    }
    this.loading.set(true);
    this.idempotencyKey ??= crypto.randomUUID();
    this.store
      .confirmCancel(this.publicId, this.idempotencyKey, { stepUpToken, otp })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.idempotencyKey = null;
          this.loading.set(false);
          this.notify('scheduledTransfers.detail.cancelled');
          void this.router.navigate(['/transfers', 'scheduled']);
        },
        error: () => this.loading.set(false),
      });
  }

  private notify(key: string): void {
    void this.overlay.toast({
      message: this.i18n.translate(key),
      duration: 5000,
      position: 'bottom',
      dismissLabel: this.i18n.translate('common.action.dismiss'),
    });
  }
}
