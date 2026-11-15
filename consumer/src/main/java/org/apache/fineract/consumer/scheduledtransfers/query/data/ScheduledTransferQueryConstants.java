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
package org.apache.fineract.consumer.scheduledtransfers.query.data;

public final class ScheduledTransferQueryConstants {

    private ScheduledTransferQueryConstants() {
    }

    /** The name the command side gives every instruction it creates, followed by the scheduled transfer's publicId. */
    public static final String INSTRUCTION_NAME_PREFIX = "Consumer recurring transfer ";

    public static final int MAX_PAGE_SIZE = 100;

    // Fineract standing instruction codes (StandingInstructionStatus, PeriodFrequencyType).
    public static final int ACTIVE_STATUS_ID = 1;
    public static final int WEEKS_FREQUENCY_ID = 1;
    public static final int MONTHS_FREQUENCY_ID = 2;

    // Fineract run statuses (StandingInstructionHistoryStatus). "partial" only occurs when allowPartialTransfer is
    // true, which the BFF never sets.
    public static final String RUN_STATUS_SUCCESS = "success";
    public static final String RUN_STATUS_FAILED = "failed";
}
