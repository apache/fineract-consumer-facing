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

package org.apache.fineract.consumer.cucumber.hooks;

import io.cucumber.java.BeforeAll;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.UUID;
import org.apache.fineract.consumer.cucumber.clients.OpenBankingClient;
import org.apache.fineract.consumer.infrastructure.access.data.AuthenticationConstants;

public final class RegisteredTppSeed {

    private static final String JDBC_URL = System.getenv()
            .getOrDefault("BFF_DB_URL", "jdbc:postgresql://localhost:5432/consumerapp");
    private static final String JDBC_USER = System.getenv().getOrDefault("BFF_DB_USER", "consumerapp");
    private static final String JDBC_PASSWORD = System.getenv().getOrDefault("BFF_DB_PASSWORD", "password");

    private static final String CLIENT_NAME = "Demo TPP";
    private static final String SCOPE_SEPARATOR = ",";
    private static final String SCOPES = AuthenticationConstants.SCOPE_OPENBANKING_CONSENTS + SCOPE_SEPARATOR
            + AuthenticationConstants.SCOPE_OPENBANKING_ACCOUNTS_READ;
    private static final String CLIENT_SECRET_HASH =
            "{bcrypt}$2y$10$piiChDy1OaMQggEym1p4J.0npIQfqpgeUChoB8j8oOt/oIX0P19Q.";

    private static final String UPSERT_SQL = """
            INSERT INTO registered_tpps (
                id, client_id, client_secret_hash, client_name, redirect_uri, scopes,
                status, onboarded_at, status_updated_at)
            VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now())
            ON CONFLICT (client_id) DO UPDATE SET
                client_secret_hash = EXCLUDED.client_secret_hash,
                client_name        = EXCLUDED.client_name,
                redirect_uri       = EXCLUDED.redirect_uri,
                scopes             = EXCLUDED.scopes,
                status             = EXCLUDED.status,
                status_updated_at  = now()
            """;

    @BeforeAll
    public static void seedRegisteredTpp() {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
             PreparedStatement statement = connection.prepareStatement(UPSERT_SQL)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setString(2, OpenBankingClient.TPP_CLIENT_ID);
            statement.setString(3, CLIENT_SECRET_HASH);
            statement.setString(4, CLIENT_NAME);
            statement.setString(5, OpenBankingClient.REDIRECT_URI);
            statement.setString(6, SCOPES);
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to seed registered_tpps at " + JDBC_URL + " — is the Docker Compose stack up?", e);
        }
    }
}
