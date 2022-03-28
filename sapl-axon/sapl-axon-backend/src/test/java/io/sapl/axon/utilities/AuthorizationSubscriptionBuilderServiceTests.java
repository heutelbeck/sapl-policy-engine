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

package io.sapl.axon.utilities;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.sapl.spring.method.metadata.PostEnforce;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.Value;

public class AuthorizationSubscriptionBuilderServiceTests {

	private static final String AGGREGATE_TYPE = Constants.aggregateType.name();
	private static final String AGGREGATE_IDENTIFIER = Constants.aggregateIdentifier.name();
	
	private final ObjectMapper mapper = new ObjectMapper();
	private final AuthorizationSubscriptionBuilderService service = new AuthorizationSubscriptionBuilderService(mapper);

	/**
	 * Test of Method "constructAuthorizationSubscriptionForQuery" with @PreEnforce
	 * Annotation Attributes
	 */
	@Test
	public void when_PreEnforceAnnotatedQueryHandler_then_constructAuthorizationSubscriptionForQuery()
			throws NoSuchMethodException, SecurityException {
		var payload = new TopLevelTestQuery();
		var query = new GenericQueryMessage<>(payload, null, ResponseTypes.instanceOf(TestAggregate.class));
		var method =  TopLevelTestQuery.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PreEnforce.class);
		var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation,method, Optional.empty());
		assertAll(
				() -> assertNotNull(subscription), 
				() -> assertEquals("Subject", subscription.getSubject().findValue("name").asText()),
				() -> assertEquals("Action", subscription.getAction().findValue("'Action'").asText()),
				() -> assertEquals("TopLevelTestQuery", subscription.getAction().findValue("name").asText()),
				() -> assertTrue(subscription.getAction().has("metadata")),
				() -> assertFalse(subscription.getAction().has("object")),
				() -> assertEquals("TopLevelTestQuery", subscription.getResource().findValue("projectionClass").asText()),
				() -> assertEquals("method", subscription.getResource().findValue("methodName").asText()),
				() -> assertEquals("TestAggregate", subscription.getResource().findValue("responsetype").asText()),
				() -> assertEquals("TopLevelTestQuery", subscription.getResource().findValue("queryname").asText()),
				() -> assertEquals("Environment", subscription.getEnvironment().asText())
				);
	}
	
	@Test
	public void when_PreEnforceAnnotatedQueryWithSpELNull_then_constructAuthorizationSubscriptionForQuery()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestQueryWithSpELAnnotation("testString", 42);
		var query = new GenericQueryMessage<>(payload, null, ResponseTypes.instanceOf(TestAggregate.class));
		var method = TestQueryWithSpELAnnotation.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PostEnforce.class);

		var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation,method, Optional.of(payload));
		assertAll(
				() -> assertNotNull(subscription), 
				() -> assertTrue(subscription.getSubject().isEmpty()),
				() -> assertTrue(subscription.getAction().findValue("null").isEmpty()),
				() -> assertEquals("TestQueryWithSpELAnnotation", subscription.getAction().findValue("name").asText()),
				() -> assertTrue(subscription.getAction().has("metadata")),
				() -> assertFalse(subscription.getAction().has("object")),
				() -> assertEquals("TestQueryWithSpELAnnotation", subscription.getResource().findValue("projectionClass").asText()),
				() -> assertEquals("AuthorizationSubscriptionBuilderServiceTests", subscription.getResource().findValue("classname").asText()),
				() -> assertEquals("method", subscription.getResource().findValue("methodName").asText()),
				() -> assertEquals("TestAggregate", subscription.getResource().findValue("responsetype").asText()),
				() -> assertEquals("TestQueryWithSpELAnnotation", subscription.getResource().findValue("queryname").asText()),
				() -> assertTrue(subscription.getSubject().isEmpty())
				);
	}

	@Test
	public void when_PostEnforeAnnotatedQueryHandlerWithoutAnnotationAttributes_then_constructAuthorizationSubscriptionForQuery()
			throws NoSuchMethodException, SecurityException {
		var payload = new TestQueryWithoutAnnotation("testString", 42);
		var query = new GenericQueryMessage<>(payload, null, ResponseTypes.instanceOf(TestAggregate.class));
		var method = TestQueryWithoutAnnotation.class.getDeclaredMethod("method");
		var annotation = method.getAnnotation(PostEnforce.class);

		var subscription = service.constructAuthorizationSubscriptionForQuery(query, annotation,method, Optional.of(payload));
		assertAll(
				() -> assertNotNull(subscription),
				() -> assertTrue(subscription.getSubject().isNull()),
				() -> assertEquals("TestQueryWithoutAnnotation", subscription.getAction().findValue("name").asText()),
				() -> assertEquals("TestAggregate", subscription.getResource().findValue("responsetype").asText()),
				() -> assertEquals("TestQueryWithoutAnnotation", subscription.getResource().findValue("queryname").asText()),
				() -> assertEquals("AuthorizationSubscriptionBuilderServiceTests", subscription.getResource().findValue("classname").asText()),
				() -> assertNull(subscription.getEnvironment()),
				() -> assertEquals(mapper.valueToTree(payload), subscription.getResource().findValue("queryResult"))
		);
	}

	/**
	 * Test of Method "constructAuthorizationSubscriptionForCommand"
	 * with @PreEnforce Annotation Attributes without Action and Resource
	 */
	@Test
	public void when_PreEnforceAnnotatedCommandHandlerWithoutAction_then_constructAuthorizationSubscriptionForCommand()
			throws NoSuchMethodException, SecurityException {
		var target = new TestAggregateWithoutAction();
		var message = new GenericCommandMessage<>(new TestCommand("test123"));
		var m = TestAggregateWithoutAction.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregateWithoutAction>(m, null, null, null);
		var subscription = service.constructAuthorizationSubscriptionForCommand(message, target, delegate);
		assertAll(
				() -> assertNotNull(subscription),
				() -> assertEquals("Subject", subscription.getSubject().findValue("name").asText()),
				() -> assertEquals("TestCommand", subscription.getAction().findValue("name").asText()),
				() -> assertEquals("test123", subscription.getAction().findValue("message").findValue("testId").asText()),
				() -> assertEquals("", subscription.getAction().findValue("metadata").asText()),
				() -> assertEquals("TestAggregateWithoutAction", subscription.getResource().findValue(AGGREGATE_TYPE).asText()),
				() -> assertEquals("test123", subscription.getResource().findValue(AGGREGATE_IDENTIFIER).asText()),
				() -> assertEquals("Environment", subscription.getEnvironment().asText())
		);
	}
	
	/**
	 * Test of Method "constructAuthorizationSubscriptionForCommand"
	 * with @PreEnforce Annotation Attributes with SpEL Action and Resource
	 */
	@Test
	public void when_PreEnforceAnnotatedCommandHandlerWithAction_then_constructAuthorizationSubscriptionForCommand()
			throws NoSuchMethodException, SecurityException {
		var target = new TestAggregate();
		target.setTestValue("value123");
		var message = new GenericCommandMessage<>(new TestCommand2("test123", "value"));
		ObjectNode subject = mapper.createObjectNode();
		subject.put("name", "testSubject");
		message = message.andMetaData(Map.of("subject",subject));
		Map<String,Object> aggregateType = new HashMap<>();
		aggregateType.put(AGGREGATE_TYPE, "TestAggregate");
		message = message.andMetaData(aggregateType);
		var m = TestAggregate.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregate>(m, null, null, null);
		var subscription = service.constructAuthorizationSubscriptionForCommand(message, target, delegate);
		assertAll(
				() -> assertNotNull(subscription),
				() -> assertEquals("testSubject",subscription.getSubject().findValue("name").asText()),
				() -> assertEquals("test123", subscription.getAction().findValue("testId").asText()),
				() -> assertEquals("TestCommand2", subscription.getAction().findValue("name").asText()),
				() -> assertTrue(subscription.getAction().has("message")),
				() -> assertTrue(subscription.getAction().has("metadata")),
				() -> assertEquals("value123",subscription.getResource().findValue("testValue").asText()),
				() -> assertEquals("TestAggregate",subscription.getResource().findValue(AGGREGATE_TYPE).asText()),
				() -> assertEquals("test123",subscription.getResource().findValue(AGGREGATE_IDENTIFIER).asText()),
				() -> assertNull(subscription.getEnvironment())
		);
	}


	@Test
	public void when_PreEnforceAnnotatedCommandHandlerWithoutAttributes_then_constructAuthorizationSubscriptionForCommand()
			throws NoSuchMethodException, Exception {
		var message = new GenericCommandMessage<>(new TestCommand("test"));
		var target = new TestAggregateWithoutAnnotation();
		var m = TestAggregateWithoutAnnotation.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregateWithoutAnnotation>(m, null, null, null);
		var subscription = service.constructAuthorizationSubscriptionForCommand(message, target, delegate);
		assertAll(
				() -> assertNotNull(subscription),
				() -> assertTrue(subscription.getSubject().isNull()),
				() -> assertNull(subscription.getEnvironment()),
				() -> assertTrue(subscription.getAction().has("message")),
				() -> assertTrue(subscription.getAction().has("metadata")),
				() -> assertEquals("TestCommand", subscription.getAction().findValue("name").asText()),
				() -> assertEquals("TestAggregateWithoutAnnotation",subscription.getResource().findValue(AGGREGATE_TYPE).asText()),
				() -> assertEquals("test",subscription.getResource().findValue(AGGREGATE_IDENTIFIER).asText())
				
		);
	}

	@Test
	public void when_NoAggregateAnnotation_and_constructAuthorizationSubscriptionForCommand_then_AggregateTypeNotAvailable()
			throws NoSuchMethodException {
		var message = new GenericCommandMessage<>(new TestCommand(null));
		var target = new NoAggregate();
		var m = NoAggregate.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<NoAggregate>(m, null, null, null);
		var subscription = service.constructAuthorizationSubscriptionForCommand(message, target, delegate);
		assertNull(subscription.getResource().findValue(AGGREGATE_TYPE));
		}
	
	@Test
	public void when_NoFields_and_constructAuthorizationSubscriptionForCommand_then_AggregateIdentifierNotAvailable()
			throws NoSuchMethodException {
		var message = new GenericCommandMessage<>(new TestCommandWithoutFields());
		var target = new TestAggregateWithoutAnnotation();
		var m = TestAggregateWithoutAnnotation.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregateWithoutAnnotation>(m, null, null, null);
		var subscription = service.constructAuthorizationSubscriptionForCommand(message, target, delegate);
		assertNull(subscription.getResource().findValue(AGGREGATE_IDENTIFIER));
		}
	
	@Test
	public void when_NoTargetIdentifier_and_constructAuthorizationSubscriptionForCommand_then_AggregateIdentifierNotAvailable()
			throws NoSuchMethodException {
		var message = new GenericCommandMessage<>(new TestCommandWithoutIdentifier("test"));
		var target = new TestAggregate();
		var m = TestAggregate.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregate>(m, null, null, null);
		var subscription = service.constructAuthorizationSubscriptionForCommand(message, target, delegate);
		assertNull(subscription.getResource().findValue(AGGREGATE_IDENTIFIER));
		}
	

	@Test
	public void when_targetNull_then_constructAuthorizationSubscriptionForCommand()
			throws NoSuchMethodException {
		var message = new GenericCommandMessage<>(new TestCommand(null));
		TestAggregateWithoutAnnotation target = null;
		var m = TestAggregateWithoutAnnotation.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregateWithoutAnnotation>(m, null, null, null);
		var subscription = service.constructAuthorizationSubscriptionForCommand(message, target, delegate);
		assertTrue(subscription.getResource().findValue(AGGREGATE_IDENTIFIER).isNull());
	}
	
	@Test
	public void when_PreEnforceAnnotatedCommandHandlerWithError_then_throwException()
			throws NoSuchMethodException, SecurityException {
		var target = new TestAggregate();
		target.setTestValue("value123");
		var message = new GenericCommandMessage<>(new TestCommand2("test123", "value"));
		var m = TestAggregate.class.getDeclaredMethod("method2");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregate>(m, null, null, null);
		Exception e = assertThrows(IllegalArgumentException.class, () -> service.constructAuthorizationSubscriptionForCommand(message, target, delegate));
		assertTrue(e.getMessage().contains("Failed to evaluate expression"));
	}
	
	@Test
	public void when_PreEnforceAnnotatedWithSpELNull() throws NoSuchMethodException, SecurityException {
		var message = new GenericCommandMessage<>(new TestCommand("test"));
		var target = new TestAggregateWithNull();
		var m = TestAggregateWithNull.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregateWithNull>(m, null, null, null);
		var subscription = service.constructAuthorizationSubscriptionForCommand(message, target, delegate);
		assertAll(
				() -> assertNotNull(subscription),
				() -> assertTrue(subscription.getSubject().isNull()),
				() -> assertNull(subscription.getEnvironment()),
				() -> assertTrue(subscription.getAction().has("message")),
				() -> assertTrue(subscription.getAction().has("metadata")),
				() -> assertEquals("TestCommand", subscription.getAction().findValue("name").asText()),
				() -> assertEquals("TestAggregateWithNull",subscription.getResource().findValue(AGGREGATE_TYPE).asText()),
				() -> assertEquals("test",subscription.getResource().findValue(AGGREGATE_IDENTIFIER).asText())
				
		);
	}




	@Value
	private static class TestQueryWithoutAnnotation {

		  String testString;
		  int testInt;

		@PostEnforce
		@QueryHandler
		public void method() {
		}
	}
	
	@Value
	private static class TestQueryWithSpELAnnotation {

		  String testString;
		  int testInt;

		@PostEnforce(subject = "null", action = "null", resource = "null", environment = "null")
		@QueryHandler
		public void method() {
		}
	}

	@Aggregate
	@NoArgsConstructor
	private static class TestAggregate {
		
		@AggregateIdentifier
		String testId;
		
		@Getter
		@Setter
		String testValue;
		
		@PreEnforce(action = "testId", resource = "testValue")
		@CommandHandler
		public void method() {
		}
		
		@PreEnforce(action = "error")
		@CommandHandler
		public void method2() {
		}
		
	}
	
	private static class NoAggregate {
		@PreEnforce
		@CommandHandler
		public void method() {
		}
	}
	
	
	
	@Aggregate
	@NoArgsConstructor
	private static class TestAggregateWithoutAnnotation {
		
		@AggregateIdentifier
		String testId;
		
		@PreEnforce
		@CommandHandler
		public void method() {
		}
		
	}

	@JsonAutoDetect(fieldVisibility = Visibility.ANY)
	@Value
	// By default, Jackson 2 will only work with fields that are either public,
	// or have public getter methods serializing an entity that has all fields
	// private or package private will fail.
	// JsonAutoDetect will allow the private and package private fields to be
	// detected without getters, and serialization will work correctly
	private static class TestCommand {
		@TargetAggregateIdentifier
		String testId;
	}
	
	@JsonAutoDetect(fieldVisibility = Visibility.ANY)
	private static class TestCommandWithoutFields {
	}
	
	@Value
	private static class TestCommandWithoutIdentifier {
		String testId;
	}
	
	@Value
	private static class TestCommand2 {
		@TargetAggregateIdentifier
		String testId;
		String testValue;
	}

	@Aggregate
	@NoArgsConstructor
	private static class TestAggregateWithoutAction {
		
		@AggregateIdentifier
		String testId;
		
		@CommandHandler
		@PreEnforce(subject = "'Subject'", environment = "'Environment'")
		public void method() {
		}
	}
	
	@Aggregate
	@NoArgsConstructor
	private static class TestAggregateWithNull {
		
		@AggregateIdentifier
		String testId;
		
		@CommandHandler
		@PreEnforce(subject = "null", environment = "null")
		public void method() {
		}
	}


}
