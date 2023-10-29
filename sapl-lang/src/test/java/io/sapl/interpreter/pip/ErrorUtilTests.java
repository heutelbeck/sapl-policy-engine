package io.sapl.interpreter.pip;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;

public class ErrorUtilTests {

    @Test
    void withCauseReturnsCauseMessage() {
        var t = new RuntimeException("A", new RuntimeException("B"));
        assertThat(ErrorUtil.causeOrMessage(t)).isEqualTo(Val.error("B"));
    }

    @Test
    void withoutCauseReturnsMessage() {
        var t = new RuntimeException("A");
        assertThat(ErrorUtil.causeOrMessage(t)).isEqualTo(Val.error("A"));
    }
}
