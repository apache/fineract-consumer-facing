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

package org.apache.fineract.consumer.otp.command.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.data.PendingOtp;
import org.apache.fineract.consumer.otp.command.exception.OtpDestinationInvalidException;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.repository.OtpCommandRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OtpCommandServiceImplTest {

    private static final UUID PUBLIC_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EMAIL = "user@example.com";
    private static final String TOKEN = "AB12CD";

    @Mock
    private OtpCommandRepository otpCommandRepository;

    @Mock
    private OtpEmailDeliveryService otpEmailDeliveryService;

    private OtpCommandServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new OtpCommandServiceImpl(otpCommandRepository, otpEmailDeliveryService);
    }

    @Test
    void createOtpStoresHashNotRawToken() {
        PendingOtp created = service.createOtp(PUBLIC_ID, emailDestination());

        ArgumentCaptor<String> storedCaptor = ArgumentCaptor.forClass(String.class);
        verify(otpCommandRepository).savePendingOtp(eq(PUBLIC_ID), storedCaptor.capture());
        assertThat(storedCaptor.getValue())
                .isEqualTo(sha256Hex(created.getToken()))
                .isNotEqualTo(created.getToken());
    }

    @Test
    void createOtpDeliversRawTokenByEmail() {
        PendingOtp created = service.createOtp(PUBLIC_ID, emailDestination());

        verify(otpEmailDeliveryService).deliver(EMAIL, created.getToken());
    }

    @Test
    void createOtpRejectsUnknownDeliveryMethod() {
        OtpDestination smsDestination = OtpDestination.builder()
                .deliveryMethod(OtpConstants.SMS_DELIVERY_METHOD_NAME)
                .target("+15551234567")
                .build();

        assertThatThrownBy(() -> service.createOtp(PUBLIC_ID, smsDestination))
                .isInstanceOf(OtpDestinationInvalidException.class);
    }

    @Test
    void validateOtpAcceptsCaseInsensitiveTokenAndDeletes() {
        when(otpCommandRepository.getPendingTokenHash(PUBLIC_ID)).thenReturn(sha256Hex(TOKEN));

        service.validateOtp(PUBLIC_ID, TOKEN.toLowerCase(Locale.ROOT));

        verify(otpCommandRepository).deletePendingOtp(PUBLIC_ID);
    }

    @Test
    void validateOtpRejectsWrongToken() {
        when(otpCommandRepository.getPendingTokenHash(PUBLIC_ID)).thenReturn(sha256Hex(TOKEN));

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, "XX99XX"))
                .isInstanceOf(OtpTokenInvalidException.class);
        verify(otpCommandRepository, never()).deletePendingOtp(PUBLIC_ID);
    }

    @Test
    void validateOtpRejectsMissingOtp() {
        when(otpCommandRepository.getPendingTokenHash(PUBLIC_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, TOKEN))
                .isInstanceOf(OtpTokenInvalidException.class);
        verify(otpCommandRepository, never()).deletePendingOtp(PUBLIC_ID);
    }

    @Test
    void validateOtpRejectsNullToken() {
        when(otpCommandRepository.getPendingTokenHash(PUBLIC_ID)).thenReturn(sha256Hex(TOKEN));

        assertThatThrownBy(() -> service.validateOtp(PUBLIC_ID, null))
                .isInstanceOf(OtpTokenInvalidException.class);
        verify(otpCommandRepository, never()).deletePendingOtp(PUBLIC_ID);
    }

    private static OtpDestination emailDestination() {
        return OtpDestination.builder()
                .deliveryMethod(OtpConstants.EMAIL_DELIVERY_METHOD_NAME)
                .target(EMAIL)
                .build();
    }

    private static String sha256Hex(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(token.toUpperCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
