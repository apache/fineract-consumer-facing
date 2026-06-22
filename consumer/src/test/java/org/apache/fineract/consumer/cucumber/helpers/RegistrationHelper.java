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

package org.apache.fineract.consumer.cucumber.helpers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.apache.fineract.consumer.client.ApiClient;
import org.apache.fineract.consumer.client.api.RegistrationCommandControllerApi;
import org.apache.fineract.consumer.client.model.SendOtpCommandRequest;
import org.apache.fineract.consumer.client.model.SubmitRegistrationCommandData;
import org.apache.fineract.consumer.client.model.SubmitRegistrationCommandRequest;
import org.apache.fineract.consumer.client.model.VerifyOtpCommandData;
import org.apache.fineract.consumer.client.model.VerifyOtpCommandRequest;
import org.apache.fineract.consumer.cucumber.clients.MailpitClient;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;

public class RegistrationHelper {

    private static final String BFF_BASE_URL = System.getenv().getOrDefault("BASE_URL", "http://localhost:8080");
    private static final String PASSWORD = "Cucumber-password1";

    private final FineractSeeder fineractSeeder = new FineractSeeder();
    private final MailpitClient mailpit = new MailpitClient();
    private final RegistrationCommandControllerApi bff = buildBffClient();

    public record BoundUser(String email, String password) {}

    public record BoundUserWithAccounts(
            String email, String password, long savingsAccountId, long loanAccountId) {}

    public BoundUser registerBoundUser() {
        FineractSeeder.SeededClient seededClient = fineractSeeder.seedClientWithPassport();
        String email = bindUser(seededClient.fineractClientId(),
                seededClient.documentTypeName(), seededClient.documentKey());
        return new BoundUser(email, PASSWORD);
    }

    public BoundUserWithAccounts registerBoundUserWithAccounts() {
        FineractSeeder.SeededClientWithAccounts seededClient = fineractSeeder.seedActiveClientWithAccounts();
        String email = bindUser(seededClient.fineractClientId(),
                seededClient.documentTypeName(), seededClient.documentKey());
        return new BoundUserWithAccounts(
                email, PASSWORD, seededClient.savingsAccountId(), seededClient.loanAccountId());
    }

    private String bindUser(long fineractClientId, String documentTypeName, String documentKey) {
        String email = "user-" + UUID.randomUUID().toString().substring(0, 8) + "@test.com";

        SubmitRegistrationCommandData submitted = bff.submitRegistration(
                new SubmitRegistrationCommandRequest()
                        .fineractClientId(fineractClientId)
                        .email(email)
                        .password(PASSWORD)
                        .documentTypeName(documentTypeName)
                        .documentKey(documentKey));
        assertThat(submitted.getStatus()).isEqualTo(SubmitRegistrationCommandData.StatusEnum.PENDING_OTP);

        bff.sendRegistrationOtp(new SendOtpCommandRequest()
                .registrationId(submitted.getRegistrationId())
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME));

        String otp = mailpit.waitForOtp(email);
        VerifyOtpCommandData verified = bff.verifyRegistrationOtp(new VerifyOtpCommandRequest()
                .registrationId(submitted.getRegistrationId())
                .token(otp));
        assertThat(verified.getStatus()).isEqualTo(VerifyOtpCommandData.StatusEnum.BOUND);

        return email;
    }

    private static RegistrationCommandControllerApi buildBffClient() {
        ApiClient apiClient = new ApiClient();
        apiClient.setBasePath(BFF_BASE_URL);
        return apiClient.buildClient(RegistrationCommandControllerApi.class);
    }
}
