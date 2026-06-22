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

package org.apache.fineract.consumer.loans.command.service;

import org.apache.fineract.consumer.loans.command.data.ConfirmLoanChargePaymentCommand;
import org.apache.fineract.consumer.loans.command.data.InitiateLoanChargePaymentCommand;
import org.apache.fineract.consumer.loans.command.data.LoanApplicationCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentChallengeCommandData;
import org.apache.fineract.consumer.loans.command.data.LoanChargePaymentCommandData;
import org.apache.fineract.consumer.loans.command.data.ModifyLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.SubmitLoanApplicationCommand;
import org.apache.fineract.consumer.loans.command.data.WithdrawLoanApplicationCommand;
import org.springframework.security.oauth2.jwt.Jwt;

public interface LoansCommandService {

    String WITHDRAW_COMMAND = "withdrawnByApplicant";

    LoanApplicationCommandData submitApplication(Jwt jwt, SubmitLoanApplicationCommand command);

    LoanApplicationCommandData modifyApplication(Jwt jwt, ModifyLoanApplicationCommand command);

    LoanApplicationCommandData withdrawApplication(Jwt jwt, WithdrawLoanApplicationCommand command);

    LoanChargePaymentChallengeCommandData initiateChargePayment(Jwt jwt, InitiateLoanChargePaymentCommand command);

    LoanChargePaymentCommandData confirmChargePayment(Jwt jwt, ConfirmLoanChargePaymentCommand command);
}
