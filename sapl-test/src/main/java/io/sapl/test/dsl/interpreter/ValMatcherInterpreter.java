/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.hamcrest.Matchers.val;
import static io.sapl.hamcrest.Matchers.valError;
import static org.hamcrest.CoreMatchers.is;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.AnyVal;
import io.sapl.test.grammar.sAPLTest.PlainString;
import io.sapl.test.grammar.sAPLTest.StringMatcher;
import io.sapl.test.grammar.sAPLTest.ValMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithError;
import io.sapl.test.grammar.sAPLTest.ValWithMatcher;
import io.sapl.test.grammar.sAPLTest.ValWithValue;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
class ValMatcherInterpreter {

    private final ValInterpreter             valInterpreter;
    private final JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;
    private final StringMatcherInterpreter   stringMatcherInterpreter;

    Matcher<Val> getHamcrestValMatcher(final ValMatcher valMatcher) {
        if (valMatcher instanceof ValWithValue valWithValueMatcher) {
            final var value = valWithValueMatcher.getValue();

            return is(valInterpreter.getValFromValue(value));
        } else if (valMatcher instanceof AnyVal) {
            return anyVal();
        } else if (valMatcher instanceof ValWithMatcher valWithMatcherMatcher) {
            final var matcher = valWithMatcherMatcher.getMatcher();

            return val(jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcher));
        } else if (valMatcher instanceof ValWithError valWithErrorStringMatcher) {
            final var errorMatcher = valWithErrorStringMatcher.getError();

            if (errorMatcher instanceof PlainString plainString) {
                return valError(plainString.getText());
            } else if (errorMatcher instanceof StringMatcher stringMatcher) {
                return valError(stringMatcherInterpreter.getHamcrestStringMatcher(stringMatcher));
            }

            return valError();
        }

        throw new SaplTestException("Unknown type of ValMatcher");
    }
}
