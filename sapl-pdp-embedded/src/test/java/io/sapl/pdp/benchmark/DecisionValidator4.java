/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package io.sapl.pdp.benchmark;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.PolicyDecisionPointFactory;

import java.util.List;
import java.util.Map;

public class DecisionValidator4 {

    public static void main(String[] args) throws Exception {
        // Policy with no target (should always be checked)
        var policy1 = """
                policy "always_permit"
                permit
                """;

        // Policy with target matching admin
        var policy2 = """
                policy "admin_permit"
                permit subject.role == "admin"
                """;

        // Policy with where clause
        var policy3 = """
                policy "admin_where"
                permit
                where
                    subject.role == "admin";
                """;

        System.out.println("Testing policy matching with DENY_OVERRIDES...\n");

        var adminSub = AuthorizationSubscription.of("{\"role\": \"admin\"}", "read", "{\"type\": \"doc\"}");

        // Test 1: Always permit
        var pdp1 = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policy1),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        System.out.println("1. Always permit: " + pdp1.decide(adminSub).blockFirst().getDecision());

        // Test 2: Target expression
        var pdp2 = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policy2),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        System.out
                .println("2. Target expr (subject.role == admin): " + pdp2.decide(adminSub).blockFirst().getDecision());

        // Test 3: Where clause
        var pdp3 = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policy3),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        System.out.println(
                "3. Where clause (subject.role == admin): " + pdp3.decide(adminSub).blockFirst().getDecision());

        // Test 4: Both policies
        var pdp4 = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policy2, policy3),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        System.out.println("4. Both policies: " + pdp4.decide(adminSub).blockFirst().getDecision());

        // Test with non-admin
        var userSub = AuthorizationSubscription.of("{\"role\": \"user\"}", "read", "{\"type\": \"doc\"}");
        System.out.println("\n5. User with where clause: " + pdp3.decide(userSub).blockFirst().getDecision());
    }
}
