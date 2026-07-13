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
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { TranslatePipe, TranslateService } from '@ngx-translate/core';
import { BeneficiaryQueryData } from '@bff/client';
import { OtpComponent } from '../../shared/otp/otp.component';
import { PageHeaderComponent } from '../../shared/ui/page-header.component';
import { BeneficiariesStore } from './beneficiaries.store';

@Component({
  selector: 'app-beneficiaries',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatProgressBarModule,
    MatSelectModule,
    MatTableModule,
    DecimalPipe,
    TranslatePipe,
    OtpComponent,
    PageHeaderComponent,
  ],
  template: `
    <app-page-header [title]="'beneficiaries.title' | translate" />

    <mat-card>
      @if (loading()) {
        <mat-progress-bar mode="indeterminate" />
      }

      <mat-card-content>
        @switch (step()) {
          @case ('list') {
            <table mat-table [dataSource]="store.beneficiaries()">
              <ng-container matColumnDef="name">
                <th mat-header-cell *matHeaderCellDef>{{ 'beneficiaries.list.nameColumn' | translate }}</th>
                <td mat-cell *matCellDef="let row">{{ row.name }}</td>
              </ng-container>
              <ng-container matColumnDef="accountType">
                <th mat-header-cell *matHeaderCellDef>
                  {{ 'beneficiaries.list.accountTypeColumn' | translate }}
                </th>
                <td mat-cell *matCellDef="let row">
                  @if (typeLabelKey(row.accountType); as key) {
                    {{ key | translate }}
                  } @else {
                    {{ row.accountType }}
                  }
                </td>
              </ng-container>
              <ng-container matColumnDef="transferLimit">
                <th mat-header-cell *matHeaderCellDef>
                  {{ 'beneficiaries.list.transferLimitColumn' | translate }}
                </th>
                <td mat-cell *matCellDef="let row">
                  @if (row.transferLimit != null) {
                    {{ row.transferLimit | number: '1.2-2' }}
                  } @else {
                    —
                  }
                </td>
              </ng-container>
              <ng-container matColumnDef="actions">
                <th mat-header-cell *matHeaderCellDef>
                  {{ 'beneficiaries.list.actionsColumn' | translate }}
                </th>
                <td mat-cell *matCellDef="let row" class="row-actions">
                  @if (pendingDeleteId() === row.publicId) {
                    <button
                      mat-stroked-button
                      color="warn"
                      [disabled]="loading()"
                      (click)="confirmDelete(row.publicId)"
                    >
                      {{ 'beneficiaries.delete.confirm' | translate }}
                    </button>
                    <button mat-button [disabled]="loading()" (click)="cancelDelete()">
                      {{ 'beneficiaries.delete.cancel' | translate }}
                    </button>
                  } @else {
                    <button
                      mat-icon-button
                      [attr.aria-label]="'beneficiaries.edit.title' | translate"
                      (click)="startEdit(row)"
                    >
                      <mat-icon>edit</mat-icon>
                    </button>
                    <button
                      mat-icon-button
                      [attr.aria-label]="'beneficiaries.delete.confirm' | translate"
                      (click)="requestDelete(row.publicId)"
                    >
                      <mat-icon>delete</mat-icon>
                    </button>
                  }
                </td>
              </ng-container>

              <tr mat-header-row *matHeaderRowDef="columns"></tr>
              <tr mat-row *matRowDef="let row; columns: columns"></tr>
              <tr class="empty-row" *matNoDataRow>
                <td [attr.colspan]="columns.length">{{ 'beneficiaries.list.empty' | translate }}</td>
              </tr>
            </table>

            <div class="actions">
              <button mat-flat-button color="primary" (click)="startAdd()">
                <mat-icon>person_add</mat-icon>
                {{ 'beneficiaries.list.addCta' | translate }}
              </button>
            </div>
          }
          @case ('add-form') {
            <form [formGroup]="addForm" (ngSubmit)="submitAdd()">
              <mat-form-field appearance="fill">
                <mat-label>{{ 'beneficiaries.form.nameLabel' | translate }}</mat-label>
                <input matInput formControlName="name" maxlength="50" />
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'beneficiaries.form.officeNameLabel' | translate }}</mat-label>
                <input matInput formControlName="officeName" />
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'beneficiaries.form.accountNumberLabel' | translate }}</mat-label>
                <input matInput formControlName="accountNumber" />
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'beneficiaries.form.accountTypeLabel' | translate }}</mat-label>
                <mat-select formControlName="accountType">
                  @for (option of typeOptions(); track option.value) {
                    <mat-option [value]="option.value">
                      @if (option.labelKey) {
                        {{ option.labelKey | translate }}
                      } @else {
                        {{ option.value }}
                      }
                    </mat-option>
                  }
                </mat-select>
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'beneficiaries.form.transferLimitLabel' | translate }}</mat-label>
                <input matInput type="number" step="0.01" formControlName="transferLimit" />
              </mat-form-field>
              <div class="actions">
                <button mat-button type="button" [disabled]="loading()" (click)="backToList()">
                  {{ 'beneficiaries.form.cancelCta' | translate }}
                </button>
                <button
                  mat-flat-button
                  color="primary"
                  type="submit"
                  [disabled]="loading() || addForm.invalid"
                >
                  {{ 'beneficiaries.form.submitCta' | translate }}
                </button>
              </div>
            </form>
          }
          @case ('edit-form') {
            <h3>{{ 'beneficiaries.edit.title' | translate }}</h3>
            <form [formGroup]="editForm" (ngSubmit)="submitEdit()">
              <mat-form-field appearance="fill">
                <mat-label>{{ 'beneficiaries.form.nameLabel' | translate }}</mat-label>
                <input matInput formControlName="name" maxlength="50" />
              </mat-form-field>
              <mat-form-field appearance="fill">
                <mat-label>{{ 'beneficiaries.form.transferLimitLabel' | translate }}</mat-label>
                <input matInput type="number" step="0.01" formControlName="transferLimit" />
              </mat-form-field>
              <div class="actions">
                <button mat-button type="button" [disabled]="loading()" (click)="backToList()">
                  {{ 'beneficiaries.form.cancelCta' | translate }}
                </button>
                <button
                  mat-flat-button
                  color="primary"
                  type="submit"
                  [disabled]="loading() || editForm.invalid"
                >
                  {{ 'beneficiaries.form.submitCta' | translate }}
                </button>
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
      </mat-card-content>
    </mat-card>
  `,
  styles: `
    table {
      width: 100%;
    }
    .row-actions {
      white-space: nowrap;
    }
    form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
      max-width: 28rem;
    }
    .actions {
      display: flex;
      gap: 0.5rem;
      justify-content: flex-end;
      margin-top: 1rem;
    }
    .empty-row td {
      padding: 1rem 0;
    }
    .otp-container {
      max-width: 24rem;
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
  private readonly snackBar = inject(MatSnackBar);
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
      accountType,
      ...(transferLimit != null ? { transferLimit } : {}),
    };
  }

  private editPayload() {
    const { name, transferLimit } = this.editForm.getRawValue();
    return { name, ...(transferLimit != null ? { transferLimit } : {}) };
  }

  private notify(key: string): void {
    this.snackBar.open(this.translate.instant(key), this.translate.instant('common.action.dismiss'), {
      duration: 5000,
    });
  }
}
