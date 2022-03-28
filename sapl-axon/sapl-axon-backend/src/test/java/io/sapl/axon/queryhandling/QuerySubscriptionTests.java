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

import static org.axonframework.messaging.responsetypes.ResponseTypes.instanceOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Type;
import java.util.Objects;

import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.junit.jupiter.api.Test;

public class QuerySubscriptionTests {
	
	final Type ResponseType = String.class;

	@Test
	void when_MessageCreated_Then_ReturnObject(){    
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, null);
	    org.axonframework.messaging.responsetypes.ResponseType<?> queryResponseType = instanceOf(String.class);
	    assertTrue(result.canHandle(queryResponseType));
	    assertEquals(ResponseType, result.getResponseType());
		assertNull(result.getQueryHandler());
	}
	
	@Test
	void when_MessageHash_Then_EqualHashFunction(){
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, null);
	    assertEquals(result.hashCode(), Objects.hash(ResponseType, null));
	}
	
	
	@Test
	void when_MessageEquals_Then_ReturnTrue(){
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, null);
		assertEquals(result, result);
	}
	
	@Test
	void when_MessageNull_Then_ReturnFalse(){
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, null);
		assertNotEquals(null, result);
	}
	
	@Test
	void when_MessageDifferentObjcType_Then_ReturnFalse(){
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, null);
	    QueryMessage<String, String> testQueryMessage = new GenericQueryMessage<>("hello", null);
		assertNotEquals(result, testQueryMessage);
	}
	
	@Test
	void when_MessageDifferentResponseType_Then_ReturnFalse(){
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, null);
	    QuerySubscription<Object> diff = new QuerySubscription<>(Integer.class, null);
		assertNotEquals(result, diff);
	}
	
	@Test
	void when_MessageDifferentHandler_Then_ReturnFalse(){
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, (q) -> q.getPayload() + "1234");
	    QuerySubscription<Object> diff = new QuerySubscription<>(ResponseType, (q) -> q.getPayload() + "5678");
		assertNotEquals(result, diff);
	}
	
	@Test
	void when_MessageDifferentObjResponseTypeAndHandler_Then_ReturnFalse(){
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, (q) -> q.getPayload() + "1234");
	    QuerySubscription<Object> diff = new QuerySubscription<>(Integer.class, (q) -> q.getPayload() + "5678");
		assertNotEquals(result, diff);
	}
	
	@Test
	void when_MessageSameObjResponseTypeAndHandler_Then_ReturnTrue(){
	    QuerySubscription<Object> result = new QuerySubscription<>(ResponseType, null);
	    QuerySubscription<Object> diff = new QuerySubscription<>(ResponseType, null);
		assertEquals(result, diff);
	}
	
}
