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
package io.sapl.benchmark.sapl4;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.model.jackson.SaplJacksonModule;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.benchmark.sapl4.oopsla.GdriveScenarioGenerator;
import io.sapl.benchmark.sapl4.oopsla.GithubScenarioGenerator;
import io.sapl.benchmark.sapl4.oopsla.TinytodoScenarioGenerator;
import lombok.experimental.UtilityClass;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory that builds {@link Scenario} instances by name. Each scenario is
 * constructed on demand with no static initialization tricks.
 */
@UtilityClass
class ScenarioFactory {

    private static final JsonMapper MAPPER = JsonMapper.builder().addModule(new SaplJacksonModule()).build();

    private static final String ERROR_UNKNOWN_SCENARIO = "Unknown scenario: %s. Available: %s.";

    private static final String MATCHING_SIMPLE = """
            policy "matching"
            permit
                action == "read";
                resource == "document";
            """;

    private static final String FILLER_SIMPLE = """
            policy "filler-%04d"
            permit
                action == "action-%04d";
                resource == "resource-%04d";
            """;

    private static final String MATCHING_COMPLEX = """
            policy "matching-complex"
            permit
                action == "read";
                resource == "document";
                "admin" in subject.roles;
                subject.department =~ "^engineering.*";
            """;

    private static final String FILLER_COMPLEX = """
            policy "filler-complex-%04d"
            permit
                action == "action-%04d";
                resource == "resource-%04d";
                "role-%04d" in subject.roles;
            """;

    private static final String MATCHING_SHARED = """
            policy "matching-shared"
            permit
                action == "read";
                resource.type == "document";
            """;

    private static final String FILLER_SHARED = """
            policy "filler-shared-%04d"
            permit
                action == "%s";
                resource.type == "type-%04d";
            """;

    private static final String[] SHARED_ACTIONS = { "read", "write", "delete", "update" };

    private static final AuthorizationSubscription SHARED_SUB = MAPPER.readValue("""
            {
              "subject":  "alice",
              "action":   "read",
              "resource": {"type":"document"}
            }
            """, AuthorizationSubscription.class);

    private static final String[] DEPARTMENTS = { "engineering", "qa", "sales", "marketing", "finance", "hr", "ops",
            "legal", "security", "support" };
    private static final String[] LOCATIONS   = { "london", "berlin", "new-york", "singapore", "sydney" };
    private static final String[] SENIORITIES = { "junior", "senior", "lead", "director" };
    private static final String[] ACTIONS     = { "read", "write", "delete", "approve" };

    private static final String PDP_JSON_TEMPLATE = """
            {
              "algorithm": {
                "votingMode": "PRIORITY_DENY",
                "defaultDecision": "DENY",
                "errorHandling": "PROPAGATE"
              },
              "compilerFlags": {
                "indexing": "%s"
              },
              "variables": %s
            }
            """;

    private static final CombiningAlgorithm ALGORITHM = new CombiningAlgorithm(VotingMode.PRIORITY_DENY,
            DefaultDecision.DENY, ErrorHandling.PROPAGATE);

    private static final AuthorizationSubscription RBAC_SUB = MAPPER.readValue("""
            {
              "subject":  {"username":"bob","role":"test"},
              "action":   "write",
              "resource": {"type":"foo123"}
            }
            """, AuthorizationSubscription.class);

    private static final AuthorizationSubscription SIMPLE_SUB = MAPPER.readValue("""
            {
              "subject":  "alice",
              "action":   "read",
              "resource": "document"
            }
            """, AuthorizationSubscription.class);

    private static final AuthorizationSubscription COMPLEX_SUB = MAPPER.readValue("""
            {
              "subject":  {"name":"alice","roles":["admin"],"department":"engineering"},
              "action":   "read",
              "resource": "document"
            }
            """, AuthorizationSubscription.class);

    private static final Scenario RBAC = new Scenario("rbac", () -> List.of("""
            policy "RBAC"
            permit
                { "type" : resource.type, "action": action } in permissions[(subject.role)];
            """), (ObjectValue) ValueJsonMarshaller.json("""
            {
              "permissions": {
                "dev":  [{"type":"foo123","action":"write"},{"type":"foo123","action":"read"}],
                "test": [{"type":"foo123","action":"read"}]
              }
            }
            """), ALGORITHM, RBAC_SUB, AuthorizationDecision.DENY);

