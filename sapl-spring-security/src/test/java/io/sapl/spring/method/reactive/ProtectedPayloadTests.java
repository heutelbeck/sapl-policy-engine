package io.sapl.spring.method.reactive;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;

import org.junit.jupiter.api.Test;

import reactor.test.StepVerifier;

class ProtectedPayloadTests {

    @Test
    void withPayload() {
        assertThatThrownBy(() -> ProtectedPayload.withPayload(null)).isInstanceOf(NullPointerException.class);
        var pp = ProtectedPayload.withPayload("Payload");
        assertThat(pp.isError()).isFalse();
        assertThat(pp.hasPayload()).isTrue();
        StepVerifier.create(pp.getPayload()).expectNext("Payload").verifyComplete();
        assertThatThrownBy(() -> pp.getError()).isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void withException() {
        assertThatThrownBy(() -> ProtectedPayload.withError(null)).isInstanceOf(NullPointerException.class);
        var pp = ProtectedPayload.withError(new RuntimeException("ERROR"));
        assertThat(pp.isError()).isTrue();
        assertThat(pp.hasPayload()).isFalse();
        assertThatThrownBy(() -> pp.getPayload()).isInstanceOf(RuntimeException.class);
        assertThat(pp.getError()).hasMessage("ERROR");
    }
}
