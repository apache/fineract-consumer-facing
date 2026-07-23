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

package org.apache.fineract.consumer.cucumber.steps;

import static org.assertj.core.api.Assertions.assertThat;

import feign.FeignException;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import org.apache.fineract.consumer.client.api.UserQueryControllerApi;
import org.apache.fineract.consumer.client.model.UserChargesQueryResponse;
import org.apache.fineract.consumer.client.model.UserObligeeQueryData;
import org.apache.fineract.consumer.client.model.UserProfileQueryData;
import org.apache.fineract.consumer.cucumber.helpers.ConsumerApiClientFactory;
import org.apache.fineract.consumer.cucumber.helpers.LoginHelper;
import org.apache.fineract.consumer.cucumber.helpers.RegistrationHelper;
import org.apache.fineract.consumer.user.query.exception.UserImageNotFoundException;

public class ProfileSteps {

    private static final String DEVICE_FINGERPRINT = "cucumber-profile-device";
    private static final int UNAUTHORIZED = 401;
    private static final int NOT_FOUND = 404;

    private final RegistrationHelper registrationHelper = new RegistrationHelper();
    private final LoginHelper loginHelper = new LoginHelper();

    private RegistrationHelper.BoundUserWithAccounts user;
    private UserQueryControllerApi userApi;

    private UserProfileQueryData profileResult;
    private UserChargesQueryResponse chargesResult;
    private List<UserObligeeQueryData> obligeesResult;
    private List<Integer> errorStatuses;
    private FeignException imageError;

    @Given("a logged-in profile customer with seeded accounts")
    public void loggedInProfileCustomer() {
        user = registrationHelper.registerBoundUserWithAccounts();
        String accessToken = loginHelper.login(user.email(), user.password(), DEVICE_FINGERPRINT);
        userApi = ConsumerApiClientFactory.authenticated(
                UserQueryControllerApi.class, accessToken, DEVICE_FINGERPRINT);
    }

    @When("I get my profile")
    public void getProfile() {
        profileResult = userApi.getUserProfile();
    }

    @Then("the profile shows my client details with a masked email")
    public void profileShowsMaskedDetails() {
        assertThat(profileResult).isNotNull();
        assertThat(profileResult.getDisplayName()).isNotBlank();
        assertThat(profileResult.getAccountNo()).isNotBlank();
        assertThat(profileResult.getActive()).isTrue();
        assertThat(profileResult.getMemberSince()).isNotNull();
        assertThat(profileResult.getMaskedEmail()).isEqualTo(expectedMaskedEmail(user.clientEmail()));
        assertThat(profileResult.getMaskedEmail()).isNotEqualTo(user.clientEmail());
        assertThat(profileResult.getMaskedMobile()).isNull();
        assertThat(profileResult.getKycVerified()).isTrue();
        assertThat(profileResult.getHasImage()).isFalse();
    }

    @When("I get my charges")
    public void getCharges() {
        chargesResult = userApi.getUserCharges(null, null, null);
    }

    @Then("the charges response is returned")
    public void chargesResponseReturned() {
        assertThat(chargesResult).isNotNull();
        assertThat(chargesResult.getCharges()).isEmpty();
    }

    @When("I get my obligees")
    public void getObligees() {
        obligeesResult = userApi.getUserObligees();
    }

    @Then("the obligees list is returned")
    public void obligeesListReturned() {
        assertThat(obligeesResult).isEmpty();
    }

    @When("I get my profile image")
    public void getProfileImage() {
        try {
            userApi.getUserImage(null, null);
            imageError = null;
        } catch (FeignException e) {
            imageError = e;
        }
    }

    @Then("the profile image request is rejected as not found")
    public void profileImageRejectedNotFound() {
        assertThat(imageError).isNotNull();
        assertThat(imageError.status()).isEqualTo(NOT_FOUND);
        assertThat(imageError.contentUTF8()).contains(UserImageNotFoundException.CODE);
    }

    @When("I request the profile endpoints without a session")
    public void requestProfileEndpointsWithoutSession() {
        UserQueryControllerApi anonymous =
                ConsumerApiClientFactory.unauthenticated(UserQueryControllerApi.class);
        errorStatuses = new ArrayList<>();
        errorStatuses.add(statusOf(anonymous::getUserProfile));
        errorStatuses.add(statusOf(() -> anonymous.getUserCharges(null, null, null)));
        errorStatuses.add(statusOf(anonymous::getUserObligees));
        errorStatuses.add(statusOf(() -> anonymous.getUserImage(null, null)));
    }

    @Then("each profile request is rejected as unauthorized")
    public void eachProfileRequestRejectedUnauthorized() {
        assertThat(errorStatuses).containsExactly(UNAUTHORIZED, UNAUTHORIZED, UNAUTHORIZED, UNAUTHORIZED);
    }

    private static int statusOf(Supplier<Object> call) {
        try {
            call.get();
            throw new AssertionError("Expected the request to be rejected, but it succeeded");
        } catch (FeignException e) {
            return e.status();
        }
    }

    private static String expectedMaskedEmail(String email) {
        return email.charAt(0) + "***" + email.substring(email.indexOf('@'));
    }
}
