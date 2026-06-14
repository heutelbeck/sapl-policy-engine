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

import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.params.provider.Arguments;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.spring.pep.constraints.EnforcementPlan;
import io.sapl.spring.pep.constraints.EnforcementResult;
import io.sapl.spring.pep.streaming.MealyMachine.DenyKind;
import io.sapl.spring.pep.streaming.MealyMachine.Emission;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.Emit;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitComplete;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitError;
import io.sapl.spring.pep.streaming.MealyMachine.Emission.EmitTransition;
import io.sapl.spring.pep.streaming.MealyMachine.Event;
import io.sapl.spring.pep.streaming.MealyMachine.Event.Cancel;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpDeny;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpError;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpPermit;
import io.sapl.spring.pep.streaming.MealyMachine.Event.PdpSuspend;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapComplete;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapError;
import io.sapl.spring.pep.streaming.MealyMachine.Event.RapItem;
import io.sapl.spring.pep.streaming.MealyMachine.State;
import io.sapl.spring.pep.streaming.MealyMachine.State.Pending;
import io.sapl.spring.pep.streaming.MealyMachine.State.Permitting;
import io.sapl.spring.pep.streaming.MealyMachine.State.Suspended;
import io.sapl.spring.pep.streaming.MealyMachine.State.Terminated;
import io.sapl.spring.pep.streaming.MealyMachine.TransitionReason;
import io.sapl.spring.util.Maybe;
import lombok.experimental.UtilityClass;

/**
 * Shared fixtures, subset providers, and translation helpers for the
 * MealyMachine test suites. Used by:
 * <ul>
 * <li>{@code MealyMachineCellTests} — content checks of one row of δ.</li>
 * <li>{@code MealyMachineUniversalInvariantTests} — Lean theorems universally
 * quantified over a finite subset of S ×
 * Σ.</li>
 * <li>{@code MealyMachineSequenceInvariantTests} — Lean theorems and
 * supplementary properties over multi-step
 * traces.</li>
 * </ul>
 */
@UtilityClass
class MealyTestSupport {

    static final EnforcementPlan PLAN = new EnforcementPlan(Map.of());

    /** Single-emission symbol used in the CSV table. */
    static final String EMIT_VALUE                = "EMIT_VALUE";
    /** Single-emission symbol used in the CSV table. */
    static final String EMIT_ERROR                = "EMIT_ERROR";
    /** Single-emission symbol used in the CSV table. */
    static final String EMIT_COMPLETE             = "EMIT_COMPLETE";
    /** Single-emission symbol used in the CSV table. */
    static final String EMIT_TRANSITION_GRANTED   = "EMIT_TRANSITION_GRANTED";
    /** Single-emission symbol used in the CSV table. */
    static final String EMIT_TRANSITION_SUSPENDED = "EMIT_TRANSITION_SUSPENDED";

    static EnforcementResult<Object> resultPresent(Object value) {
        return new EnforcementResult<>(Maybe.of(value), false);
    }

    static EnforcementResult<Object> resultAbsent() {
        return new EnforcementResult<>(Maybe.absent(), false);
    }

    static EnforcementResult<Object> resultFailed() {
        return new EnforcementResult<>(Maybe.absent(), true);
    }

    static Permitting permitting() {
        return new Permitting(PLAN);
    }

    static PdpPermit pdpPermit() {
        return new PdpPermit(AuthorizationDecision.PERMIT, PLAN);
    }

    static PdpSuspend pdpSuspend() {
        return new PdpSuspend(AuthorizationDecision.SUSPEND, PLAN,
                new TransitionReason.Suspended(AuthorizationDecision.SUSPEND));
    }

    static PdpDeny pdpDeny() {
        return new PdpDeny(AuthorizationDecision.DENY, PLAN, DenyKind.POLICY_DENIED);
    }

    static PdpError pdpError() {
        return new PdpError(new RuntimeException("pdp boom"));
    }

    static RapItem rapItemPresent() {
        return new RapItem("payload", resultPresent("post-mapper"));
    }

    static RapItem rapItemAbsent() {
        return new RapItem("payload", resultAbsent());
    }

