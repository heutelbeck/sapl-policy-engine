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

import static io.sapl.spring.pep.streaming.StreamingPepScenarioSupport.OUTPUT;
import static io.sapl.spring.pep.streaming.StreamingPepScenarioSupport.TIMEOUT;
import static java.util.function.UnaryOperator.identity;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.api.model.Value;
import io.sapl.api.pdp.Decision;
import io.sapl.spring.pep.constraints.Signal;
import lombok.val;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Timeout(10)
@DisplayName("Streaming item drops and sticky obligation failures")
class StreamingOutputFailureContractTests {

    private final StreamingPepScenarioSupport scenario = new StreamingPepScenarioSupport();

    @AfterEach
    void releaseSubscriptions() {
        scenario.close();
    }

    @Test
    @DisplayName("F1: dropping an item stops its handler chain but a later item is still processed")
    void whenItemIsDroppedThenSkipRemainingHandlersWithoutTerminating() {
        val filter = scenario.outputMapper("filter", 10, value -> "skip".equals(value) ? null : value);
        val tail   = scenario.runner("tail", OUTPUT, 20, () -> {});
        val output = scenario.pipeline(false);

        StepVerifier.create(output).then(() -> scenario.permit(filter, tail)).then(() -> scenario.emit(0, "skip"))
                .then(() -> {
                    assertThat(scenario.trace).doesNotContain("tail");
                    scenario.assertPdpOpen();
                    scenario.emit(0, "keep");
                }).expectNext("keep").then(() -> assertThat(scenario.trace).contains("tail")).thenCancel()
                .verify(TIMEOUT);
    }

    @Test
    @DisplayName("F1: a filtered item does not consume the outstanding subscriber demand")
    void whenItemIsDroppedThenRequestAReplacementForTheSameDemand() {
        val filter = scenario.outputMapper("filter", 10, value -> "skip".equals(value) ? null : value);
        val output = scenario.pipeline(false);

        StepVerifier.create(output, 0).thenRequest(1).then(() -> scenario.permit(filter))
                .then(() -> scenario.emit(0, "skip"))
                .then(() -> assertThat(scenario.calls.getFirst().requests).containsExactly(1L, 1L))
                .then(() -> scenario.emit(0, "keep")).expectNext("keep").thenCancel().verify(TIMEOUT);
    }

    @Test
    @DisplayName("F2: an obligation failure preserves best effort and then closes both upstreams")
    void whenOutputObligationFailsThenRunRemainingHandlersAndTerminate() {
        val failure = scenario.runner("failed-runner", OUTPUT, 10, () -> {
                        throw new IllegalStateException("Audit unavailable");
                    });
        val tail    = scenario.runner("tail", OUTPUT, 20, () -> {});
        val output  = scenario.pipeline(false);

        StepVerifier.create(output).then(() -> scenario.permit(failure, tail)).then(() -> scenario.emit(0, "reading"))
                .expectError(AccessDeniedException.class).verify(TIMEOUT);

        assertThat(scenario.trace).containsSubsequence("failed-runner", "tail");
        assertClosedWithoutRevival();
    }

    @Test
    @DisplayName("F3: a mapper can replace a failure value, but the replacement is the last item")
    void whenFailedMappingIsReplacedThenDeliverReplacementAndComplete() {
        val failure     = scenario.outputFailure();
        val replacement = scenario.outputMapper("replace-failure", 20, value -> {
                            scenario.observed.add(value);
                            return value instanceof AccessDeniedException ? "redacted-failure" : value;
                        });
        val output      = scenario.pipeline(false);

        StepVerifier.create(output).then(() -> scenario.permit(failure, replacement))
                .then(() -> scenario.emit(0, "malformed-reading")).expectNext("redacted-failure").expectComplete()
                .verify(TIMEOUT);

        assertThat(scenario.observed).singleElement().isInstanceOf(AccessDeniedException.class);
        assertClosedWithoutRevival();
    }

