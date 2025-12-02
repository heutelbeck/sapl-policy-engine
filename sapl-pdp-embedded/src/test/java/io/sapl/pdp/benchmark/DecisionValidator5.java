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

public class DecisionValidator5 {

    public static void main(String[] args) throws Exception {
        var policy = """
                policy "test"
                permit
                where
                    subject == "admin";
                """;

        System.out.println("Testing simple string comparison...\n");

        // Subject as simple string
        var sub1 = AuthorizationSubscription.of("admin", "read", "doc");
        var pdp  = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policy),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());
        System.out.println("subject == 'admin' (subject='admin'): " + pdp.decide(sub1).blockFirst().getDecision());

        var sub2 = AuthorizationSubscription.of("user", "read", "doc");
        System.out.println("subject == 'admin' (subject='user'): " + pdp.decide(sub2).blockFirst().getDecision());

        // Now test with object and field access
        var policy2 = """
                policy "test2"
                permit
                where
                    subject.name == "alice";
                """;

        var pdp2 = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policy2),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        var sub3 = AuthorizationSubscription.of("{\"name\": \"alice\"}", "read", "doc");
        System.out.println("subject.name == 'alice' (name='alice'): " + pdp2.decide(sub3).blockFirst().getDecision());

        var sub4 = AuthorizationSubscription.of("{\"name\": \"bob\"}", "read", "doc");
        System.out.println("subject.name == 'alice' (name='bob'): " + pdp2.decide(sub4).blockFirst().getDecision());

        // Test with action matching
        var policy3 = """
                policy "action_test"
                permit action == "read"
                """;

        var pdp3 = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policy3),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        var sub5 = AuthorizationSubscription.of("user", "read", "doc");
        System.out.println("action == 'read' (action='read'): " + pdp3.decide(sub5).blockFirst().getDecision());

        var sub6 = AuthorizationSubscription.of("user", "write", "doc");
        System.out.println("action == 'read' (action='write'): " + pdp3.decide(sub6).blockFirst().getDecision());
    }
}
