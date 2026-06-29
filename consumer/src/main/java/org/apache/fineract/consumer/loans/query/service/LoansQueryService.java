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

public interface LoansQueryService {

    String CALCULATE_SCHEDULE_COMMAND = "calculateLoanSchedule";

    List<LoanAccountListItemQueryData> listAccounts(Long clientId);

    LoanScheduleQueryData calculateSchedule(Long clientId, CalculateLoanScheduleQuery query);

    LoanAccountQueryData getLoan(Long clientId, Long loanId);

    List<LoanTransactionQueryData> listTransactions(Long clientId, LoanTransactionListQuery query);

    LoanTransactionQueryData getTransaction(Long clientId, Long loanId, Long transactionId);

    List<LoanChargeQueryData> getCharges(Long clientId, Long loanId);

    LoanChargeQueryData getCharge(Long clientId, Long loanId, Long chargeId);

    List<LoanGuarantorQueryData> getGuarantors(Long clientId, Long loanId);

    LoanApplicationTemplateQueryData getApplicationTemplate(Long productId);
}
