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
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.fineract.consumer.infrastructure.configs;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.fineract.consumer.authentication.command.data.AuthenticationConstants;
import org.apache.fineract.consumer.infrastructure.access.filter.DeviceFingerprintFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.util.WebUtils;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String REGISTRATION_PATH_PREFIX = "/api/v1/registration/";
    private static final String[] PUBLIC_POST_PATHS = {
            "/api/v1/authentication/login",
            "/api/v1/authentication/2fa",
            "/api/v1/authentication/refresh",
            "/api/v1/user/password/forgot",
            "/api/v1/user/password/reset"
    };

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            @Qualifier("accessTokenJwtDecoder") JwtDecoder accessTokenJwtDecoder,
            ObjectMapper objectMapper) throws Exception {
        return http
                .authorizeHttpRequests(authz -> authz
                        .requestMatchers(
                                "/v3/api-docs/**",
                                "/v3/api-docs.yaml",
                                "/swagger-ui/**",
                                "/swagger-ui.html",
                                "/actuator/health",
                                REGISTRATION_PATH_PREFIX + "**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, PUBLIC_POST_PATHS).permitAll()
                        .requestMatchers("/api/v1/savings/**").authenticated()
                        .requestMatchers("/api/v1/loans/**").authenticated()
                        .requestMatchers("/api/v1/transfers/**").authenticated()
                        .requestMatchers("/api/v1/beneficiaries/**").authenticated()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(SecurityConfig::resolveAccessTokenCookie)
                        .jwt(jwt -> jwt.decoder(accessTokenJwtDecoder)))
                .addFilterAfter(new DeviceFingerprintFilter(objectMapper), BearerTokenAuthenticationFilter.class)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .csrf(csrf -> csrf.disable())
                .build();
    }

    private static String resolveAccessTokenCookie(HttpServletRequest request) {
        if (isPublicPath(request.getRequestURI())) {
            return null;
        }
        Cookie cookie = WebUtils.getCookie(request, AuthenticationConstants.ACCESS_TOKEN_COOKIE_NAME);
        return cookie != null ? cookie.getValue() : null;
    }

    private static boolean isPublicPath(String path) {
        if (path.startsWith(REGISTRATION_PATH_PREFIX)) {
            return true;
        }
        for (String publicPath : PUBLIC_POST_PATHS) {
            if (publicPath.equals(path)) {
                return true;
            }
        }
        return false;
    }
}
