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

import { ChangeDetectionStrategy, Component, DestroyRef, computed, inject, signal } from '@angular/core';
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonIcon,
  IonInput,
  IonProgressBar,
  IonSelect,
  IonSelectOption,
  ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { pencil, personAdd, trash } from 'ionicons/icons';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { BeneficiaryQueryData, InitiateAddBeneficiaryCommandRequest } from '@bff/client';
import { OtpComponent } from '../../shared/otp/otp.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { BeneficiariesStore } from './beneficiaries.store';

@Component({
  selector: 'app-beneficiaries',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    CdkTableModule,
    IonButton,
    IonCard,
    IonCardContent,
    IonIcon,
    IonInput,
    IonProgressBar,
    IonSelect,
    IonSelectOption,
    DecimalPipe,
    TranslatePipe,
    OtpComponent,
    PageHeaderComponent,
  ],
  template: `
    <app-page-header [title]="'beneficiaries.title' | translate" />

    <ion-card>
      @if (loading()) {
        <ion-progress-bar type="indeterminate" />
      }

      <ion-card-content>
        @switch (step()) {
          @case ('list') {
            <div class="table-scroll">
            <table cdk-table [dataSource]="store.beneficiaries()">
              <ng-container cdkColumnDef="name">
                <th cdk-header-cell *cdkHeaderCellDef>{{ 'beneficiaries.list.nameColumn' | translate }}</th>
                <td cdk-cell *cdkCellDef="let row">{{ row.name }}</td>
              </ng-container>
              <ng-container cdkColumnDef="accountType">
                <th cdk-header-cell *cdkHeaderCellDef>
                  {{ 'beneficiaries.list.accountTypeColumn' | translate }}
                </th>
                <td cdk-cell *cdkCellDef="let row">
                  @if (typeLabelKey(row.accountType); as key) {
                    {{ key | translate }}
                  } @else {
                    {{ row.accountType }}
                  }
                </td>
              </ng-container>
              <ng-container cdkColumnDef="transferLimit">
                <th cdk-header-cell *cdkHeaderCellDef class="num">
                  {{ 'beneficiaries.list.transferLimitColumn' | translate }}
                </th>
                <td cdk-cell *cdkCellDef="let row" class="num">
                  @if (row.transferLimit != null) {
                    {{ row.transferLimit | number: '1.2-2' }}
                  } @else {
                    —
                  }
                </td>
              </ng-container>
              <ng-container cdkColumnDef="actions">
                <th cdk-header-cell *cdkHeaderCellDef>
                  {{ 'beneficiaries.list.actionsColumn' | translate }}
                </th>
                <td cdk-cell *cdkCellDef="let row" class="row-actions">
                  @if (pendingDeleteId() === row.publicId) {
                    <ion-button
                      class="btn-danger"
                      [disabled]="loading()"
                      (click)="confirmDelete(row.publicId)"
                    >
                      {{ 'beneficiaries.delete.confirm' | translate }}
                    </ion-button>
                    <ion-button fill="clear" [disabled]="loading()" (click)="cancelDelete()">
                      {{ 'beneficiaries.delete.cancel' | translate }}
                    </ion-button>
                  } @else {
                    <ion-button
                      fill="clear"
                      class="icon-action"
                      [attr.aria-label]="'beneficiaries.edit.title' | translate"
                      (click)="startEdit(row)"
                    >
                      <ion-icon slot="icon-only" name="pencil" />
                    </ion-button>
                    <ion-button
                      fill="clear"
                      class="icon-action"
                      [attr.aria-label]="'beneficiaries.delete.confirm' | translate"
                      (click)="requestDelete(row.publicId)"
                    >
                      <ion-icon slot="icon-only" name="trash" />
                    </ion-button>
                  }
                </td>
              </ng-container>

              <tr cdk-header-row *cdkHeaderRowDef="columns"></tr>
              <tr cdk-row *cdkRowDef="let row; columns: columns"></tr>
              <tr class="empty-row" *cdkNoDataRow>
                <td [attr.colspan]="columns.length">{{ 'beneficiaries.list.empty' | translate }}</td>
              </tr>
            </table>
            </div>

            <div class="actions-end">
              <ion-button (click)="startAdd()">
                <ion-icon slot="start" name="person-add" />
                {{ 'beneficiaries.list.addCta' | translate }}
              </ion-button>
            </div>
          }
          @case ('add-form') {
            <form [formGroup]="addForm" (ngSubmit)="submitAdd()">
              <ion-input
                formControlName="name"
                [maxlength]="50"
                fill="outline"
                labelPlacement="stacked"
                [label]="'beneficiaries.form.nameLabel' | translate"
              />
              <ion-input
                formControlName="officeName"
                fill="outline"
                labelPlacement="stacked"
                [label]="'beneficiaries.form.officeNameLabel' | translate"
              />
              <ion-input
                formControlName="accountNumber"
                fill="outline"
                labelPlacement="stacked"
                [label]="'beneficiaries.form.accountNumberLabel' | translate"
              />
              <ion-select
                formControlName="accountType"
                interface="popover"
                fill="outline"
                labelPlacement="stacked"
                [label]="'beneficiaries.form.accountTypeLabel' | translate"
              >
                @for (option of typeOptions(); track option.value) {
                  <ion-select-option [value]="option.value">
                    @if (option.labelKey) {
                      {{ option.labelKey | translate }}
                    } @else {
                      {{ option.value }}
                    }
                  </ion-select-option>
                }
              </ion-select>
              <ion-input
                type="number"
                step="0.01"
                formControlName="transferLimit"
                fill="outline"
                labelPlacement="stacked"
                [label]="'beneficiaries.form.transferLimitLabel' | translate"
              />
              <div class="actions-end">
                <ion-button fill="outline" type="button" [disabled]="loading()" (click)="backToList()">
                  {{ 'beneficiaries.form.cancelCta' | translate }}
                </ion-button>
                <ion-button type="submit" [disabled]="loading() || addForm.invalid">
                  {{ 'beneficiaries.form.submitCta' | translate }}
                </ion-button>
              </div>
            </form>
          }
          @case ('edit-form') {
            <h3>{{ 'beneficiaries.edit.title' | translate }}</h3>
            <form [formGroup]="editForm" (ngSubmit)="submitEdit()">
              <ion-input
                formControlName="name"
                [maxlength]="50"
                fill="outline"
                labelPlacement="stacked"
                [label]="'beneficiaries.form.nameLabel' | translate"
              />
              <ion-input
                type="number"
                step="0.01"
                formControlName="transferLimit"
                fill="outline"
                labelPlacement="stacked"
                [label]="'beneficiaries.form.transferLimitLabel' | translate"
              />
              <div class="actions-end">
                <ion-button fill="outline" type="button" [disabled]="loading()" (click)="backToList()">
                  {{ 'beneficiaries.form.cancelCta' | translate }}
                </ion-button>
                <ion-button type="submit" [disabled]="loading() || editForm.invalid">
                  {{ 'beneficiaries.form.submitCta' | translate }}
                </ion-button>
              </div>
            </form>
          }
          @case ('otp') {
            <div class="otp-container">
              <app-otp
                [sentTo]="store.challenge()?.sentTo ?? null"
                [loading]="loading()"
                (submitted)="confirmOtp($event)"
                (cancelled)="cancelOtp()"
              />
            </div>
          }
        }
      </ion-card-content>
    </ion-card>
  `,
  styleUrls: [
    '../../shared/css/form.scss',
    '../../shared/css/table.scss',
    '../../shared/css/actions.scss',
  ],
  styles: `
    .row-actions {
      white-space: nowrap;
    }
    form {
      max-width: 28rem;
      gap: 0.875rem;
    }
    .actions-end {
      margin-top: 1rem;
    }
    .otp-container {
      max-width: 24rem;
    }
    ion-button.icon-action {
      width: 2rem;
      height: 2rem;
      font-size: var(--text-lg);
      --padding-start: 0;
      --padding-end: 0;
      --padding-top: 0;
      --padding-bottom: 0;
    }
  `,
})
export class BeneficiariesComponent {
  private static readonly KNOWN_TYPE_LABEL_KEYS: Record<string, string> = {
    SAVINGS: 'beneficiaries.accountType.savings',
    LOAN: 'beneficiaries.accountType.loan',
  };

  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toastCtrl = inject(ToastController);
  private readonly translate = inject(TranslateService);
  protected readonly store = inject(BeneficiariesStore);

