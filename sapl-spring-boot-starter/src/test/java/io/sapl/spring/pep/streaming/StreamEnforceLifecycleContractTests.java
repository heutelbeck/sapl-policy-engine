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
package io.sapl.spring.pep.streaming;

import static io.sapl.spring.pep.streaming.StreamingPepScenarioSupport.TIMEOUT;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.StreamingPolicyDecisionPoint;
import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.spring.config.EnableReactiveSaplMethodSecurity;
import io.sapl.spring.method.metadata.StreamEnforce;
import io.sapl.spring.pep.constraints.ConstraintHandlerProvider;
import io.sapl.spring.pep.constraints.EnforcementPlanContext;
import io.sapl.spring.testsupport.SaplPepTestApp;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Timeout(10)
@WithMockUser(username = "operator")
@DisplayName("Streaming activity monitor through Spring method security")
@SpringBootTest(classes = StreamEnforceLifecycleContractTests.TestApplication.class)
class StreamEnforceLifecycleContractTests {

    @Autowired
    ActivityMonitor monitor;

    @Autowired
    StreamingPepScenarioSupport scenario;

    @MockitoBean
    ReactivePolicyDecisionPoint pdp;

    @MockitoBean
    StreamingPolicyDecisionPoint blockingPdp;

    @BeforeEach
    void prepareScenario() {
        scenario.reset();
        when(pdp.decide(any(AuthorizationSubscription.class), anyString())).thenReturn(scenario.decisions());
    }

    @AfterEach
    void releaseSubscriptions() {
        scenario.close();
    }

    @Nested
    @DisplayName("Input enforcement and actual method reinvocation")
    class InputEnforcement {

        @Test
        @DisplayName("R1/R13: a device restriction changes the actual RAP arguments before the first call")
        void whenFirstPermitRestrictsDevicesThenInvokeWithRestrictedScope() {
            val restriction = scenario.inputScope("device-a");
            val output      = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.permit(restriction)).then(() -> {
                assertThat(scenario.calls).singleElement().satisfies(call -> {
                    assertThat(call.scope).isEqualTo("device-a");
                    assertThat(call.start).isEqualTo("09:00");
                });
                assertThat(scenario.trace).containsSubsequence("input:device-a", "invoke:device-a");
                scenario.emit(0, "reading-a");
            }).expectNext("reading-a").thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("R2/R13: a narrower permit replaces the source without replacing the PDP or client subscription")
        void whenPermitNarrowsScopeThenReinvokeAndRejectOldSourceData() {
            val first    = scenario.inputScope("devices-a-b");
            val narrowed = scenario.inputScope("device-a");
            val output   = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.permit(first)).then(() -> scenario.permit(narrowed))
                    .then(() -> {
                        assertThat(scenario.calls).extracting(call -> call.scope).containsExactly("devices-a-b",
                                "device-a");
                        assertThat(scenario.calls.getFirst().cancellations).hasValue(1);
                        assertThat(scenario.pdpSubscriptions).hasValue(1);
                        scenario.assertPdpOpen();
                        scenario.emit(0, "stale-device-b");
                        scenario.emit(1, "current-device-a");
                    }).expectNext("current-device-a").thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("R3: non-idempotent input mapping always starts from the original arguments")
        void whenInputMappingChangesRepeatedlyThenDoNotAccumulateChanges() {
            val first  = scenario.appendScope("/a");
            val second = scenario.appendScope("/b");
            val third  = scenario.appendScope("/c");
            val output = monitor.watch("devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.permit(first)).then(() -> scenario.permit(second))
                    .then(() -> scenario.permit(third)).then(() -> assertThat(scenario.calls)
                            .extracting(call -> call.scope).containsExactly("devices/a", "devices/b", "devices/c"))
                    .thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("C1: an equivalent permit does not execute input handlers again or restart the RAP")
        void whenEquivalentPermitArrivesThenKeepActiveInvocation() {
            val restriction = scenario.inputScope("device-a");
            val output      = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.permit(restriction))
                    .then(() -> scenario.permit(restriction)).then(() -> {
                        assertThat(scenario.calls).hasSize(1);
                        assertThat(scenario.trace).containsExactly("input:device-a", "invoke:device-a");
                    }).thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("C4: adding and removing the last input constraint both require a fresh call")
        void whenInputPlanBecomesNonemptyThenEmptyThenRestoreOriginalArguments() {
            val restriction = scenario.inputScope("device-a");
            val output      = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(scenario::permit).then(() -> scenario.permit(restriction))
                    .then(scenario::permit).then(() -> assertThat(scenario.calls).extracting(call -> call.scope)
                            .containsExactly("all-devices", "device-a", "all-devices"))
                    .thenCancel().verify(TIMEOUT);
        }

        @ValueSource(strings = { "initial", "restart", "resume" })
        @ParameterizedTest(name = "R4: input obligation failure during {0}")
        @DisplayName("Input obligation failure blocks the call but remaining input handlers still run")
        void whenInputObligationFailsThenAttemptRemainingHandlersAndTerminate(String entryPath) {
            val failure = scenario.inputFailure();
            val audit   = scenario.inputAudit("audit-denied-input");
            val output  = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> {
                if (!"initial".equals(entryPath)) {
                    scenario.permit();
                }
                if ("resume".equals(entryPath)) {
                    scenario.suspend();
                }
                scenario.permit(failure, audit);
            }).expectError(AccessDeniedException.class).verify(TIMEOUT);

            assertThat(scenario.trace).containsSubsequence("input-failure", "audit-denied-input");
            assertThat(scenario.calls).hasSize("initial".equals(entryPath) ? 0 : 1);
            assertThat(scenario.pdpCancellations).hasValue(1);
        }

        @Test
        @DisplayName("R5: failing input advice does not block the protected operation")
        void whenInputAdviceFailsThenContinueWithTheRap() {
            val failure = scenario.inputFailure();
            val output  = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.decide(Decision.PERMIT, List.of(), List.of(failure)))
                    .then(() -> {
                        assertThat(scenario.trace).containsSubsequence("input-failure", "invoke:all-devices");
                        scenario.emit(0, "reading");
                    }).expectNext("reading").thenCancel().verify(TIMEOUT);
        }

