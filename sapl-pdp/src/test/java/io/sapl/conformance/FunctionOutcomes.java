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
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.conformance.ConformanceCorpus.Case;
import io.sapl.functions.DefaultFunctionBroker;
import io.sapl.functions.DefaultLibraries;
import lombok.experimental.UtilityClass;
import lombok.val;
import tools.jackson.databind.cfg.JsonNodeFeature;
import tools.jackson.databind.json.JsonMapper;

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
 * error value. Placeholders are replaced in the arguments and in the outcome,
 * so a stated outcome that means the same as the outcome of this engine stays
 * as it is written. A stated message that ends with a colon states only how
 * the message starts.
 */
@UtilityClass
public class FunctionOutcomes {

    private static final String ERROR           = "error: ";
    private static final String UNDEFINED       = "undefined";
    private static final String UNREPRESENTABLE = "UNREPRESENTABLE ";
    private static final String VALUE           = "value: ";

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
     * outcome line of the corpus
     */
    public static String outcomeOf(Case functionCase) {
        val lines     = functionCase.documentAsWritten().lines().toList();
        val arguments = lines.stream().skip(1)
                .map(line -> valueOf(withPlaceholdersReplaced(line, functionCase.origin()))).toList();
        val function  = lines.isEmpty() ? "" : lines.getFirst();
        return written(BROKER.evaluateFunction(new FunctionInvocation(function, arguments)));
    }

    /**
     * Values are compared as values, so that numbers agree by their quantity
     * and objects without regard to the order of their keys. Messages are
     * compared as text. A stated message that ends with a colon states only how
     * the message starts.
     */
    private static boolean agrees(Case functionCase, String outcome) {
        val stated = withPlaceholdersReplaced(functionCase.expectation(), functionCase.origin());
        if (stated.startsWith(ERROR) && stated.endsWith(":")) {
            return outcome.startsWith(stated);
        }
        if (stated.startsWith(VALUE) && outcome.startsWith(VALUE) && !outcome.contains(UNREPRESENTABLE)) {
            return valueOf(stated.substring(VALUE.length())).equals(valueOf(outcome.substring(VALUE.length())));
        }
        return stated.equals(outcome);
    }

    private static Value valueOf(String written) {
        return UNDEFINED.equals(written) ? Value.UNDEFINED : ValueJsonMarshaller.fromJsonNode(JSON.readTree(written));
    }

    private static String written(Value value) {
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

    private static FunctionBroker defaultBroker() {
        val broker = new DefaultFunctionBroker(0);
        DefaultLibraries.defaults().forEach(broker::load);
        return broker;
    }
}
