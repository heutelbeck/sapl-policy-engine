package io.sapl.test.dsl.interpreter.matcher;

import static io.sapl.hamcrest.Matchers.anyDecision;
import static io.sapl.hamcrest.Matchers.hasAdvice;
import static io.sapl.hamcrest.Matchers.hasAdviceContainingKeyValue;
import static io.sapl.hamcrest.Matchers.hasObligation;
import static io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue;
import static io.sapl.hamcrest.Matchers.hasResource;
import static io.sapl.hamcrest.Matchers.isDeny;
import static io.sapl.hamcrest.Matchers.isIndeterminate;
import static io.sapl.hamcrest.Matchers.isNotApplicable;
import static io.sapl.hamcrest.Matchers.isPermit;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.SaplTestException;
import io.sapl.test.dsl.interpreter.ValInterpreter;
import io.sapl.test.grammar.sAPLTest.AnyDecision;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcher;
import io.sapl.test.grammar.sAPLTest.AuthorizationDecisionMatcherType;
import io.sapl.test.grammar.sAPLTest.DefaultObjectMatcher;
import io.sapl.test.grammar.sAPLTest.ExtendedObjectMatcher;
import io.sapl.test.grammar.sAPLTest.HasObligationOrAdvice;
import io.sapl.test.grammar.sAPLTest.HasResource;
import io.sapl.test.grammar.sAPLTest.IsDecision;
import io.sapl.test.grammar.sAPLTest.ObjectWithExactMatch;
import io.sapl.test.grammar.sAPLTest.ObjectWithKeyValueMatcher;
import io.sapl.test.grammar.sAPLTest.ObjectWithMatcher;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class AuthorizationDecisionMatcherInterpreter {

    private final ValInterpreter valInterpreter;
    private final JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;

    public Matcher<AuthorizationDecision> getHamcrestAuthorizationDecisionMatcher(final AuthorizationDecisionMatcher authorizationDecisionMatcher) {
        if (authorizationDecisionMatcher instanceof AnyDecision) {
            return anyDecision();
        } else if (authorizationDecisionMatcher instanceof IsDecision isDecision) {
            return getIsDecisionMatcher(isDecision);
        } else if (authorizationDecisionMatcher instanceof HasObligationOrAdvice hasObligationOrAdvice) {
            final var extendedObjectMatcher = hasObligationOrAdvice.getMatcher();

            return getAuthorizationDecisionMatcherFromObjectMatcher(extendedObjectMatcher, hasObligationOrAdvice.getType());
        } else if (authorizationDecisionMatcher instanceof HasResource hasResource) {
            final var defaultObjectMatcher = hasResource.getMatcher();

            return getAuthorizationDecisionMatcherFromObjectMatcher(defaultObjectMatcher, null);
        }

        throw new SaplTestException("Unknown type of AuthorizationDecisionMatcher");
    }

    private Matcher<AuthorizationDecision> getIsDecisionMatcher(final IsDecision isDecisionMatcher) {
        final var decision = isDecisionMatcher.getDecision();

        if (decision == null) {
            throw new SaplTestException("Decision is null");
        }

        return switch (decision) {
            case PERMIT -> isPermit();
            case DENY -> isDeny();
            case INDETERMINATE -> isIndeterminate();
            case NOT_APPLICABLE -> isNotApplicable();
        };
    }

    private Matcher<AuthorizationDecision> getAuthorizationDecisionMatcherFromObjectMatcher(final DefaultObjectMatcher defaultObjectMatcher, final AuthorizationDecisionMatcherType authorizationDecisionMatcherType) {
        if (defaultObjectMatcher instanceof ObjectWithExactMatch objectWithExactMatch) {
            final var matcher = is(valInterpreter.getValFromValue(objectWithExactMatch.getObject()).get());

            return getMatcher(authorizationDecisionMatcherType, matcher);
        } else if (defaultObjectMatcher instanceof ObjectWithMatcher objectWithMatcher) {
            final var jsonNodeMatcher = objectWithMatcher.getMatcher();
            final var matcher = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

            return getMatcher(authorizationDecisionMatcherType, matcher);
        }

        return getMatcher(authorizationDecisionMatcherType, null);
    }

    private Matcher<AuthorizationDecision> getMatcher(final AuthorizationDecisionMatcherType authorizationDecisionMatcherType, final Matcher<? super JsonNode> matcher) {
        if (authorizationDecisionMatcherType == null) {
            return matcher == null ? hasResource() : hasResource(matcher);
        }

        return switch (authorizationDecisionMatcherType) {
            case OBLIGATION -> matcher == null ? hasObligation() : hasObligation(matcher);
            case ADVICE -> matcher == null ? hasAdvice() : hasAdvice(matcher);
        };
    }

    private Matcher<AuthorizationDecision> getAuthorizationDecisionMatcherFromObjectMatcher(final ExtendedObjectMatcher extendedObjectMatcher, final AuthorizationDecisionMatcherType authorizationDecisionMatcherType) {
        if (extendedObjectMatcher instanceof DefaultObjectMatcher defaultObjectMatcher) {
            return getAuthorizationDecisionMatcherFromObjectMatcher(defaultObjectMatcher, authorizationDecisionMatcherType);
        }

        if (extendedObjectMatcher instanceof ObjectWithKeyValueMatcher objectWithKeyValueMatcher) {
            final var key = objectWithKeyValueMatcher.getKey();
            final var valueMatcher = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(objectWithKeyValueMatcher.getMatcher());
            if (valueMatcher == null) {
                return switch (authorizationDecisionMatcherType) {
                    case OBLIGATION -> hasObligationContainingKeyValue(key);
                    case ADVICE -> hasAdviceContainingKeyValue(key);
                };
            }
            return switch (authorizationDecisionMatcherType) {
                case OBLIGATION -> hasObligationContainingKeyValue(key, valueMatcher);
                case ADVICE -> hasAdviceContainingKeyValue(key, valueMatcher);
            };
        }

        throw new SaplTestException("Unknown type of ExtendedObjectMatcher");
    }
}
