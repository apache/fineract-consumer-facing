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

package org.apache.fineract.consumer.user.query.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.user.query.data.UserChargesQuery;
import org.apache.fineract.consumer.user.query.data.UserChargesQueryResponse;
import org.apache.fineract.consumer.user.query.data.UserImageQueryData;
import org.apache.fineract.consumer.user.query.data.UserObligeeQueryData;
import org.apache.fineract.consumer.user.query.data.UserProfileQueryData;
import org.apache.fineract.consumer.user.query.service.UserQueryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/user", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class UserQueryController {

    private final UserQueryService userQueryService;

    @Operation(operationId = "getUserProfile")
    @GetMapping("/profile")
    public UserProfileQueryData getProfile(@AuthenticationPrincipal Jwt jwt) {
        return userQueryService.getProfile(jwt);
    }

    @Operation(operationId = "getUserCharges")
    @GetMapping("/charges")
    public UserChargesQueryResponse getCharges(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "20") Integer size) {
        UserChargesQuery query = UserChargesQuery.builder()
                .status(status)
                .page(page)
                .size(size)
                .build();
        return userQueryService.getCharges(jwt, query);
    }

    @Operation(operationId = "getUserImage")
    @GetMapping("/image")
    public UserImageQueryData getImage(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) Integer maxWidth,
            @RequestParam(required = false) Integer maxHeight) {
        return userQueryService.getImage(jwt, maxWidth, maxHeight);
    }

    @Operation(operationId = "getUserObligees")
    @GetMapping("/obligees")
    public List<UserObligeeQueryData> getObligees(@AuthenticationPrincipal Jwt jwt) {
        return userQueryService.getObligees(jwt);
    }
}
