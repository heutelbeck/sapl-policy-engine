/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.JsonNode;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
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
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import static org.hamcrest.Matchers.is;

@RequiredArgsConstructor
class AuthorizationDecisionMatcherInterpreter {

    private final ValueInterpreter           valueInterpreter;
    private final JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;

    public Matcher<AuthorizationDecision> getHamcrestAuthorizationDecisionMatcher(
            AuthorizationDecisionMatcher authorizationDecisionMatcher) {
        if (authorizationDecisionMatcher instanceof AnyDecision) {
            return anyDecision();
        } else if (authorizationDecisionMatcher instanceof IsDecision isDecision) {
            return getIsDecisionMatcher(isDecision);
        } else if (authorizationDecisionMatcher instanceof HasObligationOrAdvice hasObligationOrAdvice) {
            var extendedObjectMatcher            = hasObligationOrAdvice.getMatcher();
            var authorizationDecisionMatcherType = hasObligationOrAdvice.getType();

            if (extendedObjectMatcher == null) {
                return switch (authorizationDecisionMatcherType) {
                case OBLIGATION -> hasObligation();
                case ADVICE     -> hasAdvice();
                };
            }

            return getAuthorizationDecisionMatcherFromObjectMatcher(extendedObjectMatcher,
                    authorizationDecisionMatcherType);
        } else if (authorizationDecisionMatcher instanceof HasResource hasResource) {
            var defaultObjectMatcher = hasResource.getMatcher();

            return getAuthorizationDecisionMatcherFromObjectMatcher(defaultObjectMatcher, null);
        }

        throw new SaplTestException("Unknown type of AuthorizationDecisionMatcher.");
    }

    private Matcher<AuthorizationDecision> getIsDecisionMatcher(IsDecision isDecision) {
        var decision = isDecision.getDecision();

        if (decision == null) {
            throw new SaplTestException("Decision is null.");
        }

        return switch (decision) {
        case PERMIT         -> isPermit();
        case DENY           -> isDeny();
        case INDETERMINATE  -> isIndeterminate();
        case NOT_APPLICABLE -> isNotApplicable();
        };
    }

    private Matcher<AuthorizationDecision> getAuthorizationDecisionMatcherFromObjectMatcher(
            DefaultObjectMatcher defaultObjectMatcher,
            AuthorizationDecisionMatcherType authorizationDecisionMatcherType) {
        if (defaultObjectMatcher instanceof ObjectWithExactMatch objectWithExactMatch) {
            var valueToMatch = valueInterpreter.getValueFromDslValue(objectWithExactMatch.getEqualTo());

            if (valueToMatch == null) {
                throw new SaplTestException("Value to match is null.");
            }

            var matcher = is(ValueJsonMarshaller.toJsonNode(valueToMatch));

            return getMatcher(authorizationDecisionMatcherType, matcher);
        } else if (defaultObjectMatcher instanceof ObjectWithMatcher objectWithMatcher) {
            var jsonNodeMatcher = objectWithMatcher.getMatcher();
            var matcher         = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(jsonNodeMatcher);

            if (matcher == null) {
                throw new SaplTestException("Matcher for JsonNode is null.");
            }

            return getMatcher(authorizationDecisionMatcherType, matcher);
        }

        throw new SaplTestException("Unknown type of DefaultObjectMatcher.");
    }

    private Matcher<AuthorizationDecision> getMatcher(AuthorizationDecisionMatcherType authorizationDecisionMatcherType,
            Matcher<? super JsonNode> matcher) {
        if (authorizationDecisionMatcherType == null) {
            return hasResource(matcher);
        }

        return switch (authorizationDecisionMatcherType) {
        case OBLIGATION -> hasObligation(matcher);
        case ADVICE     -> hasAdvice(matcher);
        };
    }

    private Matcher<AuthorizationDecision> getAuthorizationDecisionMatcherFromObjectMatcher(
            ExtendedObjectMatcher extendedObjectMatcher,
            AuthorizationDecisionMatcherType authorizationDecisionMatcherType) {

        if (extendedObjectMatcher instanceof DefaultObjectMatcher defaultObjectMatcher) {
            return getAuthorizationDecisionMatcherFromObjectMatcher(defaultObjectMatcher,
                    authorizationDecisionMatcherType);
        }

        if (extendedObjectMatcher instanceof ObjectWithKeyValueMatcher objectWithKeyValueMatcher) {
            var key     = objectWithKeyValueMatcher.getKey();
            var matcher = objectWithKeyValueMatcher.getMatcher();

            if (matcher == null) {
                return switch (authorizationDecisionMatcherType) {
                case OBLIGATION -> hasObligationContainingKeyValue(key);
                case ADVICE     -> hasAdviceContainingKeyValue(key);
                };
            }

            var valueMatcher = jsonNodeMatcherInterpreter
                    .getHamcrestJsonNodeMatcher(objectWithKeyValueMatcher.getMatcher());

            if (valueMatcher == null) {
                throw new SaplTestException("Matcher for JsonNode is null.");
            }

            return switch (authorizationDecisionMatcherType) {
            case OBLIGATION -> hasObligationContainingKeyValue(key, valueMatcher);
            case ADVICE     -> hasAdviceContainingKeyValue(key, valueMatcher);
            };
        }

        throw new SaplTestException("Unknown type of ExtendedObjectMatcher.");
    }

