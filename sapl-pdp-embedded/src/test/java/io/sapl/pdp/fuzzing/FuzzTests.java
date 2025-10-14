/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.BeforeEach;
import reactor.test.StepVerifier;

public class FuzzTests {
    private PolicyDecisionPoint pdpDenyOverrides;

    private PolicyDecisionPoint pdpDenyUnlessPermit;

    private PolicyDecisionPoint pdpPermitUnlessDeny;

    private PolicyDecisionPoint pdpPermitOverrides;

    private PolicyDecisionPoint pdpOnlyOneApplicable;

    @BeforeEach
    void setup() throws InitializationException {
        pdpDenyOverrides     = buildPDPWithConfiguration("/pdp-configurations/deny-overrides-configuration");
        pdpDenyUnlessPermit  = buildPDPWithConfiguration("/pdp-configurations/deny-unless-permit-configuration");
        pdpPermitUnlessDeny  = buildPDPWithConfiguration("/pdp-configurations/permit-unless-deny-configuration");
        pdpPermitOverrides   = buildPDPWithConfiguration("/pdp-configurations/permit-overrides-configuration");
        pdpOnlyOneApplicable = buildPDPWithConfiguration("/pdp-configurations/only-one-applicable-configuration");
    }

    @FuzzTest(maxExecutions = 10000L)
    public void decideWithFuzzedSubscriptionTests(FuzzedDataProvider data) {
        final var asciiString        = data.consumeAsciiString(100);
        final var fuzzedSubscription = generateFuzzedSubscriptionFor(asciiString);
        decideWithFuzzedSubscriptionDenyOverrides(fuzzedSubscription);
        decideWithFuzzedSubscriptionDenyUnlessPermit(fuzzedSubscription);
        decideWithFuzzedSubscriptionPermitUnlessDeny(fuzzedSubscription);
        decideWithFuzzedSubscriptionPermitOverrides(fuzzedSubscription);
        decideWithFuzzedSubscriptionOnlyOneApplicable(fuzzedSubscription);
    }

    public void decideWithFuzzedSubscriptionDenyOverrides(AuthorizationSubscription fuzzedSubscription) {
        assertFuzzedSubscriptionReturns(pdpDenyOverrides, fuzzedSubscription, AuthorizationDecision.NOT_APPLICABLE);
    }

    public void decideWithFuzzedSubscriptionDenyUnlessPermit(AuthorizationSubscription fuzzedSubscription) {
        assertFuzzedSubscriptionReturns(pdpDenyUnlessPermit, fuzzedSubscription, AuthorizationDecision.DENY);
    }

    public void decideWithFuzzedSubscriptionPermitUnlessDeny(AuthorizationSubscription fuzzedSubscription) {
        assertFuzzedSubscriptionReturns(pdpPermitUnlessDeny, fuzzedSubscription, AuthorizationDecision.PERMIT);
    }

    public void decideWithFuzzedSubscriptionPermitOverrides(AuthorizationSubscription fuzzedSubscription) {
        assertFuzzedSubscriptionReturns(pdpPermitOverrides, fuzzedSubscription, AuthorizationDecision.NOT_APPLICABLE);
    }

    public void decideWithFuzzedSubscriptionOnlyOneApplicable(AuthorizationSubscription fuzzedSubscription) {
        assertFuzzedSubscriptionReturns(pdpOnlyOneApplicable, fuzzedSubscription, AuthorizationDecision.NOT_APPLICABLE);
    }

    public AuthorizationSubscription generateFuzzedSubscriptionFor(String input) {
        if (StringUtils.isEmpty(input)) {
            return emptyAuthorizationSubscription();
        }

        int length = input.length();

        if (length < 3) {
            return subjectOnlyAuthorizationSubscription(input);
        }

        int partLength = length / 3;

        String subject  = input.substring(0, partLength);
        String action   = input.substring(partLength, 2 * partLength);
        String resource = input.substring(2 * partLength);

        return AuthorizationSubscription.of(subject, action, resource);
    }

    private AuthorizationSubscription subjectOnlyAuthorizationSubscription(String input) {
        return AuthorizationSubscription.of(input, "", "");
    }

    private AuthorizationSubscription emptyAuthorizationSubscription() {
        return AuthorizationSubscription.of("", "", "");
    }

    public void assertFuzzedSubscriptionReturns(PolicyDecisionPoint pdp, AuthorizationSubscription fuzzedSubscription,
            AuthorizationDecision expectedAuthorizationDecision) {
        StepVerifier.create(pdp.decide(fuzzedSubscription)).expectNext(expectedAuthorizationDecision).verifyComplete();
    }

    public PolicyDecisionPoint buildPDPWithConfiguration(String configuration) throws InitializationException {
        return PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(configuration);
    }
}
