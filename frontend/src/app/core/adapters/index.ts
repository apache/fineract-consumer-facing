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

/**
 * Adapter boundary: the seam between this application and what it does not own. See
 * `docs/frontend/architecture/adapter-boundary.adoc`.
 *
 * Application code imports the contracts and tokens from here. The implementation files are
 * the only places permitted to import the underlying library, the generated BFF client or
 * Web Storage, and `eslint.config.js` enforces that.
 */

export * from './i18n/i18n.adapter';
export * from './i18n/translate.pipe';
export * from './overlay/overlay.adapter';
export * from './storage/storage.adapter';

// The generated BFF client, behind one contract per domain.
export * from './api/page';
export * from './api/audit.api';
export * from './api/auth.api';
export * from './api/beneficiary.api';
export * from './api/consent.api';
export * from './api/loan.api';
export * from './api/registration.api';
export * from './api/savings.api';
export * from './api/scheduled-transfer.api';
export * from './api/summary.api';
export * from './api/transfer.api';
export * from './api/user.api';

// The default implementations each token resolves to. Exported so a deployment replacing one
// can name what it replaces, and so a TestBed can ask for the real thing explicitly.
// Application code depends on the tokens above, never on these.
export { NgxTranslateI18nAdapter } from './i18n/ngx-translate-i18n.adapter';
export { IonicOverlayAdapter } from './overlay/ionic-overlay.adapter';
export { WebStorageAdapter } from './storage/web-storage.adapter';
export { BffAuditApi } from './api/bff-audit.api';
export { BffAuthApi } from './api/bff-auth.api';
export { BffBeneficiaryApi } from './api/bff-beneficiary.api';
export { BffConsentApi } from './api/bff-consent.api';
export { BffLoanApi } from './api/bff-loan.api';
export { BffRegistrationApi } from './api/bff-registration.api';
export { BffSavingsApi } from './api/bff-savings.api';
export { BffScheduledTransferApi } from './api/bff-scheduled-transfer.api';
export { BffSummaryApi } from './api/bff-summary.api';
export { BffTransferApi } from './api/bff-transfer.api';
export { BffUserApi } from './api/bff-user.api';
