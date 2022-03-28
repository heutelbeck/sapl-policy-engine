/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.axon.queryhandling;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

public class SinksManyWrapperTests {


	@Test
	<T> void when_SinksManyWrapperCreated_then_Initialized() {
		final Sinks.Many<T> sink = null;
		SinksManyWrapper<T> result = new SinksManyWrapper<>(sink);
		assertEquals(result.getClass(), SinksManyWrapper.class);
	}

	@Test
	<T> void when_SinksManyWrapperCompleted_then_SinksManyCompleted() {
		
		final Sinks.Many<T> sink = Sinks.many().unicast().onBackpressureBuffer();
		SinksManyWrapper<T> result = new SinksManyWrapper<>(sink);



		result.complete();
		assertEquals(result.getClass(), SinksManyWrapper.class);
		StepVerifier.create(sink.asFlux()).expectSubscription()
				.expectComplete().verify();

	}

	@Test
	void when_SinksManyWrapperReceivesConcurrentNext_Then_NoUpdatesAreLost()  {
		final Sinks.Many<Integer> sink = Sinks.many().unicast().onBackpressureBuffer();
		SinksManyWrapper<Integer> result = new SinksManyWrapper<>(sink);



		for (int j = 1; j<1000;j++)result.next(j);
		result.complete();

	}

	@Test
	<T> void when_SinksManyWrapperError_then_SinksManyError() {
		
		final Sinks.Many<T> sink = Sinks.many().unicast().onBackpressureBuffer();

		SinksManyWrapper<T> result = new SinksManyWrapper<>(sink);
		Throwable t = new RuntimeException("exception");

		result.error(t);

		assertEquals(result.getClass(), SinksManyWrapper.class);
		StepVerifier.create(sink.asFlux()).expectSubscription().expectError().verify();
	}

}
