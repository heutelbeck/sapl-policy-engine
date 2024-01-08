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

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sAPLTest.RepeatedExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpect;
import io.sapl.test.grammar.sAPLTest.SingleExpectWithMatcher;
import io.sapl.test.grammar.sAPLTest.TestCase;
import io.sapl.test.steps.ExpectOrVerifyStep;
import io.sapl.test.steps.VerifyStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultVerifyStepConstructor {

    private final ExpectInterpreter expectInterpreter;

    VerifyStep constructVerifyStep(final TestCase testCase, final ExpectOrVerifyStep expectStep) {
        if (testCase == null || expectStep == null) {
            throw new SaplTestException("TestCase or expectStep is null");
        }

        final var expect = testCase.getExpect();

        if (expect instanceof SingleExpect singleExpect) {
            return expectInterpreter.interpretSingleExpect(expectStep, singleExpect);
        } else if (expect instanceof SingleExpectWithMatcher singleExpectWithMatcher) {
            return expectInterpreter.interpretSingleExpectWithMatcher(expectStep, singleExpectWithMatcher);
        } else if (expect instanceof RepeatedExpect repeatedExpect) {
            return expectInterpreter.interpretRepeatedExpect(expectStep, repeatedExpect);
        }

        throw new SaplTestException("Unknown type of ExpectChain");
    }
}
