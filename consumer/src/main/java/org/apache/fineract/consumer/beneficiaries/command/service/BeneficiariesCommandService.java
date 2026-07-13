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
package org.apache.fineract.consumer.beneficiaries.command.service;

import java.util.UUID;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryChallengeCommandData;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryCommandData;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmAddBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmUpdateBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateAddBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateUpdateBeneficiaryCommand;
import org.springframework.security.oauth2.jwt.Jwt;

public interface BeneficiariesCommandService {

    BeneficiaryChallengeCommandData initiateAdd(Jwt jwt, InitiateAddBeneficiaryCommand command);

    BeneficiaryCommandData confirmAdd(Jwt jwt, ConfirmAddBeneficiaryCommand command);

    BeneficiaryChallengeCommandData initiateUpdate(Jwt jwt, InitiateUpdateBeneficiaryCommand command);

    BeneficiaryCommandData confirmUpdate(Jwt jwt, ConfirmUpdateBeneficiaryCommand command);

    void delete(Jwt jwt, UUID publicId);
}
