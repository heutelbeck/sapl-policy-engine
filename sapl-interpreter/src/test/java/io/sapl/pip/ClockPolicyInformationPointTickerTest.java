/**
 * Copyright © 2020 Dominic Heutelbeck (dominic.heutelbeck@gmail.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.pip;

import static org.junit.Assert.assertEquals;
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
			}
			catch (AttributeException e) {
				fail(e.getMessage());
				return null;
			}
		}).expectSubscription().expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
			/* the first node is provided some nano seconds after its creation */ })
				.expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					try {
						final LocalDateTime localDateTime = LocalDateTime.now();
						final String actual = TemporalFunctionLibrary.localDateTime(node).textValue();
						final String expected = localDateTime.truncatedTo(ChronoUnit.SECONDS).toString();
						assertEquals("<clock.ticker> or time.localDateTime() do not work as expected", expected,
								actual);
					}
					catch (FunctionException e) {
						fail(e.getMessage());
					}
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					try {
						final LocalTime localTime = LocalTime.now();
						final String actual = TemporalFunctionLibrary.localTime(node).textValue();
						final String expected = localTime.truncatedTo(ChronoUnit.SECONDS).toString();
						assertEquals("<clock.ticker> or time.localTime() do not work as expected", expected, actual);
					}
					catch (FunctionException e) {
						fail(e.getMessage());
					}
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					try {
						final LocalTime localTime = LocalTime.now();
						final Number actual = TemporalFunctionLibrary.localHour(node).numberValue();
						final Number expected = BigDecimal.valueOf(localTime.getHour());
						assertEquals("<clock.ticker> or time.localHour() do not work as expected", expected.longValue(),
								actual.longValue());
					}
					catch (FunctionException e) {
						fail(e.getMessage());
					}
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					try {
						final LocalTime localTime = LocalTime.now();
						final Number actual = TemporalFunctionLibrary.localMinute(node).numberValue();
						final Number expected = BigDecimal.valueOf(localTime.getMinute());
						assertEquals("<clock.ticker> or time.localMinute() do not work as expected",
								expected.longValue(), actual.longValue());
					}
					catch (FunctionException e) {
						fail(e.getMessage());
					}
				}).expectNoEvent(Duration.ofSeconds(30)).consumeNextWith(node -> {
					try {
						final LocalTime localTime = LocalTime.now();
						final Number actual = TemporalFunctionLibrary.localSecond(node).numberValue();
						final Number expected = BigDecimal.valueOf(localTime.getSecond());
						assertEquals("<clock.ticker> or time.localSecond() do not work as expected",
								expected.longValue(), actual.longValue());
					}
					catch (FunctionException e) {
						fail(e.getMessage());
					}
				}).thenCancel().verify();
	}

}
