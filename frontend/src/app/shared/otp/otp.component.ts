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
import { InputCustomEvent, IonButton, IonInput } from '@ionic/angular/standalone';
import { TranslatePipe } from '@ngx-translate/core';

@Component({
  selector: 'app-otp',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [ReactiveFormsModule, IonButton, IonInput, TranslatePipe],
  template: `
    <p>
      @if (sentTo()) {
        {{ 'common.otp.promptSentTo' | translate: { target: sentTo() } }}
      } @else {
        {{ 'common.otp.prompt' | translate }}
      }
    </p>
    <form [formGroup]="form" (ngSubmit)="submit()">
      <ion-input
        formControlName="otp"
        fill="outline"
        labelPlacement="stacked"
        [label]="'common.otp.codeLabel' | translate"
        autocomplete="one-time-code"
        autocapitalize="characters"
        [maxlength]="6"
        (ionInput)="uppercase($event)"
      />
      <div class="actions-end">
        @if (showCancel()) {
          <ion-button fill="outline" type="button" [disabled]="loading()" (click)="cancelled.emit()">
            {{ 'common.action.cancel' | translate }}
          </ion-button>
        }
        <ion-button type="submit" [disabled]="loading() || form.invalid">
          {{ 'common.action.verify' | translate }}
        </ion-button>
      </div>
    </form>
  `,
  styleUrls: ['../css/form.scss', '../css/actions.scss'],
  styles: `
    p { margin-bottom: 1.25rem; }
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

  protected uppercase(event: InputCustomEvent): void {
    const value = (event.detail.value ?? '').toUpperCase();
    this.form.controls.otp.setValue(value);
  }

  protected submit(): void {
    if (this.form.invalid) {
      return;
    }
    this.submitted.emit(this.form.controls.otp.value);
  }
}
