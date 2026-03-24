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
package io.sapl.node.cli.benchmark;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Generates a standardized benchmark policy corpus for performance testing.
 * Produces directories with varying policy counts and complexity levels.
 * <p>
 * All scenarios use the same subscription contract:
 *
 * <pre>
 * subject="alice"  action="read"  resource="document"
 * </pre>
 * <p>
 * Usage:
 * {@code java -cp test-classes io.sapl.node.cli.BenchmarkPolicyGenerator /tmp/benchmark-policies}
 */
class BenchmarkPolicyGenerator {

    private static final String PDP_JSON = """
            {
              "algorithm": {
                "votingMode": "PRIORITY_DENY",
                "defaultDecision": "DENY",
                "errorHandling": "PROPAGATE"
              },
              "variables": {}
            }
            """;

    private static final String MATCHING_SIMPLE = """
            policy "matching-policy"
            permit
                action == "read";
                resource == "document";
            """;

    private static final String NON_MATCHING_SIMPLE = """
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
                var level = subject.clearanceLevel;
            obligation
                {
                    "type"    : "logAccess",
                    "message" : "User " + subject.name + " accessed " + resource + " at level " + level
                }
            advice
                {
                    "type"    : "audit",
                    "details" : "read access to " + resource + " by " + subject.name
                }
            """;

    private static final String NON_MATCHING_COMPLEX = """
            policy "filler-complex-%04d"
            permit
                action == "action-%04d";
                resource == "resource-%04d";
                "role-%04d" in subject.roles;
                subject.department =~ "^department-%04d.*";
                var ref = subject.clearanceLevel;
            obligation
                {
                    "type"    : "logAccess",
                    "message" : "User " + subject.name + " accessed resource-%04d at level " + ref
                }
            advice
                {
                    "type"    : "audit",
                    "details" : "access to resource-%04d by " + subject.name
                }
            """;

    private static final String ALL_MATCHING_SIMPLE = """
            policy "match-all-%04d"
            permit
                action == "read";
                resource == "document";
            """;

    public static void main(String[] args) throws IOException {
        if (args.length < 1 || args.length > 2) {
            System.err.println("Usage: BenchmarkPolicyGenerator <output-directory> [--large]");
            System.exit(1);
        }
        var outputDir = Path.of(args[0]);
        var large     = args.length == 2 && "--large".equals(args[1]);
        Files.createDirectories(outputDir);

        generateEmpty(outputDir.resolve("empty"));
        generateSimple(outputDir.resolve("simple-1"), 1);
        generateSimple(outputDir.resolve("simple-10"), 10);
        generateSimple(outputDir.resolve("simple-100"), 100);
        generateSimple(outputDir.resolve("simple-500"), 500);

        generateComplex(outputDir.resolve("complex-1"), 1);
        generateComplex(outputDir.resolve("complex-10"), 10);
        generateComplex(outputDir.resolve("complex-100"), 100);

        generateAllMatch(outputDir.resolve("all-match-100"), 100);

        generateRbacOpa(outputDir.resolve("rbac-small"));
        generateRbacExplosion(outputDir.resolve("rbac-large"));
        generateAbacEquivalent(outputDir.resolve("abac-equivalent"));

        if (large) {
            generateSimple(outputDir.resolve("simple-1000"), 1000);
            generateSimple(outputDir.resolve("simple-5000"), 5000);
            generateSimple(outputDir.resolve("simple-10000"), 10000);
            generateComplex(outputDir.resolve("complex-1000"), 1000);
            generateComplex(outputDir.resolve("complex-5000"), 5000);
            generateComplex(outputDir.resolve("complex-10000"), 10000);
            generateAllMatch(outputDir.resolve("all-match-1000"), 1000);
        }

        System.out.println("Generated benchmark policies in: " + outputDir + (large ? " (including large sets)" : ""));
        System.out.println(
                "Subscription: -s '{\"name\":\"alice\",\"roles\":[\"admin\"],\"department\":\"engineering\",\"clearanceLevel\":5}' -a '\"read\"' -r '\"document\"'");
    }

    private static void generateEmpty(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pdp.json"), PDP_JSON);
    }

