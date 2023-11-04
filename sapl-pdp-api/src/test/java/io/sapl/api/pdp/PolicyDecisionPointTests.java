package io.sapl.api.pdp;

import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class PolicyDecisionPointTests {

    @Test
    void decideOnce() {
        class SomePDP implements PolicyDecisionPoint {

            @Override
            public Flux<AuthorizationDecision> decide(AuthorizationSubscription authzSubscription) {
                return Flux.just(AuthorizationDecision.DENY, AuthorizationDecision.PERMIT);
            }

            @Override
            public Flux<IdentifiableAuthorizationDecision> decide(
                    MultiAuthorizationSubscription multiAuthzSubscription) {
                return Flux.empty();
            }

            @Override
            public Flux<MultiAuthorizationDecision> decideAll(MultiAuthorizationSubscription multiAuthzSubscription) {
                return Flux.empty();
            }

        }
        
        var pdp = new SomePDP();
        StepVerifier.create(pdp.decideOnce(mock(AuthorizationSubscription.class))).expectNext(AuthorizationDecision.DENY).verifyComplete();
    }
}
