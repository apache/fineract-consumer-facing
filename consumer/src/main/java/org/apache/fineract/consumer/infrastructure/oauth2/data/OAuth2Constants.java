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

package org.apache.fineract.consumer.infrastructure.oauth2.data;

public final class OAuth2Constants {

    private OAuth2Constants() {
    }

    public static final int AUTHORIZATION_SERVER_CHAIN_ORDER = 1;
    public static final int TPP_RESOURCE_CHAIN_ORDER = 2;
    public static final int LOGIN_CHAIN_ORDER = 3;

    public static final String OPENBANKING_PURPOSE_VALUE = "openbanking";
    public static final String CONSENT_ID = "consent_id";

    public static final String TPP_ACCOUNTS_PATTERN = "/api/v1/openbanking/accounts/**";
    public static final String TPP_ACCOUNT_ACCESS_CONSENTS_PATTERN = "/api/v1/openbanking/account-access-consents/**";

    public static final String AUTHORIZATION_ENDPOINT = "/api/v1/oauth2/authorize";
    public static final String TOKEN_ENDPOINT = "/api/v1/oauth2/token";
    public static final String JWK_SET_ENDPOINT = "/api/v1/oauth2/jwks";
    public static final String TOKEN_REVOCATION_ENDPOINT = "/api/v1/oauth2/revoke";
    public static final String TOKEN_INTROSPECTION_ENDPOINT = "/api/v1/oauth2/introspect";
    public static final String DEVICE_AUTHORIZATION_ENDPOINT = "/api/v1/oauth2/device_authorization";
    public static final String DEVICE_VERIFICATION_ENDPOINT = "/api/v1/oauth2/device_verification";
    public static final String PUSHED_AUTHORIZATION_REQUEST_ENDPOINT = "/api/v1/oauth2/par";
    public static final String OIDC_CLIENT_REGISTRATION_ENDPOINT = "/api/v1/oauth2/connect/register";
    public static final String OIDC_USER_INFO_ENDPOINT = "/api/v1/oauth2/userinfo";
    public static final String OIDC_LOGOUT_ENDPOINT = "/api/v1/oauth2/connect/logout";

    public static final String LOGIN_PAGE_PATH = "/login";
    public static final String CONSENT_PAGE_PATH = "/consent";
    public static final String RETURN_URL_PARAM = "returnUrl";
}
