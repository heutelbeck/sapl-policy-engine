/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.conformance;

import io.sapl.api.functions.FunctionBroker;
import io.sapl.api.functions.FunctionInvocation;
import io.sapl.api.model.ArrayValue;
import io.sapl.api.model.BooleanValue;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.NumberValue;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.conformance.ConformanceCorpus.Case;
import io.sapl.functions.DefaultFunctionBroker;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import static io.sapl.conformance.ConformanceCorpus.withPlaceholdersReplaced;

/**
 * The outcomes of the function corpus: what a library function of this engine
 * returns for the arguments of a case, called through the function broker as
 * a policy calls it, and written in the format of the corpus.
 * <p>
 * The document of a case names the function in its first line and writes each
 * argument in a line of its own, as JSON or as {@code undefined}. Numbers are
 * read exactly. The outcome
 * is {@code value: } with the value or {@code error: } with the message of an
 * error value. A case that states its outcome as {@code validation: } states
 * a validation result by its validity and the instance locations of its
 * errors, without their messages. A case that states its outcome as
 * {@code value within <tolerance>: } states a number that the returned number
 * may differ from by at most the tolerance. Placeholders are replaced in the arguments
 * and in the outcome, so a stated outcome that means the same as the outcome
 * of this engine stays as it is written. Error messages are information only
 * and are not compared.
 */
@UtilityClass
public class FunctionOutcomes {

    private static final String ERROR           = "error: ";
    private static final String UNDEFINED       = "undefined";
    private static final String UNREPRESENTABLE = "UNREPRESENTABLE ";
    private static final String VALIDATION      = "validation: ";
    private static final String VALUE           = "value: ";
    private static final String VALUE_WITHIN    = "value within ";
    private static final String WITHIN_END      = ": ";

    private static final String FIELD_ERRORS = "errors";
    private static final String FIELD_PATH   = "path";
    private static final String FIELD_VALID  = "valid";

    private static final FunctionBroker BROKER = defaultBroker();

