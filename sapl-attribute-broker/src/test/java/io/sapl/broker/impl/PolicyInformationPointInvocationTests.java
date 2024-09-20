package io.sapl.broker.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

class PolicyInformationPointInvocationTests {

    @Test
    void whenConstructionOfPolicyInformationPointInvocationHasBadParametersThenThrowElseDoNotThrow() {
        final List<Val>        emptyList = List.of();
        final Map<String, Val> emptyMap  = Map.of();
        assertThatThrownBy(() -> new PolicyInformationPointInvocation(null, Val.TRUE, emptyList, emptyMap))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("abc.def", Val.TRUE, null, emptyMap))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("abc.def", Val.TRUE, emptyList, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PolicyInformationPointInvocation("123 ", Val.TRUE, emptyList, emptyMap))
                .isInstanceOf(IllegalArgumentException.class);
        assertDoesNotThrow(() -> new PolicyInformationPointInvocation("abc.def", null, emptyList, emptyMap));
    }

}
