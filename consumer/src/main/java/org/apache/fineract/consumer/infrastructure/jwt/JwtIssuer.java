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

package org.apache.fineract.consumer.infrastructure.jwt;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.configs.JwtProperties;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class JwtIssuer {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties jwtProperties;

    public IssuedJwt issue(String subject, Map<String, Object> claims, Duration timeToLive) {
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(timeToLive);
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer(jwtProperties.getIssuer())
                .subject(subject)
                .issuedAt(issuedAt)
                .expiresAt(expiresAt);
        claims.forEach(claimsBuilder::claim);
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.ES256).build();
        String tokenValue = jwtEncoder.encode(JwtEncoderParameters.from(header, claimsBuilder.build()))
                .getTokenValue();
        return IssuedJwt.builder()
                .tokenValue(tokenValue)
                .expiresAt(expiresAt)
                .build();
    }
}
