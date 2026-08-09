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

import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.access.filter.RateLimitFilter;
import org.apache.fineract.consumer.infrastructure.access.repository.PrincipalUserLookupPort;
import org.apache.fineract.consumer.infrastructure.access.service.RateLimitCounter;
import org.apache.fineract.consumer.infrastructure.configs.JwtProperties;
import org.apache.fineract.consumer.infrastructure.fineractclient.configs.FineractClientProperties;
import org.apache.fineract.consumer.infrastructure.jwt.data.JwtClaims;
import org.apache.fineract.consumer.infrastructure.kyc.service.ClientStandingChecker;
import org.apache.fineract.consumer.infrastructure.oauth2.data.OAuth2Constants;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentLookupPort;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentRecorderPort;
import org.apache.fineract.consumer.infrastructure.oauth2.filter.OAuth2UserAuthenticationFilter;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2RefreshTokenConsentValidator;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentAuthorizeRequestValidator;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2ConsentDecisionCustomizer;
import org.apache.fineract.consumer.infrastructure.oauth2.service.OAuth2AccessTokenCustomizer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.CsrfConfigurer;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.authorization.JdbcOAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationCodeRequestAuthenticationValidator;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AuthorizationConsentAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationProvider;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;
import org.springframework.security.oauth2.server.authorization.token.DelegatingOAuth2TokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.JwtGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2AccessTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2RefreshTokenGenerator;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.AnyRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableConfigurationProperties(OAuth2ConsumerProperties.class)
public class OAuth2AuthorizationServerConfig {

