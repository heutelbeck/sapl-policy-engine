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

import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.ErrorValue;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.conformance.ConformanceCorpus.Case;
import lombok.experimental.UtilityClass;
import lombok.val;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluationContext;

/** Outcomes of individual expression rounds, including every reached attribute occurrence. */
@UtilityClass
class StreamRoundOutcomes {

    static Case withOutcome(Case source) {
        val context = evaluationContext();
        val root    = (StreamOperator) compileExpression(source.document(), compilationContext());
        val written = new ArrayList<String>();
        for (val input : source.expectation().lines().filter(line -> line.equals("given") || line.startsWith("given "))
                .toList()) {
            val observations = new HashMap<SubscriptionKey, AttributeSnapshot>();
            for (val assignment : input.split(" ")) {
                if (assignment.equals("given")) {
                    continue;
                }
                val pair  = assignment.split("=", 2);
                val name  = pair[0];
                val read  = name.startsWith("^") ? "|<" + name.substring(1) + ">" : "<" + name + ">";
                val leaf  = (StreamOperator) compileExpression(read, compilationContext());
                val key   = leaf.evaluate(context.withSnapshot(Map.of())).dependencies().keySet().iterator().next();
                val value = switch (pair[1]) {
                          case "undefined" -> Value.UNDEFINED;
                          case "error"     -> Value.error("Source offline.");
                          default          -> ValueJsonMarshaller.json(pair[1]);
                          };
                observations.put(key, new AttributeSnapshot(value, Instant.EPOCH));
            }
            val result  = root.evaluate(context.withSnapshot(Map.copyOf(observations)));
            val value   = result.result();
            val outcome = switch (value) {
                        case null               -> "pending";
                        case ErrorValue ignored -> "error";
                        default                 -> value.toString();
                        };
            val reads   = result.dependencies().entrySet().stream()
                    .flatMap(entry -> entry.getValue().stream()
                            .map(site -> (entry.getKey().head() ? "^" : "")
                                    + entry.getKey().invocation().attributeName() + "@" + site.location().start()))
                    .sorted().collect(Collectors.joining(" "));
            written.add(input);
            written.add("result " + outcome);
            written.add(reads.isEmpty() ? "reads" : "reads " + reads);
        }
        return source.withExpectation(String.join("\n", written));
    }
}
