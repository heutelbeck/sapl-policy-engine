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

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.reactivestreams.Subscription;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.Signal;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Timeout(10)
@DisplayName("Streaming adapter lifecycle contract")
class StreamingPipelineLifecycleContractTests {

    private final StreamingPepScenarioSupport scenario = new StreamingPepScenarioSupport();

    @AfterEach
    void releaseSubscriptions() {
        scenario.close();
    }

    @Nested
    @DisplayName("Permit updates")
    class PermitUpdates {

        @Test
        @DisplayName("C1: rebuilding equivalent input handlers keeps the existing source")
        void whenInputPlanMatchesThenKeepRapAndPdpSubscriptions() {
            val input  = scenario.inputAudit("input-audit");
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(() -> scenario.permit(input)).then(() -> scenario.permit(input))
                    .then(() -> {
                        assertThat(scenario.calls).hasSize(1);
                        assertThat(scenario.calls.getFirst().cancellations).hasValue(0);
                        assertThat(scenario.pdpSubscriptions).hasValue(1);
                    }).thenCancel().verify(TIMEOUT);
        }

        @ValueSource(strings = { "add", "remove", "order", "tag", "duplicates" })
        @ParameterizedTest(name = "C3/C4/C8: input {0}")
        @DisplayName("An input-plan change restarts even when argument values would be unchanged")
        void whenInputScheduleChangesThenReplaceSource(String change) {
            val first  = scenario.inputAudit("audit-first");
            val second = scenario.inputAudit("audit-second");
            val before = "add".equals(change) ? List.<Value>of() : List.of(first, second);
            val after  = switch (change) {
                       case "remove"     -> List.<Value>of();
                       case "order"      -> List.of(second, first);
                       case "duplicates" -> List.of(first, second, first);
                       default           -> List.of(first, second);
                       };
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(() -> scenario.decide(Decision.PERMIT, before, List.of())).then(() -> {
                if ("tag".equals(change)) {
                    scenario.decide(Decision.PERMIT, List.of(), after);
                } else {
                    scenario.decide(Decision.PERMIT, after, List.of());
                }
            }).then(() -> {
                assertThat(scenario.calls).as("one replacement invocation").hasSize(2);
                assertThat(scenario.calls.getFirst().cancellations).hasValue(1);
                assertThat(scenario.trace).containsSubsequence("dispose:all-devices", "invoke:all-devices");
                scenario.assertPdpOpen();
                scenario.emit(1, "new-source");
            }).expectNext("new-source").thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("C5: output-only changes take effect without restarting the RAP")
        void whenOnlyOutputPlanChangesThenReuseSourceWithNewMapping() {
            val first  = scenario.outputMapper("prefix-first", 10, value -> "first:" + value);
            val second = scenario.outputMapper("prefix-second", 10, value -> "second:" + value);
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(() -> scenario.permit(first)).then(() -> scenario.emit(0, "reading"))
                    .expectNext("first:reading").then(() -> scenario.permit(second))
                    .then(() -> scenario.emit(0, "reading")).expectNext("second:reading")
                    .then(() -> assertThat(scenario.calls).hasSize(1)).thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("C7/R4: conflicting input mappers terminate instead of retaining the old source")
        void whenUpdatedInputMappersConflictThenRetireSourceWithoutReplacement() {
            val first  = scenario.inputScope("device-a");
            val second = scenario.inputScope("device-b");
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(scenario::permit).then(() -> scenario.permit(first, second))
                    .then(() -> assertThat(scenario.calls.getFirst().cancellations)
                            .as("conflicting input cannot leave the original source active").hasValue(1))
                    .expectError(AccessDeniedException.class).verify(TIMEOUT);

            assertThat(scenario.calls).hasSize(1);
        }

        @Test
        @DisplayName("R12: an internal permit-to-permit restart emits no extra grant notification")
        void whenAccessAwarePermitChangesThenRestartWithoutAdditionalGrant() {
            val first       = scenario.inputAudit("input-first");
            val second      = scenario.inputAudit("input-second");
            val transitions = new ArrayList<Throwable>();
            val output      = scenario.pipeline(true).onErrorContinue((error, value) -> transitions.add(error));

            StepVerifier.create(output).then(() -> scenario.permit(first)).then(() -> scenario.permit(second))
                    .then(() -> {
                        assertThat(transitions).singleElement().isInstanceOf(AccessGrantedException.class);
                        assertThat(scenario.calls).hasSize(2);
                        assertThat(scenario.downstreamTerminations).hasValue(0);
                    }).thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("R12/R4: failed initial input does not announce a successful grant")
        void whenInitialInputFailsThenDoNotEmitGranted() {
            val failure     = scenario.inputFailure();
            val transitions = new ArrayList<Throwable>();
            val output      = scenario.pipeline(true).onErrorContinue((error, value) -> transitions.add(error));

            StepVerifier.create(output).then(() -> scenario.permit(failure))
                    .then(() -> assertThat(transitions).as("failed input is not a successful grant").isEmpty())
                    .expectError(AccessDeniedException.class).verify(TIMEOUT);

            assertThat(scenario.calls).isEmpty();
        }

        @Test
        @DisplayName("R2: replacement receives only the remaining downstream demand")
        void whenRestartingWithOutstandingDemandThenTransferRemainingDemand() {
            val changed = scenario.inputAudit("new-input");
            val output  = scenario.pipeline(false);

            StepVerifier.create(output, 0).thenRequest(2).then(scenario::permit).then(() -> scenario.emit(0, "first"))
                    .expectNext("first").then(() -> scenario.permit(changed)).then(() -> {
                        assertThat(scenario.calls).hasSize(2);
                        assertThat(scenario.calls.get(1).requests).containsExactly(1L);
                        scenario.emit(1, "second");
                    }).expectNext("second").thenCancel().verify(TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Suspension and retired sources")
    class Suspension {

        @Test
        @DisplayName("R6: suspension always disposes the RAP, including through the former retention path")
        void whenSuspendedThenAlwaysDisposeActiveRap() {
            val input  = scenario.inputAudit("suspended-input");
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(scenario::permit).then(() -> scenario.suspend(input)).then(() -> {
                assertThat(scenario.calls.getFirst().cancellations).hasValue(1);
                assertThat(scenario.trace).doesNotContain("suspended-input");
                scenario.assertPdpOpen();
            }).thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("R9: repeated suspensions still execute decision handlers without duplicate disposal")
        void whenRepeatedlySuspendedThenExecuteDecisionsButDisposeOnlyOnce() {
            val audit  = scenario.runner("suspend-audit", Signal.DecisionSignal.SIGNAL_TYPE, 10, () -> {});
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(scenario::permit).then(() -> scenario.suspend(audit))
                    .then(() -> scenario.suspend(audit)).then(() -> {
                        assertThat(scenario.calls).hasSize(1);
                        assertThat(scenario.calls.getFirst().cancellations).hasValue(1);
                        assertThat(scenario.trace.stream().filter("suspend-audit"::equals)).hasSize(2);
                        scenario.assertPdpOpen();
                    }).thenCancel().verify(TIMEOUT);
        }

        @Test
        @DisplayName("R16: a failed decision obligation on SUSPEND is terminal")
        void whenSuspendDecisionObligationFailsThenTerminateBothSubscriptions() {
            val failure = scenario.decisionFailure();
            val output  = scenario.pipeline(false);

            StepVerifier.create(output).then(scenario::permit).then(() -> scenario.suspend(failure))
                    .then(() -> assertThat(scenario.pdpCancellations)
                            .as("failed suspension must terminate rather than remain resumable").hasValue(1))
                    .expectError(AccessDeniedException.class).verify(TIMEOUT);
        }

        @ValueSource(strings = { "item", "error", "complete" })
        @ParameterizedTest(name = "R18: late {0} while suspended")
        @DisplayName("Retired source callbacks cannot terminate a suspended subscription")
        void whenRetiredSourceSignalsDuringSuspensionThenIgnoreIt(String event) {
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(scenario::permit).then(scenario::suspend)
                    .then(() -> emitRetiredEvent(event)).then(() -> {
                        assertThat(scenario.downstreamTerminations).hasValue(0);
                        scenario.assertPdpOpen();
                    }).then(scenario::permit).then(() -> scenario.emit(1, "resumed")).expectNext("resumed").thenCancel()
                    .verify(TIMEOUT);
        }

        @ValueSource(strings = { "item", "error", "complete" })
        @ParameterizedTest(name = "R10: late {0} after replacement on resume")
        @DisplayName("Retired source callbacks cannot affect the replacement source")
        void whenRetiredSourceSignalsAfterResumeThenIgnoreIt(String event) {
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(scenario::permit).then(scenario::suspend).then(scenario::permit)
                    .then(() -> emitRetiredEvent(event)).then(() -> {
                        assertThat(scenario.calls).hasSize(2);
                        assertThat(scenario.downstreamTerminations).hasValue(0);
                        scenario.emit(1, "current");
                    }).expectNext("current").thenCancel().verify(TIMEOUT);
        }

        private void emitRetiredEvent(String event) {
            val source = scenario.calls.getFirst().source;
            switch (event) {
            case "item"     -> source.next("stale");
            case "error"    -> source.error(new IllegalStateException("Retired source failed"));
            case "complete" -> source.complete();
            default         -> throw new IllegalArgumentException(event);
            }
        }
    }

    @Nested
    @DisplayName("Interruption during replacement")
    class InterruptedReplacement {

        @EnumSource(value = Decision.class, names = { "SUSPEND", "DENY" })
        @ParameterizedTest(name = "R11: {0} during input enforcement")
        @DisplayName("A newer decision during input enforcement prevents the pending RAP call")
        void whenInputHandlerProcessesNewDecisionThenDoNotInvokeRap(Decision interruption) {
            val input = scenario.runner("interrupt-input", Signal.InputSignal.SIGNAL_TYPE, 10,
                    () -> scenario.decide(interruption, List.of(), List.of()));
            val output = scenario.pipeline(false);
            val verifier = StepVerifier.create(output).then(() -> scenario.permit(input))
                    .then(() -> assertThat(scenario.calls).isEmpty());

            if (interruption == Decision.DENY) {
                verifier.expectError(AccessDeniedException.class).verify(TIMEOUT);
            } else {
                verifier.then(scenario::permit).then(() -> assertThat(scenario.calls).hasSize(1))
                        .thenCancel().verify(TIMEOUT);
            }
        }

        @ValueSource(strings = { "suspend", "deny" })
        @ParameterizedTest(name = "R11: {0} while retiring the old source")
        @DisplayName("A processed suspension or denial prevents the pending replacement from starting")
        void whenRetirementProcessesNewDecisionThenDoNotActivateEarlierPermit(String interruption) {
            val changed = scenario.inputAudit("changed-input");
            val output  = scenario.pipeline(false);

            val verifier = StepVerifier.create(output).then(scenario::permit).then(() -> {
                scenario.calls.getFirst().cancelAction = () -> {
                    scenario.trace.add("interrupt:" + interruption);
                    if ("suspend".equals(interruption)) {
                        scenario.suspend();
                    } else {
                        scenario.decide(Decision.DENY, List.of(), List.of());
                    }
                };
                scenario.permit(changed);
            }).then(() -> {
                assertThat(scenario.trace).as("the old source must actually be retired")
                        .contains("interrupt:" + interruption);
                assertThat(scenario.calls).as("the interrupted permit cannot invoke a replacement").hasSize(1);
            });
            if ("deny".equals(interruption)) {
                verifier.expectError(AccessDeniedException.class).verify(TIMEOUT);
            } else {
                verifier.then(scenario::assertPdpOpen).then(() -> scenario.permit(changed))
                        .then(() -> assertThat(scenario.calls).hasSize(2)).thenCancel().verify(TIMEOUT);
            }
        }

        @Test
        @DisplayName("R11: client cancellation during retirement cannot be followed by a replacement")
        void whenClientCancelsDuringReplacementThenDoNotReviveSubscription() {
            val changed = scenario.inputAudit("changed-input");
            val client  = new AtomicReference<Subscription>();
            val output  = scenario.pipeline(false).doOnSubscribe(client::set);

            StepVerifier.create(output).then(scenario::permit).then(() -> {
                scenario.calls.getFirst().cancelAction = () -> {
                    scenario.trace.add("client-cancelled");
                    client.get().cancel();
                };
                scenario.permit(changed);
            }).then(() -> {
                assertThat(scenario.trace).contains("client-cancelled");
                assertThat(scenario.calls).hasSize(1);
                assertThat(scenario.pdpCancellations).hasValue(1);
            }).thenCancel().verify(TIMEOUT);
        }
    }

    @Nested
    @DisplayName("Synchronous setup")
    class SynchronousSetup {

        @Test
        @DisplayName("R15: synchronous completion of a replacement is delivered exactly once")
        void whenReplacementCompletesDuringSetupThenDoNotLoseCompletion() {
            val changed = scenario.inputAudit("changed-input");
            scenario.rapBody = call -> scenario.calls.size() == 1 ? call.source.flux() : Flux.just("replacement");
            val output = scenario.pipeline(false);

            StepVerifier.create(output).then(scenario::permit).then(() -> scenario.permit(changed))
                    .then(() -> assertThat(scenario.calls).hasSize(2)).expectNext("replacement").expectComplete()
                    .verify(TIMEOUT);

            assertThat(scenario.downstreamTerminations).hasValue(1);
            assertThat(scenario.pdpCancellations).hasValue(1);
        }
    }
}
