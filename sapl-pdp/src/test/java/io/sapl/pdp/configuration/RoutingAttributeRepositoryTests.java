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
package io.sapl.pdp.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.shaded.org.awaitility.Awaitility;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import lombok.val;

class RoutingAttributeRepositoryTests {
    @Test
    @DisplayName("An observer with an unknown configId triggers an error and queues the observation")
    void whenObserveCalledForUnknownConfigThenErrorTriggeredAndQueued() {
        AttributeAccessContext context = new AttributeAccessContext(Value.ofObject(Map.of()), Value.ofObject(Map.of()),
                Value.ofObject(Map.of()));

        AttributeFinderInvocation invocation = new AttributeFinderInvocation("pdp-1", "unknown-config", "sapl.test",
                List.of(), Duration.ofSeconds(1), Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, false, context);

        try (var router = new RoutingAttributeRepository()) {
            List<Value> received     = new ArrayList<>();
            var         registration = router.observe(invocation, received::add);

            assertThat(received).hasSize(1);
            registration.close();
        }
    }

    @Test
    @DisplayName("canPrepare method accepts a configuration with an attributeRepository extension (fallback)")
    void whenExtensionIsMissingThenCanPrepareAccepts() {
        val configuration = new PDPConfiguration("tenant-1", "config-1", CombiningAlgorithm.DEFAULT,
                List.of("policy \"p\" permit true;"), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
        try (var router = new RoutingAttributeRepository()) {
            assertThat(router.canPrepare(configuration)).isTrue();
        }
    }

    @Test
    @DisplayName("canPrepare method merges cleartext and secrets config into a valid combination")
    void whenExtensionIsSplitThenCanPrepareAccepts() {
        val config = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                .put("port", Value.of(5432)).put("database", Value.of("sapl")).build();

        val secrets = ObjectValue.builder().put("username", Value.of("sapl")).put("password", Value.of("secret"))
                .build();

        val configuration = new PDPConfiguration("tenant-1", "config-1", CombiningAlgorithm.DEFAULT,
                List.of("policy \"p\" permit true;"), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT))
                .withExtensions(Map.of("attributeRepository", config), Map.of("attributeRepository", secrets),
                        Set.of());

        try (var router = new RoutingAttributeRepository()) {
            assertThat(router.canPrepare(configuration)).isTrue();
        }
    }

    @Test
    @DisplayName("canPrepare method rejects an existing config that is invalid but existing for the attribute repository")
    void whenExtensionExistsButIsInvalidThenCanPrepareRejects() {
        val config = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                .put("port", Value.of(5432)).put("database", Value.of("sapl")).build();

        val configuration = new PDPConfiguration("tenant-1", "config-1", CombiningAlgorithm.DEFAULT,
                List.of("policy \"p\" permit true;"), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT))
                .withExtensions(Map.of("attributeRepository", config), Map.of(), Set.of());

        try (var router = new RoutingAttributeRepository()) {
            assertThat(router.canPrepare(configuration)).isFalse();
        }
    }

    @Nested
    @DisplayName("when an observation arrives before the repository is built")
    class WhenObservationArrivesBeforeBuild {
        private final String                     pdpId    = "tenant-1";
        private final String                     configId = "config-1";
        private final RoutingAttributeRepository router   = new RoutingAttributeRepository();
        private final List<Value>                received = new CopyOnWriteArrayList<>();

        @Test
        @DisplayName("then the queued observation is replayed after the build is finished")
        void thenQueuedObservationIsReplayedAfterBuild() {
            router.observe(invocation(pdpId, configId), received::add);
            router.buildOrRoute(pdpId, configForInMemory(pdpId, configId));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 2);
            assertThat(received.getLast()).isEqualTo(Value.UNDEFINED);
        }

        @Test
        @DisplayName("then a closed queued registration is not replayed")
        void thenClosedQueueRegistrationIsNotReplayed() {
            val closedObserver = new CopyOnWriteArrayList<Value>();

            router.observe(invocation(pdpId, configId), closedObserver::add).close();
            router.observe(invocation(pdpId, configId), received::add);
            router.buildOrRoute(pdpId, configForInMemory(pdpId, configId));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 2);
            assertThat(closedObserver).hasSize(1);
        }

        @AfterEach
        void closeRouter() {
            router.close();
        }
    }

    @Nested
    @DisplayName("when the repository is built")
    class WhenRepositoryIsBuilt {
        private final String                     pdpId     = "tenant-1";
        private final String                     configId1 = "config-1";
        private final String                     configId2 = "config-2";
        private final RoutingAttributeRepository router    = new RoutingAttributeRepository();
        private final List<Value>                received  = new CopyOnWriteArrayList<>();

        @BeforeEach
        void build() {
            router.buildOrRoute(pdpId, configForInMemory(pdpId, configId1));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> router.currentRawConfig(pdpId).isPresent());
        }

        @AfterEach
        void closeRouter() {
            router.close();
        }

        private Value firstValueFor(String configId) {
            val values = new CopyOnWriteArrayList<Value>();
            router.observe(invocation(pdpId, configId), values::add).close();
            return values.getFirst();
        }

        @Test
        @DisplayName("then new observations are delegated directly")
        void thenNewObservationsAreDelegatedDirectly() {
            router.observe(invocation(pdpId, configId1), received::add);
            assertThat(received).containsExactly(Value.UNDEFINED);
        }

        @Test
        @DisplayName("then a changed configuration id makes the old one unroutable")
        void thenChangedConfigurationIdMakesOldOneUnroutable() {
            router.buildOrRoute(pdpId, configForInMemory(pdpId, configId2));
            Awaitility.await().atMost(Duration.ofSeconds(5))
                    .until(() -> firstValueFor(configId1) instanceof ErrorValue);
        }

        @Test
        @DisplayName("then removing the pdp from the router removes also the raw config")
        void thenRemovingThePdpAlsoRemovesRoutingAndRawConfiguration() {
            router.removeForPdp(pdpId);
            assertThat(router.currentRawConfig(pdpId)).isEmpty();
            assertThat(firstValueFor(configId1)).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("then closing the router also closes the cached repositories")
        void thenClosingRouterClosedCachedRepositories() {
            router.close();
            assertThat(firstValueFor(configId1)).isInstanceOf(ErrorValue.class);
        }
    }

    private static PDPConfiguration configForInMemory(String pdpId, String configId) {
        return new PDPConfiguration(pdpId, configId, CombiningAlgorithm.DEFAULT,
                List.of("policy \"test-policy\" permit true;"), new PdpData(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private static AttributeFinderInvocation invocation(String pdpId, String configId) {
        return new AttributeFinderInvocation(pdpId, configId, "sapl.test", List.of(), Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofSeconds(1), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }
}
