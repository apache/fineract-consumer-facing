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

import { ChangeDetectionStrategy, Component, inject, input, output } from '@angular/core';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-otp',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatInputModule, TranslatePipe],
  template: `
    <p>
      @if (sentTo()) {
        {{ 'common.otp.promptSentTo' | translate: { target: sentTo() } }}
      } @else {
        {{ 'common.otp.prompt' | translate }}
      }
    </p>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <mat-form-field appearance="fill">
        <mat-label>{{ 'common.otp.codeLabel' | translate }}</mat-label>
        <input
          matInput
          formControlName="otp"
          autocomplete="one-time-code"
          autocapitalize="characters"
          maxlength="6"
          (input)="uppercase($event)"
        />
      </mat-form-field>
      <div class="actions">
        @if (showCancel()) {
          <button mat-button type="button" [disabled]="loading()" (click)="cancelled.emit()">
            {{ 'common.action.cancel' | translate }}
          </button>
        }
        <button mat-flat-button color="primary" type="submit" [disabled]="loading() || form.invalid">
          {{ 'common.action.verify' | translate }}
        </button>
      </div>
    </form>
  `,
  styles: `
    form {
      display: flex;
      flex-direction: column;
      gap: 0.5rem;
    }
    input {
      text-transform: uppercase;
    }
    .actions {
      display: flex;
      gap: 0.5rem;
      justify-content: flex-end;
    }
  `,
})
export class OtpComponent {
  private readonly fb = inject(NonNullableFormBuilder);

  readonly sentTo = input<string | null>(null);
  readonly loading = input(false);
  readonly showCancel = input(true);
  readonly submitted = output<string>();
  readonly cancelled = output<void>();

  protected readonly form = this.fb.group({
    otp: ['', [Validators.required, Validators.pattern(/^[A-Z0-9]{6}$/)]],
  });

  protected uppercase(event: Event): void {
    const value = (event.target as HTMLInputElement).value.toUpperCase();
    this.form.controls.otp.setValue(value);
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitted.emit(this.form.controls.otp.value);
  }
}
