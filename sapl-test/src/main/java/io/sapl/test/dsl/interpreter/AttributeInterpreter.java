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

import static io.sapl.test.Imports.arguments;
import static io.sapl.test.Imports.parentValue;
import static io.sapl.test.Imports.whenAttributeParams;
import static io.sapl.test.Imports.whenParentValue;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sapltest.Attribute;
import io.sapl.test.grammar.sapltest.AttributeWithParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
class AttributeInterpreter {

    private final ValueInterpreter      valueInterpreter;
    private final ValMatcherInterpreter matcherInterpreter;
    private final DurationInterpreter   durationInterpreter;

    GivenOrWhenStep interpretAttribute(final GivenOrWhenStep givenOrWhenStep, final Attribute attribute) {
        final var importName  = attribute.getName();
        final var returnValue = attribute.getReturnValue();

        if (returnValue == null || returnValue.isEmpty()) {
            return givenOrWhenStep.givenAttribute(importName);
        } else {
            final var values = returnValue.stream().map(valueInterpreter::getValFromValue).toArray(Val[]::new);

            final var dslTiming = attribute.getTiming();

            if (dslTiming == null) {
                return givenOrWhenStep.givenAttribute(importName, values);
            }

            final var timing = durationInterpreter.getJavaDurationFromDuration(dslTiming);

            return givenOrWhenStep.givenAttribute(importName, timing, values);
        }
    }

    GivenOrWhenStep interpretAttributeWithParameters(final GivenOrWhenStep givenOrWhenStep,
            final AttributeWithParameters attributeWithParameters) {
        final var importName = attributeWithParameters.getName();

        final var parentValueMatcher = matcherInterpreter
                .getHamcrestValMatcher(attributeWithParameters.getParentMatcher());
        final var returnValue        = valueInterpreter.getValFromValue(attributeWithParameters.getReturnValue());

        final var parameterMatchers = attributeWithParameters.getParameterMatchers();

        if (parameterMatchers != null) {

            final var matchers = parameterMatchers.getMatchers();

            if (matchers != null && !matchers.isEmpty()) {
                final var arguments = matchers.stream().map(matcherInterpreter::getHamcrestValMatcher)
                        .<Matcher<Val>>toArray(Matcher[]::new);

                return givenOrWhenStep.givenAttribute(importName,
                        whenAttributeParams(parentValue(parentValueMatcher), arguments(arguments)), returnValue);

            }
        }
        return givenOrWhenStep.givenAttribute(importName, whenParentValue(parentValueMatcher), returnValue);
    }
}
