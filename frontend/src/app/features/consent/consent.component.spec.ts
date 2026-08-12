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

import { provideZonelessChangeDetection } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { provideIonicAngular } from '@ionic/angular/standalone';
import { provideTranslateService } from '@ngx-translate/core';
import { ConsentComponent } from './consent.component';

const AUTHORIZE_URL = '/api/v1/oauth2/authorize';
const TPP_CLIENT_ID = 'tpp-demo';
const STATE = 'abc123';
const KNOWN_SCOPE = 'openbanking:accounts.read';
const KNOWN_SCOPE_LABEL_KEY = 'consent.scope.accountsRead';
const UNKNOWN_SCOPE = 'openbanking:payments.write';
const CSRF_TOKEN = 'csrf-token-value';
const INVALID_KEY = 'consent.invalid';

function createComponent(params: Record<string, string>): ComponentFixture<ConsentComponent> {
  TestBed.configureTestingModule({
    providers: [
      provideZonelessChangeDetection(),
      provideIonicAngular({ mode: 'md' }),
      provideTranslateService(),
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: convertToParamMap(params) } },
      },
    ],
  });
  const fixture = TestBed.createComponent(ConsentComponent);
  fixture.detectChanges();
  return fixture;
}

function forms(fixture: ComponentFixture<ConsentComponent>): {
  deny: HTMLFormElement;
  allow: HTMLFormElement;
} {
  const all = (fixture.nativeElement as HTMLElement).querySelectorAll('form');
  expect(all.length).toBe(2);
  return { deny: all[0] as HTMLFormElement, allow: all[1] as HTMLFormElement };
}

function inputValue(form: HTMLFormElement, name: string): string {
  const input = form.querySelector<HTMLInputElement>(`input[name="${name}"]`);
  expect(input).not.toBeNull();
  return input!.value;
}

describe('ConsentComponent', () => {
  beforeEach(() => {
    document.cookie = `XSRF-TOKEN=${encodeURIComponent(CSRF_TOKEN)}`;
  });

  it('renders one permission per scope, using the label key for known scopes and the raw value for unknown ones', () => {
    const fixture = createComponent({
      client_id: TPP_CLIENT_ID,
      state: STATE,
      scope: `${KNOWN_SCOPE} ${UNKNOWN_SCOPE}`,
    });

    const items = (fixture.nativeElement as HTMLElement).querySelectorAll('.permissions li');
    expect(items.length).toBe(2);
    expect(items[0].textContent?.trim()).toBe(KNOWN_SCOPE_LABEL_KEY);
    expect(items[1].textContent?.trim()).toBe(UNKNOWN_SCOPE);
  });

  it('builds the Allow form with the authorize action, client_id, state, CSRF token, and one scope input per approved scope', () => {
    const fixture = createComponent({
      client_id: TPP_CLIENT_ID,
      state: STATE,
      scope: `${KNOWN_SCOPE} ${UNKNOWN_SCOPE}`,
    });

    const { allow } = forms(fixture);
    expect(allow.getAttribute('action')).toBe(AUTHORIZE_URL);
    expect(allow.getAttribute('method')).toBe('post');
    expect(inputValue(allow, 'client_id')).toBe(TPP_CLIENT_ID);
    expect(inputValue(allow, 'state')).toBe(STATE);
    expect(inputValue(allow, '_csrf')).toBe(CSRF_TOKEN);

    const scopeInputs = allow.querySelectorAll<HTMLInputElement>('input[name="scope"]');
    expect(scopeInputs.length).toBe(2);
    expect(scopeInputs[0].value).toBe(KNOWN_SCOPE);
    expect(scopeInputs[1].value).toBe(UNKNOWN_SCOPE);
  });

  it('builds the Deny form with the same fields but no scope inputs', () => {
    const fixture = createComponent({
      client_id: TPP_CLIENT_ID,
      state: STATE,
      scope: KNOWN_SCOPE,
    });

    const { deny } = forms(fixture);
    expect(deny.getAttribute('action')).toBe(AUTHORIZE_URL);
    expect(inputValue(deny, 'client_id')).toBe(TPP_CLIENT_ID);
    expect(inputValue(deny, 'state')).toBe(STATE);
    expect(inputValue(deny, '_csrf')).toBe(CSRF_TOKEN);
    expect(deny.querySelectorAll('input[name="scope"]').length).toBe(0);
  });

  it('renders the invalid message instead of forms when client_id is missing', () => {
    const fixture = createComponent({ state: STATE, scope: KNOWN_SCOPE });

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('form').length).toBe(0);
    expect(host.textContent).toContain(INVALID_KEY);
  });

  it('renders the invalid message instead of forms when state is missing', () => {
    const fixture = createComponent({ client_id: TPP_CLIENT_ID, scope: KNOWN_SCOPE });

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelectorAll('form').length).toBe(0);
    expect(host.textContent).toContain(INVALID_KEY);
  });
});
