package io.sapl.test.dsl.interpreter;

import static org.hamcrest.Matchers.allOf;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.dsl.interpreter.matcher.AuthorizationDecisionMatcherInterpreter;
import io.sapl.test.grammar.sAPLTest.AttributeAdjustment;
import io.sapl.test.grammar.sAPLTest.Await;
import io.sapl.test.grammar.sAPLTest.Multiple;
import io.sapl.test.grammar.sAPLTest.Next;
import io.sapl.test.grammar.sAPLTest.NextWithMatcher;
import io.sapl.test.grammar.sAPLTest.NoEvent;
import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class ExpectInterpreter {

    private final ValInterpreter valInterpreter;
    private final AuthorizationDecisionInterpreter authorizationDecisionInterpreter;
    private final AuthorizationDecisionMatcherInterpreter authorizationDecisionMatcherInterpreter;
    private final DurationInterpreter durationInterpreter;

    VerifyStep interpretSingleExpect(final ExpectOrVerifyStep expectOrVerifyStep, final SingleExpect singleExpect) {
        final var decision = singleExpect.getDecision();
        final var obligations = singleExpect.getObligations();
        final var resource = singleExpect.getResource();
        final var advice = singleExpect.getAdvice();

        final var authorizationDecision = authorizationDecisionInterpreter.constructAuthorizationDecision(decision, resource, obligations, advice);

        return expectOrVerifyStep.expect(authorizationDecision);
    }


    VerifyStep interpretRepeatedExpect(ExpectOrVerifyStep expectOrVerifyStep, final RepeatedExpect repeatedExpect) {
        final var expectOrAdjustmentSteps = repeatedExpect.getExpectSteps();

        if (expectOrAdjustmentSteps == null || expectOrAdjustmentSteps.isEmpty()) {
            return expectOrVerifyStep;
        }

        for (var expectOrAdjustmentStep : expectOrAdjustmentSteps) {
            if (expectOrAdjustmentStep instanceof Next nextExpect) {
                expectOrVerifyStep = constructNext(expectOrVerifyStep, nextExpect);
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
            }
        }
        return expectOrVerifyStep;
    }

    private ExpectOrVerifyStep constructNext(final ExpectOrVerifyStep expectOrVerifyStep, final Next nextExpect) {
        final var actualAmount = nextExpect.getAmount() instanceof Multiple multiple ? multiple.getAmount().intValue() : 1;

        return switch (nextExpect.getExpectedDecision()) {
            case PERMIT -> expectOrVerifyStep.expectNextPermit(actualAmount);
            case DENY -> expectOrVerifyStep.expectNextDeny(actualAmount);
            case INDETERMINATE -> expectOrVerifyStep.expectNextIndeterminate(actualAmount);
            default -> expectOrVerifyStep.expectNextNotApplicable(actualAmount);
        };
    }

    private ExpectOrVerifyStep constructNextWithMatcher(final ExpectOrVerifyStep expectOrVerifyStep, final NextWithMatcher nextWithMatcher) {
        final var matchers = nextWithMatcher.getMatcher();

        if (matchers == null || matchers.isEmpty()) {
            return expectOrVerifyStep;
        }

        final var actualMatchers = matchers.stream().map(authorizationDecisionMatcherInterpreter::getHamcrestAuthorizationDecisionMatcher).filter(Objects::nonNull).<Matcher<AuthorizationDecision>>toArray(Matcher[]::new);

        return switch (actualMatchers.length) {
            case 0 -> expectOrVerifyStep;
            case 1 -> expectOrVerifyStep.expectNext(actualMatchers[0]);
            default -> expectOrVerifyStep.expectNext(allOf(actualMatchers));
        };
    }
}
