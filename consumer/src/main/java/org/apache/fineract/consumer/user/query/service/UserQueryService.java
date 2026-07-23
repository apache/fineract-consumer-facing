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

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.apache.fineract.consumer.user.query.data.UserChargesQuery;
import org.apache.fineract.consumer.user.query.data.UserChargesQueryResponse;
import org.apache.fineract.consumer.user.query.data.UserCredentialsQueryData;
import org.apache.fineract.consumer.user.query.data.UserImageQueryData;
import org.apache.fineract.consumer.user.query.data.UserObligeeQueryData;
import org.apache.fineract.consumer.user.query.data.UserProfileQueryData;
import org.apache.fineract.consumer.user.query.data.UserQueryData;
import org.springframework.security.oauth2.jwt.Jwt;

public interface UserQueryService {

    UserQueryData findByPublicId(UUID publicId);

    UserQueryData findById(Long id);

    Optional<UserCredentialsQueryData> findCredentialsByEmail(String email);

    UserProfileQueryData getProfile(Jwt jwt);

    UserChargesQueryResponse getCharges(Jwt jwt, UserChargesQuery query);

    List<UserObligeeQueryData> getObligees(Jwt jwt);

    UserImageQueryData getImage(Jwt jwt, Integer maxWidth, Integer maxHeight);
}
