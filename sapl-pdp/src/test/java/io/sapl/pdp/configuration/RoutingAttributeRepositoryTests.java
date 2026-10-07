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
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.awaitility.Awaitility;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.attributes.broker.repository.RepositoryKey;
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
        private static final String              PDP_ID    = "tenant-1";
        private static final String              CONFIG_ID = "config-1";
        private final RoutingAttributeRepository router    = new RoutingAttributeRepository();
        private final List<Value>                received  = new CopyOnWriteArrayList<>();

        @Test
        @DisplayName("then the queued observation is replayed after the build is finished")
        void thenQueuedObservationIsReplayedAfterBuild() {
            router.observe(invocation(PDP_ID, CONFIG_ID), received::add);
            router.buildOrRoute(PDP_ID, configForInMemory(PDP_ID, CONFIG_ID));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> received.size() == 2);
            assertThat(received.getLast()).isEqualTo(Value.UNDEFINED);
        }

        @Test
        @DisplayName("then a closed queued registration is not replayed")
        void thenClosedQueueRegistrationIsNotReplayed() {
            val closedObserver = new CopyOnWriteArrayList<Value>();

            router.observe(invocation(PDP_ID, CONFIG_ID), closedObserver::add).close();
            router.observe(invocation(PDP_ID, CONFIG_ID), received::add);
            router.buildOrRoute(PDP_ID, configForInMemory(PDP_ID, CONFIG_ID));

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
        private static final String              PDP_ID     = "tenant-1";
        private static final String              CONFIG_ID1 = "config-1";
        private static final String              CONFIG_ID2 = "config-2";
        private static final RepositoryKey       KEY        = new RepositoryKey(Value.of("alice"), "test.attribute",
                List.of(), "testPdp");
        private static final Value               VALUE      = Value.of("test");
        private static final Duration            TTL        = Duration.ofSeconds(60);
        private final RoutingAttributeRepository router     = new RoutingAttributeRepository();
        private final List<Value>                received   = new CopyOnWriteArrayList<>();

        @BeforeEach
        void build() {
            router.buildOrRoute(PDP_ID, configForInMemory(PDP_ID, CONFIG_ID1));
            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> router.currentRawConfig(PDP_ID).isPresent());
        }

        @AfterEach
        void closeRouter() {
            router.close();
        }

        private Value firstValueFor(String configId) {
            val values = new CopyOnWriteArrayList<Value>();
            router.observe(invocation(PDP_ID, configId), values::add).close();
            return values.getFirst();
        }

        @Test
        @DisplayName("then new observations are delegated directly")
        void thenNewObservationsAreDelegatedDirectly() {
            router.observe(invocation(PDP_ID, CONFIG_ID1), received::add);
            assertThat(received).containsExactly(Value.UNDEFINED);
        }

        @Test
        @DisplayName("then a changed configuration id makes the old one unroutable")
        void thenChangedConfigurationIdMakesOldOneUnroutable() {
            router.buildOrRoute(PDP_ID, configForInMemory(PDP_ID, CONFIG_ID2));
            Awaitility.await().atMost(Duration.ofSeconds(5))
                    .until(() -> firstValueFor(CONFIG_ID1) instanceof ErrorValue);
        }

        @Test
        @DisplayName("then removing the pdp from the router removes also the raw config")
        void thenRemovingThePdpAlsoRemovesRoutingAndRawConfiguration() {
            router.removeForPdp(PDP_ID);
            assertThat(router.currentRawConfig(PDP_ID)).isEmpty();
            assertThat(firstValueFor(CONFIG_ID1)).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("then closing the router also closes the cached repositories")
        void thenClosingRouterClosedCachedRepositories() {
            router.close();
            assertThat(firstValueFor(CONFIG_ID1)).isInstanceOf(ErrorValue.class);
        }

        @Test
        @DisplayName("then publish without ttl throws an exception on that layer")
        void thenPublishWithoutTtlThrowsExceptionOnRoutingLayer() {
            assertThatThrownBy(() -> router.publish(KEY, VALUE)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> router.publish(null, VALUE)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> router.publish(KEY, null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("then publish with ttl throws an exception on that layer")
        void thenPublishWitTtlThrowsExceptionOnRoutingLayer() {
            assertThatThrownBy(() -> router.publish(KEY, VALUE, TTL)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> router.publish(null, VALUE, TTL)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> router.publish(KEY, VALUE, null)).isInstanceOf(NullPointerException.class);
            assertThatThrownBy(() -> router.publish(KEY, null, TTL)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("then publish without ttl throws an exception on that layer")
        void thenRemoveThrowsExceptionOnRoutingLayer() {
            assertThatThrownBy(() -> router.remove(KEY)).isInstanceOf(UnsupportedOperationException.class);
            assertThatThrownBy(() -> router.remove(null)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("then building the same configuration again only routes it")
        void thenBuildingSameConfigurationAgainOnlyRoutesIt() {
            router.observe(invocation(PDP_ID, CONFIG_ID1), received::add);
            router.buildOrRoute(PDP_ID, configForInMemory(PDP_ID, CONFIG_ID1));

            assertThat(router.currentRawConfig(PDP_ID)).isPresent();
            assertThat(received).containsExactly(Value.UNDEFINED);
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

    @Nested
    @DisplayName("when the repository build is still pending")
    class WhenBuildIsPending {
        private static final String              PDP_ID    = "tenant-1";
        private static final String              CONFIG_ID = "config-pg";
        private final RoutingAttributeRepository router    = new RoutingAttributeRepository();
        private final List<Value>                received  = new CopyOnWriteArrayList<>();

        @AfterEach
        void closeRouter() {
            router.close();
        }

        private static PDPConfiguration configForUnreachablePostgres(String pdpId, String configId) {
            val config  = ObjectValue.builder().put("type", Value.of("postgres")).put("host", Value.of("localhost"))
                    .put("port", Value.of(1)).put("database", Value.of("sapl")).build();
            val secrets = ObjectValue.builder().put("username", Value.of("sapl")).put("password", Value.of("secret"))
                    .build();
            return configForInMemory(pdpId, configId).withExtensions(Map.of("attributeRepository", config),
                    Map.of("attributeRepository", secrets), Set.of());
        }

        @Test
        @DisplayName("then observe reports that the repository is still connecting")
        void thenObserveReportsThatRepositoryIsStillConnecting() {
            router.buildOrRoute(PDP_ID, configForUnreachablePostgres(PDP_ID, CONFIG_ID));
            router.observe(invocation(PDP_ID, CONFIG_ID), received::add);

            assertThat(received.getFirst()).isInstanceOfSatisfying(ErrorValue.class,
                    error -> assertThat(error.message()).contains("still connecting"));
        }

        @Test
        @DisplayName("then a second build request for the same configuration does not fail")
        void thenSecondBuildRequestForSameConfigurationDoesNotFail() {
            router.buildOrRoute(PDP_ID, configForUnreachablePostgres(PDP_ID, CONFIG_ID));
            router.buildOrRoute(PDP_ID, configForUnreachablePostgres(PDP_ID, CONFIG_ID));
            router.observe(invocation(PDP_ID, CONFIG_ID), received::add);

            assertThat(received.getFirst()).isInstanceOfSatisfying(ErrorValue.class,
                    error -> assertThat(error.message()).contains("still connecting"));
        }
    }
}
