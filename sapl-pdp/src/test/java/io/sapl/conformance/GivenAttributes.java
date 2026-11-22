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

import io.sapl.api.model.EvaluationContext;
import io.sapl.api.model.Occurrence;
import io.sapl.api.model.AttributeSnapshot;
import io.sapl.api.model.StreamOperator;
import io.sapl.api.model.SubscriptionKey;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.attributes.broker.BrokerEvalLoops;
import io.sapl.attributes.broker.EvaluationException;
import io.sapl.attributes.broker.pip.PolicyInformationPointAttributeBroker;
import io.sapl.attributes.broker.repository.AttributeAssignment;
import io.sapl.attributes.broker.repository.AttributeAssignment.BoundValue;
import io.sapl.attributes.broker.repository.InMemoryAttributeRepository;
import io.sapl.attributes.broker.repository.RepositoryKey;
import io.sapl.compiler.expressions.CompilationContext;
import lombok.val;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.function.Function;

import static io.sapl.util.SaplTesting.compilationContext;
import static io.sapl.util.SaplTesting.compileExpression;
import static io.sapl.util.SaplTesting.evaluationContext;

/** The same authored inputs as a fixed snapshot and as an ordered repository publication. */
record GivenAttributes(Map<SubscriptionKey, AttributeSnapshot> observations, List<AttributeAssignment> assignments) {

    static GivenAttributes read(String input) {
        return read(input, compilationContext(), evaluationContext());
    }

    static GivenAttributes read(String input, CompilationContext compilation, EvaluationContext context) {
        val observations = new HashMap<SubscriptionKey, AttributeSnapshot>();
        val assignments  = new ArrayList<AttributeAssignment>();
        for (val assignment : input.split(" ")) {
            if ("given".equals(assignment) || "snapshot".equals(assignment)) {
                continue;
            }
            val pair  = assignment.split("=", 2);
            val name  = pair[0];
            val read  = readExpression(name);
            val leaf  = (StreamOperator) compileExpression(read, compilation);
            val key   = leaf.evaluate(context.withSnapshot(Map.of())).dependencies().keySet().iterator().next();
            val value = switch (pair[1]) {
                      case "undefined" -> Value.UNDEFINED;
                      case "error"     -> Value.error("Source offline.");
                      default          -> ValueJsonMarshaller.json(pair[1]);
                      };
            observations.put(key, new AttributeSnapshot(value, Instant.EPOCH));
            assignments.add(new BoundValue(RepositoryKey.fromInvocation(key.invocation()), value, null));
        }
        return new GivenAttributes(Map.copyOf(observations), List.copyOf(assignments));
    }

    <R, V> V first(Function<Map<SubscriptionKey, AttributeSnapshot>, R> evaluator, Function<R, V> value,
            Function<R, Set<SubscriptionKey>> dependencies) {
        val initial = evaluator.apply(Map.of());
        val first   = value.apply(initial);
        if (first != null) {
            return first;
        }
        try (val repository = new InMemoryAttributeRepository();
                val broker = new PolicyInformationPointAttributeBroker(Duration.ZERO, repository)) {
            repository.publish(assignments);
            return BrokerEvalLoops.awaitFirstResult(broker, "conformance", dependencies.apply(initial), evaluator,
                    (round, snapshot) -> value.apply(round), dependencies);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while generating a repository outcome.", e);
        } catch (EvaluationException e) {
            throw new IllegalStateException("Failed to generate a repository outcome.", e);
        }
    }

    static String reads(Map<SubscriptionKey, List<Occurrence>> dependencies) {
        val sites = dependencies.entrySet().stream().flatMap(entry -> entry.getValue().stream().map(site -> {
            val head = entry.getKey().head() ? "^" : "";
            return head + entry.getKey().invocation().attributeName() + "@" + site.location().start();
        })).sorted().collect(Collectors.joining(" "));
        return sites.isEmpty() ? "reads" : "reads " + sites;
    }

    private static String readExpression(String name) {
        if (name.startsWith("@")) {
            return name.substring(1);
        }
        if (name.startsWith("^")) {
            return "|<" + name.substring(1) + ">";
        }
        return "<" + name + ">";
    }
}
