package io.sapl.pdp.fuzzing;

import com.code_intelligence.jazzer.api.FuzzedDataProvider;
import com.code_intelligence.jazzer.junit.FuzzTest;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.interpreter.InitializationException;
import io.sapl.pdp.PolicyDecisionPointFactory;
import org.junit.jupiter.api.BeforeEach;
import reactor.test.StepVerifier;

public class FuzzTests {
    private PolicyDecisionPoint pdpDenyOverrides;

    private PolicyDecisionPoint pdpDenyUnlessPermit;

    private PolicyDecisionPoint pdpPermitUnlessDeny;

    private PolicyDecisionPoint pdpPermitOverrides;

    private PolicyDecisionPoint pdpOnlyOneApplicable;

    @BeforeEach
    void setup() {
        pdpDenyOverrides     = buildPDPWithConfiguration("/pdp-configurations/deny-overrides-configuration");
        pdpDenyUnlessPermit  = buildPDPWithConfiguration("/pdp-configurations/deny-unless-permit-configuration");
        pdpPermitUnlessDeny  = buildPDPWithConfiguration("/pdp-configurations/permit-unless-deny-configuration");
        pdpPermitOverrides   = buildPDPWithConfiguration("/pdp-configurations/permit-overrides-configuration");
        pdpOnlyOneApplicable = buildPDPWithConfiguration("/pdp-configurations/only-one-applicable-configuration");
    }

    @FuzzTest(maxExecutions = 1000000L)
    public void decideWithFuzzedSubscriptionTests(FuzzedDataProvider data) {
        var asciiString      = data.consumeAsciiString(100);
        var asciiStringParts = splitStringIntoThreeParts(asciiString);

        decideWithFuzzedSubscriptionDenyOverrides(asciiStringParts);
        decideWithFuzzedSubscriptionDenyUnlessPermit(asciiStringParts);
        decideWithFuzzedSubscriptionPermitUnlessDeny(asciiStringParts);
        decideWithFuzzedSubscriptionPermitOverrides(asciiStringParts);
        decideWithFuzzedSubscriptionOnlyOneApplicable(asciiStringParts);
    }

    public void decideWithFuzzedSubscriptionDenyOverrides(String... asciiStringParts) {
        assertFuzzedSubscriptionReturns(pdpDenyOverrides, asciiStringParts, AuthorizationDecision.NOT_APPLICABLE);
    }

    public void decideWithFuzzedSubscriptionDenyUnlessPermit(String... asciiStringParts) {
        assertFuzzedSubscriptionReturns(pdpDenyUnlessPermit, asciiStringParts, AuthorizationDecision.DENY);
    }

    public void decideWithFuzzedSubscriptionPermitUnlessDeny(String... asciiStringParts) {
        assertFuzzedSubscriptionReturns(pdpPermitUnlessDeny, asciiStringParts, AuthorizationDecision.PERMIT);
    }

    public void decideWithFuzzedSubscriptionPermitOverrides(String... asciiStringParts) {
        assertFuzzedSubscriptionReturns(pdpPermitOverrides, asciiStringParts, AuthorizationDecision.NOT_APPLICABLE);
    }

    public void decideWithFuzzedSubscriptionOnlyOneApplicable(String... asciiStringParts) {
        assertFuzzedSubscriptionReturns(pdpOnlyOneApplicable, asciiStringParts, AuthorizationDecision.NOT_APPLICABLE);
    }

    public String[] splitStringIntoThreeParts(String input) {
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

    public void assertFuzzedSubscriptionReturns(PolicyDecisionPoint pdp, String[] asciiStringParts,
            AuthorizationDecision expectedAuthorizationDecision) {
        var subscription = AuthorizationSubscription.of(asciiStringParts[0], asciiStringParts[1], asciiStringParts[2]);
        StepVerifier.create(pdp.decide(subscription)).expectNext(expectedAuthorizationDecision).verifyComplete();
    }

    public PolicyDecisionPoint buildPDPWithConfiguration(String configuration) {
        try {
            return PolicyDecisionPointFactory.resourcesPolicyDecisionPoint(configuration);
        } catch (InitializationException e) {
            throw new RuntimeException(e);
        }
    }
}
