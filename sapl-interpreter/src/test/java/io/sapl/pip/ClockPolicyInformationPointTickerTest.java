package io.sapl.pip;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.fail;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Collections;

import org.junit.Test;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import io.sapl.api.functions.FunctionException;
import io.sapl.api.pip.AttributeException;
import io.sapl.functions.TemporalFunctionLibrary;
import reactor.test.StepVerifier;

public class ClockPolicyInformationPointTickerTest {

    @Test
    public void ticker() {
        final ClockPolicyInformationPoint clockPip = new ClockPolicyInformationPoint();
        final JsonNodeFactory json = JsonNodeFactory.instance;
        StepVerifier.withVirtualTime(() -> {
                    try {
                        return clockPip.ticker(json.numberNode(BigDecimal.valueOf(30)), Collections.emptyMap());
                    } catch (AttributeException e) {
                        fail(e.getMessage());
                        return null;
                    }
                })
                .expectSubscription()
                .expectNoEvent(Duration.ofSeconds(30))
                .consumeNextWith(node -> { /* the first node is provided some nano seconds after its creation */ })
                .expectNoEvent(Duration.ofSeconds(30))
                .consumeNextWith(node -> {
                    try {
                        final LocalDateTime localDateTime = LocalDateTime.now();
                        final String actual = TemporalFunctionLibrary.localDateTime(node).textValue();
                        final String expected = localDateTime.truncatedTo(ChronoUnit.SECONDS).toString();
                        assertThat("<clock.ticker> or time.localDateTime() do not work as expected", actual, is(expected));
                    } catch (FunctionException e) {
                        fail(e.getMessage());
                    }
                })
                .expectNoEvent(Duration.ofSeconds(30))
                .consumeNextWith(node -> {
                    try {
                        final LocalTime localTime = LocalTime.now();
                        final String actual = TemporalFunctionLibrary.localTime(node).textValue();
                        final String expected = localTime.truncatedTo(ChronoUnit.SECONDS).toString();
                        assertThat("<clock.ticker> or time.localTime() do not work as expected", actual, is(expected));
                    } catch (FunctionException e) {
                        fail(e.getMessage());
                    }
                })
                .expectNoEvent(Duration.ofSeconds(30))
                .consumeNextWith(node -> {
                    try {
                        final LocalTime localTime = LocalTime.now();
                        final Number actual = TemporalFunctionLibrary.localHour(node).numberValue();
                        final Number expected = BigDecimal.valueOf(localTime.getHour());
                        assertThat("<clock.ticker> or time.localHour() do not work as expected", actual, is(expected));
                    } catch (FunctionException e) {
                        fail(e.getMessage());
                    }
                })
                .expectNoEvent(Duration.ofSeconds(30))
                .consumeNextWith(node -> {
                    try {
                        final LocalTime localTime = LocalTime.now();
                        final Number actual = TemporalFunctionLibrary.localMinute(node).numberValue();
                        final Number expected = BigDecimal.valueOf(localTime.getMinute());
                        assertThat("<clock.ticker> or time.localMinute() do not work as expected", actual, is(expected));
                    } catch (FunctionException e) {
                        fail(e.getMessage());
                    }
                })
                .expectNoEvent(Duration.ofSeconds(30))
                .consumeNextWith(node -> {
                    try {
                        final LocalTime localTime = LocalTime.now();
                        final Number actual = TemporalFunctionLibrary.localSecond(node).numberValue();
                        final Number expected = BigDecimal.valueOf(localTime.getSecond());
                        assertThat("<clock.ticker> or time.localSecond() do not work as expected", actual, is(expected));
                    } catch (FunctionException e) {
                        fail(e.getMessage());
                    }
                })
                .thenCancel()
                .verify();
    }
}
