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

import java.util.List;

import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.Attribute;
import io.sapl.test.grammar.sapltest.AttributeWithParameters;
import io.sapl.test.grammar.sapltest.Function;
import io.sapl.test.grammar.sapltest.FunctionInvokedOnce;
import io.sapl.test.grammar.sapltest.GivenStep;
import io.sapl.test.grammar.sapltest.VirtualTime;
import io.sapl.test.steps.GivenOrWhenStep;
import io.sapl.test.steps.WhenStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class DefaultWhenStepConstructor {

    private final FunctionInterpreter  functionInterpreter;
    private final AttributeInterpreter attributeInterpreter;

    WhenStep constructWhenStep(final List<GivenStep> givenSteps, final GivenOrWhenStep initialTestCase) {
        if (givenSteps != null) {

            if (givenSteps.stream().filter(VirtualTime.class::isInstance).count() > 1) {
                throw new SaplTestException("TestCase contains more than one virtual-time declaration");
            }

            applyGivenSteps(givenSteps, initialTestCase);
        }

        return initialTestCase;
    }

    private void applyGivenSteps(final List<GivenStep> givenSteps, GivenOrWhenStep initialTestCase) {
        for (GivenStep givenStep : givenSteps) {
            if (givenStep instanceof Function function) {
                initialTestCase = functionInterpreter.interpretFunction(initialTestCase, function);
            } else if (givenStep instanceof FunctionInvokedOnce functionInvokedOnce) {
                initialTestCase = functionInterpreter.interpretFunctionInvokedOnce(initialTestCase,
                        functionInvokedOnce);
            } else if (givenStep instanceof Attribute attribute) {
                initialTestCase = attributeInterpreter.interpretAttribute(initialTestCase, attribute);
            } else if (givenStep instanceof AttributeWithParameters attributeWithParameters) {
                initialTestCase = attributeInterpreter.interpretAttributeWithParameters(initialTestCase,
                        attributeWithParameters);
            } else if (givenStep instanceof VirtualTime) {
                initialTestCase = initialTestCase.withVirtualTime();
            } else {
                throw new SaplTestException("Unknown type of GivenStep");
            }
        }
    }
}
