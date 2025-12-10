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

import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.AnyVal;
import io.sapl.test.grammar.sapltest.ValMatcher;
import io.sapl.test.grammar.sapltest.ValWithMatcher;
import io.sapl.test.grammar.sapltest.ValWithValue;
import lombok.RequiredArgsConstructor;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;

import static org.hamcrest.CoreMatchers.is;

@RequiredArgsConstructor
class ValueMatcherInterpreter {

    private final ValueInterpreter           valueInterpreter;
    private final JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;

    Matcher<Value> getHamcrestValueMatcher(ValMatcher valMatcher) {
        if (valMatcher instanceof ValWithValue valWithValue) {
            return handleValWithValue(valWithValue);
        } else if (valMatcher instanceof AnyVal) {
            return anyValue();
        } else if (valMatcher instanceof ValWithMatcher valWithMatcher) {
            return handleValWithMatcher(valWithMatcher);
        }

        throw new SaplTestException("Unknown type of ValMatcher.");
    }

    private Matcher<Value> handleValWithValue(ValWithValue valWithValue) {
        var value      = valWithValue.getValue();
        var modelValue = valueInterpreter.getValueFromDslValue(value);

        if (modelValue == null) {
            throw new SaplTestException("Value is null.");
        }

        return is(modelValue);
    }

    private Matcher<Value> handleValWithMatcher(ValWithMatcher valWithMatcher) {
        var matcher         = valWithMatcher.getMatcher();
        var jsonNodeMatcher = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcher);

        if (jsonNodeMatcher == null) {
            throw new SaplTestException("JsonNodeMatcher is null.");
        }

        // For Value model, we need to convert Value to JsonNode for matching
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                if (actual instanceof Value v && ValueJsonMarshaller.isJsonCompatible(v)) {
                    return jsonNodeMatcher.matches(ValueJsonMarshaller.toJsonNode(v));
                }
                return false;
            }

            @Override
            public void describeTo(Description description) {
                jsonNodeMatcher.describeTo(description);
            }
        };
    }

    /**
     * Returns a matcher that matches any Value.
     *
     * @return a matcher that matches any Value
     */
    private static Matcher<Value> anyValue() {
        return new BaseMatcher<>() {
            @Override
            public boolean matches(Object actual) {
                return actual instanceof Value;
            }

            @Override
            public void describeTo(Description description) {
                description.appendText("any Value");
            }
        };
    }
}
