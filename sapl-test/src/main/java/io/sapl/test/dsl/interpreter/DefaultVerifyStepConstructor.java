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

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.RepeatedExpect;
import io.sapl.test.grammar.sapltest.Scenario;
import io.sapl.test.grammar.sapltest.SingleExpect;
import io.sapl.test.grammar.sapltest.SingleExpectWithMatcher;
import io.sapl.test.steps.ExpectStep;
import io.sapl.test.steps.VerifyStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultVerifyStepConstructor {

    private final ExpectationInterpreter expectationInterpreter;

    VerifyStep constructVerifyStep(final Scenario scenario, final ExpectStep expectStep) {
        if (scenario == null || expectStep == null) {
            throw new SaplTestException("Scenario or expectStep is null");
        }

        final var expectation = scenario.getExpectation();

        if (expectation instanceof SingleExpect singleExpect) {
            return expectationInterpreter.interpretSingleExpect(expectStep, singleExpect);
        } else if (expectation instanceof SingleExpectWithMatcher singleExpectWithMatcher) {
            return expectationInterpreter.interpretSingleExpectWithMatcher(expectStep, singleExpectWithMatcher);
        } else if (expectation instanceof RepeatedExpect repeatedExpect) {
            return expectationInterpreter.interpretRepeatedExpect(expectStep, repeatedExpect);
        }

        throw new SaplTestException("Unknown type of Expectation");
    }
}
