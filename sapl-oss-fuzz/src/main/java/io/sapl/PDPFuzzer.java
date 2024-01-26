/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import reactor.test.StepVerifier;

/**
 * PDPFuzzer for .clusterfuzzlite
 */
public class PDPFuzzer {
    public static String[] splitStringIntoThreeParts(String input) {
        String[] result = new String[3];

        if (input == null || input.isEmpty()) {
            result[0] = "";
            result[1] = "";
            result[2] = "";
            return result;
        }

        int length = input.length();
        if (length < 3) {
            result[0] = input;
            return result;
        }
        int partLength = length / 3;

        String part1 = input.substring(0, partLength);
        String part2 = input.substring(partLength, 2 * partLength);
        String part3 = input.substring(2 * partLength);

        result[0] = part1;
        result[1] = part2;
        result[2] = part3;

        return result;
    }

    public static void fuzzerTestOneInput(FuzzedDataProvider data) {
        decideWithFuzzedSubscriptionDenyOverrides(data);
        decideWithFuzzedSubscriptionOnlyOneApplicable(data);
        decideWithFuzzedSubscriptionDenyUnlessPermit(data);
        decideWithFuzzedSubscriptionPermitUnlessDeny(data);
        decideWithFuzzedSubscriptionPermitOverrides(data);
    }

    public static void decideWithFuzzedSubscriptionDenyOverrides(FuzzedDataProvider data) {
        assertFuzzedSubscriptionReturns(buildPDPWithConfiguration("/pdp-configurations/deny-overrides-configuration"),
                data, AuthorizationDecision.NOT_APPLICABLE);
    }

    public static void decideWithFuzzedSubscriptionOnlyOneApplicable(FuzzedDataProvider data) {
        assertFuzzedSubscriptionReturns(
                buildPDPWithConfiguration("/pdp-configurations/only-one-applicable-configuration"), data,
                AuthorizationDecision.NOT_APPLICABLE);
    }

    public static void decideWithFuzzedSubscriptionDenyUnlessPermit(FuzzedDataProvider data) {
        assertFuzzedSubscriptionReturns(
                buildPDPWithConfiguration("/pdp-configurations/deny-unless-permit-configuration"), data,
                AuthorizationDecision.DENY);
    }

    public static void decideWithFuzzedSubscriptionPermitUnlessDeny(FuzzedDataProvider data) {
        assertFuzzedSubscriptionReturns(
                buildPDPWithConfiguration("/pdp-configurations/permit-unless-deny-configuration"), data,
                AuthorizationDecision.PERMIT);
    }

    public static void decideWithFuzzedSubscriptionPermitOverrides(FuzzedDataProvider data) {
        assertFuzzedSubscriptionReturns(buildPDPWithConfiguration("/pdp-configurations/permit-overrides-configuration"),
                data, AuthorizationDecision.NOT_APPLICABLE);
    }

    public static void assertFuzzedSubscriptionReturns(PolicyDecisionPoint pdp, FuzzedDataProvider data,
            AuthorizationDecision expectedAuthorizationDecision) {
        var asciiString  = data.consumeAsciiString(100);
        var result       = splitStringIntoThreeParts(asciiString);
        var subscription = AuthorizationSubscription.of(result[0], result[1], result[2]);

        StepVerifier.create(pdp.decide(subscription)).expectNext(expectedAuthorizationDecision).verifyComplete();
    }

    public static PolicyDecisionPoint buildPDPWithConfiguration(String configuration) {
        try {
            return PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(configuration);
        } catch (InitializationException e) {
            throw new RuntimeException(e);
        }
    }
}