    // Simple matchers for AuthorizationDecision

    private static Matcher<AuthorizationDecision> anyDecision() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                return actual instanceof AuthorizationDecision;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("any authorization decision");
            }
        };
    }

    private static Matcher<AuthorizationDecision> isPermit() {
        return isDecision(Decision.PERMIT);
    }

    private static Matcher<AuthorizationDecision> isDeny() {
        return isDecision(Decision.DENY);
    }

    private static Matcher<AuthorizationDecision> isIndeterminate() {
        return isDecision(Decision.INDETERMINATE);
    }

    private static Matcher<AuthorizationDecision> isNotApplicable() {
        return isDecision(Decision.NOT_APPLICABLE);
    }

    private static Matcher<AuthorizationDecision> isDecision(Decision expected) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    return d.decision() == expected;
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("decision is ").appendValue(expected);
            }
        };
    }

    private static Matcher<AuthorizationDecision> hasObligation() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    return !d.obligations().isEmpty();
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has obligations");
            }
        };
    }

    private static Matcher<AuthorizationDecision> hasAdvice() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    return !d.advice().isEmpty();
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has advice");
            }
        };
    }

    private static Matcher<AuthorizationDecision> hasObligation(Matcher<? super JsonNode> matcher) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    return d.obligations().stream().filter(ValueJsonMarshaller::isJsonCompatible)
                            .map(ValueJsonMarshaller::toJsonNode).anyMatch(matcher::matches);
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has obligation matching ").appendDescriptionOf(matcher);
            }
        };
    }

    private static Matcher<AuthorizationDecision> hasAdvice(Matcher<? super JsonNode> matcher) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    return d.advice().stream().filter(ValueJsonMarshaller::isJsonCompatible)
                            .map(ValueJsonMarshaller::toJsonNode).anyMatch(matcher::matches);
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has advice matching ").appendDescriptionOf(matcher);
            }
        };
    }

    private static Matcher<AuthorizationDecision> hasResource(Matcher<? super JsonNode> matcher) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    var resource = d.resource();
                    if (ValueJsonMarshaller.isJsonCompatible(resource)) {
                        return matcher.matches(ValueJsonMarshaller.toJsonNode(resource));
                    }
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has resource matching ").appendDescriptionOf(matcher);
            }
        };
    }

    private static Matcher<AuthorizationDecision> hasObligationContainingKeyValue(String key) {
        return hasObligationContainingKeyValue(key, null);
    }

    private static Matcher<AuthorizationDecision> hasAdviceContainingKeyValue(String key) {
        return hasAdviceContainingKeyValue(key, null);
    }

    private static Matcher<AuthorizationDecision> hasObligationContainingKeyValue(String key,
            Matcher<? super JsonNode> valueMatcher) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    return d.obligations().stream().filter(ValueJsonMarshaller::isJsonCompatible)
                            .map(ValueJsonMarshaller::toJsonNode)
                            .anyMatch(node -> nodeContainsKeyValue(node, key, valueMatcher));
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has obligation containing key ").appendValue(key);
            }
        };
    }

    private static Matcher<AuthorizationDecision> hasAdviceContainingKeyValue(String key,
            Matcher<? super JsonNode> valueMatcher) {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof AuthorizationDecision d) {
                    return d.advice().stream().filter(ValueJsonMarshaller::isJsonCompatible)
                            .map(ValueJsonMarshaller::toJsonNode)
                            .anyMatch(node -> nodeContainsKeyValue(node, key, valueMatcher));
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("has advice containing key ").appendValue(key);
            }
        };
    }

    private static boolean nodeContainsKeyValue(JsonNode node, String key, Matcher<? super JsonNode> valueMatcher) {
        if (!node.has(key)) {
            return false;
        }
        if (valueMatcher == null) {
            return true;
        }
        return valueMatcher.matches(node.get(key));
    }
}