  protected readonly step = signal<'list' | 'add-form' | 'edit-form' | 'otp'>('list');
  protected readonly loading = signal(false);
  protected readonly otpMode = signal<'add' | 'edit'>('add');
  protected readonly editing = signal<BeneficiaryQueryData | null>(null);
  protected readonly pendingDeleteId = signal<string | null>(null);

  protected readonly columns = ['name', 'accountType', 'transferLimit', 'actions'];

  protected readonly typeOptions = computed(() =>
    this.store.accountTypeOptions().map(value => ({
      value,
      labelKey: BeneficiariesComponent.KNOWN_TYPE_LABEL_KEYS[value] ?? null,
    })),
  );

  protected readonly addForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    officeName: ['', [Validators.required]],
    accountNumber: ['', [Validators.required]],
    accountType: ['', [Validators.required]],
    transferLimit: [null as number | null, [Validators.min(0.01)]],
  });

  protected readonly editForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(50)]],
    transferLimit: [null as number | null, [Validators.min(0.01)]],
  });

  constructor() {
    addIcons({ pencil, personAdd, trash });
    this.refresh();
    this.store.loadTemplate().pipe(takeUntilDestroyed(this.destroyRef)).subscribe();
  }

  protected typeLabelKey(accountType: string | undefined): string | null {
    if (!accountType) {
      return null;
    }
    return BeneficiariesComponent.KNOWN_TYPE_LABEL_KEYS[accountType] ?? null;
  }

  protected startAdd(): void {
    this.addForm.reset();
    this.pendingDeleteId.set(null);
    this.step.set('add-form');
  }

  protected startEdit(row: BeneficiaryQueryData): void {
    this.editing.set(row);
    this.editForm.reset({ name: row.name ?? '', transferLimit: row.transferLimit ?? null });
    this.pendingDeleteId.set(null);
    this.step.set('edit-form');
  }

  protected backToList(): void {
    this.step.set('list');
  }

  protected submitAdd(): void {
    if (this.addForm.invalid) {
      return;
    }
    this.otpMode.set('add');
    this.loading.set(true);
    this.store
      .initiateAdd(this.addPayload())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.step.set('otp');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected submitEdit(): void {
    const publicId = this.editing()?.publicId;
    if (this.editForm.invalid || !publicId) {
      return;
    }
    this.otpMode.set('edit');
    this.loading.set(true);
    this.store
      .initiateUpdate(publicId, this.editPayload())
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.step.set('otp');
          this.loading.set(false);
        },
        error: () => this.loading.set(false),
      });
  }

  protected confirmOtp(otp: string): void {
    const stepUpToken = this.store.challenge()?.stepUpToken;
    if (!stepUpToken) {
      return;
    }
    this.loading.set(true);
    const confirmed$ =
      this.otpMode() === 'add'
        ? this.store.confirmAdd({ stepUpToken, otp, ...this.addPayload() })
        : this.store.confirmUpdate(this.editing()?.publicId ?? '', {
            stepUpToken,
            otp,
            ...this.editPayload(),
          });
    confirmed$.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: () => {
        this.notify(
          this.otpMode() === 'add' ? 'beneficiaries.snackbar.added' : 'beneficiaries.snackbar.updated',
        );
        this.step.set('list');
        this.refresh();
      },
      error: () => this.loading.set(false),
    });
  }

  protected cancelOtp(): void {
    this.step.set(this.otpMode() === 'add' ? 'add-form' : 'edit-form');
  }

  protected requestDelete(publicId: string): void {
    this.pendingDeleteId.set(publicId);
  }

  protected cancelDelete(): void {
    this.pendingDeleteId.set(null);
  }

  protected confirmDelete(publicId: string): void {
    this.loading.set(true);
    this.store
      .delete(publicId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pendingDeleteId.set(null);
          this.notify('beneficiaries.snackbar.deleted');
          this.refresh();
        },
        error: () => this.loading.set(false),
      });
  }

  private refresh(): void {
    this.loading.set(true);
    this.store
      .load()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => this.loading.set(false),
        error: () => this.loading.set(false),
      });
  }

  private addPayload() {
    const { name, officeName, accountNumber, accountType, transferLimit } = this.addForm.getRawValue();
    return {
      name,
      officeName,
      accountNumber,
      accountType: accountType as InitiateAddBeneficiaryCommandRequest.AccountTypeEnum,
      ...(transferLimit != null ? { transferLimit } : {}),
    };
  }

  private editPayload() {
    const { name, transferLimit } = this.editForm.getRawValue();
    return { name, ...(transferLimit != null ? { transferLimit } : {}) };
  }

  private notify(key: string): void {
    void this.toastCtrl
      .create({
        message: this.translate.instant(key),
        duration: 5000,
        buttons: [{ text: this.translate.instant('common.action.dismiss'), role: 'cancel' }],
      })
      .then(toast => toast.present());
  }
}
