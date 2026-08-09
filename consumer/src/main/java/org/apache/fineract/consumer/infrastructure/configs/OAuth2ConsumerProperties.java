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

package org.apache.fineract.consumer.infrastructure.configs;

import java.time.Duration;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties("consumer.oauth2")
public class OAuth2ConsumerProperties {

    private final TppClient tppClient;
    private final Duration accessTokenTtl;
    private final Duration refreshTokenTtl;
    private final Duration consentTtl;
    private final String frontendBaseUrl;
    private final String kid;

    @Getter
    @RequiredArgsConstructor
    @ToString(onlyExplicitlyIncluded = true)
    public static class TppClient {

        @ToString.Include
        private final String id;
        private final String secret;
        @ToString.Include
        private final String redirectUri;
    }
}
