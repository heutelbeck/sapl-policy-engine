/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.pdp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.condition.DisabledOnOs;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.junit.jupiter.api.condition.OS;
import org.springframework.r2dbc.core.DatabaseClient;
import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.attributes.broker.repository.PostgresAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.val;

@Testcontainers
@DisabledOnOs(OS.WINDOWS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("Functional correctness of policy evaluation on attribute changes (PostgreSQL)")
class PostgresAttributeChangeEvaluationIT {
    private static final String        PDP_ID = "default";
    private static final RepositoryKey KEY    = new RepositoryKey(Value.of("alice"), "company.department", List.of(),
            PDP_ID);

    @Container
    static PostgreSQLContainer postgres = createPostgresContainer();

    private static PostgreSQLContainer createPostgresContainer() {
        return new PostgreSQLContainer("postgres:18.6");
    }

    private PostgresAttributeRepository observingRepo;
    private PostgresAttributeRepository apiRepo;
    private PDPComponents               components;

    @BeforeAll
    void setUp() {
        observingRepo = newRepository();
        apiRepo       = newRepository();
        components    = PolicyDecisionPointBuilder.withDefaults()
                .withPolicy("policy \"p\" permit subject.<company.department> == \"IT\";")
                .withCombiningAlgorithm(new CombiningAlgorithm(CombiningAlgorithm.VotingMode.PRIORITY_DENY,
                        CombiningAlgorithm.DefaultDecision.DENY, CombiningAlgorithm.ErrorHandling.ABSTAIN))
                .withRepository(observingRepo).build();
    }

    @AfterAll
    void tearDown() {
        components.close();
        observingRepo.close();
        apiRepo.close();
        postgres.close();
    }

    @BeforeEach
    void resetState() {
        // Reset so that no attribute exists
        apiRepo.remove(KEY);
    }

    @RepeatedTest(100)
    @DisplayName("publish, delete, republish, and update each trigger the correct decision")
    void whenAttributeChangeThenDecisionTracksEachChangeCorrectly() {
        val subscription = AuthorizationSubscription.of("alice", "read", "document");

        // Step 1: No attribute --> DENY
        awaitDecision(subscription, Decision.DENY);

        // Step 2: Publish attribute IT --> PERMIT
        apiRepo.publish(KEY, Value.of("IT"));
        awaitDecision(subscription, Decision.PERMIT);

        // Step 3: Delete the attribute --> DENY
        apiRepo.remove(KEY);
        awaitDecision(subscription, Decision.DENY);

        // Step 4: Publish again --> PERMIT
        apiRepo.publish(KEY, Value.of("IT"));
        awaitDecision(subscription, Decision.PERMIT);

        // Step 5: Update the attribute to HR without a delete --> DENY
        apiRepo.publish(KEY, Value.of("HR"));
        awaitDecision(subscription, Decision.DENY);
    }

    private void awaitDecision(AuthorizationSubscription subscription, Decision expected) {
        await().atMost(Duration.ofSeconds(5)).untilAsserted(
                () -> assertThat(components.pdp().decideOnce(subscription, PDP_ID).decision()).isEqualTo(expected));
    }

    private PostgresAttributeRepository newRepository() {
        val config  = PostgresqlConnectionConfiguration.builder().host(postgres.getHost())
                .port(postgres.getMappedPort(5432)).database(postgres.getDatabaseName())
                .username(postgres.getUsername()).password(postgres.getPassword()).build();
        val factory = new PostgresqlConnectionFactory(config);
        return new PostgresAttributeRepository(DatabaseClient.create(factory), factory, PDP_ID, "attributes");
    }
}
