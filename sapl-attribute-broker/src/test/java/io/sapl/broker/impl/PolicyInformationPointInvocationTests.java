package io.sapl.broker.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class PolicyInformationPointInvocationTests {

    private static final Duration BACKOFF    = Duration.ofMillis(50L);
    private static final Duration ONE_SECOND = Duration.ofSeconds(1L);

    @Test
    void whenConstructionOfPolicyInformationPointInvocationHasBadParametersThenThrowElseDoNotThrow() {
        final List<Val>        emptyList = List.of();
        final Map<String, Val> emptyMap  = Map.of();
        assertThatThrownBy(() -> new PolicyInformationPointInvocation(null, Val.TRUE, emptyList, emptyMap, ONE_SECOND,
                ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("abc.def", Val.TRUE, null, emptyMap, ONE_SECOND,
                ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("abc.def", Val.TRUE, emptyList, null, ONE_SECOND,
                ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("abc.def", Val.TRUE, emptyList, emptyMap, null,
                ONE_SECOND, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("abc.def", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, null, BACKOFF, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("abc.def", Val.TRUE, emptyList, emptyMap,
                ONE_SECOND, ONE_SECOND, null, 20L)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("123 ", Val.TRUE, emptyList, emptyMap, ONE_SECOND,
                ONE_SECOND, BACKOFF, 20L)).isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new PolicyInformationPointInvocation("abc.def", null, emptyList, emptyMap, ONE_SECOND,
                ONE_SECOND, BACKOFF, 20L));
    }

}
