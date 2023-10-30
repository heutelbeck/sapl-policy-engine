package io.sapl.prp;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Flux;

class PolicyRetrievalPointTests {

    static class TestPRP implements PolicyRetrievalPoint {

        @Override
        public Flux<PolicyRetrievalResult> retrievePolicies() {
            return Flux.empty();
        }

    }

    @Test
    void when_destroy_then_nothingThrown() {
        var sut = new TestPRP();
        assertDoesNotThrow(() -> sut.destroy());
    }
}
