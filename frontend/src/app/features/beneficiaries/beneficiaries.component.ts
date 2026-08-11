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
import { DecimalPipe } from '@angular/common';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import {
  AbstractControl,
  NonNullableFormBuilder,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { CdkTableModule } from '@angular/cdk/table';
import {
  IonButton,
  IonCard,
  IonCardContent,
  IonIcon,
  IonInput,
  IonProgressBar,
  ToastController,
} from '@ionic/angular/standalone';
import { addIcons } from 'ionicons';
import { pencil, personAdd, trash } from 'ionicons/icons';
import { TranslatePipe } from '@ngx-translate/core';
import { BeneficiaryQueryData } from '@bff/client';
import { I18nService } from '../../core/i18n/i18n.service';
import { NotificationService } from '../../core/notifications/notification.service';
import { OtpComponent } from '../../shared/otp/otp.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { BeneficiariesStore } from './beneficiaries.store';

const NAME_MAX_LENGTH = 50;
const MIN_TRANSFER_LIMIT = 0.01;

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
                  <th cdk-header-cell *cdkHeaderCellDef>
                    {{ 'beneficiaries.list.nameColumn' | translate }}
                  </th>
                  <td cdk-cell *cdkCellDef="let row">{{ row.name }}</td>
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
                      [attr.aria-label]="'beneficiaries.delete.action' | translate"
                      [disabled]="deletingId() === row.publicId"
                      (click)="confirmDelete(row.publicId)"
                    >
                      <ion-icon slot="icon-only" name="trash" />
                    </ion-button>
                  </td>
                </ng-container>

                <tr cdk-header-row *cdkHeaderRowDef="columns"></tr>
                <tr cdk-row *cdkRowDef="let row; columns: columns"></tr>
                <tr class="empty-row" *cdkNoDataRow>
                  <td [attr.colspan]="columns.length">
                    {{ 'beneficiaries.list.empty' | translate }}
                  </td>
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
                [maxlength]="nameMaxLength"
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
              <ion-input
                type="number"
                step="0.01"
                formControlName="transferLimit"
                fill="outline"
                labelPlacement="stacked"
                [label]="'beneficiaries.form.transferLimitLabel' | translate"
              />
              <div class="actions-end">
                <ion-button
                  fill="outline"
                  type="button"
                  [disabled]="loading()"
                  (click)="backToList()"
                >
                  {{ 'beneficiaries.form.cancelCta' | translate }}
                </ion-button>
                <ion-button type="submit" [disabled]="loading()">
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
                [maxlength]="nameMaxLength"
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
                <ion-button
                  fill="outline"
                  type="button"
                  [disabled]="loading()"
                  (click)="backToList()"
                >
                  {{ 'beneficiaries.form.cancelCta' | translate }}
                </ion-button>
                <ion-button type="submit" [disabled]="loading()">
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
  private readonly fb = inject(NonNullableFormBuilder);
  private readonly destroyRef = inject(DestroyRef);
  private readonly toastCtrl = inject(ToastController);
  private readonly i18n = inject(I18nService);
  private readonly notifications = inject(NotificationService);
  protected readonly store = inject(BeneficiariesStore);

  protected readonly step = signal<'list' | 'add-form' | 'edit-form' | 'otp'>('list');
  protected readonly loading = signal(false);
  protected readonly otpMode = signal<'add' | 'edit'>('add');
  protected readonly editing = signal<BeneficiaryQueryData | null>(null);
  protected readonly deletingId = signal<string | null>(null);

  protected readonly columns = ['name', 'transferLimit', 'actions'];
  protected readonly nameMaxLength = NAME_MAX_LENGTH;

  protected readonly addForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(NAME_MAX_LENGTH)]],
    officeName: ['', [Validators.required]],
    accountNumber: ['', [Validators.required]],
    transferLimit: [null as number | null, [Validators.min(MIN_TRANSFER_LIMIT)]],
  });

  protected readonly editForm = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(NAME_MAX_LENGTH)]],
    transferLimit: [null as number | null, [Validators.min(MIN_TRANSFER_LIMIT)]],
  });

  constructor() {
    addIcons({ pencil, personAdd, trash });
    this.refresh();
  }

  protected startAdd(): void {
    this.addForm.reset();
    this.step.set('add-form');
  }

  protected startEdit(row: BeneficiaryQueryData): void {
    this.editing.set(row);
    this.editForm.reset({ name: row.name ?? '', transferLimit: row.transferLimit ?? null });
    this.step.set('edit-form');
  }

  protected backToList(): void {
    this.step.set('list');
  }

  protected submitAdd(): void {
    const { name, officeName, accountNumber, transferLimit } = this.addForm.controls;
    const error =
      this.nameError(name) ??
      this.requiredError(officeName, 'beneficiaries.form.error.officeNameRequired') ??
      this.requiredError(accountNumber, 'beneficiaries.form.error.accountNumberRequired') ??
      this.transferLimitError(transferLimit);
    if (error) {
      this.notifications.showError(error);
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
    const { name, transferLimit } = this.editForm.controls;
    const error = this.nameError(name) ?? this.transferLimitError(transferLimit);
    if (error) {
      this.notifications.showError(error);
      return;
    }
    const publicId = this.editing()?.publicId;
    if (!publicId) {
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
          this.otpMode() === 'add'
            ? 'beneficiaries.snackbar.added'
            : 'beneficiaries.snackbar.updated',
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

  protected confirmDelete(publicId: string): void {
    this.deletingId.set(publicId);
    this.loading.set(true);
    this.store
      .delete(publicId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.deletingId.set(null);
          this.notify('beneficiaries.snackbar.deleted');
          this.refresh();
        },
        error: () => {
          this.deletingId.set(null);
          this.loading.set(false);
        },
      });
  }

  private nameError(control: AbstractControl): string | null {
    if (control.hasError('required')) {
      return 'beneficiaries.form.error.nameRequired';
    }
    return control.hasError('maxlength') ? 'beneficiaries.form.error.nameTooLong' : null;
  }

  private transferLimitError(control: AbstractControl): string | null {
    return control.hasError('min') ? 'beneficiaries.form.error.transferLimitTooSmall' : null;
  }

  private requiredError(control: AbstractControl, key: string): string | null {
    return control.hasError('required') ? key : null;
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
    const { name, officeName, accountNumber, transferLimit } = this.addForm.getRawValue();
    return {
      name,
      officeName,
      accountNumber,
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
        message: this.i18n.translate(key),
        duration: 5000,
        buttons: [{ text: this.i18n.translate('common.action.dismiss'), role: 'cancel' }],
      })
      .then((toast) => toast.present());
  }
}
