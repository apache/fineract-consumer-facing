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

package org.apache.fineract.consumer.otp.command.repository;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.fineract.consumer.otp.command.data.PendingOtp;
import org.springframework.stereotype.Repository;
import org.springframework.util.Assert;

@Repository
public class OtpCommandRepository {

    private final ConcurrentHashMap<UUID, PendingOtp> store = new ConcurrentHashMap<>();

    public PendingOtp getPendingOtpForUser(UUID externalId) {
        Assert.notNull(externalId, "externalId must not be null");
        return store.get(externalId);
    }

    public void addPendingOtp(UUID externalId, PendingOtp request) {
        Assert.notNull(externalId, "externalId must not be null");
        Assert.notNull(request, "request must not be null");
        store.put(externalId, request);
    }

    public void deletePendingOtpForUser(UUID externalId) {
        Assert.notNull(externalId, "externalId must not be null");
        store.remove(externalId);
    }
}
