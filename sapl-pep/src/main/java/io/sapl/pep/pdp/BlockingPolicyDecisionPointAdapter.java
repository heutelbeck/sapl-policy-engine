package io.sapl.pep.pdp;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import io.sapl.api.pdp.BlockingPolicyDecisionPoint;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import reactor.core.Disposable;
import reactor.core.Exceptions;

/**
 * Adapts a reactive policy decision point to the blocking interface.
 */
public class BlockingPolicyDecisionPointAdapter implements BlockingPolicyDecisionPoint {

    private final PolicyDecisionPoint reactivePdp;

    public BlockingPolicyDecisionPointAdapter(PolicyDecisionPoint reactivePdp) {
        this.reactivePdp = Objects.requireNonNull(reactivePdp, "reactivePdp must not be null");
    }

    @Override
    public Response decide(Request request) {
        return reactivePdp.reactiveDecide(request).blockFirst();
    }

    @Override
    public Response decide(Object subject, Object action, Object resource, Object environment) {
        return reactivePdp.reactiveDecide(subject, action, resource, environment).blockFirst();
    }

    @Override
    public Response decide(Object subject, Object action, Object resource) {
        return reactivePdp.reactiveDecide(subject, action, resource).blockFirst();
    }

    @Override
    public MultiResponse decide(MultiRequest multiRequest) {
        final MultiResponse multiResponse = new MultiResponse();

        final Set<String> keys = multiRequest.getRequests().keySet();
        final CountDownLatch cdl = new CountDownLatch(keys.size());

        final Disposable subscription = reactivePdp.reactiveMultiDecide(multiRequest)
                .subscribe(
                        identifiableResponse -> {
                            // collect the responses and wait until at least one response has arrived for each request
                            final String requestId = identifiableResponse.getRequestId();
                            final Response response = identifiableResponse.getResponse();
                            multiResponse.setResponseForRequestWithId(requestId, response);
                            if (keys.contains(requestId)) {
                                keys.remove(requestId);
                                cdl.countDown();
                            }
                        },
                        error -> {
                            throw Exceptions.propagate(error);
                        },
                        () -> {
                            long cdlCount = cdl.getCount();
                            while (cdlCount > 0) {
                                cdlCount--;
                                cdl.countDown();
                            }
                        }
                );

        try {
            cdl.await();
        }
        catch (InterruptedException e) {
            subscription.dispose();
            throw new RuntimeException(e);
        }

        if (! subscription.isDisposed()) {
            subscription.dispose();;
        }
        return multiResponse;
    }
}