    private static void generateSimple(Path dir, int count) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pdp.json"), PDP_JSON);
        Files.writeString(dir.resolve("matching.sapl"), MATCHING_SIMPLE);
        for (int i = 2; i <= count; i++) {
            var content = NON_MATCHING_SIMPLE.formatted(i, i, i);
            Files.writeString(dir.resolve("filler-%04d.sapl".formatted(i)), content);
        }
    }

    private static void generateComplex(Path dir, int count) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pdp.json"), PDP_JSON);
        Files.writeString(dir.resolve("matching.sapl"), MATCHING_COMPLEX);
        for (int i = 2; i <= count; i++) {
            var content = NON_MATCHING_COMPLEX.formatted(i, i, i, i, i, i, i);
            Files.writeString(dir.resolve("filler-%04d.sapl".formatted(i)), content);
        }
    }

    private static void generateAllMatch(Path dir, int count) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pdp.json"), PDP_JSON);
        for (int i = 1; i <= count; i++) {
            var content = ALL_MATCHING_SIMPLE.formatted(i);
            Files.writeString(dir.resolve("match-%04d.sapl".formatted(i)), content);
        }
    }

    private static void generateRbacOpa(Path dir) throws IOException {
        Files.createDirectories(dir);
        var pdpJson = """
                {
                  "algorithm": {
                    "votingMode": "PRIORITY_DENY",
                    "defaultDecision": "DENY",
                    "errorHandling": "PROPAGATE"
                  },
                  "variables": {
                    "permissions" : {
                      "dev" : [
                          { "type": "foo123", "action": "write" },
                          { "type": "foo123", "action": "read"  }
                        ],
                      "test" : [
                          { "type": "foo123", "action": "read" }
                        ]
                    }
                  }
                }
                """;
        var policy  = """
                policy "RBAC"
                permit
                    { "type" : resource.type, "action": action } in permissions[(subject.role)];
                """;
        Files.writeString(dir.resolve("pdp.json"), pdpJson);
        Files.writeString(dir.resolve("rbac.sapl"), policy);
    }

    private static final String[] DEPARTMENTS = { "engineering", "qa", "sales", "marketing", "finance", "hr", "ops",
            "legal", "security", "support" };
    private static final String[] LOCATIONS   = { "london", "berlin", "new-york", "singapore", "sydney" };
    private static final String[] SENIORITIES = { "junior", "senior", "lead", "director" };
    private static final String[] ACTIONS     = { "read", "write", "delete", "approve" };

    private static void generateRbacExplosion(Path dir) throws IOException {
        Files.createDirectories(dir);
        var permissions = new StringBuilder("{\n");
        var first       = true;
        for (var dept : DEPARTMENTS) {
            for (var loc : LOCATIONS) {
                for (var seniority : SENIORITIES) {
                    var roleName = dept + "-" + loc + "-" + seniority;
                    if (!first) {
                        permissions.append(",\n");
                    }
                    first = false;
                    permissions.append("        \"%s\" : [\n".formatted(roleName));
                    var perms     = new java.util.ArrayList<String>();
                    var maxAction = switch (seniority) {
                                  case "junior"   -> 1;
                                  case "senior"   -> 2;
                                  case "lead"     -> 3;
                                  case "director" -> 4;
                                  default         -> 1;
                                  };
                    for (int a = 0; a < maxAction; a++) {
                        perms.add("            { \"type\": \"%s-%s\", \"action\": \"%s\" }".formatted(dept, loc,
                                ACTIONS[a]));
                    }
                    permissions.append(String.join(",\n", perms));
                    permissions.append("\n          ]");
                }
            }
        }
        permissions.append("\n      }");

        var pdpJson = """
                {
                  "algorithm": {
                    "votingMode": "PRIORITY_DENY",
                    "defaultDecision": "DENY",
                    "errorHandling": "PROPAGATE"
                  },
                  "variables": {
                    "permissions" : %s
                  }
                }
                """.formatted(permissions);

        var policy = """
                policy "RBAC"
                permit
                    { "type" : resource.type, "action": action } in permissions[(subject.role)];
                """;

        Files.writeString(dir.resolve("pdp.json"), pdpJson);
        Files.writeString(dir.resolve("rbac.sapl"), policy);
    }

    private static void generateAbacEquivalent(Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("pdp.json"), PDP_JSON);
        Files.writeString(dir.resolve("read-access.sapl"), """
                policy "read access"
                permit
                    action == "read";
                    subject.department == resource.department;
                    resource.location == subject.location;
                """);
        Files.writeString(dir.resolve("write-access.sapl"), """
                policy "write access"
                permit
                    action == "write";
                    subject.department == resource.department;
                    resource.location == subject.location;
                    subject.seniority in ["senior", "lead", "director"];
                """);
        Files.writeString(dir.resolve("delete-access.sapl"), """
                policy "delete access"
                permit
                    action == "delete";
                    subject.department == resource.department;
                    resource.location == subject.location;
                    subject.seniority in ["lead", "director"];
                """);
        Files.writeString(dir.resolve("approve-access.sapl"), """
                policy "approve access"
                permit
                    action == "approve";
                    subject.department == resource.department;
                    resource.location == subject.location;
                    subject.seniority == "director";
                """);
    }

}
