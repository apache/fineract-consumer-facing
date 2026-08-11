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

import { inject, Injectable } from '@angular/core';
import { ToastController } from '@ionic/angular/standalone';
import { I18nService } from '../i18n/i18n.service';

const DISMISS_KEY = 'common.action.dismiss';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly toast = inject(ToastController);
  private readonly i18n = inject(I18nService);

  showError(key: string): void {
    void this.toast
      .create({
        message: this.i18n.translate(key),
        duration: 5000,
        position: 'bottom',
        buttons: [{ text: this.i18n.translate(DISMISS_KEY), role: 'cancel' }],
      })
      .then((t) => t.present());
  }
}
