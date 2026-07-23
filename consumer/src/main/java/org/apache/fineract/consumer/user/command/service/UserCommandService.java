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

package org.apache.fineract.consumer.user.command.service;

import org.apache.fineract.consumer.authentication.command.data.EstablishedSessionCommandData;
import org.apache.fineract.consumer.user.command.data.ConfirmPasswordChangeCommand;
import org.apache.fineract.consumer.user.command.data.CreateUserCommand;
import org.apache.fineract.consumer.user.command.data.ForgotPasswordCommand;
import org.apache.fineract.consumer.user.command.data.InitiatePasswordChangeCommand;
import org.apache.fineract.consumer.user.command.data.ResetPasswordCommand;
import org.apache.fineract.consumer.user.command.data.UserCreatedCommandData;
import org.apache.fineract.consumer.user.command.data.UserPasswordChangeChallengeCommandData;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserCommandService {

    UserCreatedCommandData create(CreateUserCommand command);

    void markOtpVerified(Long userId);

    UserPasswordChangeChallengeCommandData initiatePasswordChange(Jwt jwt, InitiatePasswordChangeCommand command);

    EstablishedSessionCommandData confirmPasswordChange(Jwt jwt, ConfirmPasswordChangeCommand command);

    void forgotPassword(ForgotPasswordCommand command);

    void resetPassword(ResetPasswordCommand command);
}
