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
package io.sapl.compiler;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.InitializationException;
import lombok.val;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@Disabled
class PerformanceBenchmarkTest {

    private static final int ITERATIONS      = 50_000_000;
    private static final int COLD_START_RUNS = 5;

    private static final String SIMPLE_POLICY = """
            policy "simple_benchmark"
            permit action == "read"
            where subject.role == "admin";
            """;

    private static final String SIMPLE_POLICY_2 = """
            policy "simple_benchmark2"
            permit
            where
               resource.account.state == "open";
               subject.risk_score in ["low", "medium"];
               action.transaction.amount <= 1000;
            """;

    private static final String COMPLEX_POLICY = """
            policy "complex_benchmark"
            permit action == "read"
            where
                subject.role == "admin" || subject.role == "manager";
                resource.type == "document";
                resource.classification != "top_secret";
                var minAge = 18;
                subject.age >= minAge;
                subject.department == resource.owner.department;
                filter.replace(action, "execute") != "execute";
            obligation "log_access"
            advice "notify_owner"
            transform resource.content
            """;

    private AuthorizationSubscription createSimpleSubscription() {
        val subject = ObjectValue.builder().put("role", Value.of("admin")).build();
        return new AuthorizationSubscription(subject, Value.of("read"), Value.of("resource"), Value.UNDEFINED);
    }

    private AuthorizationSubscription createSimple2Subscription() {
        val subject  = ObjectValue.builder().put("risk_score", Value.of("low")).build();
        val resource = ObjectValue.builder()
                .put("account", ObjectValue.builder().put("state", Value.of("open")).build()).build();
        val action   = ObjectValue.builder()
                .put("transaction", ObjectValue.builder().put("amount", Value.of(500)).build()).build();
        return new AuthorizationSubscription(subject, action, resource, Value.UNDEFINED);
    }

    private AuthorizationSubscription createComplexSubscription() {
        val subject  = ObjectValue.builder().put("role", Value.of("admin")).put("age", Value.of(25))
                .put("department", Value.of("engineering")).build();
        val resource = ObjectValue.builder().put("type", Value.of("document"))
                .put("classification", Value.of("confidential")).put("content", Value.of("sensitive data"))
                .put("owner", ObjectValue.builder().put("department", Value.of("engineering")).build()).build();
        return new AuthorizationSubscription(subject, Value.of("read"), resource, Value.UNDEFINED);
    }

    private double[] runColdStart(String policyDoc, AuthorizationSubscription subscription)
            throws InitializationException {
        double[] results = new double[COLD_START_RUNS];

        for (int run = 0; run < COLD_START_RUNS; run++) {
            val pdp = new SingleDocumentPolicyDecisionPoint();
            pdp.loadDocument(policyDoc);

            val startTime = System.nanoTime();
            pdp.decide(subscription).blockFirst();
            val endTime = System.nanoTime();

            results[run] = (endTime - startTime) / 1000.0; // microseconds
        }

        return results;
    }

    private double runWarmedUp(String policyDoc, AuthorizationSubscription subscription)
            throws InitializationException {
        val pdp = new SingleDocumentPolicyDecisionPoint();
        pdp.loadDocument(policyDoc);

        val startTime = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            pdp.decide(subscription).blockFirst();
        }
        val endTime = System.nanoTime();