    @Bean
    @Order(OAuth2Constants.AUTHORIZATION_SERVER_CHAIN_ORDER)
    public SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http,
            @Qualifier("accessTokenJwtDecoder") JwtDecoder accessTokenJwtDecoder,
            OAuth2ConsumerProperties oauth2Properties,
            OAuth2ConsentLookupPort consentLookup,
            OAuth2ConsentRecorderPort consentRecorder,
            OAuth2AuthorizationService oAuth2AuthorizationService,
            ApplicationEventPublisher eventPublisher,
            RateLimitProperties rateLimitProperties,
            RateLimitCounter rateLimitCounter,
            ObjectMapper objectMapper) throws Exception {
        OAuth2AuthorizationServerConfigurer authorizationServerConfigurer = new OAuth2AuthorizationServerConfigurer();
        return http
                .securityMatcher(authorizationServerConfigurer.getEndpointsMatcher())
                .with(authorizationServerConfigurer, authorizationServer ->
                        configureConsentAndTokenEndpoints(authorizationServer, oauth2Properties,
                                consentLookup, consentRecorder,
                                oAuth2AuthorizationService, eventPublisher))
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(OAuth2AuthorizationServerConfig::configureConsentPageCsrf)
                .exceptionHandling(exceptions -> exceptions
                        .defaultAuthenticationEntryPointFor(
                                loginRedirectEntryPoint(oauth2Properties),
                                new OrRequestMatcher(
                                        PathPatternRequestMatcher.pathPattern(OAuth2Constants.AUTHORIZATION_ENDPOINT),
                                        PathPatternRequestMatcher.pathPattern(OAuth2Constants.DEVICE_VERIFICATION_ENDPOINT)))
                        .defaultAuthenticationEntryPointFor(
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                                AnyRequestMatcher.INSTANCE))
                .addFilterAfter(new OAuth2UserAuthenticationFilter(accessTokenJwtDecoder),
                        SecurityContextHolderFilter.class)
                .addFilterAfter(RateLimitFilter.perTppClient(rateLimitProperties, rateLimitCounter, objectMapper),
                        SecurityContextHolderFilter.class)
                .addFilterAfter(new CsrfCookieRenderingFilter(), CsrfFilter.class)
                .build();
    }

    @Bean
    @Order(OAuth2Constants.TPP_RESOURCE_CHAIN_ORDER)
    public SecurityFilterChain openBankingResourceFilterChain(HttpSecurity http,
            @Qualifier("openbankingTokenJwtDecoder") JwtDecoder openbankingTokenJwtDecoder) throws Exception {
        return http
                .securityMatcher(
                        OAuth2Constants.TPP_ACCOUNTS_PATTERN,
                        OAuth2Constants.TPP_ACCOUNT_ACCESS_CONSENTS_PATTERN)
                .authorizeHttpRequests(authz -> authz.anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(jwt -> jwt.decoder(openbankingTokenJwtDecoder)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    @Bean
    public AuthorizationServerSettings authorizationServerSettings(JwtProperties jwtProperties) {
        return AuthorizationServerSettings.builder()
                .issuer(jwtProperties.getIssuer())
                .authorizationEndpoint(OAuth2Constants.AUTHORIZATION_ENDPOINT)
                .tokenEndpoint(OAuth2Constants.TOKEN_ENDPOINT)
                .jwkSetEndpoint(OAuth2Constants.JWK_SET_ENDPOINT)
                .tokenRevocationEndpoint(OAuth2Constants.TOKEN_REVOCATION_ENDPOINT)
                .tokenIntrospectionEndpoint(OAuth2Constants.TOKEN_INTROSPECTION_ENDPOINT)
                .deviceAuthorizationEndpoint(OAuth2Constants.DEVICE_AUTHORIZATION_ENDPOINT)
                .deviceVerificationEndpoint(OAuth2Constants.DEVICE_VERIFICATION_ENDPOINT)
                .pushedAuthorizationRequestEndpoint(OAuth2Constants.PUSHED_AUTHORIZATION_REQUEST_ENDPOINT)
                .oidcClientRegistrationEndpoint(OAuth2Constants.OIDC_CLIENT_REGISTRATION_ENDPOINT)
                .oidcUserInfoEndpoint(OAuth2Constants.OIDC_USER_INFO_ENDPOINT)
                .oidcLogoutEndpoint(OAuth2Constants.OIDC_LOGOUT_ENDPOINT)
                .build();
    }

    @Bean
    public RegisteredClientRepository registeredClientRepository(OAuth2ConsumerProperties oauth2Properties) {
        OAuth2ConsumerProperties.TppClient tppClient = oauth2Properties.getTppClient();
        RegisteredClient demoTpp = RegisteredClient.withId(tppClient.getId())
                .clientId(tppClient.getId())
                .clientSecret(PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(tppClient.getSecret()))
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri(tppClient.getRedirectUri())
                .scope(AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS)
                .scope(AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ)
                .clientSettings(ClientSettings.builder()
                        .requireProofKey(true)
                        .requireAuthorizationConsent(true)
                        .build())
                .tokenSettings(TokenSettings.builder()
                        .accessTokenTimeToLive(oauth2Properties.getAccessTokenTtl())
                        .refreshTokenTimeToLive(oauth2Properties.getRefreshTokenTtl())
                        .reuseRefreshTokens(false)
                        .build())
                .build();
        return new InMemoryRegisteredClientRepository(demoTpp);
    }

    @Bean
    public OAuth2AuthorizationService oAuth2AuthorizationService(JdbcOperations jdbcOperations,
            RegisteredClientRepository registeredClientRepository) {
        return new JdbcOAuth2AuthorizationService(jdbcOperations, registeredClientRepository);
    }

    @Bean
    public OAuth2AuthorizationConsentService oAuth2AuthorizationConsentService() {
        return new PerCeremonyAuthorizationConsentService();
    }

    @Bean
    public JWKSource<SecurityContext> jwkSource(ECKey jwtSigningKey, OAuth2ConsumerProperties oauth2Properties) {
        return new ImmutableJWKSet<>(new JWKSet(openBankingKey(jwtSigningKey, oauth2Properties)));
    }

    @Bean
    public OAuth2TokenCustomizer<JwtEncodingContext> openBankingTokenCustomizer(
            FineractClientProperties fineractClientProperties,
            PrincipalUserLookupPort principalUserLookup,
            ClientStandingChecker clientStandingChecker) {
        return new OAuth2AccessTokenCustomizer(fineractClientProperties, principalUserLookup, clientStandingChecker);
    }

    @Bean
    public OAuth2TokenGenerator<? extends OAuth2Token> openBankingTokenGenerator(
            JWKSource<SecurityContext> jwkSource,
            OAuth2TokenCustomizer<JwtEncodingContext> openBankingTokenCustomizer) {
        JwtGenerator jwtGenerator = new JwtGenerator(new NimbusJwtEncoder(jwkSource));
        jwtGenerator.setJwtCustomizer(openBankingTokenCustomizer);
        return new DelegatingOAuth2TokenGenerator(jwtGenerator, new OAuth2AccessTokenGenerator(),
                new OAuth2RefreshTokenGenerator());
    }

    @Bean
    public JwtDecoder openbankingTokenJwtDecoder(ECKey jwtSigningKey, OAuth2ConsumerProperties oauth2Properties,
            JwtProperties jwtProperties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withJwkSource(new ImmutableJWKSet<>(
                        new JWKSet(openBankingKey(jwtSigningKey, oauth2Properties).toPublicJWK())))
                .jwsAlgorithm(SignatureAlgorithm.ES256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer()),
                OAuth2AuthorizationServerConfig::requireOpenBankingPurpose));
        return decoder;
    }

    private static void configureConsentAndTokenEndpoints(OAuth2AuthorizationServerConfigurer authorizationServer,
            OAuth2ConsumerProperties oauth2Properties,
            OAuth2ConsentLookupPort consentLookup,
            OAuth2ConsentRecorderPort consentRecorder,
            OAuth2AuthorizationService oAuth2AuthorizationService,
            ApplicationEventPublisher eventPublisher) {
        OAuth2ConsentAuthorizeRequestValidator consentValidator =
                new OAuth2ConsentAuthorizeRequestValidator(consentLookup);
        OAuth2ConsentDecisionCustomizer consentDecisionCustomizer =
                new OAuth2ConsentDecisionCustomizer(consentRecorder, eventPublisher);
        authorizationServer
                .authorizationEndpoint(authorizationEndpoint -> authorizationEndpoint
                        .consentPage(oauth2Properties.getFrontendBaseUrl() + OAuth2Constants.CONSENT_PAGE_PATH)
                        .authenticationProviders(providers -> customizeAuthorizationProviders(providers,
                                consentValidator, consentDecisionCustomizer)))
                .tokenEndpoint(tokenEndpoint -> tokenEndpoint
                        .authenticationProviders(providers -> wrapRefreshTokenProvider(providers,
                                oAuth2AuthorizationService, consentLookup)));
    }

    private static void configureConsentPageCsrf(CsrfConfigurer<HttpSecurity> csrf) {
        csrf
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                .ignoringRequestMatchers(
                        PathPatternRequestMatcher.pathPattern(OAuth2Constants.TOKEN_ENDPOINT),
                        PathPatternRequestMatcher.pathPattern(OAuth2Constants.TOKEN_INTROSPECTION_ENDPOINT),
                        PathPatternRequestMatcher.pathPattern(OAuth2Constants.TOKEN_REVOCATION_ENDPOINT));
    }

    private static void customizeAuthorizationProviders(List<AuthenticationProvider> providers,
            OAuth2ConsentAuthorizeRequestValidator consentValidator,
            OAuth2ConsentDecisionCustomizer consentDecisionCustomizer) {
        for (AuthenticationProvider provider : providers) {
            if (provider instanceof OAuth2AuthorizationCodeRequestAuthenticationProvider codeRequestProvider) {
                codeRequestProvider.setAuthenticationValidator(
                        new OAuth2AuthorizationCodeRequestAuthenticationValidator().andThen(consentValidator));
            }
            if (provider instanceof OAuth2AuthorizationConsentAuthenticationProvider consentProvider) {
                consentProvider.setAuthorizationConsentCustomizer(consentDecisionCustomizer);
            }
        }
    }

    private static void wrapRefreshTokenProvider(List<AuthenticationProvider> providers,
            OAuth2AuthorizationService oAuth2AuthorizationService, OAuth2ConsentLookupPort consentLookup) {
        providers.replaceAll(provider -> provider instanceof OAuth2RefreshTokenAuthenticationProvider
                ? new OAuth2RefreshTokenConsentValidator(provider, oAuth2AuthorizationService,
                        consentLookup)
                : provider);
    }

    private static AuthenticationEntryPoint loginRedirectEntryPoint(OAuth2ConsumerProperties oauth2Properties) {
        return (request, response, authException) -> {
            String query = request.getQueryString();
            String returnUrl = query != null ? request.getRequestURI() + "?" + query : request.getRequestURI();
            response.sendRedirect(oauth2Properties.getFrontendBaseUrl() + OAuth2Constants.LOGIN_PAGE_PATH
                    + "?" + OAuth2Constants.RETURN_URL_PARAM + "="
                    + URLEncoder.encode(returnUrl, StandardCharsets.UTF_8));
        };
    }

    private static OAuth2TokenValidatorResult requireOpenBankingPurpose(Jwt jwt) {
        if (!OAuth2Constants.OPENBANKING_PURPOSE_VALUE.equals(jwt.getClaimAsString(JwtClaims.PURPOSE))) {
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_TOKEN, "not an open banking access token", null));
        }
        return OAuth2TokenValidatorResult.success();
    }

    private static ECKey openBankingKey(ECKey jwtSigningKey, OAuth2ConsumerProperties oauth2Properties) {
        return new ECKey.Builder(jwtSigningKey).keyID(oauth2Properties.getKid()).build();
    }

    private static final class CsrfCookieRenderingFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                FilterChain filterChain) throws ServletException, IOException {
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }

    // Deliberate no-op: consent is re-collected on every authorization process, never persisted.
    private static final class PerCeremonyAuthorizationConsentService implements OAuth2AuthorizationConsentService {

        @Override
        public void save(OAuth2AuthorizationConsent authorizationConsent) {
        }

        @Override
        public void remove(OAuth2AuthorizationConsent authorizationConsent) {
        }

        @Override
        public OAuth2AuthorizationConsent findById(String registeredClientId, String principalName) {
            return null;
        }
    }
}
