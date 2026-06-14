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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import org.apache.fineract.consumer.infrastructure.configs.JwtConfig;
import org.apache.fineract.consumer.infrastructure.configs.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtIssuerTest {

    private static final String ISSUER = "test-issuer";
    private static final String SUBJECT = "0b8e7b0e-9c2d-4f6a-8d3e-1a2b3c4d5e6f";

    private JwtIssuer jwtIssuer;
    private JwtDecoder jwtDecoder;
    private JwtProperties jwtProperties;

    @BeforeEach
    void setUp() throws Exception {
        jwtProperties = propertiesFor(generateKeyPairPem(), ISSUER);
        JwtConfig jwtConfig = new JwtConfig();
        var signingKey = jwtConfig.jwtSigningKey(jwtProperties);
        jwtIssuer = new JwtIssuer(jwtConfig.jwtEncoder(signingKey), jwtProperties);
        jwtDecoder = jwtConfig.jwtDecoder(signingKey, jwtProperties);
    }

    @Test
    void issuedTokenDecodesWithSubjectIssuerAndCustomClaims() {
        IssuedJwt issued = jwtIssuer.issue(SUBJECT,
                Map.of("tenant", "default", "roles", List.of("CONSUMER")),
                Duration.ofMinutes(15));

        Jwt decoded = jwtDecoder.decode(issued.getTokenValue());

        assertThat(decoded.getSubject()).isEqualTo(SUBJECT);
        assertThat(decoded.getClaimAsString(JwtClaimNames.ISS)).isEqualTo(ISSUER);
        assertThat(decoded.getClaimAsString("tenant")).isEqualTo("default");
        assertThat(decoded.getClaimAsStringList("roles")).containsExactly("CONSUMER");
        assertThat(decoded.getExpiresAt()).isEqualTo(issued.getExpiresAt().truncatedTo(ChronoUnit.SECONDS));
        assertThat(issued.getExpiresAt())
                .isCloseTo(Instant.now().plus(Duration.ofMinutes(15)), within(Duration.ofSeconds(5)));
    }

    @Test
    void tamperedTokenIsRejected() {
        IssuedJwt issued = jwtIssuer.issue(SUBJECT, Map.of(), Duration.ofMinutes(5));
        String[] parts = issued.getTokenValue().split("\\.");
        String tamperedPayload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString("{\"sub\":\"someone-else\"}".getBytes());
        String tampered = parts[0] + "." + tamperedPayload + "." + parts[2];

        assertThatThrownBy(() -> jwtDecoder.decode(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedByDifferentKeyIsRejected() throws Exception {
        JwtProperties otherProperties = propertiesFor(generateKeyPairPem(), ISSUER);
        JwtConfig otherConfig = new JwtConfig();
        var otherKey = otherConfig.jwtSigningKey(otherProperties);
        JwtIssuer otherIssuer = new JwtIssuer(otherConfig.jwtEncoder(otherKey), otherProperties);
        IssuedJwt foreignToken = otherIssuer.issue(SUBJECT, Map.of(), Duration.ofMinutes(5));

        assertThatThrownBy(() -> jwtDecoder.decode(foreignToken.getTokenValue()))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void pemWithoutPrivateKeyBlockFailsFast() throws Exception {
        String publicOnlyPem = generateKeyPairPem().replaceAll(
                "(?s)-----BEGIN PRIVATE KEY-----.*?-----END PRIVATE KEY-----", "");

        assertThatThrownBy(() -> new JwtConfig().jwtSigningKey(propertiesFor(publicOnlyPem, ISSUER)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("PRIVATE KEY");
    }

    @Test
    void issuedJwtToStringNeverContainsTheTokenValue() {
        IssuedJwt issued = jwtIssuer.issue(SUBJECT, Map.of(), Duration.ofMinutes(5));

        assertThat(issued.toString()).doesNotContain(issued.getTokenValue());
    }

    private static JwtProperties propertiesFor(String pem, String issuer) {
        return new JwtProperties(new ByteArrayResource(pem.getBytes()), issuer);
    }

    private static String generateKeyPairPem() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        return pemBlock("PRIVATE KEY", keyPair.getPrivate().getEncoded())
                + pemBlock("PUBLIC KEY", keyPair.getPublic().getEncoded());
    }

    private static String pemBlock(String pemType, byte[] derBytes) {
        return "-----BEGIN " + pemType + "-----\n"
                + Base64.getMimeEncoder().encodeToString(derBytes)
                + "\n-----END " + pemType + "-----\n";
    }
}
