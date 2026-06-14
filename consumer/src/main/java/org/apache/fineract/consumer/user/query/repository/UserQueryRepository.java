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

package org.apache.fineract.consumer.user.query.repository;

import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.user.command.domain.User;
import org.apache.fineract.consumer.user.query.data.UserCredentialsQueryData;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface UserQueryRepository extends Repository<User, Long> {

    @Query("""
            SELECT new org.apache.fineract.consumer.user.query.data.UserQueryData(u.id, u.externalId, u.email, u.status)
            FROM User u
            WHERE u.externalId = :externalId
            """)
    Optional<UserQueryData> findByExternalId(UUID externalId);

    @Query("""
            SELECT new org.apache.fineract.consumer.user.query.data.UserQueryData(u.id, u.externalId, u.email, u.status)
            FROM User u
            WHERE u.id = :id
            """)
    Optional<UserQueryData> findById(Long id);

    @Query("""
            SELECT new org.apache.fineract.consumer.user.query.data.UserCredentialsQueryData(u.id, u.externalId, u.status, u.passwordHash)
            FROM User u
            WHERE u.email = :email
            """)
    Optional<UserCredentialsQueryData> findCredentialsByEmail(String email);
}
