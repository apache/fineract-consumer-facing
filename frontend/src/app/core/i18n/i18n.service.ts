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
import { TranslateService } from '@ngx-translate/core';

const STORAGE_KEY = 'app.lang';

@Injectable({ providedIn: 'root' })
export class I18nService {
  private readonly delegate = inject(TranslateService);

  constructor() {
    const stored = localStorage.getItem(STORAGE_KEY);
    if (stored) {
      this.delegate.use(stored);
    }
  }

  translate(key: string, params?: Record<string, unknown>): string {
    return this.delegate.instant(key, params);
  }

  use(languageCode: string): void {
    this.delegate.use(languageCode);
    localStorage.setItem(STORAGE_KEY, languageCode);
  }
}
