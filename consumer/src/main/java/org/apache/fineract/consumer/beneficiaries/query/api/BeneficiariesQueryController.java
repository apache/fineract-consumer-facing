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

package org.apache.fineract.consumer.beneficiaries.query.api;

import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryQueryData;
import org.apache.fineract.consumer.beneficiaries.query.data.BeneficiaryTemplateQueryData;
import org.apache.fineract.consumer.beneficiaries.query.service.BeneficiariesQueryService;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/v1/beneficiaries", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor
public class BeneficiariesQueryController {

    private final BeneficiariesQueryService beneficiariesQueryService;

    @Operation(operationId = "listBeneficiaries")
    @GetMapping
    public List<BeneficiaryQueryData> listBeneficiaries(@AuthenticationPrincipal Jwt jwt) {
        return beneficiariesQueryService.listBeneficiaries(jwt);
    }

    @Operation(operationId = "getBeneficiaryTemplate")
    @GetMapping("/template")
    public BeneficiaryTemplateQueryData getTemplate(@AuthenticationPrincipal Jwt jwt) {
        return beneficiariesQueryService.getTemplate(jwt);
    }
}
