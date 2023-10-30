package io.sapl.api.pdp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TracedDecisionInterceptorTests {

    @Test
    void defaultPriorityIsZero() {
        TracedDecisionInterceptor interceptor = t -> t;
        assertThat(interceptor.getPriority()).isZero();
    }

    @Test
    void compareTo() {
        TracedDecisionInterceptor interceptor1 = t -> t;
        TracedDecisionInterceptor interceptor2 = t -> t;
        assertThat(interceptor1).isEqualByComparingTo(interceptor2);
    }
}
