package io.sapl.broker.impl.old;

import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import io.sapl.api.interpreter.Val;
import reactor.test.StepVerifier;

@Disabled
class EmbeddedAttributeBrokerTests {

    @Test
    void whenCreatedThenNoException() {
        assertThatCode(() -> new EmbeddedAttributeBroker()).doesNotThrowAnyException();
    }

    @Test
    void whenUnknownAttributeThenError() {
        var sut                  = new EmbeddedAttributeBroker();
        var unknownAttributeName = "something.unknown";
        var expectedInitialValue = Val.UNDEFINED;
        // Val.error(String.format(EmbeddedAttributeBroker.UNKNOWN_ATTRIBUTE_ERROR,
        // unknownAttributeName));

        StepVerifier.create(sut.evaluateEntityAttribute(unknownAttributeName, Val.NULL, List.of(), false, Map.of()))
                .expectNext(expectedInitialValue).verifyComplete();
        StepVerifier.create(sut.evaluateEntityAttribute(unknownAttributeName, Val.NULL, List.of(), false,
                Duration.ofMillis(50), Map.of())).expectNext(expectedInitialValue).verifyComplete();
        StepVerifier.create(sut.evaluateEnvironmentAttribute(unknownAttributeName, List.of(), false, Map.of()))
                .expectNext(expectedInitialValue).verifyComplete();
        StepVerifier.create(sut.evaluateEnvironmentAttribute(unknownAttributeName, List.of(), false,
                Duration.ofMillis(50), Map.of())).expectNext(expectedInitialValue).verifyComplete();
    }

}
