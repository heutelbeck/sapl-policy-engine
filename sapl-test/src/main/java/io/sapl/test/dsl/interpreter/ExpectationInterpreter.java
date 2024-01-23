/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.test.dsl.interpreter;

import static org.hamcrest.Matchers.allOf;

import java.util.Collection;

import org.hamcrest.Matcher;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.AttributeAdjustment;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionMatcher;
import io.sapl.test.grammar.sapltest.Await;
import io.sapl.test.grammar.sapltest.Multiple;
import io.sapl.test.grammar.sapltest.Next;
import io.sapl.test.grammar.sapltest.NextWithDecision;
import io.sapl.test.grammar.sapltest.NextWithMatcher;
import io.sapl.test.grammar.sapltest.NoEvent;
import io.sapl.test.grammar.sapltest.Once;
import io.sapl.test.grammar.sapltest.RepeatedExpect;
import io.sapl.test.grammar.sapltest.SingleExpect;
import io.sapl.test.grammar.sapltest.SingleExpectWithMatcher;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.VerifyStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ExpectationInterpreter {

    private final ValueInterpreter                        valueInterpreter;
    private final AuthorizationDecisionInterpreter        authorizationDecisionInterpreter;
    private final AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreter;
    private final DurationInterpreter                     durationInterpreter;
    private final MultipleInterpreter                     multipleInterpreter;

    VerifyStep interpretSingleExpect(final ExpectStep expectStep, final SingleExpect singleExpect) {
        if (expectStep == null || singleExpect == null) {
            throw new SaplTestException("ExpectOrVerifyStep or singleExpect is null");
        }

        final var dslAuthorizationDecision = singleExpect.getDecision();

        final var authorizationDecision = getAuthorizationDecisionFromDSLAuthorizationDecision(
                dslAuthorizationDecision);

        return expectStep.expect(authorizationDecision);
    }

    VerifyStep interpretSingleExpectWithMatcher(final ExpectStep expectStep,
            final SingleExpectWithMatcher singleExpectWithMatcher) {

        if (expectStep == null || singleExpectWithMatcher == null) {
            throw new SaplTestException("ExpectOrVerifyStep or singleExpectWithMatcher is null");
        }

        final var matchers = singleExpectWithMatcher.getMatchers();

        final var actualMatcher = getCombinedMatcher(matchers);

        return expectStep.expect(actualMatcher);
    }

    private Matcher<AuthorizationDecision> getCombinedMatcher(
            final Collection<AuthorizationDecisionMatcher> authorizationDecisionMatchers) {

        if (authorizationDecisionMatchers == null || authorizationDecisionMatchers.isEmpty()) {
            throw new SaplTestException("No AuthorizationDecisionMatcher found");
        }

        final var actualMatchers = authorizationDecisionMatchers.stream()
                .map(authorizationDecisionMatcherInterpreter::getHamcrestAuthorizationDecisionMatcher)
                .<Matcher<AuthorizationDecision>>toArray(Matcher[]::new);

        return actualMatchers.length == 1 ? actualMatchers[0] : allOf(actualMatchers);
    }

    VerifyStep interpretRepeatedExpect(ExpectStep expectStep, final RepeatedExpect repeatedExpect) {
        if (expectStep == null || repeatedExpect == null) {
            throw new SaplTestException("ExpectOrVerifyStep or repeatedExpect is null");
        }

        final var expectOrAdjustmentSteps = repeatedExpect.getExpectSteps();

        if (expectOrAdjustmentSteps == null || expectOrAdjustmentSteps.isEmpty()) {
            throw new SaplTestException("No ExpectOrAdjustmentStep found");
        }

        for (var expectOrAdjustmentStep : expectOrAdjustmentSteps) {
            if (expectOrAdjustmentStep instanceof Next nextExpect) {
                expectStep = constructNext(expectStep, nextExpect);
            } else if (expectOrAdjustmentStep instanceof NextWithDecision nextWithDecision) {
                final var authorizationDecision = nextWithDecision.getExpectedDecision();

                expectStep = constructNextWithDecision(expectStep, authorizationDecision);
            } else if (expectOrAdjustmentStep instanceof NextWithMatcher nextWithMatcher) {
                expectStep = constructNextWithMatcher(expectStep, nextWithMatcher);
            } else if (expectOrAdjustmentStep instanceof AttributeAdjustment attributeAdjustment) {
                final var returnValue = valueInterpreter.getValFromValue(attributeAdjustment.getReturnValue());
                expectStep = expectStep.thenAttribute(attributeAdjustment.getAttribute(), returnValue);
            } else if (expectOrAdjustmentStep instanceof Await await) {
                final var duration = durationInterpreter.getJavaDurationFromDuration(await.getDuration());
                expectStep = expectStep.thenAwait(duration);
            } else if (expectOrAdjustmentStep instanceof NoEvent noEvent) {
                final var duration = durationInterpreter.getJavaDurationFromDuration(noEvent.getDuration());
                expectStep = expectStep.expectNoEvent(duration);
            } else {
                throw new SaplTestException("Unknown type of ExpectOrAdjustmentStep");
            }
        }
        return (VerifyStep) expectStep;
    }

    private AuthorizationDecision getAuthorizationDecisionFromDSLAuthorizationDecision(
            final io.sapl.test.grammar.sapltest.AuthorizationDecision authorizationDecision) {
        if (authorizationDecision == null) {
            throw new SaplTestException("AuthorizationDecision is null");
        }

        final var decision    = authorizationDecision.getDecision();
        final var resource    = authorizationDecision.getResource();
        final var obligations = authorizationDecision.getObligations();
        final var advice      = authorizationDecision.getAdvice();

        return authorizationDecisionInterpreter.constructAuthorizationDecision(decision, resource, obligations, advice);
    }

    private ExpectStep constructNext(final ExpectStep expectStep, final Next nextExpect) {
        int amount;

        if (nextExpect.getAmount() instanceof Multiple multiple) {
            amount = multipleInterpreter.getAmountFromMultiple(multiple);
        } else if (nextExpect.getAmount() instanceof Once) {
            amount = 1;
        } else {
            throw new SaplTestException("Unknown type of NumericAmount");
        }

        return switch (nextExpect.getExpectedDecision()) {
        case PERMIT -> expectStep.expectNextPermit(amount);
        case DENY -> expectStep.expectNextDeny(amount);
        case INDETERMINATE -> expectStep.expectNextIndeterminate(amount);
        default -> expectStep.expectNextNotApplicable(amount);
        };
    }

    private ExpectStep constructNextWithMatcher(final ExpectStep expectStep, final NextWithMatcher nextWithMatcher) {
        final var matchers = nextWithMatcher.getMatcher();

        final var actualMatcher = getCombinedMatcher(matchers);

        return expectStep.expectNext(actualMatcher);
    }

    private ExpectStep constructNextWithDecision(final ExpectStep expectStep,
            final io.sapl.test.grammar.sapltest.AuthorizationDecision dslAuthorizationDecision) {
        final var authorizationDecision = getAuthorizationDecisionFromDSLAuthorizationDecision(
                dslAuthorizationDecision);

        return expectStep.expectNext(authorizationDecision);
    }
}
