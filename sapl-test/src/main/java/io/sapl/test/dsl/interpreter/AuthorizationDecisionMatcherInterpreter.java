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

import org.hamcrest.Matcher;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.AnyDecision;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionMatcher;
import io.sapl.test.grammar.sapltest.AuthorizationDecisionMatcherType;
import io.sapl.test.grammar.sapltest.DefaultObjectMatcher;
import io.sapl.test.grammar.sapltest.ExtendedObjectMatcher;
import io.sapl.test.grammar.sapltest.HasObligationOrAdvice;
import io.sapl.test.grammar.sapltest.HasResource;
import io.sapl.test.grammar.sapltest.IsDecision;
import io.sapl.test.grammar.sapltest.ObjectWithExactMatch;
import io.sapl.test.grammar.sapltest.ObjectWithKeyValueMatcher;
import io.sapl.test.grammar.sapltest.ObjectWithMatcher;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class AuthorizationDecisionMatcherInterpreter {

    private final ValueInterpreter           valueInterpreter;
    private final JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;

    public Matcher<AuthorizationDecision> getHamcrestAuthorizationDecisionMatcher(
            final AuthorizationDecisionMatcher authorizationDecisionMatcher) {
        if (authorizationDecisionMatcher instanceof AnyDecision) {
            return anyDecision();
        } else if (authorizationDecisionMatcher instanceof IsDecision isDecision) {
            return getIsDecisionMatcher(isDecision);
        } else if (authorizationDecisionMatcher instanceof HasObligationOrAdvice hasObligationOrAdvice) {
            final var extendedObjectMatcher            = hasObligationOrAdvice.getMatcher();
            final var authorizationDecisionMatcherType = hasObligationOrAdvice.getType();

            if (extendedObjectMatcher == null) {
                return switch (authorizationDecisionMatcherType) {
                case OBLIGATION -> hasObligation();
                case ADVICE -> hasAdvice();
                };
            }

            return getAuthorizationDecisionMatcherFromObjectMatcher(extendedObjectMatcher,
                    authorizationDecisionMatcherType);
        } else if (authorizationDecisionMatcher instanceof HasResource hasResource) {
            final var defaultObjectMatcher = hasResource.getMatcher();

            return getAuthorizationDecisionMatcherFromObjectMatcher(defaultObjectMatcher, null);
        }

        throw new SaplTestException("Unknown type of AuthorizationDecisionMatcher");
    }

    private Matcher<AuthorizationDecision> getIsDecisionMatcher(final IsDecision isDecision) {
        final var decision = isDecision.getDecision();

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

    private Matcher<AuthorizationDecision> getAuthorizationDecisionMatcherFromObjectMatcher(
            final DefaultObjectMatcher defaultObjectMatcher,
            final AuthorizationDecisionMatcherType authorizationDecisionMatcherType) {
        if (defaultObjectMatcher instanceof ObjectWithExactMatch objectWithExactMatch) {
            final var valToMatch = valueInterpreter.getValFromValue(objectWithExactMatch.getEqualTo());

            if (valToMatch == null) {
                throw new SaplTestException("Val to match is null");
            }

            final var matcher = is(valToMatch.get());

            return getMatcher(authorizationDecisionMatcherType, matcher);
        } else if (defaultObjectMatcher instanceof ObjectWithMatcher objectWithMatcher) {
            final var jsonNodeMatcher = objectWithMatcher.getMatcher();
            final var matcher         = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

            if (matcher == null) {
                throw new SaplTestException("Matcher for JsonNode is null");
            }

            return getMatcher(authorizationDecisionMatcherType, matcher);
        }

        throw new SaplTestException("Unknown type of DefaultObjectMatcher");
    }

    private Matcher<AuthorizationDecision> getMatcher(
            final AuthorizationDecisionMatcherType authorizationDecisionMatcherType,
            final Matcher<? super JsonNode> matcher) {
        if (authorizationDecisionMatcherType == null) {
            return hasResource(matcher);
        }

        return switch (authorizationDecisionMatcherType) {
        case OBLIGATION -> hasObligation(matcher);
        case ADVICE -> hasAdvice(matcher);
        };
    }

    private Matcher<AuthorizationDecision> getAuthorizationDecisionMatcherFromObjectMatcher(
            final ExtendedObjectMatcher extendedObjectMatcher,
            final AuthorizationDecisionMatcherType authorizationDecisionMatcherType) {

        if (extendedObjectMatcher instanceof DefaultObjectMatcher defaultObjectMatcher) {
            return getAuthorizationDecisionMatcherFromObjectMatcher(defaultObjectMatcher,
                    authorizationDecisionMatcherType);
        }

        if (extendedObjectMatcher instanceof ObjectWithKeyValueMatcher objectWithKeyValueMatcher) {
            final var key     = objectWithKeyValueMatcher.getKey();
            final var matcher = objectWithKeyValueMatcher.getMatcher();

            if (matcher == null) {
                return switch (authorizationDecisionMatcherType) {
                case OBLIGATION -> hasObligationContainingKeyValue(key);
                case ADVICE -> hasAdviceContainingKeyValue(key);
                };
            }

            final var valueMatcher = jsonNodeMatcherInterpreter
                    .getHamcrestJsonNodeMatcher(objectWithKeyValueMatcher.getMatcher());

            if (valueMatcher == null) {
                throw new SaplTestException("Matcher for JsonNode is null");
            }

            return switch (authorizationDecisionMatcherType) {
            case OBLIGATION -> hasObligationContainingKeyValue(key, valueMatcher);
            case ADVICE -> hasAdviceContainingKeyValue(key, valueMatcher);
            };
        }

        throw new SaplTestException("Unknown type of ExtendedObjectMatcher");
    }
}
