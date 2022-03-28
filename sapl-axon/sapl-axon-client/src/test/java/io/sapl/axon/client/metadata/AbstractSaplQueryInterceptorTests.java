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

package io.sapl.axon.client.metadata;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;

public class AbstractSaplQueryInterceptorTests {



	@InjectMocks
	AbstractSaplQueryInterceptor ab = mock(AbstractSaplQueryInterceptor.class, Mockito.CALLS_REAL_METHODS);
	
	@Mock
	QueryMessage<?, ?> QueryMessage = mock(QueryMessage.class);
	
	
	@SuppressWarnings("unchecked")
	@Test
	void when_HandleInvoked_Then_MetadataIsAdded() {
		
		Map<String, Object> subject = mock(Map.class);
		when(ab.getSubjectMetadata()).thenReturn(subject);
		
		ab.handle(QueryMessage);
		verify(QueryMessage).andMetaData(subject);
	}
	
	@Test
	void when_HandleInvokedReturnNull_Then_MetadataIsNotAdded() {
		when(ab.getSubjectMetadata()).thenReturn(null);
		
		ab.handle(QueryMessage);
		verify(QueryMessage,times(0)).andMetaData(any());
	}
	
	@Test
	void when_HandleInvokedReturnEmptyMap_Then_MetadataIsNotAdded() {
		Map<String,Object> emptyMap = Map.of();
		when(ab.getSubjectMetadata()).thenReturn(emptyMap);
		
		ab.handle(QueryMessage);
		verify(QueryMessage,times(0)).andMetaData(any());
	}
}
