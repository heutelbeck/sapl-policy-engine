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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

import reactor.core.publisher.FluxSink;


@SuppressWarnings("deprecation")
public class FluxSinkWrapperTests {
	

	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	<T> void when_FluxSinkWrapperCreated_then_Initialized() {
	    final FluxSink<T> fluxSink = null;
		FluxSinkWrapper result = new FluxSinkWrapper(fluxSink);
		assertEquals(result.getClass(), FluxSinkWrapper.class);
		
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	<T> void when_FluxSinkWrapperCompleted_then_FluxSinkCompleted() {
	    final FluxSink<T> fluxSink = mock(FluxSink.class);
		FluxSinkWrapper result = new FluxSinkWrapper(fluxSink);
		result.complete();
		verify(fluxSink).complete();
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	<T> void when_FluxSinkWrapperNext_then_FluxSinkNext() {
	    final FluxSink<T> fluxSink = mock(FluxSink.class);
		FluxSinkWrapper result = new FluxSinkWrapper(fluxSink);
		result.next(null);
		verify(fluxSink).next(null);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	<T> void when_FluxSinkWrapperError_then_FluxSinkError() {
	    final FluxSink<T> fluxSink = mock(FluxSink.class);
		FluxSinkWrapper result = new FluxSinkWrapper(fluxSink);
		result.error(null);
		verify(fluxSink).error(null);
	}
	
	@SuppressWarnings({ "unchecked", "rawtypes" })
	@Test
	<T> void when_FluxSinkWrapperOnDispose_then_FluxSinkonDispose() {
	    final FluxSink<T> fluxSink = mock(FluxSink.class);
		FluxSinkWrapper result = new FluxSinkWrapper(fluxSink);
		result.onDispose(null);
		verify(fluxSink).onDispose(null);
	}

}
