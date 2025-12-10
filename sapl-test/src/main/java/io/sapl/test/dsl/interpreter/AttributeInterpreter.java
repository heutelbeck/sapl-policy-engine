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
import io.sapl.test.SaplTestException;
import io.sapl.test.grammar.sapltest.Attribute;
import io.sapl.test.grammar.sapltest.AttributeWithParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

import static io.sapl.test.Imports.arguments;
import static io.sapl.test.Imports.entityValue;
import static io.sapl.test.Imports.whenAttributeParams;
import static io.sapl.test.Imports.whenEntityValue;

@RequiredArgsConstructor
class AttributeInterpreter {

    private final ValueInterpreter        valueInterpreter;
    private final ValueMatcherInterpreter matcherInterpreter;
    private final DurationInterpreter     durationInterpreter;

    GivenOrWhenStep interpretAttribute(GivenOrWhenStep givenOrWhenStep, Attribute attribute) {
        var importName  = attribute.getName();
        var returnValue = attribute.getReturnValue();

        if (null == returnValue || returnValue.isEmpty()) {
            throw new SaplTestException("Attribute has no return value.");
        } else {
            var values = returnValue.stream().map(valueInterpreter::getValueFromDslValue).toArray(Value[]::new);

            var dslTiming = attribute.getTiming();

            if (null == dslTiming) {
                return givenOrWhenStep.givenAttribute(importName, values);
            }

            var timing = durationInterpreter.getJavaDurationFromDuration(dslTiming);

            return givenOrWhenStep.givenAttribute(importName, timing, values);
        }
    }

    GivenOrWhenStep interpretAttributeWithParameters(GivenOrWhenStep givenOrWhenStep,
            AttributeWithParameters attributeWithParameters) {
        var importName = attributeWithParameters.getName();

        var parentValueMatcher = matcherInterpreter.getHamcrestValueMatcher(attributeWithParameters.getParentMatcher());
        var returnValue        = valueInterpreter.getValueFromDslValue(attributeWithParameters.getReturnValue());

        var parameterMatchers = attributeWithParameters.getParameterMatchers();

        if (parameterMatchers != null) {

            var matchers = parameterMatchers.getMatchers();

            if (matchers != null && !matchers.isEmpty()) {
                var argumentMatchers = matchers.stream().map(matcherInterpreter::getHamcrestValueMatcher)
                        .<Matcher<Value>>toArray(Matcher[]::new);

                return givenOrWhenStep.givenAttribute(importName,
                        whenAttributeParams(entityValue(parentValueMatcher), arguments(argumentMatchers)), returnValue);
            }
        }
        return givenOrWhenStep.givenAttribute(importName, whenEntityValue(parentValueMatcher), returnValue);
    }
}
