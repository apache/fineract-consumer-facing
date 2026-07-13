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

import feign.FeignException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryChallengeCommandData;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryCommandData;
import org.apache.fineract.consumer.beneficiaries.command.data.BeneficiaryConstants;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmAddBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.ConfirmUpdateBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateAddBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.InitiateUpdateBeneficiaryCommand;
import org.apache.fineract.consumer.beneficiaries.command.data.ResolvedBeneficiaryAccount;
import org.apache.fineract.consumer.beneficiaries.command.domain.Beneficiary;
import org.apache.fineract.consumer.beneficiaries.command.domain.BeneficiaryAccountType;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryAccountInvalidException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryDuplicateNameException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryInvalidException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryNotFoundException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryStepUpInvalidException;
import org.apache.fineract.consumer.beneficiaries.command.exception.BeneficiaryUpstreamUnavailableException;
import org.apache.fineract.consumer.beneficiaries.command.repository.BeneficiaryCommandRepository;
import org.apache.fineract.consumer.infrastructure.access.data.ConsumerAction;
import org.apache.fineract.consumer.infrastructure.access.service.AccessPolicyEvaluator;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.ClientApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.LoansApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SavingsAccountApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SearchApiApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetClientsClientIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansLoanIdResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetLoansResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetSearchResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.SavingsAccountData;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpConstants;
import org.apache.fineract.consumer.infrastructure.stepup.StepUpTokenService;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.service.OtpCommandService;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BeneficiariesCommandServiceImpl implements BeneficiariesCommandService {

    private static final Set<Long> NON_CLOSED_STATUS_IDS = Set.of(100L, 200L, 300L, 303L, 304L);

    private final AccessPolicyEvaluator accessPolicyEvaluator;
    private final UserQueryService userQueryService;
    private final OtpCommandService otpCommandService;
    private final StepUpTokenService stepUpTokenService;
    private final BeneficiaryCommandRepository beneficiaryCommandRepository;
    private final LoansApi loansApi;
    private final SavingsAccountApi savingsAccountApi;
    private final SearchApiApi searchApiApi;
    private final ClientApi clientApi;

    @Override
    @Command
    public BeneficiaryChallengeCommandData initiateAdd(Jwt jwt, InitiateAddBeneficiaryCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.BENEFICIARY_ADD);
        BeneficiaryAccountType accountType = parseAccountType(command.getAccountType());
        UUID publicId = publicId(jwt);
        UserQueryData user = userQueryService.findByPublicId(publicId);
        requireNameAvailable(user.getId(), command.getName());
        resolveAccount(command.getAccountNumber(), command.getOfficeName(), accountType);

        sendOtp(publicId, user);
        IssuedJwt issued = stepUpTokenService.issue(publicId, command.getDeviceFingerprint(),
                addActionFingerprint(command.getName(), command.getOfficeName(), command.getAccountNumber(),
                        accountType, command.getTransferLimit()),
                StepUpConstants.STEPUP_TTL);

        return challenge(issued, user.getEmail());
    }

    @Override
    @Command
    @Transactional
    public BeneficiaryCommandData confirmAdd(Jwt jwt, ConfirmAddBeneficiaryCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.BENEFICIARY_ADD);
        BeneficiaryAccountType accountType = parseAccountType(command.getAccountType());
        UUID publicId = publicId(jwt);
        String actionFingerprint = addActionFingerprint(command.getName(), command.getOfficeName(),
                command.getAccountNumber(), accountType, command.getTransferLimit());
        if (!stepUpTokenService.verify(
                command.getStepUpToken(), publicId, command.getDeviceFingerprint(), actionFingerprint)) {
            throw new BeneficiaryStepUpInvalidException();
        }
        validateOtp(publicId, command.getOtp());

        UserQueryData user = userQueryService.findByPublicId(publicId);
        requireNameAvailable(user.getId(), command.getName());
        ResolvedBeneficiaryAccount resolved =
                resolveAccount(command.getAccountNumber(), command.getOfficeName(), accountType);

        Beneficiary beneficiary = Beneficiary.register(UUID.randomUUID(), user.getId(), command.getName(),
                resolved.getOfficeId(), resolved.getClientId(), resolved.getAccountId(), accountType,
                command.getTransferLimit());
        persist(beneficiary);
        return toCommandData(beneficiary);
    }

    @Override
    @Command
    public BeneficiaryChallengeCommandData initiateUpdate(Jwt jwt, InitiateUpdateBeneficiaryCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.BENEFICIARY_MODIFY);
        UUID publicId = publicId(jwt);
        UserQueryData user = userQueryService.findByPublicId(publicId);
        Beneficiary beneficiary = requireOwnedActive(command.getPublicId(), user.getId());
        requireNameAvailableForUpdate(user.getId(), command.getName(), beneficiary);

        sendOtp(publicId, user);
        IssuedJwt issued = stepUpTokenService.issue(publicId, command.getDeviceFingerprint(),
                updateActionFingerprint(command.getPublicId(), command.getName(), command.getTransferLimit()),
                StepUpConstants.STEPUP_TTL);
        return challenge(issued, user.getEmail());
    }

    @Override
    @Command
    @Transactional
    public BeneficiaryCommandData confirmUpdate(Jwt jwt, ConfirmUpdateBeneficiaryCommand command) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.BENEFICIARY_MODIFY);
        UUID publicId = publicId(jwt);
        String actionFingerprint =
                updateActionFingerprint(command.getPublicId(), command.getName(), command.getTransferLimit());
        if (!stepUpTokenService.verify(
                command.getStepUpToken(), publicId, command.getDeviceFingerprint(), actionFingerprint)) {
            throw new BeneficiaryStepUpInvalidException();
        }
        validateOtp(publicId, command.getOtp());

        UserQueryData user = userQueryService.findByPublicId(publicId);
        Beneficiary beneficiary = requireOwnedActive(command.getPublicId(), user.getId());
        requireNameAvailableForUpdate(user.getId(), command.getName(), beneficiary);

        beneficiary.update(command.getName(), command.getTransferLimit());
        persist(beneficiary);
        return toCommandData(beneficiary);
    }

    @Override
    @Command
    @Transactional
    public void delete(Jwt jwt, UUID publicId) {
        accessPolicyEvaluator.authorize(jwt, ConsumerAction.BENEFICIARY_DELETE);
        UserQueryData user = userQueryService.findByPublicId(publicId(jwt));
        Beneficiary beneficiary = requireOwnedActive(publicId, user.getId());
        beneficiary.deactivate();
        beneficiaryCommandRepository.save(beneficiary);
    }

    private ResolvedBeneficiaryAccount resolveAccount(
            String accountNumber, String officeName, BeneficiaryAccountType accountType) {
        return accountType == BeneficiaryAccountType.LOAN
                ? resolveLoanAccount(accountNumber, officeName)
                : resolveSavingsAccount(accountNumber, officeName);
    }

    private ResolvedBeneficiaryAccount resolveLoanAccount(String accountNumber, String officeName) {
        GetLoansResponse response =
                call(() -> loansApi.retrieveAllLoans(null, null, null, null, null, accountNumber, null, null, null));
        GetLoansLoanIdResponse loan = response.getPageItems() == null ? null
                : response.getPageItems().stream()
                        .filter(item -> accountNumber.equals(item.getAccountNo()))
                        .findFirst()
                        .orElse(null);
        if (loan == null || loan.getId() == null || loan.getClientId() == null
                || !isNonClosed(loan.getStatus() == null ? null : loan.getStatus().getId())) {
            throw new BeneficiaryAccountInvalidException();
        }
        return withVerifiedOffice(loan.getClientId(), officeName, loan.getId());
    }

    private ResolvedBeneficiaryAccount resolveSavingsAccount(String accountNumber, String officeName) {
        List<GetSearchResponse> matches =
                call(() -> searchApiApi.searchData(accountNumber, BeneficiaryConstants.SAVINGS_SEARCH_RESOURCE, true));
        if (matches != null) {
            for (GetSearchResponse match : matches) {
                if (match.getEntityId() == null) {
                    continue;
                }
                SavingsAccountData account =
                        call(() -> savingsAccountApi.retrieveSavingsAccount(match.getEntityId(), null, null, null));
                if (accountNumber.equals(account.getAccountNo()) && account.getClientId() != null
                        && isNonClosed(account.getStatus() == null ? null : account.getStatus().getId())) {
                    return withVerifiedOffice(account.getClientId(), officeName, match.getEntityId());
                }
            }
        }
        throw new BeneficiaryAccountInvalidException();
    }

    private ResolvedBeneficiaryAccount withVerifiedOffice(Long clientId, String officeName, Long accountId) {
        GetClientsClientIdResponse client = call(() -> clientApi.retrieveOneClient(clientId, false));
        if (client.getOfficeId() == null || !officeName.equals(client.getOfficeName())) {
            throw new BeneficiaryAccountInvalidException();
        }
        return ResolvedBeneficiaryAccount.builder()
                .officeId(client.getOfficeId())
                .clientId(clientId)
                .accountId(accountId)
                .build();
    }

    private static boolean isNonClosed(Long statusId) {
        return statusId != null && NON_CLOSED_STATUS_IDS.contains(statusId);
    }

    private Beneficiary requireOwnedActive(UUID beneficiaryPublicId, Long userId) {
        return beneficiaryCommandRepository.findByPublicIdAndUserIdAndActiveTrue(beneficiaryPublicId, userId)
                .orElseThrow(BeneficiaryNotFoundException::new);
    }

    private void requireNameAvailable(Long userId, String name) {
        if (beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(userId, name)) {
            throw new BeneficiaryDuplicateNameException();
        }
    }

    private void requireNameAvailableForUpdate(Long userId, String name, Beneficiary current) {
        if (!current.getName().equalsIgnoreCase(name)
                && beneficiaryCommandRepository.existsByUserIdAndNameIgnoreCaseAndActiveTrue(userId, name)) {
            throw new BeneficiaryDuplicateNameException();
        }
    }

    private void persist(Beneficiary beneficiary) {
        try {
            beneficiaryCommandRepository.saveAndFlush(beneficiary);
        } catch (DataIntegrityViolationException e) {
            throw new BeneficiaryDuplicateNameException();
        }
    }

    private void sendOtp(UUID publicId, UserQueryData user) {
        otpCommandService.createOtp(publicId, OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(user.getEmail())
                .build());
    }

    private void validateOtp(UUID publicId, String otp) {
        try {
            otpCommandService.validateOtp(publicId, otp);
        } catch (OtpTokenInvalidException e) {
            throw new BeneficiaryStepUpInvalidException();
        }
    }

    private String addActionFingerprint(String name, String officeName, String accountNumber,
            BeneficiaryAccountType accountType, BigDecimal transferLimit) {
        return stepUpTokenService.actionFingerprint(BeneficiaryConstants.ADD_ENDPOINT,
                name, officeName, accountNumber, accountType.name(), fingerprintPart(transferLimit));
    }

    private String updateActionFingerprint(UUID beneficiaryPublicId, String name, BigDecimal transferLimit) {
        return stepUpTokenService.actionFingerprint(BeneficiaryConstants.UPDATE_ENDPOINT,
                beneficiaryPublicId.toString(), name, fingerprintPart(transferLimit));
    }

    private static String fingerprintPart(BigDecimal transferLimit) {
        return transferLimit == null ? "" : transferLimit.stripTrailingZeros().toPlainString();
    }

    private static BeneficiaryChallengeCommandData challenge(IssuedJwt issued, String email) {
        return BeneficiaryChallengeCommandData.builder()
                .stepUpToken(issued.getTokenValue())
                .expiresAt(issued.getExpiresAt())
                .sentTo(maskEmail(email))
                .build();
    }

    private static BeneficiaryCommandData toCommandData(Beneficiary beneficiary) {
        return BeneficiaryCommandData.builder()
                .publicId(beneficiary.getPublicId())
                .name(beneficiary.getName())
                .accountType(beneficiary.getAccountType())
                .transferLimit(beneficiary.getTransferLimit())
                .build();
    }

    private static BeneficiaryAccountType parseAccountType(String accountType) {
        try {
            return BeneficiaryAccountType.valueOf(accountType.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BeneficiaryInvalidException();
        }
    }

    private static UUID publicId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }

    private <T> T call(Supplier<T> upstream) {
        try {
            return upstream.get();
        } catch (FeignException.NotFound | FeignException.BadRequest e) {
            throw new BeneficiaryAccountInvalidException();
        } catch (FeignException e) {
            throw new BeneficiaryUpstreamUnavailableException();
        }
    }

    private static String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 1) {
            return "***" + email.substring(Math.max(at, 0));
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
