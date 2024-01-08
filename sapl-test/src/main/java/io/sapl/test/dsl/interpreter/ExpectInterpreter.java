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

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.AttributeAdjustment;
import io.sapl.test.grammar.sAPLTest.Await;
import io.sapl.test.grammar.sAPLTest.Multiple;
import io.sapl.test.grammar.sAPLTest.Next;
import io.sapl.test.grammar.sAPLTest.NextWithDecision;
import io.sapl.test.grammar.sAPLTest.NextWithMatcher;
import io.sapl.test.grammar.sAPLTest.NoEvent;
import io.sapl.test.grammar.sAPLTest.Once;
import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpectWithMatcher;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
class ExpectInterpreter {

    private final ValInterpreter                          valInterpreter;
    private final AuthorizationDecisionInterpreter        authorizationDecisionInterpreter;
    private final AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreter;
    private final DurationInterpreter                     durationInterpreter;
    private final MultipleAmountInterpreter               multipleAmountInterpreter;

    VerifyStep interpretSingleExpect(final ExpectOrVerifyStep expectOrVerifyStep, final SingleExpect singleExpect) {
        if (expectOrVerifyStep == null || singleExpect == null) {
            throw new SaplTestException("ExpectOrVerifyStep or singleExpect is null");
        }

        final var dslAuthorizationDecision = singleExpect.getDecision();

        final var authorizationDecision = getAuthorizationDecisionFromDSLAuthorizationDecision(
                dslAuthorizationDecision);

        return expectOrVerifyStep.expect(authorizationDecision);
    }

    VerifyStep interpretSingleExpectWithMatcher(final ExpectOrVerifyStep expectOrVerifyStep,
            final SingleExpectWithMatcher singleExpectWithMatcher) {
        if (expectOrVerifyStep == null || singleExpectWithMatcher == null) {
            throw new SaplTestException("ExpectOrVerifyStep or singleExpectWithMatcher is null");
        }

        final var dslAuthorizationDecisionMatcher = singleExpectWithMatcher.getMatcher();

        if (dslAuthorizationDecisionMatcher == null) {
            throw new SaplTestException("SingleExpectWithMatcher does not contain a matcher");
        }

        final var matcher = authorizationDecisionMatcherInterpreter
                .getHamcrestAuthorizationDecisionMatcher(dslAuthorizationDecisionMatcher);

        return expectOrVerifyStep.expect(matcher);
    }

    VerifyStep interpretRepeatedExpect(ExpectOrVerifyStep expectOrVerifyStep, final RepeatedExpect repeatedExpect) {
        if (expectOrVerifyStep == null || repeatedExpect == null) {
            throw new SaplTestException("ExpectOrVerifyStep or repeatedExpect is null");
        }

        final var expectOrAdjustmentSteps = repeatedExpect.getExpectSteps();

        if (expectOrAdjustmentSteps == null || expectOrAdjustmentSteps.isEmpty()) {
            throw new SaplTestException("No ExpectOrAdjustmentStep found");
        }

        for (var expectOrAdjustmentStep : expectOrAdjustmentSteps) {
            if (expectOrAdjustmentStep instanceof Next nextExpect) {
                expectOrVerifyStep = constructNext(expectOrVerifyStep, nextExpect);
            } else if (expectOrAdjustmentStep instanceof NextWithDecision nextWithDecision) {
                final var dslAuthorizationDecision = nextWithDecision.getExpectedDecision();

                expectOrVerifyStep = constructNextWithDecision(expectOrVerifyStep, dslAuthorizationDecision);
            } else if (expectOrAdjustmentStep instanceof NextWithMatcher nextWithMatcher) {
                expectOrVerifyStep = constructNextWithMatcher(expectOrVerifyStep, nextWithMatcher);
            } else if (expectOrAdjustmentStep instanceof AttributeAdjustment attributeAdjustment) {
                final var returnValue = valInterpreter.getValFromValue(attributeAdjustment.getReturnValue());
                expectOrVerifyStep = expectOrVerifyStep.thenAttribute(attributeAdjustment.getAttribute(), returnValue);
            } else if (expectOrAdjustmentStep instanceof Await await) {
                final var duration = durationInterpreter.getJavaDurationFromDuration(await.getDuration());
                expectOrVerifyStep = expectOrVerifyStep.thenAwait(duration);
            } else if (expectOrAdjustmentStep instanceof NoEvent noEvent) {
                final var duration = durationInterpreter.getJavaDurationFromDuration(noEvent.getDuration());
                expectOrVerifyStep = expectOrVerifyStep.expectNoEvent(duration);
            } else {
                throw new SaplTestException("Unknown type of ExpectOrAdjustmentStep");
            }
        }
        return expectOrVerifyStep;
    }

    private AuthorizationDecision getAuthorizationDecisionFromDSLAuthorizationDecision(
            final io.sapl.test.grammar.sAPLTest.AuthorizationDecision authorizationDecision) {
        if (authorizationDecision == null) {
            throw new SaplTestException("AuthorizationDecision is null");
        }

        final var decision    = authorizationDecision.getDecision();
        final var resource    = authorizationDecision.getResource();
        final var obligations = authorizationDecision.getObligations();
        final var advice      = authorizationDecision.getAdvice();

        return authorizationDecisionInterpreter.constructAuthorizationDecision(decision, resource, obligations, advice);
    }

    private ExpectOrVerifyStep constructNext(final ExpectOrVerifyStep expectOrVerifyStep, final Next nextExpect) {
        int amount;

        if (nextExpect.getAmount() instanceof Multiple multiple) {
            amount = multipleAmountInterpreter.getAmountFromMultipleAmountString(multiple.getAmount());
        } else if (nextExpect.getAmount() instanceof Once) {
            amount = 1;
        } else {
            throw new SaplTestException("Unknown type of NumericAmount");
        }

        return switch (nextExpect.getExpectedDecision()) {
        case PERMIT -> expectOrVerifyStep.expectNextPermit(amount);
        case DENY -> expectOrVerifyStep.expectNextDeny(amount);
        case INDETERMINATE -> expectOrVerifyStep.expectNextIndeterminate(amount);
        default -> expectOrVerifyStep.expectNextNotApplicable(amount);
        };
    }

    private ExpectOrVerifyStep constructNextWithMatcher(final ExpectOrVerifyStep expectOrVerifyStep,
            final NextWithMatcher nextWithMatcher) {
        final var matchers = nextWithMatcher.getMatcher();

        if (matchers == null || matchers.isEmpty()) {
            throw new SaplTestException("No AuthorizationDecisionMatcher found");
        }

        final var actualMatchers = matchers.stream()
                .map(authorizationDecisionMatcherInterpreter::getHamcrestAuthorizationDecisionMatcher)
                .<Matcher<AuthorizationDecision>>toArray(Matcher[]::new);

        return expectOrVerifyStep.expectNext(actualMatchers.length == 1 ? actualMatchers[0] : allOf(actualMatchers));
    }

    private ExpectOrVerifyStep constructNextWithDecision(final ExpectOrVerifyStep expectOrVerifyStep,
            final io.sapl.test.grammar.sAPLTest.AuthorizationDecision dslAuthorizationDecision) {
        final var authorizationDecision = getAuthorizationDecisionFromDSLAuthorizationDecision(
                dslAuthorizationDecision);

        return expectOrVerifyStep.expectNext(authorizationDecision);
    }
}
