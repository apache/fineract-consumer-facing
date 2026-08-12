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

import { allowedReturnUrl } from './login.component';

const OAUTH2_AUTHORIZE_PATH =
  '/api/v1/oauth2/authorize?client_id=tpp-demo&scope=openbanking%3Aaccounts.read&state=abc123';

describe('allowedReturnUrl', () => {
  it('accepts a relative BFF OAuth2 authorize path', () => {
    expect(allowedReturnUrl(OAUTH2_AUTHORIZE_PATH)).toBe(OAUTH2_AUTHORIZE_PATH);
  });

  it('rejects a missing returnUrl', () => {
    expect(allowedReturnUrl(null)).toBeNull();
  });

  it('rejects an internal app route', () => {
    expect(allowedReturnUrl('/summary')).toBeNull();
  });

  it('rejects an absolute external URL', () => {
    expect(allowedReturnUrl('https://evil.example/api/v1/oauth2/authorize')).toBeNull();
  });

  it('rejects a protocol-relative URL', () => {
    expect(allowedReturnUrl('//evil.example')).toBeNull();
  });

  it('rejects a non-OAuth2 BFF API path', () => {
    expect(allowedReturnUrl('/api/v1/savings')).toBeNull();
  });
});
