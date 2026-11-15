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

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.Future;

/**
 * Holds a lock on a beneficiary row from its own connection to the BFF's database, the way a beneficiary delete or a
 * scheduled transfer confirm holds it, so a scenario can put a BFF request behind it and choose when it goes on.
 * Postgres reports who waits for whom ({@code pg_blocking_pids}), so the scenario knows the request reached the lock.
 */
public final class BeneficiaryRowLock implements AutoCloseable {

    private static final String JDBC_URL = System.getenv()
            .getOrDefault("BFF_DB_URL", "jdbc:postgresql://localhost:5432/consumerapp");
    private static final String JDBC_USER = System.getenv().getOrDefault("BFF_DB_USER", "consumerapp");
    private static final String JDBC_PASSWORD = System.getenv().getOrDefault("BFF_DB_PASSWORD", "password");
    private static final String FOR_UPDATE_SQL = "SELECT id FROM beneficiaries WHERE public_id = ? FOR UPDATE";
    private static final String FOR_SHARE_SQL = "SELECT id FROM beneficiaries WHERE public_id = ? FOR SHARE";
    private static final String BACKEND_PID_SQL = "SELECT pg_backend_pid()";
    private static final String WAITER_SQL =
            "SELECT EXISTS (SELECT 1 FROM pg_stat_activity WHERE ? = ANY (pg_blocking_pids(pid)))";
    private static final String DEACTIVATE_SQL =
            "UPDATE beneficiaries SET is_active = false, updated_at = now() WHERE public_id = ?";
    private static final Duration WAIT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    private final Connection connection;
    private final UUID publicId;
    private final int backendPid;
    private boolean held;

    private BeneficiaryRowLock(UUID publicId, String lockSql) {
        this.publicId = publicId;
        try {
            connection = connect();
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Failed to connect to the BFF database at " + JDBC_URL + "; is the Docker Compose stack up?", e);
        }
        try {
            connection.setAutoCommit(false);
            backendPid = queryInt(BACKEND_PID_SQL);
            try (PreparedStatement statement = connection.prepareStatement(lockSql)) {
                statement.setObject(1, publicId);
                try (ResultSet row = statement.executeQuery()) {
                    if (!row.next()) {
                        throw new IllegalStateException("no beneficiary " + publicId + " to lock");
                    }
                }
            }
            held = true;
        } catch (SQLException | RuntimeException e) {
            closeQuietly();
            throw e instanceof RuntimeException runtime
                    ? runtime
                    : new IllegalStateException("Failed to lock beneficiary " + publicId, e);
        }
    }

    /** Locks the row as a beneficiary delete, or the confirm of a limit change, does. */
    public static BeneficiaryRowLock forUpdate(UUID beneficiaryPublicId) {
        return new BeneficiaryRowLock(beneficiaryPublicId, FOR_UPDATE_SQL);
    }

    /** Locks the row as a scheduled transfer confirm's re-check of its beneficiary does. */
    public static BeneficiaryRowLock forShare(UUID beneficiaryPublicId) {
        return new BeneficiaryRowLock(beneficiaryPublicId, FOR_SHARE_SQL);
    }

    /** Waits until another session waits for this lock. Fails if {@code request} finishes first: it never waited. */
    public void awaitWaiter(Future<?> request) {
        long deadline = System.nanoTime() + WAIT_TIMEOUT.toNanos();
        try (Connection observer = connect(); PreparedStatement statement = observer.prepareStatement(WAITER_SQL)) {
            statement.setInt(1, backendPid);
            while (System.nanoTime() < deadline) {
                if (request.isDone()) {
                    throw new AssertionError("the request finished without waiting for the beneficiary lock");
                }
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next() && result.getBoolean(1)) {
                        return;
                    }
                }
                sleep();
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to read lock waits from the BFF database", e);
        }
        throw new AssertionError(
                "no request waited for the beneficiary lock within " + WAIT_TIMEOUT.toMillis() + "ms");
    }

    /** Marks the beneficiary deleted, as the BFF's delete does, and commits, which releases the lock. */
    public void deactivateAndCommit() {
        try (PreparedStatement statement = connection.prepareStatement(DEACTIVATE_SQL)) {
            statement.setObject(1, publicId);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("no beneficiary " + publicId + " to mark deleted");
            }
            connection.commit();
            held = false;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to mark beneficiary " + publicId + " deleted", e);
        }
    }

    /** Commits without changing anything, which releases the lock. */
    public void commit() {
        try {
            connection.commit();
            held = false;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to release the lock on beneficiary " + publicId, e);
        }
    }

    /** Releases the lock if it is still held, changing nothing, and closes the connection. */
    @Override
    public void close() {
        closeQuietly();
    }

    private void closeQuietly() {
        try {
            if (held) {
                connection.rollback();
                held = false;
            }
            connection.close();
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to close the lock on beneficiary " + publicId, e);
        }
    }

    private int queryInt(String sql) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) {
                throw new IllegalStateException(sql + " returned no row");
            }
            return result.getInt(1);
        }
    }

    private static Connection connect() throws SQLException {
        return DriverManager.getConnection(JDBC_URL, JDBC_USER, JDBC_PASSWORD);
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for a request to reach the beneficiary lock", e);
        }
    }
}
