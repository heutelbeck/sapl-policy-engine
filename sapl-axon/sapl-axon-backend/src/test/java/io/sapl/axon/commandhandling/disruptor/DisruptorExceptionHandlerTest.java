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
package io.sapl.axon.commandhandling.disruptor;

import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.axonframework.disruptor.commandhandling.CommandHandlingEntry;
import org.junit.jupiter.api.Test;

import com.lmax.disruptor.dsl.Disruptor;

public class DisruptorExceptionHandlerTest {

	@Test
	void when_handleOnStartException_then_CallDisruptorShutdownSuccessfully() {
		var disruptorMock = mock(Disruptor.class);
				
		DisruptorExceptionHandler exceptionHandler = new DisruptorExceptionHandler(disruptorMock);
		
		exceptionHandler.handleOnStartException(new Exception("Test text."));
		
		verify(disruptorMock, times(1)).shutdown();
	}
	
	@Test
	void when_handleEventException_then_CallMessageSuccessfully() {
		var disruptorMock = mock(Disruptor.class);
		var entryMock = mock(CommandHandlingEntry.class);
		
		when(entryMock.getMessage()).thenReturn(asCommandMessage("Test"));
				
		DisruptorExceptionHandler exceptionHandler = new DisruptorExceptionHandler(disruptorMock);
				
		exceptionHandler.handleEventException(new Exception(), 1, entryMock);
		
		verify(entryMock, times(1)).getMessage();
	}
	
	@Test
	void when_handleOnShutdownException_then_Successfully() {
		var disruptorMock = mock(Disruptor.class);		
		DisruptorExceptionHandler exceptionHandler = new DisruptorExceptionHandler(disruptorMock);	
		exceptionHandler.handleOnShutdownException(new Exception());
	}
}
