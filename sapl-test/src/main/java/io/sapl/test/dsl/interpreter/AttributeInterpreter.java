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

import static io.sapl.test.Imports.arguments;
import static io.sapl.test.Imports.parentValue;
import static io.sapl.test.Imports.whenAttributeParams;
import static io.sapl.test.Imports.whenParentValue;

import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.steps.GivenOrWhenStep;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
class AttributeInterpreter {

    private final ValInterpreter        valInterpreter;
    private final ValMatcherInterpreter matcherInterpreter;
    private final DurationInterpreter   durationInterpreter;

    GivenOrWhenStep interpretAttribute(final GivenOrWhenStep givenOrWhenStep, final Attribute attribute) {
        final var importName = attribute.getName();

        if (attribute.getReturnValue() == null || attribute.getReturnValue().isEmpty()) {
            return givenOrWhenStep.givenAttribute(importName);
        } else {
            final var values = attribute.getReturnValue().stream().map(valInterpreter::getValFromValue)
                    .toArray(Val[]::new);

            final var dslDuration = attribute.getDuration();

            if (dslDuration == null) {
                return givenOrWhenStep.givenAttribute(importName, values);
            }

            final var duration = durationInterpreter.getJavaDurationFromDuration(dslDuration);

            return givenOrWhenStep.givenAttribute(importName, duration, values);
        }
    }

    GivenOrWhenStep interpretAttributeWithParameters(final GivenOrWhenStep givenOrWhenStep,
            final AttributeWithParameters attributeWithParameters) {
        final var importName = attributeWithParameters.getName();

        final var parentValueMatcher = matcherInterpreter
                .getHamcrestValMatcher(attributeWithParameters.getParentMatcher());
        final var returnValue        = valInterpreter.getValFromValue(attributeWithParameters.getReturnValue());

        final var arguments = attributeWithParameters.getParameters();

        if (arguments == null || arguments.isEmpty()) {
            return givenOrWhenStep.givenAttribute(importName, whenParentValue(parentValueMatcher), returnValue);
        }

        final var args = arguments.stream().map(matcherInterpreter::getHamcrestValMatcher)
                .<Matcher<Val>>toArray(Matcher[]::new);

        return givenOrWhenStep.givenAttribute(importName,
                whenAttributeParams(parentValue(parentValueMatcher), arguments(args)), returnValue);
    }
}
