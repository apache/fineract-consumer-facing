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
package org.apache.fineract.consumer.scheduledtransfers.query.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferQueryData;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunHistoryQuery;
import org.apache.fineract.consumer.scheduledtransfers.query.data.ScheduledTransferRunQueryResponse;
import org.apache.fineract.consumer.scheduledtransfers.query.service.ScheduledTransfersQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class ScheduledTransfersQueryControllerTest {

    private static final UUID SUBJECT = UUID.fromString("3f2c8a1e-0000-4000-8000-000000000001");
    private static final UUID PUBLIC_ID = UUID.fromString("7a1b2c3d-0000-4000-8000-000000000001");

    @Mock
    private ScheduledTransfersQueryService service;

    @InjectMocks
    private ScheduledTransfersQueryController controller;

    @Test
    void listReturnsTheServicesScheduledTransfers() {
        Jwt jwt = jwt();
        List<ScheduledTransferQueryData> listed =
                List.of(ScheduledTransferQueryData.builder().publicId(PUBLIC_ID).build());
        when(service.listScheduledTransfers(jwt)).thenReturn(listed);

        assertThat(controller.listScheduledTransfers(jwt)).isEqualTo(listed);
    }

    @Test
    void runHistoryThreadsThePublicIdAndPageIntoTheQuery() {
        when(service.listRuns(any(), any())).thenReturn(ScheduledTransferRunQueryResponse.builder().build());

        controller.listScheduledTransferRuns(jwt(), PUBLIC_ID, 2, 5, null, null);

        assertThat(capturedQuery()).isEqualTo(
                ScheduledTransferRunHistoryQuery.builder().publicId(PUBLIC_ID).page(2).size(5).build());
    }

    @Test
    void runHistoryThreadsTheDateWindowIntoTheQuery() {
        LocalDate from = LocalDate.of(2029, 1, 15);
        LocalDate to = LocalDate.of(2030, 1, 14);
        when(service.listRuns(any(), any())).thenReturn(ScheduledTransferRunQueryResponse.builder().build());

        controller.listScheduledTransferRuns(jwt(), PUBLIC_ID, 0, 20, from, to);

        assertThat(capturedQuery()).isEqualTo(ScheduledTransferRunHistoryQuery.builder()
                .publicId(PUBLIC_ID)
                .page(0)
                .size(20)
                .fromDate(from)
                .toDate(to)
                .build());
    }

    private ScheduledTransferRunHistoryQuery capturedQuery() {
        ArgumentCaptor<ScheduledTransferRunHistoryQuery> query =
                ArgumentCaptor.forClass(ScheduledTransferRunHistoryQuery.class);
        verify(service).listRuns(any(), query.capture());
        return query.getValue();
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject(SUBJECT.toString())
                .claim("scope", "read")
                .build();
    }
}
