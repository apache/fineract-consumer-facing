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
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.loans.query.service;

import java.util.List;
import org.apache.fineract.consumer.loans.query.data.CalculateLoanScheduleQuery;
import org.apache.fineract.consumer.loans.query.data.LoanAccountListItemQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanAccountQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanApplicationTemplateQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanChargeQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanGuarantorQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanScheduleQueryData;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionListQuery;
import org.apache.fineract.consumer.loans.query.data.LoanTransactionQueryData;
import org.springframework.security.oauth2.jwt.Jwt;

public interface LoansQueryService {

    String CALCULATE_SCHEDULE_COMMAND = "calculateLoanSchedule";

    List<LoanAccountListItemQueryData> listAccounts(Jwt jwt);

    LoanScheduleQueryData calculateSchedule(Jwt jwt, CalculateLoanScheduleQuery query);

    LoanAccountQueryData getLoan(Jwt jwt, Long loanId);

    List<LoanTransactionQueryData> listTransactions(Jwt jwt, LoanTransactionListQuery query);

    LoanTransactionQueryData getTransaction(Jwt jwt, Long loanId, Long transactionId);

    List<LoanChargeQueryData> getCharges(Jwt jwt, Long loanId);

    LoanChargeQueryData getCharge(Jwt jwt, Long loanId, Long chargeId);

    List<LoanGuarantorQueryData> getGuarantors(Jwt jwt, Long loanId);

    LoanApplicationTemplateQueryData getApplicationTemplate(Jwt jwt, Long productId);
}
