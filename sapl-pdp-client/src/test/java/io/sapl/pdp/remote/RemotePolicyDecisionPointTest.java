package io.sapl.pdp.remote;

import io.sapl.api.pdp.Response;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

public class RemotePolicyDecisionPointTest {

    // To run this test, make sure the PDPServerApplication has been started
    public static void main(String[] args) {
        final RemotePolicyDecisionPoint pdp = new RemotePolicyDecisionPoint("localhost", 8443);
        final Flux<Response> decideFlux = pdp.decide("willi", "read", "something");
        StepVerifier.create(decideFlux)
                .expectNext(Response.permit())
                .thenCancel()
                .verify();
    }
}
