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

import feign.FeignException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.access.service.UserClientResolver;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientChargesApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.DefaultApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsChargesPageItems;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdChargesResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetObligeeData;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.query.Query;
import org.apache.fineract.consumer.infrastructure.web.EmailMasking;
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
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserQueryServiceImpl implements UserQueryService {

    private static final Set<String> VALID_CHARGE_STATUSES = Set.of("all", "active", "inactive");
    private static final String IMAGE_ENTITY_TYPE = "clients";
    private static final String IMAGE_ACCEPT_TEXT_PLAIN = "text/plain";

    private final UserQueryRepository repository;
    private final ClientApi clientApi;
    private final ClientChargesApi clientChargesApi;
    private final DefaultApi defaultApi;
    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserClientResolver userClientResolver;

    @Override
    @Query
    public UserQueryData findByPublicId(UUID publicId) {
        return repository.findByPublicId(publicId)
                .orElseThrow(UserNotFoundException::new);
    }

    @Override
    @Query
    public UserQueryData findById(Long id) {
        return repository.findById(id)
                .orElseThrow(UserNotFoundException::new);
    }

    @Override
    @Query
    public Optional<UserCredentialsQueryData> findCredentialsByEmail(String email) {
        return repository.findCredentialsByEmail(email);
    }

    @Override
    @Query
    public UserProfileQueryData getProfile(Jwt jwt) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.USER_PROFILE_VIEW);
        Long clientId = userClientResolver.resolveClientId(jwt);
        GetClientsClientIdResponse client = fetch(() -> clientApi.retrieveOneClient(clientId, null));
        return UserProfileQueryData.builder()
                .displayName(client.getDisplayName())
                .accountNo(client.getAccountNo())
                .active(client.getActive())
                .memberSince(client.getActivationDate())
                .maskedEmail(EmailMasking.mask(client.getEmailAddress()))
                .maskedMobile(maskMobile(client.getMobileNo()))
                .kycVerified(Boolean.TRUE.equals(jwt.getClaimAsBoolean(JwtClaims.KYC_VERIFIED)))
                .hasImage(Boolean.TRUE.equals(client.getImagePresent()))
                .build();
    }

    @Override
    @Query
    public UserChargesQueryResponse getCharges(Jwt jwt, UserChargesQuery query) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.USER_CHARGES_LIST);
        if (!VALID_CHARGE_STATUSES.contains(query.getStatus())) {
            throw new UserChargeStatusInvalidException();
        }
        Long clientId = userClientResolver.resolveClientId(jwt);
        Integer limit = query.getSize();
        Integer offset = query.getPage() * query.getSize();
        GetClientsClientIdChargesResponse charges = fetch(() ->
                clientChargesApi.retrieveAllClientCharges(clientId, query.getStatus(), null, limit, offset));
        List<UserChargeQueryData> items = charges == null || charges.getPageItems() == null
                ? List.of()
                : charges.getPageItems().stream().map(this::toChargeData).toList();
        return UserChargesQueryResponse.builder()
                .charges(items)
                .totalFilteredRecords(charges == null ? null : charges.getTotalFilteredRecords())
                .build();
    }

    @Override
    @Query
    public List<UserObligeeQueryData> getObligees(Jwt jwt) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.USER_OBLIGEES_VIEW);
        Long clientId = userClientResolver.resolveClientId(jwt);
        List<GetObligeeData> obligees = fetch(() -> clientApi.retrieveClientObligeeDetails(clientId));
        if (obligees == null) {
            return List.of();
        }
        return obligees.stream().map(this::toObligeeData).toList();
    }

    @Override
    @Query
    public UserImageQueryData getImage(Jwt jwt, Integer maxWidth, Integer maxHeight) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.USER_IMAGE_VIEW);
        Long clientId = userClientResolver.resolveClientId(jwt);
        try {
            String imageDataUri = defaultApi.retrieveImage(
                    IMAGE_ENTITY_TYPE, clientId, maxWidth, maxHeight, null, IMAGE_ACCEPT_TEXT_PLAIN);
            return UserImageQueryData.builder()
                    .imageDataUri(imageDataUri)
                    .build();
        } catch (FeignException.NotFound e) {
            throw new UserImageNotFoundException(e);
        } catch (FeignException e) {
            throw new UserUpstreamUnavailableException(e);
        }
    }

    private <T> T fetch(Supplier<T> call) {
        try {
            return call.get();
        } catch (FeignException e) {
            throw new UserUpstreamUnavailableException(e);
        }
    }

    private UserChargeQueryData toChargeData(GetClientsChargesPageItems charge) {
        return UserChargeQueryData.builder()
                .id(charge.getId())
                .name(charge.getName())
                .currency(charge.getCurrency() == null ? null : charge.getCurrency().getCode())
                .amount(charge.getAmount())
                .amountOutstanding(charge.getAmountOutstanding())
                .amountPaid(charge.getAmountPaid())
                .amountWaived(charge.getAmountWaived())
                .dueDate(charge.getDueDate())
                .active(charge.getIsActive())
                .paid(charge.getIsPaid())
                .waived(charge.getIsWaived())
                .penalty(charge.getPenalty())
                .build();
    }

    private UserObligeeQueryData toObligeeData(GetObligeeData obligee) {
        return UserObligeeQueryData.builder()
                .displayName(obligee.getDisplayName())
                .accountNumber(obligee.getAccountNumber())
                .loanAmount(obligee.getLoanAmount())
                .guaranteeAmount(obligee.getGuaranteeAmount())
                .amountReleased(obligee.getAmountReleased())
                .amountTransferred(obligee.getAmountTransferred())
                .build();
    }

    private static String maskMobile(String mobile) {
        if (mobile == null) {
            return null;
        }
        String digits = mobile.replaceAll("\\D", "");
        String lastFour = digits.length() >= 4 ? digits.substring(digits.length() - 4) : digits;
        return "***-***-" + lastFour;
    }
}