    @Test
    @DisplayName("F4: filtering a failure suppresses its notification, not mandatory termination")
    void whenFailedMappingIsDroppedThenCompleteSilentlyAndStopTheChain() {
        val failure = scenario.outputFailure();
        val filter  = scenario.outputMapper("suppress-failure", 20, value -> {
                        scenario.observed.add(value);
                        return value instanceof AccessDeniedException ? null : value;
                    });
        val tail    = scenario.runner("tail", OUTPUT, 30, () -> {});
        val output  = scenario.pipeline(false);

        StepVerifier.create(output).then(() -> scenario.permit(failure, filter, tail))
                .then(() -> scenario.emit(0, "malformed-reading")).expectComplete().verify(TIMEOUT);

        assertThat(scenario.observed).singleElement().isInstanceOf(AccessDeniedException.class);
        assertThat(scenario.trace).doesNotContain("tail");
        assertClosedWithoutRevival();
    }

    @Test
    @DisplayName("F4: suppressed failure still executes terminal cleanup exactly once")
    void whenFailureIsSuppressedThenExecuteTerminalHandlers() {
        val failure = scenario.outputFailure();
        val filter = scenario.outputMapper("suppress", 20, value -> null);
        val termination = scenario.runner("termination", Signal.TerminationSignal.SIGNAL_TYPE, 10, () -> {});
        val after = scenario.runner("after-termination", Signal.AfterTerminationSignal.SIGNAL_TYPE, 10, () -> {});
        val output = scenario.pipeline(false);

        StepVerifier.create(output).then(() -> scenario.permit(failure, filter, termination, after))
                .then(() -> scenario.emit(0, "reading")).expectComplete().verify(TIMEOUT);

        assertThat(scenario.trace).containsSubsequence("termination", "dispose:all-devices", "after-termination");
        assertClosedWithoutRevival();
    }

    @Test
    @DisplayName("F4: suppressing an item failure does not suppress a later cleanup obligation failure")
    void whenTerminalCleanupFailsThenDenyInsteadOfCompletingSilently() {
        val failure = scenario.outputFailure();
        val filter = scenario.outputMapper("suppress", 20, value -> null);
        val cleanup = scenario.runner("cleanup-failure", Signal.TerminationSignal.SIGNAL_TYPE, 10, () -> {
            throw new IllegalStateException("Cleanup obligation failed");
        });
        val output = scenario.pipeline(false);

        StepVerifier.create(output).then(() -> scenario.permit(failure, filter, cleanup))
                .then(() -> scenario.emit(0, "reading")).expectError(AccessDeniedException.class).verify(TIMEOUT);

        assertClosedWithoutRevival();
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "R11/F2: obligation fails after reentrant replacement {0}")
    @DisplayName("Replacement during output handling discards stale data but never an established failure")
    void whenOutputHandlerReplacesSourceThenPreserveFailureButNotStaleData(boolean fail) {
        val changed = scenario.inputAudit("changed-input");
        val mapper = scenario.outputMapper("restart", 10, value -> {
            scenario.permit(changed);
            if (fail) {
                throw new IllegalStateException("Obligation failed after replacement");
            }
            return value;
        });
        val output = scenario.pipeline(false);
        val verifier = StepVerifier.create(output).then(() -> scenario.permit(mapper))
                .then(() -> scenario.emit(0, "stale"));

        if (fail) {
            verifier.expectError(AccessDeniedException.class).verify(TIMEOUT);
            assertThat(scenario.calls).hasSize(2)
                    .allSatisfy(call -> assertThat(call.cancellations).hasValue(1));
            assertThat(scenario.pdpCancellations).hasValue(1);
        } else {
            verifier.then(() -> scenario.emit(1, "current")).expectNext("current")
                    .thenCancel().verify(TIMEOUT);
        }
    }

    @Test
    @DisplayName("F5: a second mapper failure remains terminal even if a later mapper supplies a replacement")
    void whenTwoMappersFailThenContinueBestEffortWithoutClearingFailure() {
        val first       = scenario.outputFailure();
        val second      = scenario.outputMapper("second-failure", 20, value -> {
                            scenario.observed.add(value);
                            throw new IllegalArgumentException("Cannot map failure");
                        });
        val replacement = scenario.outputMapper("replace-failure", 30, value -> {
                            scenario.observed.add(value);
                            return "redacted-failure";
                        });
        val output      = scenario.pipeline(false);

        StepVerifier.create(output).then(() -> scenario.permit(first, second, replacement))
                .then(() -> scenario.emit(0, "malformed-reading")).expectNext("redacted-failure").expectComplete()
                .verify(TIMEOUT);

        assertThat(scenario.observed).hasSize(2)
                .allSatisfy(value -> assertThat(value).isInstanceOf(AccessDeniedException.class));
        assertThat(scenario.trace).containsSubsequence("output-failure", "second-failure", "replace-failure");
        assertClosedWithoutRevival();
    }

