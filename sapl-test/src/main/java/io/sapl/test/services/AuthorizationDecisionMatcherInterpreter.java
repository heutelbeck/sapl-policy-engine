package io.sapl.test.services;

import static io.sapl.hamcrest.Matchers.*;
import static io.sapl.hamcrest.Matchers.hasAdviceContainingKeyValue;
import static io.sapl.hamcrest.Matchers.hasObligationContainingKeyValue;
import static org.hamcrest.Matchers.is;

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.grammar.sAPLTest.*;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class AuthorizationDecisionMatcherInterpreter {

    private final ValInterpreter valInterpreter;

    Matcher<AuthorizationDecision> getMatcherFromExpectMatcher(final AuthorizationDecisionMatcher authorizationDecisionMatcher) {
        if(authorizationDecisionMatcher instanceof AnyDecision) {
            return anyDecision();
        } else if (authorizationDecisionMatcher instanceof IsDecision isDecision) {
            return getIsDecisionMatcher(isDecision);
        }
        else if (authorizationDecisionMatcher instanceof HasObligationOrAdvice hasObligationOrAdvice) {
            final var extendedObjectMatcher = hasObligationOrAdvice.getMatcher();

            return getAuthorizationDecisionMatcherFromObjectMatcher(extendedObjectMatcher, hasObligationOrAdvice.getType());
        } else if(authorizationDecisionMatcher instanceof HasResource hasResource) {
            final var defaultObjectMatcher = hasResource.getMatcher();

            return getAuthorizationDecisionMatcherFromObjectMatcher(defaultObjectMatcher, null);
        }
        return null;
    }

    private static Matcher<AuthorizationDecision> getIsDecisionMatcher(final IsDecision isDecisionMatcher) {
        return switch (isDecisionMatcher.getDecision()) {
            case PERMIT -> isPermit();
            case DENY -> isDeny();
            case INDETERMINATE -> isIndeterminate();
            default -> isNotApplicable();
        };
    }

    private Matcher<AuthorizationDecision> getAuthorizationDecisionMatcherFromObjectMatcher(final DefaultObjectMatcher defaultObjectMatcher, final AuthorizationDecisionMatcherType authorizationDecisionMatcherType) {
        if(defaultObjectMatcher instanceof ObjectWithExactMatch objectWithExactMatch) {
            final var matcher = is(valInterpreter.getValFromReturnValue(objectWithExactMatch.getObject()).get());

            return getMatcher(authorizationDecisionMatcherType, matcher);
        } else if(defaultObjectMatcher instanceof ObjectWithMatcher objectWithMatcher) {
            final var jsonNodeMatcher = objectWithMatcher.getMatcher();
            final var matcher = getJsonNodeMatcherFromJsonNodeMatcher(jsonNodeMatcher);

            return getMatcher(authorizationDecisionMatcherType, matcher);
        } else {
            return getMatcher(authorizationDecisionMatcherType, null);
        }
    }

    private Matcher<AuthorizationDecision> getMatcher(final AuthorizationDecisionMatcherType authorizationDecisionMatcherType, final Matcher<? super JsonNode> matcher) {
        if(authorizationDecisionMatcherType == null) {
            return matcher == null ? hasResource() : hasResource(matcher);
        }

        return switch (authorizationDecisionMatcherType) {
            case OBLIGATION -> matcher == null ? hasObligation() : hasObligation(matcher);
            case ADVICE -> matcher == null ? hasAdvice() : hasAdvice(matcher);
        };
    }

    private Matcher<AuthorizationDecision> getAuthorizationDecisionMatcherFromObjectMatcher(final ExtendedObjectMatcher extendedObjectMatcher, final AuthorizationDecisionMatcherType authorizationDecisionMatcherType) {
        if(extendedObjectMatcher instanceof DefaultObjectMatcher defaultObjectMatcher) {
            return getAuthorizationDecisionMatcherFromObjectMatcher(defaultObjectMatcher, authorizationDecisionMatcherType);
        }

        if(extendedObjectMatcher instanceof ObjectWithKeyValueMatcher objectWithKeyValueMatcher) {
            final var key = objectWithKeyValueMatcher.getKey();
            final var valueMatcher = getJsonNodeMatcherFromJsonNodeMatcher(objectWithKeyValueMatcher.getValue());
            if(valueMatcher == null) {
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
        return null;
    }

    private Matcher<? super JsonNode> getJsonNodeMatcherFromJsonNodeMatcher(final JsonNodeMatcher jsonNodeMatcher){
        if(jsonNodeMatcher instanceof Equals equals) {
            return is(equals.getValue());
        }
        return null;
    }
}
