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

package org.apache.fineract.consumer.infrastructure.stepup;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.infrastructure.jwt.data.IssuedJwt;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.jwt.service.JwtIssuer;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class StepUpTokenService {

    private static final String FINGERPRINT_HASH_ALGORITHM = "SHA-256";

    private final JwtIssuer jwtIssuer;
    private final JwtDecoder jwtDecoder;

    public String actionFingerprint(String endpoint, Long fromAccountId, Long toAccountId, BigDecimal amount) {
        String canonical =
                endpoint + "|" + fromAccountId + "|" + toAccountId + "|" + amount.stripTrailingZeros().toPlainString();
        return hash(canonical);
    }

    public String actionFingerprint(
            String endpoint, Long fromAccountId, Long toAccountId, String toAccountType, BigDecimal amount) {
        String canonical = endpoint + "|" + fromAccountId + "|" + toAccountId + "|" + toAccountType + "|"
                + amount.stripTrailingZeros().toPlainString();
        return hash(canonical);
    }

    public String actionFingerprint(String endpoint, Long fromAccountId, Long toAccountId) {
        String canonical = endpoint + "|" + fromAccountId + "|" + toAccountId;
        return hash(canonical);
    }

    public String actionFingerprint(String endpoint, String... parts) {
        String canonical = endpoint + "|" + String.join("|", parts);
        return hash(canonical);
    }

    private String hash(String canonical) {
        try {
            MessageDigest digest = MessageDigest.getInstance(FINGERPRINT_HASH_ALGORITHM);
            return HexFormat.of().formatHex(digest.digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(FINGERPRINT_HASH_ALGORITHM + " unavailable", e);
        }
    }

    public IssuedJwt issue(UUID publicId, String deviceFingerprint, String actionFingerprint, Duration ttl) {
        return jwtIssuer.issue(
                publicId.toString(),
                Map.of(
                        JwtClaims.PURPOSE, StepUpConstants.STEPUP_PURPOSE_VALUE,
                        JwtClaims.DEVICE_FINGERPRINT, deviceFingerprint,
                        JwtClaims.ACTION_FINGERPRINT, actionFingerprint),
                ttl);
    }

    public boolean verify(String token, UUID subject, String deviceFingerprint, String actionFingerprint) {
        Jwt decoded;
        try {
            decoded = jwtDecoder.decode(token);
        } catch (JwtException e) {
            return false;
        }
        return StepUpConstants.STEPUP_PURPOSE_VALUE.equals(decoded.getClaimAsString(JwtClaims.PURPOSE))
                && subject.toString().equals(decoded.getSubject())
                && deviceFingerprint.equals(decoded.getClaimAsString(JwtClaims.DEVICE_FINGERPRINT))
                && actionFingerprint.equals(decoded.getClaimAsString(JwtClaims.ACTION_FINGERPRINT));
    }
}
