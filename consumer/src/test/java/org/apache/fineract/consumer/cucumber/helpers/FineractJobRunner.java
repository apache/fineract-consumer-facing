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
package org.apache.fineract.consumer.cucumber.helpers;

import java.time.LocalDate;
import java.time.ZoneId;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.BusinessDateManagementApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.GlobalConfigurationApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SchedulerJobApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.BusinessDateResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.BusinessDateUpdateRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.ExecuteJobRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetJobsResponse;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.PutGlobalConfigurationsRequest;

/**
 * Runs Fineract's standing instruction job as if it were a given day. Fineract runs an instruction only once the
 * tenant's business date reaches its start date, and the BFF only accepts start dates after today, so a scenario that
 * needs a run moves the business date forward, runs the job, and puts both the business date and the setting back.
 */
public class FineractJobRunner {

    private static final String BUSINESS_DATE_CONFIG = "enable-business-date";
    private static final String STANDING_INSTRUCTION_JOB = "STI_EXEC";
    private static final String EXECUTE_COMMAND = "executeJob";
    private static final String LOCALE = "en";
    private static final String ISO_DATE_FORMAT = "yyyy-MM-dd";
    private static final long JOB_TIMEOUT_MILLIS = 60_000L;
    private static final long POLL_INTERVAL_MILLIS = 500L;

    private static final GlobalConfigurationApi CONFIGURATIONS =
            FineractSeeder.buildFeignClient(GlobalConfigurationApi.class);
    private static final BusinessDateManagementApi BUSINESS_DATES =
            FineractSeeder.buildFeignClient(BusinessDateManagementApi.class);
    private static final SchedulerJobApi JOBS = FineractSeeder.buildFeignClient(SchedulerJobApi.class);

    public void runStandingInstructionsDueOn(LocalDate businessDate) {
        boolean wasEnabled = Boolean.TRUE.equals(CONFIGURATIONS.retrieveOneByName(BUSINESS_DATE_CONFIG).getEnabled());
        // Fineract keeps the stored business date while the setting is off, and a fresh tenant has none until one is
        // set. Either way the date written below stays behind, so it is always put back, to today if there was none.
        LocalDate previousBusinessDate = currentBusinessDate();
        LocalDate restoreTo =
                previousBusinessDate != null ? previousBusinessDate : LocalDate.now(ZoneId.systemDefault());
        setBusinessDateEnabled(true);
        try {
            setBusinessDate(businessDate);
            runJob();
        } finally {
            setBusinessDate(restoreTo);
            setBusinessDateEnabled(wasEnabled);
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

    /** Fineract runs the job in the background; it has finished once its run count has moved on and it is idle. */
    private static void runJob() {
        long runsBefore = completedRuns(JOBS.retrieveByShortName(STANDING_INSTRUCTION_JOB));
        JOBS.executeJobByShortName(STANDING_INSTRUCTION_JOB, EXECUTE_COMMAND, new ExecuteJobRequest());
        long deadline = System.currentTimeMillis() + JOB_TIMEOUT_MILLIS;
        while (System.currentTimeMillis() < deadline) {
            sleep();
            GetJobsResponse job = JOBS.retrieveByShortName(STANDING_INSTRUCTION_JOB);
            if (!Boolean.TRUE.equals(job.getCurrentlyRunning()) && completedRuns(job) > runsBefore) {
                return;
            }
        }
        throw new IllegalStateException("Fineract's " + STANDING_INSTRUCTION_JOB + " job did not finish within "
                + JOB_TIMEOUT_MILLIS + " ms");
    }

    private static long completedRuns(GetJobsResponse job) {
        if (job.getLastRunHistory() == null || job.getLastRunHistory().getVersion() == null) {
            return 0L;
        }
        return job.getLastRunHistory().getVersion();
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for Fineract's standing instruction job", e);
        }
    }
}
