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

import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.ExpressionResult;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.util.ArrayList;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluationContext;

/** Expression outcomes for a fixed input map and a live broker observing an atomic repository publication. */
@UtilityClass
class StreamRoundOutcomes {

    static Case withOutcome(Case source) {
        val context = evaluationContext();
        val root    = (StreamOperator) compileExpression(source.document(), compilationContext());
        val written = new ArrayList<String>();
        for (val input : source.expectation().lines().filter(line -> "given".equals(line) || line.startsWith("given "))
                .toList()) {
            val fixture = GivenAttributes.read(input);
            val result  = root.evaluate(context.withSnapshot(fixture.observations()));
            written.add(input);
            written.add("result " + outcome(result.result()));
            written.add(GivenAttributes.reads(result.dependencies()));
            written.add("repository " + outcome(fixture.first(snapshot -> root.evaluate(context.withSnapshot(snapshot)),
                    ExpressionResult::result, round -> round.dependencies().keySet())));
        }
        return source.withExpectation(String.join("\n", written));
    }

    private static String outcome(Value value) {
        return switch (value) {
        case null               -> "pending";
        case ErrorValue ignored -> "error";
        case UndefinedValue u   -> "undefined";
        default                 -> ValueJsonMarshaller.toJsonString(value);
        };
    }
}
