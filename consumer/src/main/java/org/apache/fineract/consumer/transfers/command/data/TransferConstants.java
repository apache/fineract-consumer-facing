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

package org.apache.fineract.consumer.transfers.command.data;

public final class TransferConstants {

    private TransferConstants() {
    }

    public static final String ENDPOINT = "/api/v1/transfers";

    public static final String SAVINGS_TYPE_CODE = "2";
    public static final String LOAN_TYPE_CODE = "1";

    public static final String SAVINGS_TYPE_NAME = "savings";
    public static final String LOAN_TYPE_NAME = "loan";

    public static final String LOCALE = "en";
    public static final String DATE_FORMAT = "yyyy-MM-dd";

    public static final String DEFAULT_DESCRIPTION = "Consumer initiated transfer";
}
