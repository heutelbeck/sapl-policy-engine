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
package io.sapl.benchmark;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.pdp.DynamicPolicyDecisionPoint;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Threads;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * JMH benchmark for NEW SAPL PDP (Value-based) performance measurement.
 * Compares REACTIVE (Flux-based) and PURE
 * (synchronous) evaluation paths.
 * <p>
 * For OLD PDP (Val-based) benchmarks, run EmbeddedPdpBenchmark from the
 * sapl-pdp-embedded module instead.
 * <p>
 * Measures throughput and latency across:
 * <ul>
 * <li>Evaluation path: REACTIVE (Flux), PURE (sync)</li>
 * <li>Policy set size: 1, 5, 9 policies</li>
 * <li>Combining algorithm: DENY_OVERRIDES</li>
 * <li>Concurrency: 1 to 48 threads</li>
 * </ul>
 */
@State(Scope.Benchmark)
@Warmup(iterations = 3, time = 2, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 5, time = 3, timeUnit = TimeUnit.SECONDS)
public class PdpBenchmark {

    private static final String[] ROLES       = { "admin", "manager", "engineer", "employee", "contractor" };
    private static final String[] ACTIONS     = { "read", "write", "update", "delete", "export" };
    private static final String[] DEPARTMENTS = { "engineering", "finance", "operations", "hr" };
    private static final String[] RES_TYPES   = { "document", "dataset", "code", "image", "internal_doc" };
    private static final String[] CLASSIFS    = { "public", "internal", "confidential", "top_secret" };
    private static final String[] CLEARANCES  = { "none", "confidential", "secret", "top_secret" };

    /**
     * JSON template for generating authorization subscriptions.
     */
    private static final String SUBSCRIPTION_TEMPLATE = """
            {
                "subject": {
                    "id": "user_%d",
                    "role": "%s",
                    "department": "%s",
                    "age": %d,
                    "clearanceLevel": "%s",
                    "collaborationEnabled": %s
                },
                "action": "%s",
                "resource": {
                    "id": "resource_%d",
                    "type": "%s",
                    "classification": "%s",
                    "owner": { "department": "%s" },
                    "ageRestricted": %s,
                    "containsPII": %s
                }
            }
            """;

    /**
     * Policy templates for different complexity levels.
     */
    private static final String[] ALL_POLICIES = {
            "policy \"admin_full_access\" permit where subject.role == \"admin\";",
            "policy \"manager_department_access\" permit action in [\"read\", \"write\", \"update\"] where subject.role == \"manager\"; resource.owner.department == subject.department;",
            "policy \"engineer_technical_read\" permit action == \"read\" where subject.role == \"engineer\"; resource.type in [\"document\", \"dataset\", \"code\"]; resource.classification != \"confidential\";",
            "policy \"age_restricted_content\" deny where resource.ageRestricted == true; subject.age < 18;",
            "policy \"classification_denial\" deny where resource.classification == \"top_secret\"; !(subject.clearanceLevel in [\"top_secret\", \"cosmic\"]);",
            "policy \"working_hours\" permit where subject.role in [\"employee\", \"contractor\"]; action == \"read\"; resource.type == \"internal_doc\";",
            "policy \"audit_logging\" permit where subject.role in [\"admin\", \"manager\", \"engineer\"]; action in [\"read\", \"write\", \"update\", \"delete\"];",
            "policy \"export_restriction\" deny action == \"export\" where resource.containsPII == true; !(subject.role in [\"admin\", \"data_officer\"]);",
            "policy \"cross_department_read\" permit action == \"read\" where subject.collaborationEnabled == true;" };

    private static final ObjectMapper MAPPER = new ObjectMapper().registerModule(new SaplJacksonModule());

    @Param({ "REACTIVE", "PURE" })
    private String evaluationPath;

    @Param({ "1", "5", "9" })
    private int policyCount;

    @Param({ "DENY_OVERRIDES" })
    private String combiningAlgorithm;

