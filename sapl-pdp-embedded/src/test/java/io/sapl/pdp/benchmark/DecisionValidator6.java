/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
 * SPDX-License-Identifier: Apache-2.0
 */
package io.sapl.pdp.benchmark;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.interpreter.combinators.PolicyDocumentCombiningAlgorithm;
import io.sapl.pdp.PolicyDecisionPointFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import java.util.List;
import java.util.Map;

public class DecisionValidator6 {

    public static void main(String[] args) throws Exception {
        var mapper = new ObjectMapper();

        var policy = """
                policy "test"
                permit
                where
                    subject.name == "alice";
                """;

        System.out.println("Testing with properly parsed JSON...\n");

        // Create subscription with JsonNode subject
        var subjectNode  = mapper.readTree("{\"name\": \"alice\"}");
        var actionNode   = JsonNodeFactory.instance.textNode("read");
        var resourceNode = JsonNodeFactory.instance.textNode("doc");

        var sub1 = new AuthorizationSubscription(subjectNode, actionNode, resourceNode, null);

        var pdp = PolicyDecisionPointFactory.fixedInRamPolicyDecisionPoint(List.of(policy),
                PolicyDocumentCombiningAlgorithm.DENY_OVERRIDES, Map.of());

        System.out.println("With JsonNode subject (name='alice'): " + pdp.decide(sub1).blockFirst().getDecision());

        // Test with wrong name
        var subjectNode2 = mapper.readTree("{\"name\": \"bob\"}");
        var sub2         = new AuthorizationSubscription(subjectNode2, actionNode, resourceNode, null);
        System.out.println("With JsonNode subject (name='bob'): " + pdp.decide(sub2).blockFirst().getDecision());

        // Compare with string version
        var sub3 = AuthorizationSubscription.of("{\"name\": \"alice\"}", "read", "doc");
        System.out.println("\nWith String version (should NOT work): " + pdp.decide(sub3).blockFirst().getDecision());
        System.out.println("Sub3 subject class: " + sub3.getSubject().getClass().getSimpleName());
        System.out.println("Sub3 subject value: " + sub3.getSubject());
    }
}
