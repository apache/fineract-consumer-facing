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

package org.apache.fineract.consumer.user.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import feign.FeignException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessKycRequiredException;
import org.apache.fineract.consumer.infrastructure.access.exception.AccessScopeInsufficientException;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.DefaultApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientChargeCurrency;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsChargesPageItems;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetObligeeData;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.user.query.domain.UserStatus;
import org.apache.fineract.consumer.user.command.exception.UserNotFoundException;
import org.apache.fineract.consumer.user.query.data.UserChargeQueryData;
import org.apache.fineract.consumer.user.query.data.UserChargesQuery;
import org.apache.fineract.consumer.user.query.data.UserChargesQueryResponse;
import org.apache.fineract.consumer.user.query.data.UserCredentialsQueryData;
import org.apache.fineract.consumer.user.query.data.UserImageQueryData;
import org.apache.fineract.consumer.user.query.data.UserObligeeQueryData;
import org.apache.fineract.consumer.user.query.data.UserProfileQueryData;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.exception.UserChargeStatusInvalidException;
import org.apache.fineract.consumer.user.query.exception.UserImageNotFoundException;
import org.apache.fineract.consumer.user.query.exception.UserUpstreamUnavailableException;
import org.apache.fineract.consumer.user.query.repository.UserQueryRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class UserQueryServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final Long CLIENT_ID = 11L;
    private static final String EMAIL = "user@test.com";
    private static final String PASSWORD_HASH = "{bcrypt}$2a$10$hash";

    private static final String IMAGE_ENTITY_TYPE = "clients";
    private static final String IMAGE_ACCEPT_TEXT_PLAIN = "text/plain";
    private static final String CHARGE_STATUS_ACTIVE = "active";
    private static final String CHARGE_STATUS_ALL = "all";
    private static final String CHARGE_STATUS_INVALID = "bogus";

    private static final String DISPLAY_NAME = "Jane Doe";
    private static final String ACCOUNT_NO = "000000011";
    private static final String CLIENT_EMAIL = "jane@example.com";
    private static final String CLIENT_MOBILE = "555-123-6789";
    private static final String MASKED_CLIENT_EMAIL = "j***@example.com";
    private static final String MASKED_CLIENT_MOBILE = "***-***-6789";
    private static final String MASKED_ABSENT = "***";
    private static final LocalDate MEMBER_SINCE = LocalDate.of(2024, 3, 5);

    private static final Long CHARGE_ID = 3L;
    private static final String CHARGE_NAME = "Annual Fee";
    private static final String CURRENCY_CODE = "USD";
    private static final String CHARGE_AMOUNT = "100.00";
    private static final String CHARGE_AMOUNT_OUTSTANDING = "40.00";
    private static final String CHARGE_AMOUNT_PAID = "50.00";
    private static final String CHARGE_AMOUNT_WAIVED = "10.00";
    private static final LocalDate CHARGE_DUE_DATE = LocalDate.of(2026, 1, 15);

    private static final String OBLIGEE_DISPLAY_NAME = "John Borrower";
    private static final String OBLIGEE_ACCOUNT_NUMBER = "000000099";
    private static final String OBLIGEE_LOAN_AMOUNT = "5000.00";
    private static final String OBLIGEE_GUARANTEE_AMOUNT = "1000.00";
    private static final String OBLIGEE_AMOUNT_RELEASED = "200.00";
    private static final String OBLIGEE_AMOUNT_TRANSFERRED = "100.00";

    private static final String IMAGE_DATA_URI = "data:image/png;base64,AAAA";
    private static final Integer IMAGE_DIMENSION = 256;

    @Mock
    private UserQueryRepository repository;

    @Mock
    private ClientApi clientApi;

    @Mock
    private ClientChargesApi clientChargesApi;

    @Mock
    private DefaultApi defaultApi;

    @Mock
    private AccessPolicyEvaluator accessPolicyEvaluator;

    @Mock
    private UserClientResolver userClientResolver;

    @InjectMocks
    private UserQueryServiceImpl service;

    private static UserQueryData userQueryData() {
        return UserQueryData.builder()
                .id(USER_ID)
                .publicId(PUBLIC_ID)
                .fineractClientId(CLIENT_ID)
                .email(EMAIL)
                .status(UserStatus.BOUND)
                .build();
    }

    private static Jwt jwt(boolean kycVerified) {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(PUBLIC_ID.toString())
                .claim(JwtClaims.KYC_VERIFIED, kycVerified)
                .build();
    }

    private static UserChargesQuery chargesQuery(String status, int page, int size) {
        return UserChargesQuery.builder()
                .status(status)
                .page(page)
                .size(size)
                .build();
    }

    @Test
    void findByPublicIdReturnsUser() {
        UserQueryData user = userQueryData();
        when(repository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.of(user));

        assertThat(service.findByPublicId(PUBLIC_ID)).isEqualTo(user);
    }

    @Test
    void findByPublicIdUnknownIsRejected() {
        when(repository.findByPublicId(PUBLIC_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByPublicId(PUBLIC_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void findByIdReturnsUser() {
        UserQueryData user = userQueryData();
        when(repository.findById(USER_ID)).thenReturn(Optional.of(user));

        assertThat(service.findById(USER_ID)).isEqualTo(user);
    }

    @Test
    void findByIdUnknownIsRejected() {
        when(repository.findById(USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findById(USER_ID))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void findCredentialsByEmailReturnsCredentialsWhenPresent() {
        UserCredentialsQueryData credentials = UserCredentialsQueryData.builder()
                .id(USER_ID)
                .publicId(PUBLIC_ID)
                .status(UserStatus.BOUND)
                .passwordHash(PASSWORD_HASH)
                .build();
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(Optional.of(credentials));

        assertThat(service.findCredentialsByEmail(EMAIL)).contains(credentials);
    }

    @Test
    void findCredentialsByEmailReturnsEmptyWhenUnknown() {
        when(repository.findCredentialsByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThat(service.findCredentialsByEmail(EMAIL)).isEmpty();
    }

    @Test
    void getProfileMapsClientAndMasksContactInfo() {
        Jwt jwt = jwt(true);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveOneClient(CLIENT_ID, null)).thenReturn(new GetClientsClientIdResponse()
                .displayName(DISPLAY_NAME)
                .accountNo(ACCOUNT_NO)
                .active(true)
                .activationDate(MEMBER_SINCE)
                .emailAddress(CLIENT_EMAIL)
                .mobileNo(CLIENT_MOBILE)
                .imagePresent(true));

        UserProfileQueryData profile = service.getProfile(jwt);

        assertThat(profile.getDisplayName()).isEqualTo(DISPLAY_NAME);
        assertThat(profile.getAccountNo()).isEqualTo(ACCOUNT_NO);
        assertThat(profile.getActive()).isTrue();
        assertThat(profile.getMemberSince()).isEqualTo(MEMBER_SINCE);
        assertThat(profile.getMaskedEmail()).isEqualTo(MASKED_CLIENT_EMAIL);
        assertThat(profile.getMaskedMobile()).isEqualTo(MASKED_CLIENT_MOBILE);
        assertThat(profile.isKycVerified()).isTrue();
        assertThat(profile.isHasImage()).isTrue();
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.USER_PROFILE_VIEW);
    }

    @Test
    void getProfileHandlesNullContactInfo() {
        Jwt jwt = jwt(false);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveOneClient(CLIENT_ID, null)).thenReturn(new GetClientsClientIdResponse()
                .displayName(DISPLAY_NAME));

        UserProfileQueryData profile = service.getProfile(jwt);

        assertThat(profile.getMaskedEmail()).isEqualTo(MASKED_ABSENT);
        assertThat(profile.getMaskedMobile()).isNull();
        assertThat(profile.isKycVerified()).isFalse();
        assertThat(profile.isHasImage()).isFalse();
    }

    @Test
    void getProfileUpstreamFailureIsTranslated() {
        Jwt jwt = jwt(true);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveOneClient(CLIENT_ID, null)).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.getProfile(jwt))
                .isInstanceOf(UserUpstreamUnavailableException.class);
    }

    @Test
    void getChargesMapsItemsAndTranslatesPageToOffset() {
        Jwt jwt = jwt(true);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        GetClientsChargesPageItems charge = new GetClientsChargesPageItems()
                .id(CHARGE_ID)
                .name(CHARGE_NAME)
                .currency(new GetClientChargeCurrency().code(CURRENCY_CODE))
                .amount(new BigDecimal(CHARGE_AMOUNT))
                .amountOutstanding(new BigDecimal(CHARGE_AMOUNT_OUTSTANDING))
                .amountPaid(new BigDecimal(CHARGE_AMOUNT_PAID))
                .amountWaived(new BigDecimal(CHARGE_AMOUNT_WAIVED))
                .dueDate(CHARGE_DUE_DATE)
                .isActive(true)
                .isPaid(false)
                .isWaived(false)
                .penalty(false);
        when(clientChargesApi.retrieveAllClientCharges(CLIENT_ID, CHARGE_STATUS_ACTIVE, null, 5, 10))
                .thenReturn(new GetClientsClientIdChargesResponse()
                        .pageItems(Set.of(charge))
                        .totalFilteredRecords(12));

        UserChargesQueryResponse response = service.getCharges(jwt, chargesQuery(CHARGE_STATUS_ACTIVE, 2, 5));

        assertThat(response.getTotalFilteredRecords()).isEqualTo(12);
        assertThat(response.getCharges()).hasSize(1);
        UserChargeQueryData item = response.getCharges().get(0);
        assertThat(item.getId()).isEqualTo(CHARGE_ID);
        assertThat(item.getName()).isEqualTo(CHARGE_NAME);
        assertThat(item.getCurrency()).isEqualTo(CURRENCY_CODE);
        assertThat(item.getAmount()).isEqualByComparingTo(CHARGE_AMOUNT);
        assertThat(item.getAmountOutstanding()).isEqualByComparingTo(CHARGE_AMOUNT_OUTSTANDING);
        assertThat(item.getAmountPaid()).isEqualByComparingTo(CHARGE_AMOUNT_PAID);
        assertThat(item.getAmountWaived()).isEqualByComparingTo(CHARGE_AMOUNT_WAIVED);
        assertThat(item.getDueDate()).isEqualTo(CHARGE_DUE_DATE);
        assertThat(item.getActive()).isTrue();
        assertThat(item.getPaid()).isFalse();
        assertThat(item.getWaived()).isFalse();
        assertThat(item.getPenalty()).isFalse();
    }

    @Test
    void getChargesInvalidStatusIsRejected() {
        Jwt jwt = jwt(true);

        assertThatThrownBy(() -> service.getCharges(jwt, chargesQuery(CHARGE_STATUS_INVALID, 0, 20)))
                .isInstanceOf(UserChargeStatusInvalidException.class);
        verifyNoInteractions(clientChargesApi, clientApi);
    }

    @Test
    void getChargesKycDenialPropagatesWithoutUpstreamCall() {
        Jwt jwt = jwt(false);
        doThrow(new AccessKycRequiredException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.USER_CHARGES_LIST);

        assertThatThrownBy(() -> service.getCharges(jwt, chargesQuery(CHARGE_STATUS_ALL, 0, 20)))
                .isInstanceOf(AccessKycRequiredException.class);
        verifyNoInteractions(clientChargesApi, clientApi);
    }

    @Test
    void getObligeesMapsList() {
        Jwt jwt = jwt(true);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        GetObligeeData obligee = new GetObligeeData()
                .displayName(OBLIGEE_DISPLAY_NAME)
                .accountNumber(OBLIGEE_ACCOUNT_NUMBER)
                .loanAmount(new BigDecimal(OBLIGEE_LOAN_AMOUNT))
                .guaranteeAmount(new BigDecimal(OBLIGEE_GUARANTEE_AMOUNT))
                .amountReleased(new BigDecimal(OBLIGEE_AMOUNT_RELEASED))
                .amountTransferred(new BigDecimal(OBLIGEE_AMOUNT_TRANSFERRED));
        when(clientApi.retrieveClientObligeeDetails(CLIENT_ID)).thenReturn(List.of(obligee));

        List<UserObligeeQueryData> result = service.getObligees(jwt);

        assertThat(result).hasSize(1);
        UserObligeeQueryData item = result.get(0);
        assertThat(item.getDisplayName()).isEqualTo(OBLIGEE_DISPLAY_NAME);
        assertThat(item.getAccountNumber()).isEqualTo(OBLIGEE_ACCOUNT_NUMBER);
        assertThat(item.getLoanAmount()).isEqualByComparingTo(OBLIGEE_LOAN_AMOUNT);
        assertThat(item.getGuaranteeAmount()).isEqualByComparingTo(OBLIGEE_GUARANTEE_AMOUNT);
        assertThat(item.getAmountReleased()).isEqualByComparingTo(OBLIGEE_AMOUNT_RELEASED);
        assertThat(item.getAmountTransferred()).isEqualByComparingTo(OBLIGEE_AMOUNT_TRANSFERRED);
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.USER_OBLIGEES_VIEW);
    }

    @Test
    void getObligeesEmptyUpstreamYieldsEmptyList() {
        Jwt jwt = jwt(true);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(clientApi.retrieveClientObligeeDetails(CLIENT_ID)).thenReturn(null);

        assertThat(service.getObligees(jwt)).isEmpty();
    }

    @Test
    void getImageReturnsDataUri() {
        Jwt jwt = jwt(true);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(defaultApi.retrieveImage(IMAGE_ENTITY_TYPE, CLIENT_ID, IMAGE_DIMENSION, IMAGE_DIMENSION, null,
                IMAGE_ACCEPT_TEXT_PLAIN))
                .thenReturn(IMAGE_DATA_URI);

        UserImageQueryData image = service.getImage(jwt, IMAGE_DIMENSION, IMAGE_DIMENSION);

        assertThat(image.getImageDataUri()).isEqualTo(IMAGE_DATA_URI);
        verify(accessPolicyEvaluator).authorize(jwt, ConsumerAction.USER_IMAGE_VIEW);
    }

    @Test
    void getImageScopeDenialPropagatesWithoutUpstreamCall() {
        Jwt jwt = jwt(true);
        doThrow(new AccessScopeInsufficientException())
                .when(accessPolicyEvaluator).authorize(jwt, ConsumerAction.USER_IMAGE_VIEW);

        assertThatThrownBy(() -> service.getImage(jwt, null, null))
                .isInstanceOf(AccessScopeInsufficientException.class);
        verifyNoInteractions(defaultApi);
    }

    @Test
    void getImageMissingUpstreamImageIsTranslatedTo404() {
        Jwt jwt = jwt(true);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(defaultApi.retrieveImage(IMAGE_ENTITY_TYPE, CLIENT_ID, null, null, null, IMAGE_ACCEPT_TEXT_PLAIN))
                .thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() -> service.getImage(jwt, null, null))
                .isInstanceOf(UserImageNotFoundException.class);
    }

    @Test
    void getImageUpstreamFailureIsTranslated() {
        Jwt jwt = jwt(true);
        when(userClientResolver.resolveClientId(jwt)).thenReturn(CLIENT_ID);
        when(defaultApi.retrieveImage(IMAGE_ENTITY_TYPE, CLIENT_ID, null, null, null, IMAGE_ACCEPT_TEXT_PLAIN))
                .thenThrow(mock(FeignException.class));

        assertThatThrownBy(() -> service.getImage(jwt, null, null))
                .isInstanceOf(UserUpstreamUnavailableException.class);
    }
}
