package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.hasObligation;
import static io.sapl.hamcrest.Matchers.isDeny;
import static io.sapl.hamcrest.Matchers.isIndeterminate;
import static io.sapl.hamcrest.Matchers.isNotApplicable;
import static io.sapl.hamcrest.Matchers.isPermit;
import static org.hamcrest.Matchers.allOf;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.grammar.sAPLTest.*;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class ExpectInterpreter {

    private final ValInterpreter valInterpreter;
    private final AuthorizationDecisionInterpreter authorizationDecisionInterpreter;

    VerifyStep interpretSingleExpect(final ExpectOrVerifyStep expectOrVerifyStep, final SingleExpect singleExpect) {
        final var obligation = singleExpect.getObligation();
        final var resource = singleExpect.getResource();

        final var authorizationDecision = authorizationDecisionInterpreter.constructAuthorizationDecision(singleExpect.getDecision(), obligation, resource);

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
                final var returnValue = valInterpreter.getValFromReturnValue(attributeAdjustment.getReturnValue());
                expectOrVerifyStep = expectOrVerifyStep.thenAttribute(attributeAdjustment.getAttribute(), returnValue);
            } else if (expectOrAdjustmentStep instanceof Await await) {
                final var duration = Duration.ofSeconds(await.getDuration().getSeconds().longValue());
                expectOrVerifyStep = expectOrVerifyStep.thenAwait(duration);
            } else if (expectOrAdjustmentStep instanceof NoEvent noEvent) {
                final var duration = Duration.ofSeconds(noEvent.getDuration().getSeconds().longValue());
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

        final Matcher<AuthorizationDecision>[] actualMatchers = matchers.stream().map(getMatcherFromExpectMatcher()).filter(Objects::nonNull).toArray(Matcher[]::new);

        if (actualMatchers.length == 0) {
            return expectOrVerifyStep;
        }

        return expectOrVerifyStep.expectNext(actualMatchers.length > 1 ? allOf(actualMatchers) : actualMatchers[0]);
    }

    private java.util.function.Function<ExpectMatcher, Matcher<AuthorizationDecision>> getMatcherFromExpectMatcher() {
        return matcher -> {
            if (matcher instanceof AuthorizationDecisionMatcher authorizationDecisionMatcher) {
                return switch (authorizationDecisionMatcher.getDecision()) {
                    case PERMIT -> isPermit();
                    case DENY -> isDeny();
                    case INDETERMINATE -> isIndeterminate();
                    default -> isNotApplicable();
                };
            } else if (matcher instanceof ObligationMatcher obligationMatcher) {
                return hasObligation(obligationMatcher.getValue());
            }
            return null;
        };
    }
}
