package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.hasObligation;
import static io.sapl.hamcrest.Matchers.isPermit;
import static org.hamcrest.Matchers.allOf;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.grammar.sAPLTest.*;
import io.sapl.test.interfaces.VerifyStepBuilder;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.VerifyStep;
import java.time.Duration;
import org.hamcrest.Matcher;

public final class VerifyStepBuilderServiceDefaultImpl implements VerifyStepBuilder {
    @Override
    public VerifyStep constructVerifyStep(TestCase testCase, ExpectStep expectOrVerifyStep) {
        final var expect = testCase.getExpect();
        if (expect instanceof SingleExpect singleExpect) {
            return interpretSingleExpect(expectOrVerifyStep, singleExpect);
        } else if (expect instanceof RepeatedExpect repeatedExpect) {
            for (var expectOrAdjustment : repeatedExpect.getExpectSteps()) {
                if (expectOrAdjustment instanceof Next nextExpect) {
                    final var actualAmount = nextExpect.getAmount() instanceof Multiple multiple ? multiple.getAmount() : 1;

                    expectOrVerifyStep = switch (nextExpect.getExpectedDecision()) {
                        case "permit" -> expectOrVerifyStep.expectNextPermit(actualAmount);
                        case "deny" -> expectOrVerifyStep.expectNextDeny(actualAmount);
                        case "indeterminate" -> expectOrVerifyStep.expectNextIndeterminate(actualAmount);
                        default -> expectOrVerifyStep.expectNextNotApplicable(actualAmount);
                    };
                } else if (expectOrAdjustment instanceof NextWithMatcher nextWithMatcher) {
                    expectOrVerifyStep = constructNextWithMatcher(expectOrVerifyStep, nextWithMatcher);
                } else if (expectOrAdjustment instanceof AttributeAdjustment attributeAdjustment) {
                    expectOrVerifyStep = expectOrVerifyStep.thenAttribute(attributeAdjustment.getAttribute(), getValFromReturnValue(attributeAdjustment.getReturnValue()));
                } else if (expectOrAdjustment instanceof Await await) {
                    expectOrVerifyStep = expectOrVerifyStep.thenAwait(Duration.ofSeconds(await.getDuration()));
                } else if (expectOrAdjustment instanceof NoEvent noEvent) {
                    expectOrVerifyStep = expectOrVerifyStep.expectNoEvent(Duration.ofSeconds(noEvent.getDuration()));
                }
            }
        }
        return (VerifyStep) expectOrVerifyStep;
    }

    private ExpectStep constructNextWithMatcher(ExpectStep expectStep, NextWithMatcher nextWithMatcher) {
        final var matchers = nextWithMatcher.getMatcher();
        if(matchers.isEmpty()) {
            return expectStep;
        }

        final var actualMatchers = matchers.stream().map(matcher -> {
            if(matcher instanceof IsPermit) {
                return isPermit();
            } else if(matcher instanceof Obligation obligation) {
                return hasObligation(obligation.getValue());
            }
            return null;
        }).toArray(Matcher[]::new);

        return expectStep.expectNext(actualMatchers.length > 1 ? allOf(actualMatchers) : actualMatchers[0]);
    }

    private VerifyStep interpretSingleExpect(ExpectStep givenOrWhenStep, SingleExpect singleExpect) {
        return givenOrWhenStep.expect(getAuthorizationDecisionFromDSL(singleExpect.getDecision()));
    }

    private AuthorizationDecision getAuthorizationDecisionFromDSL(String decision) {
        return switch (decision) {
            case "permit" -> AuthorizationDecision.PERMIT;
            case "deny" -> AuthorizationDecision.DENY;
            case "indeterminate" -> AuthorizationDecision.INDETERMINATE;
            default -> AuthorizationDecision.NOT_APPLICABLE;
        };
    }

    private Val getValFromReturnValue(io.sapl.test.grammar.sAPLTest.Val value) {
        if (value instanceof IntVal intVal) {
            return Val.of(intVal.getValue());
        } else if (value instanceof StringVal stringVal) {
            return Val.of(stringVal.getValue());
        } else if (value instanceof BoolVal boolVal) {
            return Val.of(boolVal.isIsTrue());
        }
        return null;
    }
}
