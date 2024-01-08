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
import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.grammar.sAPLTest.Function;
import io.sapl.test.grammar.sAPLTest.FunctionInvokedOnce;
import io.sapl.test.grammar.sAPLTest.GivenStep;
import io.sapl.test.grammar.sAPLTest.VirtualTime;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.WhenStep;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultWhenStepConstructor {

    private final FunctionInterpreter  functionInterpreter;
    private final AttributeInterpreter attributeInterpreter;

    WhenStep constructWhenStep(final List<GivenStep> givenSteps, final GivenOrWhenStep givenOrWhenStep) {
        if (givenSteps == null || givenSteps.isEmpty()) {
            return givenOrWhenStep;
        }

        return applyGivenSteps(givenSteps, givenOrWhenStep);
    }

    private WhenStep applyGivenSteps(final List<GivenStep> givenSteps, GivenOrWhenStep fixtureWithMocks) {
        for (GivenStep givenStep : givenSteps) {
            if (givenStep instanceof Function function) {
                fixtureWithMocks = functionInterpreter.interpretFunction(fixtureWithMocks, function);
            } else if (givenStep instanceof FunctionInvokedOnce functionInvokedOnce) {
                fixtureWithMocks = functionInterpreter.interpretFunctionInvokedOnce(fixtureWithMocks,
                        functionInvokedOnce);
            } else if (givenStep instanceof Attribute attribute) {
                fixtureWithMocks = attributeInterpreter.interpretAttribute(fixtureWithMocks, attribute);
            } else if (givenStep instanceof AttributeWithParameters attributeWithParameters) {
                fixtureWithMocks = attributeInterpreter.interpretAttributeWithParameters(fixtureWithMocks,
                        attributeWithParameters);
            } else if (givenStep instanceof VirtualTime) {
                fixtureWithMocks = fixtureWithMocks.withVirtualTime();
            } else {
                throw new SaplTestException("Unknown type of GivenStep");
            }
        }

        return fixtureWithMocks;
    }

}