        return (endTime - startTime) / 1000.0 / ITERATIONS; // average microseconds
    }

    private double average(double[] values) {
        double sum = 0;
        for (double v : values)
            sum += v;
        return sum / values.length;
    }

    @Test
    void comparePerformance() throws InitializationException {
        System.out.println("\n" + "=".repeat(80));
        System.out.println("SAPL COMPILER PERFORMANCE BENCHMARK");
        System.out.println("=".repeat(80));
        System.out.println();

        // SIMPLE POLICY
        System.out.println("--- SIMPLE POLICY ---");
        System.out.println("Policy: permit action == \"read\" where subject.role == \"admin\";");
        System.out.println();

        val simpleCold = runColdStart(SIMPLE_POLICY, createSimpleSubscription());
        System.out.println("Cold start (5 runs without warmup):");
        System.out.println("  Run 1: " + String.format("%.3f", simpleCold[0]) + " µs");
        System.out.println("  Run 2: " + String.format("%.3f", simpleCold[1]) + " µs");
        System.out.println("  Run 3: " + String.format("%.3f", simpleCold[2]) + " µs");
        System.out.println("  Run 4: " + String.format("%.3f", simpleCold[3]) + " µs");
        System.out.println("  Run 5: " + String.format("%.3f", simpleCold[4]) + " µs");
        System.out.println("  Average: " + String.format("%.3f", average(simpleCold)) + " µs");
        System.out.println();

        val simpleWarm = runWarmedUp(SIMPLE_POLICY, createSimpleSubscription());
        System.out.println("Warmed up (" + ITERATIONS + " iterations):");
        System.out.println("  Average: " + String.format("%.3f", simpleWarm) + " µs");
        System.out.println("  Throughput: " + String.format("%,d", (long) (1_000_000.0 / simpleWarm)) + " ops/sec");
        System.out.println();

        // SIMPLE POLICY 2
        System.out.println("--- SIMPLE POLICY 2 ---");
        System.out
                .println("Policy: 3 conditions (resource.account.state, subject.risk_score in array, amount <= 1000)");
        System.out.println();

        val simple2Cold = runColdStart(SIMPLE_POLICY_2, createSimple2Subscription());
        System.out.println("Cold start (5 runs without warmup):");
        System.out.println("  Run 1: " + String.format("%.3f", simple2Cold[0]) + " µs");
        System.out.println("  Run 2: " + String.format("%.3f", simple2Cold[1]) + " µs");
        System.out.println("  Run 3: " + String.format("%.3f", simple2Cold[2]) + " µs");
        System.out.println("  Run 4: " + String.format("%.3f", simple2Cold[3]) + " µs");
        System.out.println("  Run 5: " + String.format("%.3f", simple2Cold[4]) + " µs");
        System.out.println("  Average: " + String.format("%.3f", average(simple2Cold)) + " µs");
        System.out.println();

        val simple2Warm = runWarmedUp(SIMPLE_POLICY_2, createSimple2Subscription());
        System.out.println("Warmed up (" + ITERATIONS + " iterations):");
        System.out.println("  Average: " + String.format("%.3f", simple2Warm) + " µs");
        System.out.println("  Throughput: " + String.format("%,d", (long) (1_000_000.0 / simple2Warm)) + " ops/sec");
        System.out.println();

        // COMPLEX POLICY
        System.out.println("--- COMPLEX POLICY ---");
        System.out.println("Policy: 7 conditions + variable + function call + obligations + advice + transform");
        System.out.println();

        val complexCold = runColdStart(COMPLEX_POLICY, createComplexSubscription());
        System.out.println("Cold start (5 runs without warmup):");
        System.out.println("  Run 1: " + String.format("%.3f", complexCold[0]) + " µs");
        System.out.println("  Run 2: " + String.format("%.3f", complexCold[1]) + " µs");
        System.out.println("  Run 3: " + String.format("%.3f", complexCold[2]) + " µs");
        System.out.println("  Run 4: " + String.format("%.3f", complexCold[3]) + " µs");
        System.out.println("  Run 5: " + String.format("%.3f", complexCold[4]) + " µs");
        System.out.println("  Average: " + String.format("%.3f", average(complexCold)) + " µs");
        System.out.println();

        val complexWarm = runWarmedUp(COMPLEX_POLICY, createComplexSubscription());
        System.out.println("Warmed up (" + ITERATIONS + " iterations):");
        System.out.println("  Average: " + String.format("%.3f", complexWarm) + " µs");
        System.out.println("  Throughput: " + String.format("%,d", (long) (1_000_000.0 / complexWarm)) + " ops/sec");
        System.out.println();

        // SUMMARY
        System.out.println("=".repeat(80));
        System.out.println("SUMMARY");
        System.out.println("=".repeat(80));
        System.out.println();
        System.out.printf("%-20s | %15s | %15s | %15s%n", "Policy", "Cold Avg (µs)", "Warm Avg (µs)", "Throughput");
        System.out.println("-".repeat(80));
        System.out.printf("%-20s | %15s | %15s | %,15d%n", "Simple", String.format("%.3f", average(simpleCold)),
                String.format("%.3f", simpleWarm), (long) (1_000_000.0 / simpleWarm));
        System.out.printf("%-20s | %15s | %15s | %,15d%n", "Simple 2", String.format("%.3f", average(simple2Cold)),
                String.format("%.3f", simple2Warm), (long) (1_000_000.0 / simple2Warm));
        System.out.printf("%-20s | %15s | %15s | %,15d%n", "Complex", String.format("%.3f", average(complexCold)),
                String.format("%.3f", complexWarm), (long) (1_000_000.0 / complexWarm));
        System.out.println("=".repeat(80));
        System.out.println("\nBenchmark complete!");
    }
}
