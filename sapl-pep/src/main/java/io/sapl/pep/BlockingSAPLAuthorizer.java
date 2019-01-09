package io.sapl.pep;

import java.util.Objects;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.MultiDecision;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;

/**
 * Wraps a {@link SAPLAuthorizer} instance and provides blocking variants of its methods.
 */
public class BlockingSAPLAuthorizer {

    private final SAPLAuthorizer delegate;

    public BlockingSAPLAuthorizer(SAPLAuthorizer delegate) {
        this.delegate = Objects.requireNonNull(delegate);
    }

    /**
     * See {@link SAPLAuthorizer#wouldAuthorize(Object, Object, Object)}.
     */
    public boolean wouldAuthorize(Object subject, Object action, Object resource) {
        final Decision decision = delegate.wouldAuthorize(subject, action, resource).blockFirst();
        return decision == Decision.PERMIT;
    }

    /**
     * See {@link SAPLAuthorizer#wouldAuthorize(Object, Object, Object, Object)}.
     */
    public boolean wouldAuthorize(Object subject, Object action, Object resource, Object environment) {
        final Decision decision = delegate.wouldAuthorize(subject, action, resource, environment).blockFirst();
        return decision == Decision.PERMIT;
    }

    /**
     * See {@link SAPLAuthorizer#wouldAuthorize(MultiRequest)}.
     */
    public MultiDecision wouldAuthorize(MultiRequest multiRequest) {
        return BlockingMultiRequestSupport.collectDecisions(multiRequest, delegate.wouldAuthorize(multiRequest));
    }

    /**
     * See {@link SAPLAuthorizer#authorize(Object, Object, Object)}.
     */
    public boolean authorize(Object subject, Object action, Object resource) {
        final Decision decision = delegate.authorize(subject, action, resource).blockFirst();
        return decision == Decision.PERMIT;
    }

    /**
     * See {@link SAPLAuthorizer#authorize(Object, Object, Object, Object)}.
     */
    public boolean authorize(Object subject, Object action, Object resource, Object environment) {
        final Decision decision = delegate.authorize(subject, action, resource, environment).blockFirst();
        return decision == Decision.PERMIT;
    }

    /**
     * See {@link SAPLAuthorizer#authorize(MultiRequest)}.
     */
    public MultiDecision authorize(MultiRequest multiRequest) {
        return BlockingMultiRequestSupport.collectDecisions(multiRequest, delegate.authorize(multiRequest));
    }

    /**
     * See {@link SAPLAuthorizer#getResponse(Object, Object, Object)}.
     */
    public Response getResponse(Object subject, Object action, Object resource) {
        return delegate.getResponse(subject, action, resource).blockFirst();
    }

    /**
     * See {@link SAPLAuthorizer#getResponse(Object, Object, Object, Object)}.
     */
    public Response getResponse(Object subject, Object action, Object resource, Object environment) {
        return delegate.getResponse(subject, action, resource, environment).blockFirst();
    }

    /**
     * See {@link SAPLAuthorizer#getResponses(MultiRequest)}.
     */
    public MultiResponse getResponses(MultiRequest multiRequest) {
        return BlockingMultiRequestSupport.collectResponses(multiRequest, delegate.getResponses(multiRequest));
    }

}
