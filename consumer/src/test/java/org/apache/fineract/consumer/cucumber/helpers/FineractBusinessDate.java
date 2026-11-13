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

package org.apache.fineract.consumer.cucumber.helpers;

import java.time.LocalDate;
import java.time.ZoneId;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.BusinessDateManagementApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.GlobalConfigurationApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.BusinessDateResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.BusinessDateUpdateRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutGlobalConfigurationsRequest;

/**
 * Moves the tenant's business date and puts it back. The first move turns on Fineract's {@code enable-business-date}
 * setting and remembers how things were; {@link #restore()} puts both back. Fineract keeps the stored business date
 * while the setting is off, and a fresh tenant has none until one is set, so the date is always put back, to today if
 * there was none.
 */
public class FineractBusinessDate {

    private static final String BUSINESS_DATE_CONFIG = "enable-business-date";
    private static final String LOCALE = "en";
    private static final String ISO_DATE_FORMAT = "yyyy-MM-dd";

    private static final GlobalConfigurationApi CONFIGURATIONS =
            FineractSeeder.buildFeignClient(GlobalConfigurationApi.class);
    private static final BusinessDateManagementApi BUSINESS_DATES =
            FineractSeeder.buildFeignClient(BusinessDateManagementApi.class);

    private Boolean wasEnabled;
    private LocalDate restoreTo;

    public void moveTo(LocalDate businessDate) {
        if (wasEnabled == null) {
            boolean enabled = Boolean.TRUE.equals(CONFIGURATIONS.retrieveOneByName(BUSINESS_DATE_CONFIG).getEnabled());
            LocalDate previousBusinessDate = currentBusinessDate();
            restoreTo = previousBusinessDate != null ? previousBusinessDate : LocalDate.now(ZoneId.systemDefault());
            wasEnabled = enabled;
            setBusinessDateEnabled(true);
        }
        setBusinessDate(businessDate);
    }

    public boolean isMoved() {
        return wasEnabled != null;
    }

    public void restore() {
        if (wasEnabled == null) {
            return;
        }
        try {
            setBusinessDate(restoreTo);
        } finally {
            setBusinessDateEnabled(wasEnabled);
            wasEnabled = null;
            restoreTo = null;
        }
    }

    private static LocalDate currentBusinessDate() {
        return BUSINESS_DATES.getBusinessDates().stream()
                .filter(date -> date.getType() == BusinessDateResponse.TypeEnum.BUSINESS_DATE)
                .map(BusinessDateResponse::getDate)
                .findFirst()
                .orElse(null);
    }

    private static void setBusinessDateEnabled(boolean enabled) {
        CONFIGURATIONS.updateConfigurationByName(
                BUSINESS_DATE_CONFIG, new PutGlobalConfigurationsRequest().enabled(enabled));
    }

    private static void setBusinessDate(LocalDate date) {
        BUSINESS_DATES.updateBusinessDate(new BusinessDateUpdateRequest()
                .type(BusinessDateUpdateRequest.TypeEnum.BUSINESS_DATE)
                .date(date.toString())
                .dateFormat(ISO_DATE_FORMAT)
                .locale(LOCALE));
    }
}
