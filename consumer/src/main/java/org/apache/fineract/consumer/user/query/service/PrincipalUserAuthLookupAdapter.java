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

import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.authentication.command.data.PrincipalUserAuthCredentialsData;
import org.apache.fineract.consumer.authentication.command.data.PrincipalUserAuthData;
import org.apache.fineract.consumer.authentication.command.service.PrincipalUserAuthLookupPort;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.apache.fineract.consumer.user.query.exception.UserQueryNotFoundException;
import org.apache.fineract.consumer.user.query.data.UserStatus;
import org.apache.fineract.consumer.user.query.repository.UserQueryRepository;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PrincipalUserAuthLookupAdapter implements PrincipalUserAuthLookupPort {

    private final UserQueryRepository userQueryRepository;

    @Override
    public Optional<PrincipalUserAuthCredentialsData> findCredentialsByEmail(String email) {
        return userQueryRepository.findCredentialsByEmail(email)
                .map(credentials -> PrincipalUserAuthCredentialsData.builder()
                        .publicId(credentials.getPublicId())
                        .bound(credentials.getStatus() == UserStatus.BOUND)
                        .passwordHash(credentials.getPasswordHash())
                        .build());
    }

    @Override
    public PrincipalUserAuthData findByPublicId(UUID publicId) {
        return toPrincipal(userQueryRepository.findByPublicId(publicId)
                .orElseThrow(UserQueryNotFoundException::new));
    }

    @Override
    public PrincipalUserAuthData findById(Long id) {
        return toPrincipal(userQueryRepository.findById(id)
                .orElseThrow(UserQueryNotFoundException::new));
    }

    private static PrincipalUserAuthData toPrincipal(UserQueryData user) {
        return PrincipalUserAuthData.builder()
                .id(user.getId())
                .publicId(user.getPublicId())
                .fineractClientId(user.getFineractClientId())
                .bound(user.getStatus() == UserStatus.BOUND)
                .build();
    }
}
