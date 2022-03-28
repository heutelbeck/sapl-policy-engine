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
package io.sapl.axon.blocking;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.security.access.AccessDeniedException;

import io.sapl.axon.commandhandling.CommandPolicyEnforcementPoint;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.NoArgsConstructor;

class DefaultSAPLCommandHandlingMemberTests {

	/**
	 * Test of handle Command Method with Decision Deny
	 * 
	 * @throws NoSuchMethodException
	 * @throws Exception
	 */

	@Test
	void when_DecisionDeny_then_notPermittedException() throws NoSuchMethodException, Exception {
		var m = TestAggregate.class.getDeclaredMethod("method");
		var delegate = new AnnotatedMessageHandlingMember<TestAggregate>(m, null, null, null);
		var pep = mock(CommandPolicyEnforcementPoint.class);
		doThrow(new AccessDeniedException("PDP - NOT PERMITTED")).when(pep).preEnforceCommandBlocking(any(), any(),
				any(), any());
		var handler = new DefaultSAPLCommandHandlingMember<>(delegate, pep);
		var command = new GenericCommandMessage<>(new TestCommand());
		AccessDeniedException exception = assertThrows(AccessDeniedException.class,
				() -> handler.handle(command, new TestAggregate()));
		assertEquals("PDP - NOT PERMITTED", exception.getMessage());
	}

	/**
	 * Test of handle Command Method with Decision Permit
	 * 
	 * @throws NoSuchMethodException
	 * @throws Exception
	 */

	@Test
	void when_DecisionPermit_then_MessageIsHandled() throws Exception {
		var m = TestAggregate.class.getDeclaredMethod("method");
		var delegate = spy(new AnnotatedMessageHandlingMember<TestAggregate>(m, null, null, null));
		doReturn("OK").when(delegate).handle(any(Message.class), any(TestAggregate.class));
		var pep = mock(CommandPolicyEnforcementPoint.class);
		OngoingStubbing<Object> testStubbing = when(
				pep.preEnforceCommandBlocking(any(), any(), any(MessageHandlingMember.class), any()));
		testStubbing.thenReturn("OK");
		var handler = new DefaultSAPLCommandHandlingMember<>(delegate, pep);
		var message = new GenericCommandMessage<>(new TestCommand());
		Object obj = handler.handle(message, new TestAggregate());
		assertEquals("OK", obj);
	}

	/**
	 * Test of handle Command Method with Decision Permit and Aggregate without
	 * preEnforce Annotation
	 * 
	 * @throws Exception
	 */
	@Test
	void when_NoPreEnforce_then_MessageIsHandled() throws Exception {
		var m = TestAggregate_NotPreEnforced.class.getDeclaredMethod("method");
		var delegate = spy(new AnnotatedMessageHandlingMember<TestAggregate_NotPreEnforced>(m, null, null, null));
		doReturn("OK").when(delegate).handle(any(Message.class), any(TestAggregate_NotPreEnforced.class));
		var pep = mock(CommandPolicyEnforcementPoint.class);
		OngoingStubbing<Object> testStubbing = when(
				pep.preEnforceCommandBlocking(any(), any(), any(MessageHandlingMember.class), any()));
		testStubbing.thenReturn("OK");
		var handler = new DefaultSAPLCommandHandlingMember<>(delegate, pep);
		var message = new GenericCommandMessage<>(new TestCommand());
		Object obj = handler.handle(message, new TestAggregate_NotPreEnforced());
		assertEquals("OK", obj);
	}

	private static class TestCommand {
	}

	@Aggregate
	@NoArgsConstructor
	private static class TestAggregate {

		@AggregateIdentifier
        private String identifier;
		
		@PreEnforce(subject = "user", action = "TestBlockCommand", resource = "test1")
		@CommandHandler
		public void method() {
		}
	}

	@Aggregate
	@NoArgsConstructor
	private static class TestAggregate_NotPreEnforced {
		
		@AggregateIdentifier
        private String identifier;
		
		@CommandHandler
		public void method() {

		}
	}
}
