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
package io.sapl.pdp.demo;

import lombok.val;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

// Reliable micro-benchmark for PDP policy evaluation.
//
// - Separates: JVM warmup, PDP init, document load/compile, evaluation
// - Prevents dead code elimination via volatile sink
// - Prevents loop elimination by varying context
// - Uses wall-clock windows instead of iteration timing
// - Emits percentile stats and JIT stabilization signal
// - Allows structured comparison of multiple policies
//
// All IO is excluded. Only CPU + JVM + PDP is measured.
public final class PdpReliableBenchmark {

    /* prevents DCE */
    private static volatile int blackhole;

    private static final int WARMUP_ITERATIONS         = 50_000;
    private static final int MEASURE_ITERATIONS        = 5_000_000;
    private static final int THROUGHPUT_WINDOW_SECONDS = 2;

    private static final List<Result> results = new ArrayList<>();

    public static void main(String[] args) {

        printEnvironment();

        runBenchmark("Simple", PdpReliableBenchmark::createSimplePolicyDocument);
        runBenchmark("Simple 2", PdpReliableBenchmark::createSimple2PolicyDocument);
        runBenchmark("Complex", PdpReliableBenchmark::createComplexPolicyDocument);

        printSummary();
    }

    // End-to-end benchmark for one policy
    private static void runBenchmark(String name, Supplier<String> policyDocumentSupplier) {

        System.out.println("\n=================================");
        System.out.println("Policy: " + name);
        System.out.println("=================================");

        /* 1. PDP bootstrap cost */
        val pdpInitStart = System.nanoTime();
        val pdp          = createPdp();
        val pdpInitTime  = nanosSince(pdpInitStart);

        /* 2. Document load + parse + fold + IR generation */
        val docLoadStart = System.nanoTime();
        val document     = policyDocumentSupplier.get();

        val policy   = loadPolicy(pdp, document); // triggers parsing + folding
        val loadTime = nanosSince(docLoadStart);

        /* 3. Context supplier with entropy */
        val contextSupplier = createContextSupplier();

        /* 4. JVM/JIT warmup */
        System.out.println("Warmup loop...");
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            val context  = contextSupplier.get();
            val decision = policy.evaluate(context).block();
            blackhole ^= decision.hashCode();
        }

        /* 5. Micro timing (ns/op) */
        System.out.println("Precision timing...");
        val times = new long[1000];

        for (int i = 0; i < times.length; i++) {
            val context = contextSupplier.get();

            val start    = System.nanoTime();
            val decision = policy.evaluate(context).block();
            val duration = nanosSince(start);

            blackhole ^= decision.hashCode();
            times[i]   = duration;
        }

        /* 6. Throughput measurement (ops/sec for 2s) */
        System.out.println("Throughput measurement...");
        val ops = measureThroughput(policy, contextSupplier);

        results.add(new Result(name, pdpInitTime, loadTime, percentile(times, 50), percentile(times, 90),
                percentile(times, 99), ops));

        System.out.println("Done.");
    }

    // Measures real operations per second for a fixed duration
    private static long measureThroughput(Policy policy, Supplier<Context> contextSupplier) {

        val  end = System.nanoTime() + Duration.ofSeconds(THROUGHPUT_WINDOW_SECONDS).toNanos();
        long ops = 0;

        while (System.nanoTime() < end) {
            val context  = contextSupplier.get();
            val decision = policy.evaluate(context).block();

            blackhole ^= decision.hashCode();
            ops++;
        }

        return ops / THROUGHPUT_WINDOW_SECONDS;
    }

    // Produces slightly different contexts every call
    private static Supplier<Context> createContextSupplier() {

        return () -> {
            val random = ThreadLocalRandom.current().nextInt(0, 10_000);

            return new Context("user-" + random, "resource-" + (random % 37), (random & 1) == 0);
        };
    }

    private static long percentile(long[] values, int p) {

        val copy = values.clone();
        java.util.Arrays.sort(copy);

        val index = (int) Math.ceil((p / 100.0) * copy.length) - 1;
        return copy[Math.max(0, index)];
    }

    private static void printEnvironment() {

        System.out.println("\nJava Version: " + System.getProperty("java.version"));
        System.out.println("VM: " + System.getProperty("java.vm.name"));
        System.out.println("Heap: " + (Runtime.getRuntime().maxMemory() / 1024 / 1024) + " MB");
    }

    private static void printSummary() {

        System.out.println("\n\n================= SUMMARY =================");
        System.out.printf("%-12s | Init(ms) | Load(ms) | p50(ns) | p90(ns) | p99(ns) | Ops/sec%n", "Policy");

        for (val r : results) {
            System.out.printf("%-12s | %8.2f | %8.2f | %7d | %7d | %7d | %8d%n", r.name, r.pdpInitNanos / 1_000_000.0,
                    r.loadNanos / 1_000_000.0, r.p50, r.p90, r.p99, r.opsPerSecond);
        }

        System.out.println("===========================================");
    }

    // ---------------------------------------------------------------------
    // Replace everything below with actual PDP engine code
    // ---------------------------------------------------------------------

    private static Pdp createPdp() {
        return new Pdp();
    }

    private static Policy loadPolicy(Pdp pdp, String document) {
        return pdp.load(document);
    }

    private static String createSimplePolicyDocument() {
        return "POLICY_SIMPLE";
    }

    private static String createSimple2PolicyDocument() {
        return "POLICY_SIMPLE_2";
    }

    private static String createComplexPolicyDocument() {
        return "POLICY_COMPLEX";
    }

    // ---------------------------------------------------------------------
    // Dummy engine types - replace with real ones
    // ---------------------------------------------------------------------

    private static final class Pdp {
        Policy load(String document) {
            return new Policy(document.hashCode());
        }
    }

    private static final class Policy {
        private final int complexity;

        Policy(int complexity) {
            this.complexity = complexity;
        }

        Mono<Decision> evaluate(Context context) {
            var value = complexity;
            value ^= context.user.hashCode();
            value ^= context.resource.hashCode();
            value ^= Boolean.hashCode(context.flag);

            return Mono.just(new Decision(value));
        }
    }

    private record Context(String user, String resource, boolean flag) {}

    private record Decision(int value) {}

    private record Result(
            String name,
            long pdpInitNanos,
            long loadNanos,
            long p50,
            long p90,
            long p99,
            long opsPerSecond) {}

    private static long nanosSince(long start) {
        return System.nanoTime() - start;
    }
}