    private DynamicPolicyDecisionPoint  pdp;
    private AuthorizationSubscription[] subscriptions;

    @Setup(Level.Trial)
    public void setup() {
        var random   = new Random(42);
        var policies = new ArrayList<String>();
        for (int i = 0; i < policyCount && i < ALL_POLICIES.length; i++) {
            policies.add(ALL_POLICIES[i]);
        }

        var jsonStrings = generateSubscriptionJsonStrings(10000, random);
        subscriptions = new AuthorizationSubscription[jsonStrings.length];
        for (int i = 0; i < jsonStrings.length; i++) {
            try {
                subscriptions[i] = MAPPER.readValue(jsonStrings[i], AuthorizationSubscription.class);
            } catch (JsonProcessingException exception) {
                throw new IllegalStateException("Failed to parse subscription JSON.", exception);
            }
        }

        try {
            var components            = PolicyDecisionPointBuilder.withoutDefaults().build();
            var configurationRegister = components.configurationRegister();
            var algorithm             = CombiningAlgorithm.valueOf(combiningAlgorithm);
            var configuration         = new PDPConfiguration("default", "benchmark-config", algorithm, policies,
                    Map.of());
            configurationRegister.loadConfiguration(configuration, false);
            pdp = (DynamicPolicyDecisionPoint) components.pdp();
        } catch (Exception exception) {
            throw new RuntimeException("Failed to initialize PDP: " + exception.getMessage(), exception);
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        pdp           = null;
        subscriptions = null;
    }

    private String[] generateSubscriptionJsonStrings(int count, Random random) {
        var jsonStrings = new String[count];
        for (int i = 0; i < count; i++) {
            var role           = ROLES[random.nextInt(ROLES.length)];
            var department     = DEPARTMENTS[random.nextInt(DEPARTMENTS.length)];
            var clearance      = CLEARANCES[random.nextInt(CLEARANCES.length)];
            var action         = ACTIONS[random.nextInt(ACTIONS.length)];
            var resourceType   = RES_TYPES[random.nextInt(RES_TYPES.length)];
            var classification = CLASSIFS[random.nextInt(CLASSIFS.length)];
            var ownerDept      = DEPARTMENTS[random.nextInt(DEPARTMENTS.length)];

            jsonStrings[i] = SUBSCRIPTION_TEMPLATE.formatted(i, role, department, 18 + random.nextInt(50), clearance,
                    random.nextBoolean(), action, random.nextInt(10000), resourceType, classification, ownerDept,
                    random.nextBoolean(), random.nextBoolean());
        }
        return jsonStrings;
    }

    private Object executeDecision() {
        var index = ThreadLocalRandom.current().nextInt(subscriptions.length);
        return "PURE".equals(evaluationPath) ? pdp.decidePure(subscriptions[index])
                : pdp.decide(subscriptions[index]).next().block();
    }

    // ========== THROUGHPUT BENCHMARKS ==========

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Threads(1)
    public void throughput_1thread(Blackhole blackhole) {
        blackhole.consume(executeDecision());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Threads(4)
    public void throughput_4threads(Blackhole blackhole) {
        blackhole.consume(executeDecision());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Threads(8)
    public void throughput_8threads(Blackhole blackhole) {
        blackhole.consume(executeDecision());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Threads(16)
    public void throughput_16threads(Blackhole blackhole) {
        blackhole.consume(executeDecision());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Threads(24)
    public void throughput_24threads(Blackhole blackhole) {
        blackhole.consume(executeDecision());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Threads(32)
    public void throughput_32threads(Blackhole blackhole) {
        blackhole.consume(executeDecision());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    @Threads(48)
    public void throughput_48threads(Blackhole blackhole) {
        blackhole.consume(executeDecision());
    }

    // ========== LATENCY BENCHMARKS ==========

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    @Threads(1)
    public void latency(Blackhole blackhole) {
        blackhole.consume(executeDecision());
    }
}
