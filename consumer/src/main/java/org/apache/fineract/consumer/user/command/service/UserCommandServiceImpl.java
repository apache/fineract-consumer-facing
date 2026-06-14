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

import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.command.Command;
import org.apache.fineract.consumer.user.command.data.CreateUserCommand;
import org.apache.fineract.consumer.user.command.data.UserCreatedCommandData;
import org.apache.fineract.consumer.user.command.domain.User;
import org.apache.fineract.consumer.user.command.exception.UserAlreadyExistsException;
import org.apache.fineract.consumer.user.command.exception.UserNotFoundException;
import org.apache.fineract.consumer.user.command.repository.UserCommandRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserCommandServiceImpl implements UserCommandService {

    private final UserCommandRepository repository;

    @Override
    @Command
    public UserCreatedCommandData create(CreateUserCommand command) {
        repository.findByEmail(command.getEmail()).ifPresent(u -> {
            throw new UserAlreadyExistsException();
        });
        repository.findByFineractClientId(command.getFineractClientId()).ifPresent(u -> {
            throw new UserAlreadyExistsException();
        });
        User user = User.createPendingOtp(
                UUID.randomUUID(),
                command.getEmail(),
                command.getPasswordHash(),
                command.getFineractClientId());
        User saved;
        try {
            saved = repository.save(user);
        } catch (DataIntegrityViolationException e) {
            throw new UserAlreadyExistsException();
        }
        return UserCreatedCommandData.builder()
                .userId(saved.getId())
                .externalId(saved.getExternalId())
                .build();
    }

    @Override
    @Command
    public void markOtpVerified(Long userId) {
        User user = repository.findById(userId).orElseThrow(UserNotFoundException::new);
        user.markOtpVerified();
        repository.save(user);
    }
}
