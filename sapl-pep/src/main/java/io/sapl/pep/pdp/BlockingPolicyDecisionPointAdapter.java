package io.sapl.pep.pdp;

import java.util.Objects;

import io.sapl.api.pdp.BlockingPolicyDecisionPoint;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import io.sapl.pep.BlockingMultiRequestSupport;

/**
 * Adapts a reactive policy decision point to the blocking interface.
 */
public class BlockingPolicyDecisionPointAdapter implements BlockingPolicyDecisionPoint {

    private final PolicyDecisionPoint reactivePdp;

    public BlockingPolicyDecisionPointAdapter(PolicyDecisionPoint reactivePdp) {
        this.reactivePdp = Objects.requireNonNull(reactivePdp, "reactivePdp must not be null");
    }

    @Override
    public Response decide(Object subject, Object action, Object resource) {
        return reactivePdp.decide(subject, action, resource).blockFirst();
    }

    @Override
    public Response decide(Object subject, Object action, Object resource, Object environment) {
        return reactivePdp.decide(subject, action, resource, environment).blockFirst();
    }

    @Override
    public Response decide(Request request) {
        return reactivePdp.decide(request).blockFirst();
    }

    @Override
    public MultiResponse decide(MultiRequest multiRequest) {
        return BlockingMultiRequestSupport.collectResponses(multiRequest, reactivePdp.decide(multiRequest));
    }
}