    private static final Scenario RBAC_LARGE = new Scenario("rbac-large", () -> List.of("""
            policy "RBAC"
            permit
                { "type" : resource.type, "action": action } in permissions[(subject.role)];
            """), buildLargePermissions(), ALGORITHM, MAPPER.readValue("""
            {
              "subject":  {"username":"bob","role":"engineering-london-senior"},
              "action":   "write",
              "resource": {"type":"engineering-london"}
            }
            """, AuthorizationSubscription.class), AuthorizationDecision.PERMIT);

    private static final Scenario SIMPLE_1     = simpleScenario(1);
    private static final Scenario SIMPLE_100   = simpleScenario(100);
    private static final Scenario SIMPLE_500   = simpleScenario(500);
    private static final Scenario SIMPLE_1000  = simpleScenario(1000);
    private static final Scenario COMPLEX_1    = complexScenario(1);
    private static final Scenario COMPLEX_100  = complexScenario(100);
    private static final Scenario COMPLEX_1000 = complexScenario(1000);
    private static final Scenario SHARED_100   = sharedScenario(100);
    private static final Scenario SHARED_500   = sharedScenario(500);
    private static final Scenario SHARED_1000  = sharedScenario(1000);

    private static final Scenario[] STATIC_SCENARIOS = { RBAC, RBAC_LARGE, SIMPLE_1, SIMPLE_100, SIMPLE_500,
            SIMPLE_1000, COMPLEX_1, COMPLEX_100, COMPLEX_1000, SHARED_100, SHARED_500, SHARED_1000 };

    private static final Map<String, Scenario> STATIC_MAP = Arrays.stream(STATIC_SCENARIOS)
            .collect(Collectors.toUnmodifiableMap(Scenario::name, Function.identity()));

    private static final int[] OOPSLA_ENTITY_COUNTS = { 5, 10, 25, 50 };

    private static final long DEFAULT_SEED = 42L;

    static final List<String> ALL_SCENARIO_NAMES;

    private static final int[] HOSPITAL_DEPARTMENT_COUNTS = { 5, 10, 50, 100, 300 };

    static {
        var names = new ArrayList<>(STATIC_MAP.keySet());
        for (var n : OOPSLA_ENTITY_COUNTS) {
            names.add("gdrive-" + n);
            names.add("github-" + n);
            names.add("tinytodo-" + n);
        }
        for (var n : HOSPITAL_DEPARTMENT_COUNTS) {
            names.add("hospital-" + n);
        }
        ALL_SCENARIO_NAMES = List.copyOf(names);
    }

    /**
     * Gets a scenario by name using the default seed (42).
     *
     * @param name the scenario name (case-insensitive)
     * @return the scenario
     * @throws IllegalArgumentException if no scenario matches
     */
    static Scenario create(String name) {
        return create(name, DEFAULT_SEED);
    }

    /**
     * Gets a scenario by name. OOPSLA scenarios (gdrive-N, github-N,
     * tinytodo-N) are built on demand with the given seed, producing a unique
     * entity graph per seed. Non-OOPSLA scenarios ignore the seed.
     *
     * @param name the scenario name (case-insensitive)
     * @param seed RNG seed for OOPSLA entity graph generation
     * @return the scenario
     * @throws IllegalArgumentException if no scenario matches
     */
    static Scenario create(String name, long seed) {
        var lower    = name.toLowerCase();
        var scenario = STATIC_MAP.get(lower);
        if (scenario != null) {
            return scenario;
        }
        var oopsla = createOopslaScenario(lower, seed);
        if (oopsla != null) {
            return oopsla;
        }
        throw new IllegalArgumentException(ERROR_UNKNOWN_SCENARIO.formatted(name, ALL_SCENARIO_NAMES));
    }

