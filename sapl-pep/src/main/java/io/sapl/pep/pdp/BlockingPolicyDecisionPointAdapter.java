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

    private final PolicyDecisionPoint delegate;

    public BlockingPolicyDecisionPointAdapter(PolicyDecisionPoint reactivePdp) {
            delegate = Objects.requireNonNull(reactivePdp, "reactivePdp must not be null");
    }

    @Override
    public Response decide(Object subject, Object action, Object resource) {
        return delegate.decide(subject, action, resource).blockFirst();
    }

    @Override
    public Response decide(Object subject, Object action, Object resource, Object environment) {
        return delegate.decide(subject, action, resource, environment).blockFirst();
    }

    @Override
    public Response decide(Request request) {
        return delegate.decide(request).blockFirst();
    }

    @Override
    public MultiResponse decide(MultiRequest multiRequest) {
        return BlockingMultiRequestSupport.collectResponses(multiRequest, delegate.decide(multiRequest));
    }

    @Override
    public void dispose() {
        delegate.dispose();
    }
}
