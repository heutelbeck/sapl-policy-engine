package io.sapl.test.services;

import static io.sapl.test.Imports.arguments;
import static io.sapl.test.Imports.parentValue;
import static io.sapl.test.Imports.whenAttributeParams;
import static io.sapl.test.Imports.whenParentValue;

import io.sapl.api.interpreter.Val;
import io.sapl.test.grammar.sAPLTest.Attribute;
import io.sapl.test.grammar.sAPLTest.AttributeWithParameters;
import io.sapl.test.grammar.sAPLTest.TemporalAmount;
import io.sapl.test.steps.GivenOrWhenStep;
import java.time.Duration;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hamcrest.Matcher;

@RequiredArgsConstructor
public class AttributeInterpreter {

    private final ValInterpreter valInterpreter;
    private final MatcherInterpreter matcherInterpreter;

    GivenOrWhenStep interpretAttribute(GivenOrWhenStep initial, Attribute attribute) {
        final var importName = attribute.getImportName();

        if (attribute.getReturn() == null || attribute.getReturn().isEmpty()) {
            return initial.givenAttribute(importName);
        } else {
            final var values = attribute.getReturn().stream().map(valInterpreter::getValFromReturnValue).toArray(Val[]::new);

            final var duration = Duration.ofSeconds(Optional.ofNullable(attribute.getAmount()).map(TemporalAmount::getSeconds).orElse(0));
            if (duration.isZero()) {
                return initial.givenAttribute(importName, values);
            }
            return initial.givenAttribute(importName, duration, values);
        }
    }

    GivenOrWhenStep interpretAttributeWithParameters(GivenOrWhenStep initial, AttributeWithParameters attributeWithParameters) {
        final var importName = attributeWithParameters.getImportName();

        final var parentValueMatcher = matcherInterpreter.getValMatcherFromParameterMatcher(attributeWithParameters.getParentMatcher());
        final var returnValue = valInterpreter.getValFromReturnValue(attributeWithParameters.getReturn());

        final var arguments = attributeWithParameters.getParameters();

        if (arguments == null || arguments.isEmpty()) {
            return initial.givenAttribute(importName, whenParentValue(parentValueMatcher), returnValue);
        }
        final var args = arguments.stream().map(valInterpreter::getValMatcherFromVal).toArray(Matcher[]::new);
        return initial.givenAttribute(importName, whenAttributeParams(parentValue(parentValueMatcher), arguments(args)), returnValue);
    }
}
