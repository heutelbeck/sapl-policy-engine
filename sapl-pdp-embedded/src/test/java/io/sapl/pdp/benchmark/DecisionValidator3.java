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

public class DecisionValidator3 {

    public static void main(String[] args) throws Exception {
        // Test with just one simple policy
        var policies = List.of("""
                policy "admin_full_access"
                permit
                where
                    subject.role == "admin";
                """);

        System.out.println("Testing with single policy (PERMIT_UNLESS_DENY)...\n");
        var pdp = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(policies,
                PolicyDocumentCombiningAlgorithm.PERMIT_UNLESS_DENY, Map.of());

        var adminSub = AuthorizationSubscription.of("{\"role\": \"admin\", \"id\": \"admin1\"}", "read",
                "{\"type\": \"document\"}");
        var adminDec = pdp.decide(adminSub).blockFirst();
        System.out.println("Admin (should be PERMIT): " + adminDec.getDecision());

        var userSub = AuthorizationSubscription.of("{\"role\": \"user\", \"id\": \"user1\"}", "read",
                "{\"type\": \"document\"}");
        var userDec = pdp.decide(userSub).blockFirst();
        System.out.println(
                "User (should be NOT_APPLICABLE then PERMIT due to PERMIT_UNLESS_DENY): " + userDec.getDecision());

        // Now test with DENY_OVERRIDES
        System.out.println("\nTesting with DENY_OVERRIDES...\n");
        var pdp2 = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(policies,
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        var adminDec2 = pdp2.decide(adminSub).blockFirst();
        System.out.println("Admin (should be PERMIT): " + adminDec2.getDecision());

        var userDec2 = pdp2.decide(userSub).blockFirst();
        System.out.println("User (should be NOT_APPLICABLE): " + userDec2.getDecision());

        // Test what the policy retrieval returns
        System.out.println("\n--- Debug: Raw decision values ---");
        System.out.println("Admin decision object: " + adminDec);
        System.out.println("Admin decision object2: " + adminDec2);
    }
}
