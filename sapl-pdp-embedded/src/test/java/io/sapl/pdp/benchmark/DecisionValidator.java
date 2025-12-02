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

public class DecisionValidator {

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
            policy "age_restricted_content"
            deny
            where
                resource.ageRestricted == true;
                subject.age < 18;
            """, """
            policy "classification_denial"
            deny
            where
                resource.classification == "top_secret";
                !(subject.clearanceLevel in ["top_secret", "cosmic"]);
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
            obligation
                {
                    "type": "log_access",
                    "user": subject.id,
                    "action": action,
                    "resource": resource.id
                }
            """, """
            policy "export_restriction"
            deny action == "export"
            where
                resource.containsPII == true;
                !(subject.role in ["admin", "data_officer"]);
            """, """
            policy "cross_department_read"
            permit action == "read"
            where
                subject.collaborationEnabled == true;
                resource.sharedWith[-(subject.department)];
            """, """
            policy "default_deny"
            deny
            """);

    public static void main(String[] args) throws Exception {
        var pdp = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(POLICIES,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        var decisionCounts               = new HashMap<Decision, Integer>();
        var subscriptionsWithObligations = 0;
        var totalSubscriptions           = 10000;

        System.out.println("Validating " + totalSubscriptions + " random subscriptions...\n");

        for (int i = 0; i < totalSubscriptions; i++) {
            var subscription = createSubscription(ThreadLocalRandom.current().nextInt());
            var decision     = pdp.decide(subscription).blockFirst();

            decisionCounts.merge(decision.getDecision(), 1, Integer::sum);
            if (!decision.getObligations().isEmpty()) {
                subscriptionsWithObligations++;
            }
        }

        System.out.println("Decision Distribution:");
        for (var entry : decisionCounts.entrySet()) {
            System.out.printf("  %s: %d (%.1f%%)%n", entry.getKey(), entry.getValue(),
                    100.0 * entry.getValue() / totalSubscriptions);
        }
        System.out.println("\nSubscriptions with obligations: " + subscriptionsWithObligations);

        // Show a few example decisions
        System.out.println("\n--- Sample Decisions ---\n");
        for (int i = 0; i < 5; i++) {
            var subscription = createSubscription(i * 12345);
            var decision     = pdp.decide(subscription).blockFirst();
            System.out.printf("Seed %d: %s%n", i * 12345, decision.getDecision());
            System.out.printf("  Subject role: %s%n", getRole(i * 12345));
            System.out.printf("  Action: %s%n", getAction(i * 12345));
            if (!decision.getObligations().isEmpty()) {
                System.out.printf("  Obligations: %s%n", decision.getObligations());
            }
            System.out.println();
        }
    }

    private static String getRole(int seed) {
        return switch (Math.abs(seed) % 5) {
        case 0  -> "admin";
        case 1  -> "manager";
        case 2  -> "engineer";
        case 3  -> "employee";
        default -> "contractor";
        };
    }

    private static String getAction(int seed) {
        return switch (Math.abs(seed) % 6) {
        case 0  -> "read";
        case 1  -> "write";
        case 2  -> "update";
        case 3  -> "delete";
        case 4  -> "export";
        default -> "execute";
        };
    }

    private static AuthorizationSubscription createSubscription(int seed) {
        var random = ThreadLocalRandom.current();

        var role       = getRole(seed);
        var department = switch (Math.abs(seed) % 4) {
                       case 0  -> "engineering";
                       case 1  -> "finance";
                       case 2  -> "operations";
                       default -> "hr";
                       };

        var action = getAction(seed);

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

        var clearance = switch (Math.abs(seed) % 4) {
        case 0  -> "none";
        case 1  -> "confidential";
        case 2  -> "secret";
        default -> "top_secret";
        };

        var subject = """
                {
                    "id": "user_%d",
                    "role": "%s",
                    "department": "%s",
                    "age": %d,
                    "clearanceLevel": "%s",
                    "collaborationEnabled": %s
                }
                """.formatted(Math.abs(seed), role, department, 18 + Math.abs(seed % 50), clearance, seed % 3 == 0);

        var resourceDept = seed % 2 == 0 ? department : "other_dept";
        var resource     = """
                {
                    "id": "resource_%d",
                    "type": "%s",
                    "classification": "%s",
                    "owner": { "department": "%s" },
                    "ageRestricted": %s,
                    "containsPII": %s,
                    "sharedWith": ["%s", "operations"]
                }
                """.formatted(random.nextInt(10000), resourceType, classification, resourceDept, seed % 7 == 0,
                seed % 5 == 0, department);

        return AuthorizationSubscription.of(subject, action, resource);
    }
}
