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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.user.command.data.CreateUserCommand;
import org.apache.fineract.consumer.user.command.data.UserCreatedCommandData;
import org.apache.fineract.consumer.user.command.domain.User;
import org.apache.fineract.consumer.user.command.domain.UserStatus;
import org.apache.fineract.consumer.user.command.exception.UserAlreadyExistsException;
import org.apache.fineract.consumer.user.command.exception.UserNotFoundException;
import org.apache.fineract.consumer.user.command.repository.UserCommandRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserCommandServiceImplTest {

    private static final Long USER_ID = 7L;
    private static final UUID EXTERNAL_ID = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final String EMAIL = "user@test.com";
    private static final String PASSWORD_HASH = "{bcrypt}$2a$10$hash";
    private static final Long FINERACT_CLIENT_ID = 42L;

    @Mock
    private UserCommandRepository repository;

    @InjectMocks
    private UserCommandServiceImpl service;

    private static CreateUserCommand createCommand() {
        return CreateUserCommand.builder()
                .email(EMAIL)
                .passwordHash(PASSWORD_HASH)
                .fineractClientId(FINERACT_CLIENT_ID)
                .build();
    }

    private static User existingUser() {
        return User.createPendingOtp(EXTERNAL_ID, EMAIL, PASSWORD_HASH, FINERACT_CLIENT_ID);
    }

    @Nested
    class Create {

        @Test
        void successPersistsPendingOtpUserAndReturnsIdentifiers() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(repository.findByFineractClientId(FINERACT_CLIENT_ID)).thenReturn(Optional.empty());
            when(repository.save(any())).thenAnswer(invocation -> {
                User saved = invocation.getArgument(0);
                ReflectionTestUtils.setField(saved, "id", USER_ID);
                return saved;
            });

            UserCreatedCommandData created = service.create(createCommand());

            ArgumentCaptor<User> saved = ArgumentCaptor.forClass(User.class);
            verify(repository).save(saved.capture());
            assertThat(saved.getValue().getEmail()).isEqualTo(EMAIL);
            assertThat(saved.getValue().getPasswordHash()).isEqualTo(PASSWORD_HASH);
            assertThat(saved.getValue().getFineractClientId()).isEqualTo(FINERACT_CLIENT_ID);
            assertThat(saved.getValue().getStatus()).isEqualTo(UserStatus.PENDING_OTP);
            assertThat(saved.getValue().getExternalId()).isNotNull();

            assertThat(created.getUserId()).isEqualTo(USER_ID);
            assertThat(created.getExternalId()).isEqualTo(saved.getValue().getExternalId());
        }

        @Test
        void existingEmailIsRejectedWithoutSaving() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.of(existingUser()));

            assertThatThrownBy(() -> service.create(createCommand()))
                    .isInstanceOf(UserAlreadyExistsException.class);
            verify(repository, never()).save(any());
        }

        @Test
        void existingFineractClientIsRejectedWithoutSaving() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(repository.findByFineractClientId(FINERACT_CLIENT_ID)).thenReturn(Optional.of(existingUser()));

            assertThatThrownBy(() -> service.create(createCommand()))
                    .isInstanceOf(UserAlreadyExistsException.class);
            verify(repository, never()).save(any());
        }

        @Test
        void uniqueConstraintRaceIsTranslatedToAlreadyExists() {
            when(repository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(repository.findByFineractClientId(FINERACT_CLIENT_ID)).thenReturn(Optional.empty());
            when(repository.save(any())).thenThrow(new DataIntegrityViolationException("duplicate key"));

            assertThatThrownBy(() -> service.create(createCommand()))
                    .isInstanceOf(UserAlreadyExistsException.class);
        }
    }

    @Nested
    class MarkOtpVerified {

        @Test
        void successTransitionsUserToBoundAndSaves() {
            User user = existingUser();
            when(repository.findById(USER_ID)).thenReturn(Optional.of(user));

            service.markOtpVerified(USER_ID);

            verify(repository).save(user);
            assertThat(user.getStatus()).isEqualTo(UserStatus.BOUND);
            assertThat(user.getBoundAt()).isNotNull();
        }

        @Test
        void unknownUserIsRejected() {
            when(repository.findById(USER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> service.markOtpVerified(USER_ID))
                    .isInstanceOf(UserNotFoundException.class);
            verify(repository, never()).save(any());
        }
    }
}