    private static Scenario createOopslaScenario(String name, long seed) {
        var parts = name.split("-", 2);
        if (parts.length != 2) {
            return null;
        }
        int entityCount;
        try {
            entityCount = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            return null;
        }
        return switch (parts[0]) {
        case "gdrive"   -> GdriveScenarioGenerator.generate(entityCount, seed);
        case "github"   -> GithubScenarioGenerator.generate(entityCount, seed);
        case "tinytodo" -> TinytodoScenarioGenerator.generate(entityCount, seed);
        case "hospital" -> HospitalScenarioGenerator.generate(entityCount, seed);
        default         -> null;
        };
    }

    /**
     * Exports a scenario's policies, pdp.json, and subscription.json to a
     * directory.
     *
     * @param scenario the scenario to export
     * @param directory target directory (created if needed)
     * @param indexingStrategy the indexing strategy to write into pdp.json
     * @throws IOException if writing fails
     */
    static void exportScenario(Scenario scenario, Path directory, String indexingStrategy) throws IOException {
        Files.createDirectories(directory);
        var variablesJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(scenario.variables());
        Files.writeString(directory.resolve("pdp.json"), PDP_JSON_TEMPLATE.formatted(indexingStrategy, variablesJson));
        Files.writeString(directory.resolve("subscription.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(scenario.subscription()));
        if (scenario.subscriptions().size() > 1) {
            Files.writeString(directory.resolve("subscriptions.json"),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(scenario.subscriptions()));
        }
        var policies = scenario.policies().get();
        for (int i = 0; i < policies.size(); i++) {
            Files.writeString(directory.resolve("policy-%04d.sapl".formatted(i + 1)), policies.get(i));
        }
    }

    private static Scenario simpleScenario(int policyCount) {
        return new Scenario("simple-" + policyCount, () -> {
            var policies = new ArrayList<String>();
            policies.add(MATCHING_SIMPLE);
            for (int i = 2; i <= policyCount; i++) {
                policies.add(FILLER_SIMPLE.formatted(i, i, i));
            }
            return policies;
        }, Value.EMPTY_OBJECT, ALGORITHM, SIMPLE_SUB, AuthorizationDecision.PERMIT);
    }

    private static Scenario complexScenario(int policyCount) {
        return new Scenario("complex-" + policyCount, () -> {
            var policies = new ArrayList<String>();
            policies.add(MATCHING_COMPLEX);
            for (int i = 2; i <= policyCount; i++) {
                policies.add(FILLER_COMPLEX.formatted(i, i, i, i));
            }
            return policies;
        }, Value.EMPTY_OBJECT, ALGORITHM, COMPLEX_SUB, AuthorizationDecision.PERMIT);
    }

    private static Scenario sharedScenario(int policyCount) {
        return new Scenario("shared-" + policyCount, () -> {
            var policies = new ArrayList<String>();
            policies.add(MATCHING_SHARED);
            for (int i = 2; i <= policyCount; i++) {
                var action = SHARED_ACTIONS[i % SHARED_ACTIONS.length];
                policies.add(FILLER_SHARED.formatted(i, action, i));
            }
            return policies;
        }, Value.EMPTY_OBJECT, ALGORITHM, SHARED_SUB, AuthorizationDecision.PERMIT);
    }

    /**
     * Generates an RBAC permissions map with 200 roles (10 departments x 5
     * locations x 4 seniority levels). Each seniority gets 1-4 actions.
     */
    private static ObjectValue buildLargePermissions() {
        var permissionsByRole = ObjectValue.builder();
        for (var department : DEPARTMENTS) {
            for (var location : LOCATIONS) {
                for (int seniorityIndex = 0; seniorityIndex < SENIORITIES.length; seniorityIndex++) {
                    var roleName        = department + "-" + location + "-" + SENIORITIES[seniorityIndex];
                    var actionCount     = seniorityIndex + 1;
                    var rolePermissions = new Value[actionCount];
                    for (int actionIndex = 0; actionIndex < actionCount; actionIndex++) {
                        rolePermissions[actionIndex] = Value.ofObject(Map.of("type",
                                Value.of(department + "-" + location), "action", Value.of(ACTIONS[actionIndex])));
                    }
                    permissionsByRole.put(roleName, Value.ofArray(rolePermissions));
                }
            }
        }
        return ObjectValue.builder().put("permissions", permissionsByRole.build()).build();
    }

}