        @EnumSource(value = Decision.class, names = { "DENY", "INDETERMINATE", "NOT_APPLICABLE" })
        @ParameterizedTest(name = "R16: {0} does not execute input handlers")
        @DisplayName("Non-permit decisions never invoke the RAP or input handlers")
        void whenDecisionDoesNotPermitThenDoNotExecuteInput(Decision decision) {
            val input  = scenario.inputAudit("input-audit");
            val output = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.decide(decision, List.of(input), List.of()))
                    .expectError(AccessDeniedException.class).verify(TIMEOUT);

            assertThat(scenario.calls).isEmpty();
            assertThat(scenario.trace).doesNotContain("input-audit");
        }
    }

    @Nested
    @DisplayName("Failure output through the public annotation")
    class FailureOutput {

        @Test
        @DisplayName("C5: public output mapping uses the current permit without invoking the RAP again")
        void whenPublicOutputPlanChangesThenApplyItWithoutRestart() {
            val first  = scenario.outputMapper("first-output", 10, value -> "first:" + value);
            val second = scenario.outputMapper("second-output", 10, value -> "second:" + value);
            val output = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.permit(first)).then(() -> scenario.emit(0, "reading"))
                    .expectNext("first:reading").then(() -> scenario.permit(second))
                    .then(() -> scenario.emit(0, "reading")).expectNext("second:reading")
                    .then(() -> assertThat(scenario.calls).hasSize(1)).thenCancel().verify(TIMEOUT);
        }

        @ValueSource(booleans = { false, true })
        @ParameterizedTest(name = "F3/F4: suppress failure output {0}")
        @DisplayName("Failure output may be replaced or suppressed without allowing subsequent data")
        void whenFailureOutputIsTransformedThenStillTerminatePublicSubscription(boolean suppress) {
            val failure = scenario.outputFailure();
            val mapping = scenario.outputMapper("failure-output", 20, value -> {
                            scenario.observed.add(value);
                            if (value instanceof AccessDeniedException) {
                                return suppress ? null : "redacted-failure";
                            }
                            return value;
                        });
            val output  = monitor.watch("all-devices", "09:00");

            val verifier = StepVerifier.create(output).then(() -> scenario.permit(failure, mapping))
                    .then(() -> scenario.emit(0, "malformed-reading"));
            if (suppress) {
                verifier.expectComplete().verify(TIMEOUT);
            } else {
                verifier.expectNext("redacted-failure").expectComplete().verify(TIMEOUT);
            }

            assertThat(scenario.observed).singleElement().isInstanceOf(AccessDeniedException.class);
            assertThat(scenario.calls.getFirst().cancellations).hasValue(1);
            assertThat(scenario.pdpCancellations).hasValue(1);
            scenario.permit();
            scenario.emit(0, "late-reading");
            assertThat(scenario.calls).hasSize(1);
        }
    }

    @Nested
    @DisplayName("Suspension, resumption, and subscription isolation")
    class SubscriptionLifecycle {

        @Test
        @DisplayName("R15/C5: synchronous and deferred RAP code sees the active enforcement plan")
        void whenOnlyOutputPlanChangesThenLazyRapCodeSeesTheNewPlan() {
            val first = scenario.outputMapper("first-plan", 10, value -> "first:" + value);
            val second = scenario.outputMapper("second-plan", 10, value -> "second:" + value);
            scenario.rapBody = call -> {
                scenario.observed.add(EnforcementPlanContext.currentBlocking().orElseThrow());
                return call.source.flux().concatMap(value -> Flux.deferContextual(context -> {
                    scenario.observed.add(EnforcementPlanContext.currentReactor(context).orElseThrow());
                    return Flux.just(value);
                }));
            };
            val output = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.permit(first)).then(() -> scenario.emit(0, "reading"))
                    .expectNext("first:reading").then(() -> scenario.permit(second))
                    .then(() -> scenario.emit(0, "reading")).expectNext("second:reading")
                    .thenCancel().verify(TIMEOUT);

            assertThat(scenario.calls).hasSize(1);
            assertThat(scenario.observed).hasSize(3);
            assertThat(scenario.observed.get(0)).isSameAs(scenario.observed.get(1))
                    .isNotSameAs(scenario.observed.get(2));
        }

        @Test
        @DisplayName("R6: default StreamEnforce disposes the RAP on suspension while retaining the PDP")
        void whenSuspendedThenDisposeRapWithoutClosingPdp() {
            val output = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(scenario::permit).then(scenario::suspend).then(() -> {
                assertThat(scenario.calls.getFirst().cancellations).hasValue(1);
                scenario.assertPdpOpen();
            }).thenCancel().verify(TIMEOUT);
        }

        @ValueSource(booleans = { false, true })
        @ParameterizedTest(name = "R7/R8/R14: changed resume input {0}")
        @DisplayName("Resumption makes a fresh call and preserves the logical window start")
        void whenResumingThenReinvokeWithCurrentScopeAndOriginalWindowStart(boolean changed) {
            val first   = scenario.inputScope("device-a");
            val resumed = changed ? scenario.inputScope("device-b") : first;
            val output  = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.permit(first)).then(scenario::suspend)
                    .then(() -> scenario.permit(resumed)).then(() -> {
                        assertThat(scenario.calls).extracting(call -> call.scope).containsExactly("device-a",
                                changed ? "device-b" : "device-a");
                        assertThat(scenario.calls).extracting(call -> call.start).containsOnly("09:00");
                    }).thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("R20: suspension before the first permit does not execute its input plan")
        void whenSuspendedBeforeFirstPermitThenOnlyPermitInputRuns() {
            val suspended = scenario.inputAudit("suspended-input");
            val permitted = scenario.inputAudit("permitted-input");
            val output    = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(() -> scenario.suspend(suspended)).then(() -> {
                assertThat(scenario.calls).isEmpty();
                assertThat(scenario.trace).doesNotContain("suspended-input");
            }).then(() -> scenario.permit(permitted)).then(() -> {
                assertThat(scenario.trace).containsSubsequence("permitted-input", "invoke:all-devices");
                assertThat(scenario.calls).hasSize(1);
            }).thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("R15: two subscribers to one returned Flux own independent RAP invocations")
        void whenTwoSubscribersShareReturnedFluxThenKeepIndependentInvocations() {
            val output   = monitor.watch("all-devices", "09:00");
            val combined = Flux.merge(output, output);

            StepVerifier.create(combined).then(scenario::permit).then(() -> {
                assertThat(scenario.calls).hasSize(2);
                scenario.emit(0, "first-subscriber");
                scenario.emit(1, "second-subscriber");
            }).expectNext("first-subscriber", "second-subscriber").thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("R15: a cold RAP can synchronously emit and complete during initial setup")
        void whenRapCompletesDuringSetupThenDeliverOnceAndReleasePdp() {
            scenario.rapBody = call -> Flux.just("initial-reading");
            val output = monitor.watch("all-devices", "09:00");

            StepVerifier.create(output).then(scenario::permit).expectNext("initial-reading").expectComplete()
                    .verify(TIMEOUT);

            assertThat(scenario.calls).hasSize(1);
            assertThat(scenario.pdpCancellations).hasValue(1);
        }

    }

    static class ActivityMonitor {
        private final StreamingPepScenarioSupport scenario;

        ActivityMonitor(StreamingPepScenarioSupport scenario) {
            this.scenario = scenario;
        }

        @StreamEnforce
        public Flux<Object> watch(String scope, String start) {
            return scenario.open(scope, start);
        }
    }

    @SaplPepTestApp
    @EnableReactiveSaplMethodSecurity
    static class TestApplication {

        @Bean
        StreamingPepScenarioSupport scenario() {
            return new StreamingPepScenarioSupport();
        }

        @Bean
        ActivityMonitor activityMonitor(StreamingPepScenarioSupport scenario) {
            return new ActivityMonitor(scenario);
        }

        @Bean
        ConstraintHandlerProvider scenarioHandlers(StreamingPepScenarioSupport scenario) {
            return scenario::handlers;
        }
    }
}
