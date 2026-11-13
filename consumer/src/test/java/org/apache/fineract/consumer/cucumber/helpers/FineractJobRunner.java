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
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.api.SchedulerJobApi;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.ExecuteJobRequest;
import org.apache.fineract.consumer.infrastructure.fineractclient.generated.model.GetJobsResponse;

/**
 * Runs Fineract's standing instruction job as if it were a given day. Fineract runs an instruction only once the
 * tenant's business date reaches its start date, and the BFF only accepts start dates after today, so a scenario that
 * needs a run moves the business date forward, runs the job, and puts both the business date and the setting back.
 */
public class FineractJobRunner {

    private static final String STANDING_INSTRUCTION_JOB = "STI_EXEC";
    private static final String EXECUTE_COMMAND = "executeJob";
    private static final long JOB_TIMEOUT_MILLIS = 60_000L;
    private static final long POLL_INTERVAL_MILLIS = 500L;

    private static final SchedulerJobApi JOBS = FineractSeeder.buildFeignClient(SchedulerJobApi.class);

    public void runStandingInstructionsDueOn(LocalDate businessDate) {
        FineractBusinessDate fineractBusinessDate = new FineractBusinessDate();
        try {
            fineractBusinessDate.moveTo(businessDate);
            runJob();
        } finally {
            fineractBusinessDate.restore();
        }
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