    /**
     * Reads numbers exactly, with the digits they are written with, as the
     * parser of SAPL reads a number in a policy.
     */
    private static final JsonMapper JSON = JsonMapper.builder().enable(JsonNodeFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .disable(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES).build();

    /**
     * @param functionCase a case of the function corpus
     * @return the case with the outcome of this engine, or with the stated
     * outcome if it means the same
     */
    public static Case withOutcome(Case functionCase) {
        val outcome = outcomeOf(functionCase);
        return functionCase.withExpectation(agrees(functionCase, outcome) ? functionCase.expectation() : outcome);
    }

    /**
     * @param functionCase a case of the function corpus
     * @return whether the stated outcome of the case is what this engine returns
     */
    public static boolean agrees(Case functionCase) {
        return agrees(functionCase, outcomeOf(functionCase));
    }

    /**
     * @param functionCase a case of the function corpus
     * @return what the function of the case returns for its arguments, as an
     * outcome line of the corpus, in the form of a validation result or of a
     * number within a tolerance if the case states one
     */
    public static String outcomeOf(Case functionCase) {
        val result      = resultOf(functionCase);
        val expectation = functionCase.expectation();
        if (expectation.startsWith(VALIDATION)) {
            return writtenAsValidation(result);
        }
        if (expectation.startsWith(VALUE_WITHIN) && expectation.contains(WITHIN_END) && result instanceof NumberValue) {
            return expectation.substring(0, expectation.indexOf(WITHIN_END) + WITHIN_END.length())
                    + ValueJsonMarshaller.toJsonString(result);
        }
        return written(result);
    }

    /**
     * @param functionCase a case of the function corpus
     * @return what the function of the case returns for its arguments, called
     * through the function broker as a policy calls it
     */
    public static Value resultOf(Case functionCase) {
        val lines     = functionCase.documentAsWritten().lines().toList();
        val arguments = lines.stream().skip(1)
                .map(line -> valueOf(withPlaceholdersReplaced(line, functionCase.origin()))).toList();
        val function  = lines.isEmpty() ? "" : lines.getFirst();
        return BROKER.evaluateFunction(new FunctionInvocation(function, arguments));
    }

    /**
     * Values are compared as values, so that numbers agree by their quantity
     * and objects without regard to the order of their keys. Any error value
     * agrees with a stated error. Validation results agree in their validity and in the
     * set of the instance locations of their errors. A number within a
     * tolerance agrees with a returned number that differs from it by at most
     * the tolerance.
     */
    private static boolean agrees(Case functionCase, String outcome) {
        val stated = withPlaceholdersReplaced(functionCase.expectation(), functionCase.origin());
        if (stated.startsWith(ERROR)) {
            return outcome.startsWith(ERROR);
        }
        if (stated.startsWith(VALIDATION) && outcome.startsWith(VALIDATION)) {
            val validation = validationOf(stated);
            return validation.isPresent() && validation.equals(validationOf(outcome));
        }
        if (stated.startsWith(VALUE_WITHIN) && outcome.startsWith(VALUE_WITHIN)) {
            return isWithinTolerance(stated, outcome);
        }
        if (stated.startsWith(VALUE) && outcome.startsWith(VALUE) && !outcome.startsWith(VALUE + UNREPRESENTABLE)) {
            return valueOf(stated.substring(VALUE.length())).equals(valueOf(outcome.substring(VALUE.length())));
        }
        return stated.equals(outcome);
    }

    /**
     * Whether the number of an outcome differs from the stated number by at
     * most the tolerance the stated outcome names.
     */
    private static boolean isWithinTolerance(String stated, String outcome) {
        val statedEnd  = stated.indexOf(WITHIN_END);
        val outcomeEnd = outcome.indexOf(WITHIN_END);
        if (statedEnd < 0 || outcomeEnd < 0) {
            return false;
        }
        try {
            val tolerance = new BigDecimal(stated.substring(VALUE_WITHIN.length(), statedEnd));
            val expected  = new BigDecimal(stated.substring(statedEnd + WITHIN_END.length()));
            val returned  = new BigDecimal(outcome.substring(outcomeEnd + WITHIN_END.length()));
            return expected.subtract(returned).abs().compareTo(tolerance) <= 0;
        } catch (NumberFormatException notANumber) {
            return false;
        }
    }

    static Value valueOf(String written) {
        return UNDEFINED.equals(written) ? Value.UNDEFINED : ValueJsonMarshaller.fromJsonNode(JSON.readTree(written));
    }

    /**
     * @param value what a function returned
     * @return the value as an outcome line of the corpus: an error value with
     * its message, any other value as JSON or as {@code undefined}
     */
    public static String written(Value value) {
        return switch (value) {
        case ErrorValue error       -> ERROR + error.message().replace('\n', ' ');
        case UndefinedValue ignored -> VALUE + UNDEFINED;
        default                     -> {
            try {
                yield VALUE + ValueJsonMarshaller.toJsonString(value);
            } catch (IllegalArgumentException notJson) {
                yield VALUE + UNREPRESENTABLE + value;
            }
        }
        };
    }

    /**
     * Writes a validation result as its validity and the instance locations of
     * its errors as a JSON array, sorted and each once. Any other value is
     * written as a value.
     */
    private static String writtenAsValidation(Value value) {
        if (!(value instanceof ObjectValue result && result.get(FIELD_VALID) instanceof BooleanValue(var valid)
                && result.get(FIELD_ERRORS) instanceof ArrayValue errors)) {
            return written(value);
        }
        val locations = new TreeSet<String>();
        for (val error : errors) {
            if (!(error instanceof ObjectValue fields && fields.get(FIELD_PATH) instanceof TextValue(var path))) {
                return written(value);
            }
            locations.add(path);
        }
        return VALIDATION + valid + " " + JSON.writeValueAsString(locations);
    }

    /**
     * Reads a validation outcome into its validity and the set of its instance
     * locations.
     *
     * @return the validation, or empty if the outcome does not follow the form
     */
    private static Optional<Validation> validationOf(String outcome) {
        val written   = outcome.substring(VALIDATION.length());
        val separator = written.indexOf(' ');
        if (separator < 0) {
            return Optional.empty();
        }
        try {
            val locations = new HashSet<String>();
            JSON.readTree(written.substring(separator + 1)).forEach(location -> locations.add(location.asString()));
            return Optional.of(new Validation(written.substring(0, separator), locations));
        } catch (JacksonException notJson) {
            return Optional.empty();
        }
    }

    private record Validation(String validity, Set<String> locations) {}

    private static FunctionBroker defaultBroker() {
        val broker = new DefaultFunctionBroker(0);
        ConformanceLibraries.libraries().forEach(broker::load);
        return broker;
    }
}
