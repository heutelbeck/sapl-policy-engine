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
import lombok.val;
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

    private static final Scenario BASELINE = new Scenario("baseline", () -> List.of("""
            policy "baseline"
            permit
            """), Value.EMPTY_OBJECT, ALGORITHM, AuthorizationSubscription.of("alice", "read", "document"),
            AuthorizationDecision.PERMIT);

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
            """), ALGORITHM, MAPPER.readValue("""
            {
              "subject":  {"username":"bob","role":"test"},
              "action":   "write",
              "resource": {"type":"foo123"}
            }
            """, AuthorizationSubscription.class), AuthorizationDecision.DENY);

    private static final Scenario[] STATIC_SCENARIOS = { BASELINE, RBAC };

    private static final Map<String, Scenario> STATIC_MAP = Arrays.stream(STATIC_SCENARIOS)
            .collect(Collectors.toUnmodifiableMap(Scenario::name, Function.identity()));

    private static final int[] OOPSLA_ENTITY_COUNTS = { 5, 10, 25, 50 };

    private static final long DEFAULT_SEED = 42L;

    static final List<String> ALL_SCENARIO_NAMES;

    private static final int[] HOSPITAL_DEPARTMENT_COUNTS = { 1, 5, 10, 50, 100, 300 };

    static {
        val names = new ArrayList<>(STATIC_MAP.keySet());
        for (val n : OOPSLA_ENTITY_COUNTS) {
            names.add("gdrive-" + n);
            names.add("github-" + n);
            names.add("tinytodo-" + n);
        }
        for (val n : HOSPITAL_DEPARTMENT_COUNTS) {
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
        val lower    = name.toLowerCase();
        val scenario = STATIC_MAP.get(lower);
        if (scenario != null) {
            return scenario;
        }
        val oopsla = createOopslaScenario(lower, seed);
        if (oopsla != null) {
            return oopsla;
        }
        throw new IllegalArgumentException(ERROR_UNKNOWN_SCENARIO.formatted(name, ALL_SCENARIO_NAMES));
    }

    private static Scenario createOopslaScenario(String name, long seed) {
        val parts = name.split("-", 2);
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
        val variablesJson = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(scenario.variables());
        Files.writeString(directory.resolve("pdp.json"), PDP_JSON_TEMPLATE.formatted(indexingStrategy, variablesJson));
        Files.writeString(directory.resolve("subscription.json"),
                MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(scenario.subscription()));
        if (scenario.subscriptions().size() > 1) {
            Files.writeString(directory.resolve("subscriptions.json"),
                    MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(scenario.subscriptions()));
        }
        val policies = scenario.policies().get();
        for (int i = 0; i < policies.size(); i++) {
            Files.writeString(directory.resolve("policy-%04d.sapl".formatted(i + 1)), policies.get(i));
        }
    }

}