    static RapItem rapItemFailed() {
        return new RapItem("payload", resultFailed());
    }

    static RapError rapError() {
        return new RapError(new RuntimeException("rap boom"));
    }

    static State stateByName(String name) {
        return switch (name) {
        case "Pending"    -> Pending.INSTANCE;
        case "Permitting" -> permitting();
        case "Suspended"  -> Suspended.INSTANCE;
        case "Terminated" -> Terminated.INSTANCE;
        default           -> throw new IllegalArgumentException("Unknown state: " + name);
        };
    }

    static Event eventByName(String name, String outcome) {
        return switch (name) {
        case "PdpPermit"   -> pdpPermit();
        case "PdpSuspend"  -> pdpSuspend();
        case "PdpDeny"     -> pdpDeny();
        case "PdpError"    -> pdpError();
        case "RapError"    -> rapError();
        case "RapComplete" -> RapComplete.INSTANCE;
        case "Cancel"      -> Cancel.INSTANCE;
        case "RapItem"     -> rapItemByOutcome(outcome);
        default            -> throw new IllegalArgumentException("Unknown event: " + name);
        };
    }

    static String emissionKind(Emission emission) {
        return switch (emission) {
        case Emit ignored                                 -> EMIT_VALUE;
        case EmitError ignored                            -> EMIT_ERROR;
        case EmitComplete ignored                         -> EMIT_COMPLETE;
        case EmitTransition(TransitionReason.Granted g)   -> EMIT_TRANSITION_GRANTED;
        case EmitTransition(TransitionReason.Suspended s) -> EMIT_TRANSITION_SUSPENDED;
        };
    }

    // ---- Subset providers (@MethodSource-compatible) ----

    static Stream<Arguments> nonTerminatedStates() {
        return Stream.of(arguments("Pending", Pending.INSTANCE), arguments("Permitting", permitting()),
                arguments("Suspended", Suspended.INSTANCE));
    }

    static Stream<Arguments> allStates() {
        return Stream.of(arguments("Pending", Pending.INSTANCE), arguments("Permitting", permitting()),
                arguments("Suspended", Suspended.INSTANCE), arguments("Terminated", Terminated.INSTANCE));
    }

    static Stream<Arguments> lifecycleTerminators() {
        return Stream.of(arguments("Cancel", Cancel.INSTANCE), arguments("RapComplete", RapComplete.INSTANCE),
                arguments("RapError", rapError()), arguments("PdpError", pdpError()));
    }

    static Stream<Arguments> itemOutcomes() {
        return Stream.of(arguments("Present", rapItemPresent()), arguments("Absent", rapItemAbsent()),
                arguments("Failed", rapItemFailed()));
    }

    static Stream<Arguments> allEvents() {
        return Stream.of(arguments("PdpPermit", pdpPermit()), arguments("PdpSuspend", pdpSuspend()),
                arguments("PdpDeny", pdpDeny()), arguments("PdpError", pdpError()),
                arguments("RapItem-Present", rapItemPresent()), arguments("RapItem-Absent", rapItemAbsent()),
                arguments("RapItem-Failed", rapItemFailed()), arguments("RapError", rapError()),
                arguments("RapComplete", RapComplete.INSTANCE), arguments("Cancel", Cancel.INSTANCE));
    }

    static Stream<Arguments> nonTerminatedStateAndLifecycleTerminator() {
        return nonTerminatedStates().flatMap(stateArgs -> {
            var stateName  = stateArgs.get()[0];
            var stateValue = stateArgs.get()[1];
            return lifecycleTerminators()
                    .map(eventArgs -> arguments(stateName, eventArgs.get()[0], stateValue, eventArgs.get()[1]));
        });
    }

    private static RapItem rapItemByOutcome(String outcome) {
        return switch (outcome) {
        case "Present" -> rapItemPresent();
        case "Absent"  -> rapItemAbsent();
        case "Failed"  -> rapItemFailed();
        default        -> throw new IllegalArgumentException("Unknown RapItem outcome: " + outcome);
        };
    }
}
