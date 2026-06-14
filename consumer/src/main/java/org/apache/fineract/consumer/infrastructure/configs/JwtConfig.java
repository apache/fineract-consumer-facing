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

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.fineract.consumer.infrastructure.jwt.JwtClaims;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtConfig {

    private static final String EC_ALGORITHM = "EC";
    private static final String PRIVATE_KEY_PEM_TYPE = "PRIVATE KEY";
    private static final String PUBLIC_KEY_PEM_TYPE = "PUBLIC KEY";

    @Bean
    public ECKey jwtSigningKey(JwtProperties jwtProperties) {
        try {
            String pem = new String(jwtProperties.getKeyLocation().getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
            KeyFactory keyFactory = KeyFactory.getInstance(EC_ALGORITHM);
            ECPrivateKey privateKey = (ECPrivateKey) keyFactory
                    .generatePrivate(new PKCS8EncodedKeySpec(extractPemBlock(pem, PRIVATE_KEY_PEM_TYPE)));
            ECPublicKey publicKey = (ECPublicKey) keyFactory
                    .generatePublic(new X509EncodedKeySpec(extractPemBlock(pem, PUBLIC_KEY_PEM_TYPE)));
            return new ECKey.Builder(Curve.P_256, publicKey)
                    .privateKey(privateKey)
                    .keyIDFromThumbprint()
                    .build();
        } catch (IOException | NoSuchAlgorithmException | InvalidKeySpecException | JOSEException e) {
            throw new IllegalStateException(
                    "Failed to load the JWT signing keypair from " + jwtProperties.getKeyLocation()
                            + " — generate it with scripts/generate-dev-jwt-key.sh or set JWT_KEY_LOCATION",
                    e);
        }
    }

    @Bean
    public JwtEncoder jwtEncoder(ECKey jwtSigningKey) {
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey)));
    }

    @Bean
    @Primary
    public JwtDecoder jwtDecoder(ECKey jwtSigningKey, JwtProperties jwtProperties) {
        NimbusJwtDecoder decoder = buildDecoder(jwtSigningKey);
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer()));
        return decoder;
    }

    @Bean
    public JwtDecoder accessTokenJwtDecoder(ECKey jwtSigningKey, JwtProperties jwtProperties) {
        NimbusJwtDecoder decoder = buildDecoder(jwtSigningKey);
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer()),
                JwtConfig::rejectSinglePurposeTokens));
        return decoder;
    }

    private static NimbusJwtDecoder buildDecoder(ECKey jwtSigningKey) {
        return NimbusJwtDecoder
                .withJwkSource(new ImmutableJWKSet<>(new JWKSet(jwtSigningKey.toPublicJWK())))
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
    }

    private static OAuth2TokenValidatorResult rejectSinglePurposeTokens(Jwt jwt) {
        if (jwt.hasClaim(JwtClaims.PURPOSE)) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN, "single-purpose token is not an access token", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static byte[] extractPemBlock(String pem, String pemType) {
        Pattern blockPattern = Pattern
                .compile("-----BEGIN " + pemType + "-----(.*?)-----END " + pemType + "-----", Pattern.DOTALL);
        Matcher matcher = blockPattern.matcher(pem);
        if (!matcher.find()) {
            throw new IllegalStateException("JWT key PEM is missing a '" + pemType + "' block");
        }
        return Base64.getMimeDecoder().decode(matcher.group(1).trim());
    }
}
