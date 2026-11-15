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

package org.apache.fineract.consumer.infrastructure.otp.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * What a one-time code was minted for, and may therefore be redeemed for.
 *
 * <p>Without this, every flow shares one slot per user, and a code is only ever "the code for this
 * person" rather than "the code for this person to do this thing". That makes the weakest challenge
 * in the system a credential for the strongest one: a code the user was asked for on a
 * "verify your email" screen also satisfies {@code POST /api/v1/user/password/reset}, which is
 * public and needs nothing but an email address, the code and a new password.
 *
 * <p>The purpose is therefore mixed into both the Redis key and the stored hash, so a code issued
 * for one purpose does not validate for another and cannot be transplanted between slots by
 * anything that can read Redis. It is also named in the delivery email, so the recipient can tell
 * what they are authorising before they type it in.
 *
 * <p>{@code keySegment} is persisted key material: changing one invalidates the codes in flight for
 * that purpose, which costs a user a resend and nothing more. It must stay free of {@code ':'} so
 * the key cannot be made ambiguous.
 */
@Getter
@RequiredArgsConstructor
public enum OtpPurpose {

    REGISTRATION("registration", "finish creating your account"),
    LOGIN_TWO_FACTOR("login-2fa", "sign in"),
    PASSWORD_CHANGE("password-change", "change the password on your account"),
    PASSWORD_RESET("password-reset", "reset the password you forgot"),
    TRANSFER("transfer", "authorise a money transfer"),
    SCHEDULED_TRANSFER("scheduled-transfer", "set up or cancel a recurring transfer"),
    BENEFICIARY("beneficiary", "change the payees saved on your account");

    private final String keySegment;
    private final String emailDescription;
}
