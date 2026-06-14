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

import java.security.SecureRandom;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.otp.command.data.OtpConstants;
import org.apache.fineract.consumer.otp.command.data.OtpDestination;
import org.apache.fineract.consumer.otp.command.data.PendingOtp;
import org.apache.fineract.consumer.otp.command.exception.OtpDestinationInvalidException;
import org.apache.fineract.consumer.otp.command.exception.OtpTokenInvalidException;
import org.apache.fineract.consumer.otp.command.repository.OtpCommandRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OtpCommandServiceImpl implements OtpCommandService {

    private static final int OTP_LENGTH = 6;
    private static final int OTP_TTL_SECONDS = 300;
    private static final String ALLOWED_CHARS = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final OtpCommandRepository otpCommandRepository;
    private final OtpEmailDeliveryService otpEmailDeliveryService;

    @Override
    @Command
    public PendingOtp createOtp(UUID externalId, OtpDestination destination) {
        if (OtpConstants.EMAIL_DELIVERY_METHOD_NAME.equalsIgnoreCase(destination.getDeliveryMethod())) {
            PendingOtp request = generateNewToken(destination);
            otpEmailDeliveryService.deliver(destination.getTarget(), request.getToken());
            otpCommandRepository.addPendingOtp(externalId, request);
            return request;
        }
        throw new OtpDestinationInvalidException();
    }

    @Override
    @Command
    public void validateOtp(UUID externalId, String token) {
        PendingOtp otpRequest = otpCommandRepository.getPendingOtpForUser(externalId);
        if (otpRequest == null || !otpRequest.isValid() || !otpRequest.getToken().equalsIgnoreCase(token)) {
            throw new OtpTokenInvalidException();
        }
        otpCommandRepository.deletePendingOtpForUser(externalId);
    }

    private PendingOtp generateNewToken(OtpDestination destination) {
        String token = generateToken(OTP_LENGTH);
        return PendingOtp.create(token, OTP_TTL_SECONDS, destination);
    }

    private static String generateToken(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALLOWED_CHARS.charAt(RANDOM.nextInt(ALLOWED_CHARS.length())));
        }
        return builder.toString();
    }
}
