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
package io.sapl.attributes.broker.pip;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.attributes.AttributeFinderInvocation;
import io.sapl.api.attributes.EnvironmentAttribute;
import io.sapl.api.attributes.PolicyInformationPoint;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.attributes.broker.AttributeBroker.Subscription;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import lombok.val;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PolicyInformationPointAttributeBroker visibility of plugin PIPs per PDP")
class PolicyInformationPointAttributeBrokerVisibilityTests {

    private static final String   FIRST_PDP     = "first-pdp";
    private static final String   SECOND_PDP    = "second-pdp";
    private static final String   FIRST_PLUGIN  = "first-plugin";
    private static final String   SECOND_PLUGIN = "second-plugin";
    private static final String   THIRD_PLUGIN  = "third-plugin";
    private static final String   SHARED        = "shared.value";
    private static final String   EXCLUSIVE     = "exclusive.value";
    private static final Duration WAIT          = Duration.ofSeconds(2);

    private InMemoryAttributeRepository           repository;
    private PolicyInformationPointAttributeBroker broker;

    @BeforeEach
    void setUp() {
        repository = new InMemoryAttributeRepository();
        broker     = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository);
    }

    @AfterEach
    void tearDown() {
        broker.close();
        repository.close();
    }

    private static AttributeFinderInvocation invocation(String pdpId, String fqn) {
        return new AttributeFinderInvocation(pdpId, "default", fqn, List.of(Value.of("argument")),
                Duration.ofSeconds(1), Duration.ofMillis(100), Duration.ofMillis(100), 0L, false,
                new AttributeAccessContext(Value.EMPTY_OBJECT, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT));
    }

    private void publishToRepository(String pdpId, String fqn, Value value) {
        repository.publish(RepositoryKey.fromInvocation(invocation(pdpId, fqn)), value);
    }

    /** Opens a subscription that records the latest value of the given attribute. */
    private Subscription subscribe(String pdpId, String fqn, AtomicReference<Value> latest) {
        val key = new SubscriptionKey(invocation(pdpId, fqn), false);
        return broker.open(pdpId + "-" + fqn, Set.of(key), snapshot -> {
            latest.set(snapshot.get(key).value());
            return Set.of(key);
        });
    }

    private static void awaitValue(AtomicReference<Value> latest, String expected) {
        Awaitility.await().atMost(WAIT).untilAsserted(() -> assertThat(latest.get()).isEqualTo(Value.of(expected)));
    }

    @Nested
    @DisplayName("resolution")
    class Resolution {

        @Test
        @DisplayName("a base PIP is visible to every PDP")
        void whenBasePipLoadedThenVisibleToEveryPdp() {
            broker.load(new FirstExactPip());

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isPresent();
            assertThat(broker.resolve(invocation(SECOND_PDP, SHARED))).isPresent();
        }

        @Test
        @DisplayName("a plugin PIP is invisible while its plugin is not enabled")
        void whenPluginNotEnabledThenPluginPipIsInvisible() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isEmpty();
        }

        @Test
        @DisplayName("a plugin PIP is visible only to the PDP that enables its plugin")
        void whenPluginEnabledForOnePdpThenVisibleOnlyToIt() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);

            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isPresent();
            assertThat(broker.resolve(invocation(SECOND_PDP, SHARED))).isEmpty();
        }

        @Test
        @DisplayName("clearing the visibility hides the plugin PIP again")
        void whenVisibilityClearedThenPluginPipIsInvisibleAgain() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));

            broker.clearVisiblePlugins(FIRST_PDP);

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isEmpty();
        }

        @Test
        @DisplayName("a swapped plugin PIP keeps its plugin")
        void whenSwappedThenOriginIsKept() {
            val handle = broker.load(new FirstExactPip(), FIRST_PLUGIN);

            broker.swap(handle, new SecondExactPip());

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isEmpty();
            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));
            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isPresent();
        }
    }

    @Nested
    @DisplayName("collisions on load")
    class CollisionsOnLoad {

        @Test
        @DisplayName("a plugin PIP colliding with a base PIP fails to load")
        void whenPluginPipCollidesWithBasePipThenLoadFails() {
            broker.load(new FirstExactPip());
            val collidingPip = new SecondExactPip();

            assertThatThrownBy(() -> broker.load(collidingPip, FIRST_PLUGIN)).isInstanceOf(PipLoadException.class);
        }

        @Test
        @DisplayName("a base PIP colliding with a plugin PIP fails to load")
        void whenBasePipCollidesWithPluginPipThenLoadFails() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            val collidingPip = new SecondExactPip();

            assertThatThrownBy(() -> broker.load(collidingPip)).isInstanceOf(PipLoadException.class);
        }

        @Test
        @DisplayName("two colliding PIPs of the same plugin fail to load")
        void whenTwoPipsOfSamePluginCollideThenLoadFails() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            val collidingPip = new SecondExactPip();

            assertThatThrownBy(() -> broker.load(collidingPip, FIRST_PLUGIN)).isInstanceOf(PipLoadException.class);
        }

        @Test
        @DisplayName("colliding PIPs of different plugins are both loaded and resolve for PDPs enabling one of them")
        void whenPipsOfDifferentPluginsCollideThenBothAreLoaded() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            broker.load(new SecondExactPip(), SECOND_PLUGIN);

            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));
            broker.setVisiblePlugins(SECOND_PDP, Set.of(SECOND_PLUGIN));

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isPresent();
            assertThat(broker.resolve(invocation(SECOND_PDP, SHARED))).isPresent();
        }
    }

    @Nested
    @DisplayName("ambiguous resolution")
    class AmbiguousResolution {

        @Test
        @DisplayName("two visible exact specs make the resolution ambiguous")
        void whenTwoExactSpecsVisibleThenResolutionIsAmbiguous() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            broker.load(new SecondExactPip(), SECOND_PLUGIN);

            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN, SECOND_PLUGIN));

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isEmpty();
        }

        @Test
        @DisplayName("two visible varargs specs make the resolution ambiguous")
        void whenTwoVarargsSpecsVisibleThenResolutionIsAmbiguous() {
            broker.load(new FirstVarargsPip(), FIRST_PLUGIN);
            broker.load(new SecondVarargsPip(), SECOND_PLUGIN);

            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN, SECOND_PLUGIN));

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED))).isEmpty();
        }

        @Test
        @DisplayName("a visible exact spec wins over ambiguous varargs specs")
        void whenExactSpecVisibleThenItWinsOverAmbiguousVarargs() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            broker.load(new FirstVarargsPip(), SECOND_PLUGIN);
            broker.load(new SecondVarargsPip(), THIRD_PLUGIN);

            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN, SECOND_PLUGIN, THIRD_PLUGIN));

            assertThat(broker.resolve(invocation(FIRST_PDP, SHARED)))
                    .hasValueSatisfying(spec -> assertThat(spec.hasVariableNumberOfArguments()).isFalse());
        }
    }

    @Nested
    @DisplayName("rerouting running invocations")
    class Rerouting {

        @Test
        @DisplayName("enabling a plugin promotes a running invocation from the repository to the plugin PIP")
        void whenPluginEnabledThenRunningInvocationIsPromoted() {
            broker.load(new ExclusivePip(), FIRST_PLUGIN);
            publishToRepository(FIRST_PDP, EXCLUSIVE, Value.of("repository"));
            val latest = new AtomicReference<Value>();

            try (val subscription = subscribe(FIRST_PDP, EXCLUSIVE, latest)) {
                awaitValue(latest, "repository");

                broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));

                awaitValue(latest, "exclusive");
            }
        }

        @Test
        @DisplayName("disabling a plugin moves a running invocation back to the repository")
        void whenPluginDisabledThenRunningInvocationFallsBackToRepository() {
            broker.load(new ExclusivePip(), FIRST_PLUGIN);
            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));
            publishToRepository(FIRST_PDP, EXCLUSIVE, Value.of("repository"));
            val latest = new AtomicReference<Value>();

            try (val subscription = subscribe(FIRST_PDP, EXCLUSIVE, latest)) {
                awaitValue(latest, "exclusive");

                broker.clearVisiblePlugins(FIRST_PDP);

                awaitValue(latest, "repository");
            }
        }

        @Test
        @DisplayName("disabling a plugin without a repository ends a running invocation with UNDEFINED")
        void whenPluginDisabledWithoutRepositoryThenRunningInvocationIsUndefined() {
            broker.close();
            broker = new PolicyInformationPointAttributeBroker();
            broker.load(new ExclusivePip(), FIRST_PLUGIN);
            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));
            val latest = new AtomicReference<Value>();

            try (val subscription = subscribe(FIRST_PDP, EXCLUSIVE, latest)) {
                awaitValue(latest, "exclusive");

                broker.clearVisiblePlugins(FIRST_PDP);

                Awaitility.await().atMost(WAIT)
                        .untilAsserted(() -> assertThat(latest.get()).isEqualTo(Value.UNDEFINED));
            }
        }

        @Test
        @DisplayName("changing the visibility of one PDP leaves the running invocations of other PDPs untouched")
        void whenVisibilityChangesThenOtherPdpsAreUnaffected() {
            broker.load(new ExclusivePip(), FIRST_PLUGIN);
            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));
            broker.setVisiblePlugins(SECOND_PDP, Set.of(FIRST_PLUGIN));
            publishToRepository(FIRST_PDP, EXCLUSIVE, Value.of("repository"));
            val first  = new AtomicReference<Value>();
            val second = new AtomicReference<Value>();

            try (val firstSubscription = subscribe(FIRST_PDP, EXCLUSIVE, first);
                    val secondSubscription = subscribe(SECOND_PDP, EXCLUSIVE, second)) {
                awaitValue(first, "exclusive");
                awaitValue(second, "exclusive");

                broker.clearVisiblePlugins(FIRST_PDP);

                awaitValue(first, "repository");
                assertThat(second.get()).isEqualTo(Value.of("exclusive"));
            }
        }

        @Test
        @DisplayName("a running invocation moves between a visible base varargs PIP and a plugin PIP, not to the repository")
        void whenVisibilityChangesThenRunningInvocationMovesBetweenBaseAndPluginPip() {
            broker.load(new BaseVarargsPip());
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            publishToRepository(FIRST_PDP, SHARED, Value.of("repository"));
            val latest = new AtomicReference<Value>();

            try (val subscription = subscribe(FIRST_PDP, SHARED, latest)) {
                awaitValue(latest, "base-varargs");

                broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));
                awaitValue(latest, "first");

                broker.clearVisiblePlugins(FIRST_PDP);
                awaitValue(latest, "base-varargs");
            }
        }

        @Test
        @DisplayName("loading a plugin PIP promotes only the running invocations of PDPs enabling its plugin")
        void whenPluginPipLoadedThenOnlyEnablingPdpsArePromoted() {
            broker.setVisiblePlugins(FIRST_PDP, Set.of(FIRST_PLUGIN));
            publishToRepository(FIRST_PDP, EXCLUSIVE, Value.of("repository"));
            publishToRepository(SECOND_PDP, EXCLUSIVE, Value.of("repository"));
            val first  = new AtomicReference<Value>();
            val second = new AtomicReference<Value>();

            try (val firstSubscription = subscribe(FIRST_PDP, EXCLUSIVE, first);
                    val secondSubscription = subscribe(SECOND_PDP, EXCLUSIVE, second)) {
                awaitValue(first, "repository");
                awaitValue(second, "repository");

                broker.load(new ExclusivePip(), FIRST_PLUGIN);

                awaitValue(first, "exclusive");
                assertThat(second.get()).isEqualTo(Value.of("repository"));
            }
        }
    }

    @Nested
    @DisplayName("conflicts among plugins")
    class ConflictsAmongPlugins {

        @Test
        @DisplayName("colliding plugins yield one conflict naming the attribute and both plugins")
        void whenPluginsCollideThenOneConflictNamingBothIsReported() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            broker.load(new SecondExactPip(), SECOND_PLUGIN);

            val conflicts = broker.conflictsAmong(Set.of(FIRST_PLUGIN, SECOND_PLUGIN));

            assertThat(conflicts).singleElement().asString().contains(SHARED, FIRST_PLUGIN, SECOND_PLUGIN);
        }

        @Test
        @DisplayName("a single plugin has no conflicts")
        void whenSinglePluginRequestedThenNoConflict() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            broker.load(new SecondExactPip(), SECOND_PLUGIN);

            assertThat(broker.conflictsAmong(Set.of(FIRST_PLUGIN))).isEmpty();
        }

        @Test
        @DisplayName("colliding plugins outside the requested set are not reported")
        void whenCollidingPluginIsNotRequestedThenNoConflict() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            broker.load(new SecondExactPip(), SECOND_PLUGIN);
            broker.load(new ExclusivePip(), THIRD_PLUGIN);

            assertThat(broker.conflictsAmong(Set.of(FIRST_PLUGIN, THIRD_PLUGIN))).isEmpty();
        }

        @Test
        @DisplayName("an exact and a varargs spec of different plugins do not conflict")
        void whenExactAndVarargsSpecsThenNoConflict() {
            broker.load(new FirstExactPip(), FIRST_PLUGIN);
            broker.load(new FirstVarargsPip(), SECOND_PLUGIN);

            assertThat(broker.conflictsAmong(Set.of(FIRST_PLUGIN, SECOND_PLUGIN))).isEmpty();
        }

        @Test
        @DisplayName("the no-op visibility reports no conflicts")
        void whenNoopThenNoConflicts() {
            assertThat(PolicyInformationPointVisibility.NOOP.conflictsAmong(Set.of(FIRST_PLUGIN, SECOND_PLUGIN)))
                    .isEmpty();
        }
    }

    @PolicyInformationPoint(name = "shared")
    static class FirstExactPip {
        @EnvironmentAttribute(name = "value")
        public static Value value(Value argument) {
            return Value.of("first");
        }
    }

    @PolicyInformationPoint(name = "shared")
    static class SecondExactPip {
        @EnvironmentAttribute(name = "value")
        public static Value value(Value argument) {
            return Value.of("second");
        }
    }

    @PolicyInformationPoint(name = "shared")
    static class FirstVarargsPip {
        @EnvironmentAttribute(name = "value")
        public static Value value(Value... arguments) {
            return Value.of("first-varargs");
        }
    }

    @PolicyInformationPoint(name = "shared")
    static class SecondVarargsPip {
        @EnvironmentAttribute(name = "value")
        public static Value value(Value... arguments) {
            return Value.of("second-varargs");
        }
    }

    @PolicyInformationPoint(name = "shared")
    static class BaseVarargsPip {
        @EnvironmentAttribute(name = "value")
        public static Value value(Value... arguments) {
            return Value.of("base-varargs");
        }
    }

    @PolicyInformationPoint(name = "exclusive")
    static class ExclusivePip {
        @EnvironmentAttribute(name = "value")
        public static Value value(Value argument) {
            return Value.of("exclusive");
        }
    }
}
