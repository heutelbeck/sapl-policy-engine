package io.sapl.api.pdp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class TracedDecisionInterceptorTests {

    @Test
    void defaultPriorityIsZero() {
        TracedDecisionInterceptor interceptor = t -> t;
        assertThat(interceptor.getPriority()).isEqualTo(0);
    }

    @Test
    void compareTo() {
        TracedDecisionInterceptor interceptor1 = t -> t;
        TracedDecisionInterceptor interceptor2 = t -> t;
        assertThat(interceptor1.compareTo(interceptor2)).isEqualTo(0);
    }
}