    @Test
    @DisplayName("F6: an error object emitted as ordinary data is not itself an obligation failure")
    void whenRapEmitsErrorAsDataThenDoNotInventEnforcementFailure() {
        val encodedError = new AccessDeniedException("An error record, not a failed handler");
        val output       = scenario.pipeline(false);

        StepVerifier.create(output).then(scenario::permit).then(() -> scenario.emit(0, encodedError))
                .expectNext(encodedError).then(() -> scenario.emit(0, "later-reading")).expectNext("later-reading")
                .then(scenario::assertPdpOpen).thenCancel().verify(TIMEOUT);
    }

    @ValueSource(booleans = { false, true })
    @ParameterizedTest(name = "F7: drop after advice failure {0}")
    @DisplayName("Advice failure remains nonterminal, including when the current item is dropped")
    void whenAdviceFailsThenKeepStreamAlive(boolean drop) {
        val advice = scenario.runner("advice-failure", OUTPUT, 5, () -> {
                       throw new IllegalStateException("Optional audit unavailable");
                   });
        val filter = scenario.outputMapper("filter", 10, value -> drop && "reading".equals(value) ? null : value);
        val tail   = scenario.runner("tail", OUTPUT, 20, () -> {});
        val output = scenario.pipeline(false);

        var verifier = StepVerifier.create(output)
                .then(() -> scenario.decide(Decision.PERMIT, List.of(filter, tail), List.of(advice)))
                .then(() -> scenario.emit(0, "reading"));
        if (drop) {
            verifier = verifier.then(() -> assertThat(scenario.trace).doesNotContain("tail"));
        } else {
            verifier = verifier.expectNext("reading");
        }
        verifier.then(() -> scenario.emit(0, "later-reading")).expectNext("later-reading").then(scenario::assertPdpOpen)
                .thenCancel().verify(TIMEOUT);
    }

    @Test
    @DisplayName("F8: a signal-local mapper conflict fails when an output item reaches it")
    void whenOutputConflictIsReachedThenTerminateAndDisposeBothUpstreams() {
        val first  = scenario.outputMapper("mapper-first", 10, identity());
        val second = scenario.outputMapper("mapper-second", 10, identity());
        val output = scenario.pipeline(false);

        StepVerifier.create(output).then(() -> scenario.permit(first, second))
                .then(() -> assertThat(scenario.calls).hasSize(1)).then(() -> scenario.emit(0, "reading"))
                .expectError(AccessDeniedException.class).verify(TIMEOUT);

        assertClosedWithoutRevival();
    }

    @Test
    @DisplayName("F9: SAPL null is a valid non-null Reactor payload, not an item drop")
    void whenRapEmitsEncodedNullThenDeliverItAsData() {
        val output = scenario.pipeline(false);

        StepVerifier.create(output).then(scenario::permit).then(() -> scenario.emit(0, Value.NULL))
                .expectNext(Value.NULL).then(scenario::assertPdpOpen).thenCancel().verify(TIMEOUT);
    }

    @Test
    @DisplayName("F9: an empty RAP completes the protected stream without emitting an item")
    void whenRapIsEmptyThenCompleteWithoutPayload() {
        scenario.rapBody = call -> Flux.empty();
        val output = scenario.pipeline(false);

        StepVerifier.create(output).then(scenario::permit).expectComplete().verify(TIMEOUT);

        assertThat(scenario.pdpCancellations).hasValue(1);
        assertThat(scenario.downstreamTerminations).hasValue(1);
    }

    private void assertClosedWithoutRevival() {
        val traceSize = scenario.trace.size();
        scenario.permit();
        scenario.emit(0, "late-reading");
        assertThat(scenario.calls).hasSize(1);
        assertThat(scenario.calls.getFirst().cancellations).hasValue(1);
        assertThat(scenario.pdpCancellations).hasValue(1);
        assertThat(scenario.downstreamTerminations).hasValue(1);
        assertThat(scenario.trace).hasSize(traceSize);
    }
}
