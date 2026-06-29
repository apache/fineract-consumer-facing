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

import { Routes } from '@angular/router';

export const LOANS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./loans-list.component').then(m => m.LoansListComponent),
  },
  {
    path: 'apply',
    loadComponent: () => import('./loan-apply.component').then(m => m.LoanApplyComponent),
  },
  {
    path: ':loanId',
    loadComponent: () => import('./loans-detail.component').then(m => m.LoansDetailComponent),
  },
  {
    path: ':loanId/transactions/:transactionId',
    loadComponent: () =>
      import('./loans-transaction.component').then(m => m.LoansTransactionComponent),
  },
];
