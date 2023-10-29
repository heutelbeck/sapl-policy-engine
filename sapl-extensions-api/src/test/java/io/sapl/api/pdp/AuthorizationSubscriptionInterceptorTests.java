package io.sapl.api.pdp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class AuthorizationSubscriptionInterceptorTests {

    @Test
    void defaultPriorityIsZero() {
        AuthorizationSubscriptionInterceptor interceptor = t -> t;
        assertThat(interceptor.getPriority()).isZero();
    }

    @Test
    void compareTo() {
        AuthorizationSubscriptionInterceptor interceptor1 = t -> t;
        AuthorizationSubscriptionInterceptor interceptor2 = t -> t;
        assertThat(interceptor1).isEqualByComparingTo(interceptor2);
    }
}
