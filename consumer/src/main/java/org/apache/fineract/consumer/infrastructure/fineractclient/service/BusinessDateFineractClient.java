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

package org.apache.fineract.consumer.infrastructure.fineractclient.service;

import java.time.LocalDate;

public interface BusinessDateFineractClient {

    /**
     * The date Fineract treats as today: the tenant's business date when business dates are enabled, otherwise the
     * current date in the tenant's time zone. Fineract's jobs run on this date, so it can differ from the BFF server's
     * date by a day around midnight or by any amount while business dates are enabled. Returns {@code null} when
     * Fineract does not report one.
     */
    LocalDate today();
}
