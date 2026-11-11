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
package io.sapl.extensions.attributerepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
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
import java.util.function.Consumer;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
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
    @DisplayName("when an observer has an unknown config then an error is triggered observe is queued")
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

    @Nested
    @DisplayName("when canPrepare is called")
    class WhenCanPrepareIsCalled {
        private final RoutingAttributeRepository router = new RoutingAttributeRepository();
        private final ObjectValue                config = ObjectValue.builder()
                .put("type", Value.of(TestAttributeRepositoryProvider.TYPE)).put("host", Value.of("localhost"))
                .put("port", Value.of(5432)).put("database", Value.of("sapl")).build();

        private final ObjectValue secrets = ObjectValue.builder().put("username", Value.of("sapl"))
                .put("password", Value.of("secret")).build();

        private static PDPConfiguration configWithRepository(ObjectValue config, ObjectValue secrets) {
            Map<String, Value> configs    = config == null ? Map.of() : Map.of("attributerepository", config);
            Map<String, Value> secretsMap = secrets == null ? Map.of() : Map.of("attributerepository", secrets);
            return configForInMemory("tenant-1", "config-1").withExtensions(configs, secretsMap, Set.of());
        }

        @AfterEach
        void closeRouter() {
            router.close();
        }

        @Test
        @DisplayName("then a configuration without extension is accepted")
        void thenMissingExtensionIsAccepted() {
            assertThat(router.canPrepare(configWithRepository(null, null))).isTrue();
        }

        @Test
        @DisplayName("then an extension split into config and secrets is accepted")
        void thenSplitExtensionIsAccepted() {
            assertThat(router.canPrepare(configWithRepository(config, secrets))).isTrue();
        }

        @Test
        @DisplayName("then an extension with only a secrets block is accepted")
        void thenSecretsOnlyExtensionIsAccepted() {
            val secretsOnly = ObjectValue.builder().putAll(config).putAll(secrets).build();
            assertThat(router.canPrepare(configWithRepository(null, secretsOnly))).isTrue();
        }

        @Test
        @DisplayName("then an extension with missing fields is rejected")
        void thenInvalidExtensionIsRejected() {
            assertThat(router.canPrepare(configWithRepository(config, null))).isFalse();
        }

        @Test
        @DisplayName("then an extension without type is rejected")
        void thenExtensionWithoutTypeIsRejected() {
            val withoutType = ObjectValue.builder().put("host", Value.of("localhost")).put("port", Value.of(5432))
                    .build();
            assertThat(router.canPrepare(configWithRepository(withoutType, null))).isFalse();
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

        @ParameterizedTest
        @NullSource
        @DisplayName("then a null invocation is rejected")
        void thenNullInvocationIsRejected(AttributeFinderInvocation invocation) {
            assertThatThrownBy(() -> router.observe(invocation, received::add))
                    .isInstanceOf(NullPointerException.class);
        }

        @ParameterizedTest
        @NullSource
        @DisplayName("then a null callback is rejected")
        void thenNullCallbackIsRejected(Consumer<Value> callback) {
            val invocation = invocation(PDP_ID, CONFIG_ID);

            assertThatThrownBy(() -> router.observe(invocation, callback)).isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("then repository build after it was closed is being closed")
        void thenRepositoryBuildAfterCloseIsClosedAgain() {
            router.close();
            router.buildOrRoute(PDP_ID, configForInMemory(PDP_ID, CONFIG_ID));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> {
                val values = new ArrayList<Value>();
                router.observe(invocation(PDP_ID, CONFIG_ID), values::add).close();
                return values.getFirst() instanceof ErrorValue error && error.message().contains("is unknown");
            });
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
        @DisplayName("then removing an unknown pdp id leaves others untouched")
        void thenRemovingAnUnknownPdpIdLeavesOthersUntouched() {
            router.removeForPdp("unknown-pdp-id");
            assertThat(router.currentRawConfig(PDP_ID)).isPresent();
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

        @Test
        @DisplayName("then a batch runner is handed to the cached repositories")
        void thenSetBatchRunnerViaTheRouter() {
            assertThatCode(() -> router.setBatchRunner(Runnable::run)).doesNotThrowAnyException();
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
        private static final String              CONFIG_ID = "config-unreachable";
        private final RoutingAttributeRepository router    = new RoutingAttributeRepository();
        private final List<Value>                received  = new CopyOnWriteArrayList<>();

        @BeforeEach
        void resetAttempts() {
            TestAttributeRepositoryProvider.UNREACHABLE_ATTEMPTS.set(0);
        }

        @AfterEach
        void closeRouter() {
            router.close();
        }

        private static PDPConfiguration configForUnreachableBackend(String pdpId, String configId) {
            val config  = ObjectValue.builder().put("type", Value.of(TestAttributeRepositoryProvider.TYPE))
                    .put("host", Value.of("localhost")).put("port", Value.of(1)).put("database", Value.of("sapl"))
                    .build();
            val secrets = ObjectValue.builder().put("username", Value.of("sapl")).put("password", Value.of("secret"))
                    .build();
            return configForInMemory(pdpId, configId).withExtensions(Map.of("attributerepository", config),
                    Map.of("attributerepository", secrets), Set.of());
        }

        @Test
        @DisplayName("then observe reports that the repository is still connecting")
        void thenObserveReportsThatRepositoryIsStillConnecting() {
            router.buildOrRoute(PDP_ID, configForUnreachableBackend(PDP_ID, CONFIG_ID));
            router.observe(invocation(PDP_ID, CONFIG_ID), received::add);

            assertThat(received.getFirst()).isInstanceOfSatisfying(ErrorValue.class,
                    error -> assertThat(error.message()).contains("still connecting"));
        }

        @Test
        @DisplayName("then a second build request for the same configuration does not fail")
        void thenSecondBuildRequestForSameConfigurationDoesNotFail() {
            router.buildOrRoute(PDP_ID, configForUnreachableBackend(PDP_ID, CONFIG_ID));
            router.buildOrRoute(PDP_ID, configForUnreachableBackend(PDP_ID, CONFIG_ID));
            router.observe(invocation(PDP_ID, CONFIG_ID), received::add);

            assertThat(received.getFirst()).isInstanceOfSatisfying(ErrorValue.class,
                    error -> assertThat(error.message()).contains("still connecting"));
        }

        @Test
        @DisplayName("then a failed build is retried")
        void thenFailedBuildIsRetried() {
            router.buildOrRoute(PDP_ID, configForUnreachableBackend(PDP_ID, CONFIG_ID));

            Awaitility.await().atMost(Duration.ofSeconds(5))
                    .until(() -> TestAttributeRepositoryProvider.UNREACHABLE_ATTEMPTS.get() >= 2);
        }

        @Test
        @DisplayName("then a failing build after the router was closed is not retried")
        void thenFailingBuildAfterCloseIsNotRetried() {
            router.close();
            router.buildOrRoute(PDP_ID, configForUnreachableBackend(PDP_ID, CONFIG_ID));

            Awaitility.await().atMost(Duration.ofSeconds(5))
                    .until(() -> TestAttributeRepositoryProvider.UNREACHABLE_ATTEMPTS.get() == 1);
            Awaitility.await().during(Duration.ofMillis(1500)).atMost(Duration.ofSeconds(3))
                    .until(() -> TestAttributeRepositoryProvider.UNREACHABLE_ATTEMPTS.get() == 1);
        }
    }

    @Nested
    @DisplayName("when the configuration id is identical but the pdp id is different")
    class WhenConfigurationIdIsIdenticalButPdpIdDifferent {
        private final RoutingAttributeRepository router    = new RoutingAttributeRepository();
        private static final String              PDP_ID_1  = "tenant-1";
        private static final String              PDP_ID_2  = "tenant-2";
        private static final String              CONFIG_ID = "config";

        @Test
        @DisplayName("then removing one repository doesn't close the other")
        void thenRemovingOneRepositoryDoesntCloseTheOther() {
            router.buildOrRoute(PDP_ID_1, configForInMemory(PDP_ID_1, CONFIG_ID));
            router.buildOrRoute(PDP_ID_2, configForInMemory(PDP_ID_2, CONFIG_ID));

            Awaitility.await().atMost(Duration.ofSeconds(5)).until(() -> router.currentRawConfig(PDP_ID_1).isPresent()
                    && router.currentRawConfig(PDP_ID_2).isPresent());

            // Both repositories exist
            val received1 = new CopyOnWriteArrayList<Value>();
            val received2 = new CopyOnWriteArrayList<Value>();
            router.observe(invocation(PDP_ID_1, CONFIG_ID), received1::add);
            router.observe(invocation(PDP_ID_2, CONFIG_ID), received2::add);

            assertThat(received1).isNotEmpty().noneMatch(ErrorValue.class::isInstance);
            assertThat(received2).isNotEmpty().noneMatch(ErrorValue.class::isInstance);

            // Remove one repository. The other one shouldn't be closed.
            router.removeForPdp(PDP_ID_1);

            val afterRemoval = new CopyOnWriteArrayList<Value>();
            router.observe(invocation(PDP_ID_2, CONFIG_ID), afterRemoval::add);

            assertThat(afterRemoval).isNotEmpty().noneMatch(ErrorValue.class::isInstance);
            assertThat(afterRemoval.getLast()).isEqualTo(Value.UNDEFINED);
            assertThat(router.currentRawConfig(PDP_ID_1)).isEmpty();
            assertThat(router.currentRawConfig(PDP_ID_2)).isPresent();
        }
    }
}
