package io.sapl.api.pdp;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

public class AuthorizationSubscriptionInterceptorTests {

    @Test
    void defaultPriorityIsZero() {
        AuthorizationSubscriptionInterceptor interceptor = t -> t;
        assertThat(interceptor.getPriority()).isEqualTo(0);
    }

    @Test
    void compareTo() {
        AuthorizationSubscriptionInterceptor interceptor1 = t -> t;
        AuthorizationSubscriptionInterceptor interceptor2 = t -> t;
        assertThat(interceptor1.compareTo(interceptor2)).isEqualTo(0);
    }
}
