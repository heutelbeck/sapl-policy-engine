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

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.hamcrest.Matchers.val;
import static org.hamcrest.CoreMatchers.is;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.AnyVal;
import io.sapl.test.grammar.sapltest.ValMatcher;
import io.sapl.test.grammar.sapltest.ValWithMatcher;
import io.sapl.test.grammar.sapltest.ValWithValue;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class ValMatcherInterpreter {

    private final ValueInterpreter           valueInterpreter;
    private final JsonNodeMatcherInterpreter jsonNodeMatcherInterpreter;

    Matcher<Val> getHamcrestValMatcher(final ValMatcher valMatcher) {
        if (valMatcher instanceof ValWithValue valWithValue) {
            return handleValWithValue(valWithValue);
        } else if (valMatcher instanceof AnyVal) {
            return anyVal();
        } else if (valMatcher instanceof ValWithMatcher valWithMatcher) {
            return handleValWithMatcher(valWithMatcher);
        }

        throw new SaplTestException("Unknown type of ValMatcher");
    }

    private Matcher<Val> handleValWithValue(ValWithValue valWithValue) {
        final var value = valWithValue.getValue();

        final var val = valueInterpreter.getValFromValue(value);

        if (val == null) {
            throw new SaplTestException("Val is null");
        }

        return is(val);
    }

    private Matcher<Val> handleValWithMatcher(ValWithMatcher valWithMatcher) {
        final var matcher = valWithMatcher.getMatcher();

        final var jsonNodeMatcher = jsonNodeMatcherInterpreter.getHamcrestJsonNodeMatcher(matcher);

        if (jsonNodeMatcher == null) {
            throw new SaplTestException("JsonNodeMatcher is null");
        }

        return val(jsonNodeMatcher);
    }
}
