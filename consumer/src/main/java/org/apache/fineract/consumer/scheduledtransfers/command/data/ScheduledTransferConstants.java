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

package org.apache.fineract.consumer.scheduledtransfers.command.data;

public final class ScheduledTransferConstants {

    private ScheduledTransferConstants() {
    }

    public static final String CREATE_ACTION_ID = "scheduled-transfers:create";

    public static final String LOCALE = "en";
    public static final String DATE_FORMAT = "yyyy-MM-dd";
    public static final String MONTH_DAY_FORMAT = "dd MMMM";
    /** Every month has this day, so a monthly transfer starting on or before it never skips a month. */
    public static final int LAST_MONTHLY_START_DAY = 28;

    /** The Fineract instruction name; Fineract requires it to be unique and prefixes it to each run's description. */
    public static final String INSTRUCTION_NAME_PREFIX = "Consumer recurring transfer ";

    // Fineract standing instruction codes (StandingInstructionDataValidator).
    public static final String SAVINGS_ACCOUNT_TYPE = "2";
    public static final String ACCOUNT_TRANSFER_TYPE = "1";
    public static final String ACTIVE_STATUS = "1";
    public static final String FIXED_INSTRUCTION_TYPE = "1";
    public static final String PERIODIC_RECURRENCE_TYPE = "1";
    public static final String WEEKS_FREQUENCY = "1";
    public static final String MONTHS_FREQUENCY = "2";
    public static final String EVERY_PERIOD = "1";
    /** LOW, so instructions the bank created on the same account, such as loan repayments (MEDIUM), run first. */
    public static final String LOW_PRIORITY = "4";
}
