/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package io.sapl.pdp.benchmark;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.PolicyDecisionPointFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DecisionValidator2 {

    // Simplified policies without problematic syntax
    private static final List<String> POLICIES = List.of("""
            policy "admin_full_access"
            permit
            where
                subject.role == "admin";
            """, """
            policy "manager_department_access"
            permit action in ["read", "write", "update"]
            where
                subject.role == "manager";
                resource.owner.department == subject.department;
            """, """
            policy "engineer_technical_read"
            permit action == "read"
            where
                subject.role == "engineer";
                resource.type in ["document", "dataset", "code"];
                resource.classification != "confidential";
            """, """
            policy "working_hours"
            permit
            where
                subject.role in ["employee", "contractor"];
                action == "read";
                resource.type == "internal_doc";
            """, """
            policy "audit_logging"
            permit
            where
                subject.role in ["admin", "manager", "engineer"];
                action in ["read", "write", "update", "delete"];
            """, """
            policy "default_deny"
            deny
            """);

    public static void main(String[] args) throws Exception {
        var pdp = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(POLICIES,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        var decisionCounts     = new HashMap<Decision, Integer>();
        var totalSubscriptions = 10000;

        System.out.println("Validating " + totalSubscriptions + " random subscriptions with simplified policies...\n");

        for (int i = 0; i < totalSubscriptions; i++) {
            var subscription = createSubscription(ThreadLocalRandom.current().nextInt());
            var decision     = pdp.decide(subscription).blockFirst();
            decisionCounts.merge(decision.getDecision(), 1, Integer::sum);
        }

        System.out.println("Decision Distribution:");
        for (var entry : decisionCounts.entrySet()) {
            System.out.printf("  %s: %d (%.1f%%)%n", entry.getKey(), entry.getValue(),
                    100.0 * entry.getValue() / totalSubscriptions);
        }

        // Test specific cases
        System.out.println("\n--- Specific Test Cases ---\n");

        // Admin should get PERMIT
        var adminSub = AuthorizationSubscription.of("{\"role\": \"admin\", \"id\": \"admin1\"}", "read",
                "{\"type\": \"document\"}");
        var adminDec = pdp.decide(adminSub).blockFirst();
        System.out.println("Admin read document: " + adminDec.getDecision());

        // Engineer read document (non-confidential) should get PERMIT
        var engSub = AuthorizationSubscription.of("{\"role\": \"engineer\", \"id\": \"eng1\"}", "read",
                "{\"type\": \"document\", \"classification\": \"public\"}");
        var engDec = pdp.decide(engSub).blockFirst();
        System.out.println("Engineer read public document: " + engDec.getDecision());

        // Unknown role should get DENY (from default_deny)
        var unknownSub = AuthorizationSubscription.of("{\"role\": \"unknown\", \"id\": \"unknown1\"}", "read",
                "{\"type\": \"document\"}");
        var unknownDec = pdp.decide(unknownSub).blockFirst();
        System.out.println("Unknown role read document: " + unknownDec.getDecision());
    }

    private static AuthorizationSubscription createSubscription(int seed) {
        var random = ThreadLocalRandom.current();

        var role = switch (Math.abs(seed) % 5) {
        case 0  -> "admin";
        case 1  -> "manager";
        case 2  -> "engineer";
        case 3  -> "employee";
        default -> "contractor";
        };

        var department = switch (Math.abs(seed) % 4) {
        case 0  -> "engineering";
        case 1  -> "finance";
        case 2  -> "operations";
        default -> "hr";
        };

        var action = switch (Math.abs(seed) % 6) {
        case 0  -> "read";
        case 1  -> "write";
        case 2  -> "update";
        case 3  -> "delete";
        case 4  -> "export";
        default -> "execute";
        };

        var resourceType = switch (Math.abs(seed) % 5) {
        case 0  -> "document";
        case 1  -> "dataset";
        case 2  -> "code";
        case 3  -> "image";
        default -> "internal_doc";
        };

        var classification = switch (Math.abs(seed) % 4) {
        case 0  -> "public";
        case 1  -> "internal";
        case 2  -> "confidential";
        default -> "top_secret";
        };

        var subject = """
                {
                    "id": "user_%d",
                    "role": "%s",
                    "department": "%s",
                    "age": %d
                }
                """.formatted(Math.abs(seed), role, department, 18 + Math.abs(seed % 50));

        var resourceDept = seed % 2 == 0 ? department : "other_dept";
        var resource     = """
                {
                    "id": "resource_%d",
                    "type": "%s",
                    "classification": "%s",
                    "owner": { "department": "%s" }
                }
                """.formatted(random.nextInt(10000), resourceType, classification, resourceDept);

        return AuthorizationSubscription.of(subject, action, resource);
    }
}
