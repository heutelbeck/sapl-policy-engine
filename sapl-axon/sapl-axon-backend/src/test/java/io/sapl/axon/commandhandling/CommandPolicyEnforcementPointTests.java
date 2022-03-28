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
package io.sapl.axon.commandhandling;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.BiFunction;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.common.lock.Lock;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AbstractRepository;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.EntityId;
import org.axonframework.modelling.command.LockAwareAggregate;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.VersionedAggregateIdentifier;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.command.inspection.ChildForwardingCommandMessageHandlingMember;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotations.ConstraintHandler;
import io.sapl.axon.constraints.AxonConstraintHandlerBundle;
import io.sapl.axon.constraints.ConstraintHandlerService;
import io.sapl.axon.utilities.AuthorizationSubscriptionBuilderService;
import io.sapl.axon.utilities.CheckedBiFunction;
import io.sapl.axon.utilities.CheckedFunction;
import io.sapl.spring.method.metadata.PreEnforce;
import reactor.core.publisher.Flux;

public class CommandPolicyEnforcementPointTests {

	private final ConstraintHandlerService constraintHandlerService = mock(ConstraintHandlerService.class);
	private final PolicyDecisionPoint pdp = mock(PolicyDecisionPoint.class);
	private final ObjectMapper mapper = new ObjectMapper();
	private final AuthorizationSubscriptionBuilderService authSubscriptionService = new AuthorizationSubscriptionBuilderService(
			mapper);
	private final String DENY = "Denied by PDP";
	private final CommandPolicyEnforcementPoint testSubject = new CommandPolicyEnforcementPoint(pdp,
			authSubscriptionService, constraintHandlerService, null);

	// PreEnforceCommandBlocking Tests
	@Test
	public void when_preEnforceCommandHandler_and_DecisionDeny_then_AccessDeniedException()
			throws NoSuchMethodException, SecurityException {
		var message = new GenericCommandMessage<>(new CommandPolicyEnforcementPointTests.TestCommand());
		var aggregate = new CommandPolicyEnforcementPointTests.TestAggregate();
		var method = CommandPolicyEnforcementPointTests.TestAggregate.class.getDeclaredMethod("methodWithValue");
		var delegate = new AnnotatedMessageHandlingMember<CommandPolicyEnforcementPointTests.TestAggregate>(method,
				null, null, null);
		var handleCommand = mock(CheckedBiFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		@SuppressWarnings("unchecked")
		Exception exception = assertThrows(Exception.class,
				() -> testSubject.preEnforceCommandBlocking(message, aggregate, delegate, handleCommand));
		assertEquals(DENY, exception.getMessage());
	}

	@Test
	public void when_preEnforceCommandHandler_and_DecisionNull_then_AccessDeniedException()
			throws NoSuchMethodException, SecurityException {
		var message = new GenericCommandMessage<>(new CommandPolicyEnforcementPointTests.TestCommand());
		var aggregate = new CommandPolicyEnforcementPointTests.TestAggregate();
		var method = CommandPolicyEnforcementPointTests.TestAggregate.class.getDeclaredMethod("methodWithValue");
		var delegate = new AnnotatedMessageHandlingMember<CommandPolicyEnforcementPointTests.TestAggregate>(method,
				null, null, null);
		var handleCommand = mock(CheckedBiFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just());
		@SuppressWarnings("unchecked")
		Exception exception = assertThrows(Exception.class,
				() -> testSubject.preEnforceCommandBlocking(message, aggregate, delegate, handleCommand));
		assertEquals(DENY, exception.getMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommandHandler_and_DecisionPermit_then_doesNotThrowException() throws Exception {
		var message = new GenericCommandMessage<>(new CommandPolicyEnforcementPointTests.TestCommand());
		var aggregate = new CommandPolicyEnforcementPointTests.TestAggregate();
		var method = CommandPolicyEnforcementPointTests.TestAggregate.class.getDeclaredMethod("methodWithValue");
		var delegate = new AnnotatedMessageHandlingMember<CommandPolicyEnforcementPointTests.TestAggregate>(method,
				null, null, null);
		var handleCommand = mock(CheckedBiFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
		.thenReturn(new AxonConstraintHandlerBundle<>());
		assertDoesNotThrow(() -> testSubject.preEnforceCommandBlocking(message, aggregate, delegate, handleCommand));
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommandChildHandler_and_DecisionPermit_then_doesNotThrowException() throws Exception {
		var message = new GenericCommandMessage<>(new CommandPolicyEnforcementPointTests.TestEntityCommand());
		var aggregate = new CommandPolicyEnforcementPointTests.TestAggregate();
		var entityMethod = TestEntity.class.getDeclaredMethod("handle", TestEntityCommand.class);
		var entityMethodDelegate = new AnnotatedMessageHandlingMember<TestEntity>(entityMethod, CommandMessage.class,
				TestEntityCommand.class, new DefaultParameterResolverFactory());
		var handleCommand = mock(CheckedBiFunction.class);
		var entityDelegate = new EntityCommandHandler(entityMethodDelegate);
		
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
		.thenReturn(new AxonConstraintHandlerBundle<>());
		assertDoesNotThrow(() -> testSubject.preEnforceCommandBlocking(message, aggregate, entityDelegate, handleCommand));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommandHandler_and_DecisionPermit_then_ReturnValue() throws Exception {
		var message = new GenericCommandMessage<>(new CommandPolicyEnforcementPointTests.TestCommand());
		var aggregate = new CommandPolicyEnforcementPointTests.TestAggregate();
		var method = CommandPolicyEnforcementPointTests.TestAggregate.class.getDeclaredMethod("methodWithValue");
		var delegate = new AnnotatedMessageHandlingMember<CommandPolicyEnforcementPointTests.TestAggregate>(method,
				null, null, null);
		var handleCommand = mock(CheckedBiFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
		.thenReturn(new AxonConstraintHandlerBundle<>());
		when(handleCommand.apply(any(), any())).thenReturn("Test");
		assertDoesNotThrow(() -> testSubject.preEnforceCommandBlocking(message, aggregate, delegate, handleCommand));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommandHandler_and_ConstructorInAggregate_and_DecisionPermit_then_ReturnValue()
			throws Exception {
		var message = new GenericCommandMessage<>(new CommandPolicyEnforcementPointTests.TestCommand());
		var constructor = CommandPolicyEnforcementPointTests.TestAggregate.class.getConstructor();
		var delegate = new AnnotatedMessageHandlingMember<CommandPolicyEnforcementPointTests.TestAggregate>(constructor,
				null, null, null);
		var handleCommand = mock(CheckedBiFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
		.thenReturn(new AxonConstraintHandlerBundle<>());
		when(handleCommand.apply(any(), any())).thenReturn("Test");
		assertDoesNotThrow(() -> testSubject.preEnforceCommandBlocking(message, null, delegate, handleCommand));
	}

	// PreEnforceCommandDisruptor Tests
	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommandDisruptor_and_DecisionDeny_then_AccessDeniedException()
			throws SecurityException {
		var message = new GenericCommandMessage<>(new CommandPolicyEnforcementPointTests.TestCommand());
		var handleCommand = mock(CheckedFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var handler = createHandlerAndProperties(false);
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, handler.commandTargetResolver);

		Exception exception = assertThrows(Exception.class,
				() -> testSubject.preEnforceCommandDisruptor(message, handleCommand));
		assertEquals(DENY, exception.getMessage());
	}

	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommandDisruptor_and_DecisionPermit_then_ReturnValue() throws Exception {
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestCommand());
		var handleCommand = mock(CheckedFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
				.thenReturn(new AxonConstraintHandlerBundle<>());

		when(handleCommand.apply(any(Message.class))).thenReturn("Test");

		var handler = createHandlerAndProperties(false);
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, handler.commandTargetResolver);

		assertDoesNotThrow(
				() -> testSubject.preEnforceCommandDisruptor(message, handleCommand));
	}

	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommandDisruptor_and_DecisionPermit_and_ConstructorInAggregate_then_ReturnValue() throws Exception {
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestCommand());
		var handleCommand = mock(CheckedFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
				.thenReturn(new AxonConstraintHandlerBundle<>());

		when(handleCommand.apply(any(Message.class))).thenReturn("Test");

		var handler = createHandlerAndProperties(true);
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, handler.commandTargetResolver);

		assertDoesNotThrow(
				() -> testSubject.preEnforceCommandDisruptor(message, handleCommand));
	}
	
	// PreEnforceCommand Tests
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommand_and_DecisionDeny_then_AccessDeniedException()
			throws SecurityException {
		var message = new GenericCommandMessage<>(new CommandPolicyEnforcementPointTests.TestCommand());
		var handleCommand = mock(CheckedFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.DENY));

		var handler = createHandlerAndProperties(false);
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, null);

		var callable = testSubject.preEnforceCommand(message, handleCommand);
		assertThrows(org.springframework.security.access.AccessDeniedException.class, callable::call);
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommand_and_DecisionPermit_then_ReturnValue() throws Exception {
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestCommand());
		var handleCommand = mock(CheckedFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
				.thenReturn(new AxonConstraintHandlerBundle<>());
		when(handleCommand.apply(any(Message.class))).thenReturn("Test");

		var handler = createHandlerAndProperties(false);
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, null);

		var value = testSubject.preEnforceCommand(message, handleCommand).call();
		assertEquals("Test", value);
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommand_and_WithoutEnforce_then_ReturnValue() throws Exception {
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestCommand());
		var handleCommand = mock(CheckedFunction.class);
		when(handleCommand.apply(any(Message.class))).thenReturn("Test");
		
		var value = testSubject.preEnforceCommand(message, handleCommand).call();
		assertEquals("Test", value);
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommand_and_DecisionPermit_and_Constructor_then_ReturnValue() throws Exception {
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestCommand());
		var handleCommand = mock(CheckedFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
				.thenReturn(new AxonConstraintHandlerBundle<>());
		when(handleCommand.apply(any(Message.class))).thenReturn("Test");

		var handler = createHandlerAndProperties(true);
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, null);

		assertDoesNotThrow(() -> testSubject.preEnforceCommand(message, handleCommand).call());
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_preEnforceCommand_and_DecisionPermit_then_NoAggregateException() throws Exception {
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestCommand());
		var handleCommand = mock(CheckedFunction.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));

		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any()))
				.thenReturn(new AxonConstraintHandlerBundle<>());
		when(handleCommand.apply(any(Message.class))).thenReturn("Test");

		var handler = createHandlerAndProperties(true);
		var repositorySpy = spy(TestRepository.builder(TestAggregate.class).build());
		doThrow(new RuntimeException("Test")).when(repositorySpy).load(anyString());
		handler.repository = repositorySpy;
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, null);

		assertDoesNotThrow(() -> testSubject.preEnforceCommand(message, handleCommand).call());
	}
	
	// gatherNecessaryHandlers Tests
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_gatherNecessaryHandlers_then_DoneSuccessfully() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestEntityCommand());
		
		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			var aggregateList = (Optional<List<Method>>) invocation.getArguments()[3];
			if (aggregateList.isEmpty() || aggregateList.get().size() != 1) {
				throw new RuntimeException("Empty optional or wrong count of aggregate methods");
			}
			var entity = (Optional<Object>) invocation.getArguments()[4];
			if (entity.isEmpty()) {
				throw new RuntimeException("Empty entity optional");
			}
			var entityList = (Optional<List<Method>>) invocation.getArguments()[5];
			if (entityList.isEmpty() || aggregateList.get().size() != 1) {
				throw new RuntimeException("Empty optional or wrong count of entity methods");
			}
					
			return new AxonConstraintHandlerBundle<>(); 
		});
		
		var handleCommand = mock(CheckedFunction.class);
		var handler = createHandlerAndProperties(false);
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, null);
		
		assertDoesNotThrow(() -> testSubject.preEnforceCommandDisruptor(message, handleCommand));
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_gatherNecessaryHandlers_then_DoneSuccessfully_with_SameAggregate() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		var testcommandMsg = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestCommand());
		var testEntitycommandMsg = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestEntityCommand());

		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			var aggregateList = (Optional<List<Method>>) invocation.getArguments()[3];
			if (aggregateList.isEmpty() || aggregateList.get().size() != 1) {
				throw new RuntimeException("Empty optional or wrong count of aggregate methods");
			}
			var entity = (Optional<Object>) invocation.getArguments()[4];
			if (entity.isPresent()) {
				throw new RuntimeException("Not empty entity optional");
			}
			var entityList = (Optional<List<Method>>) invocation.getArguments()[5];
			if (entityList.isPresent()) {
				throw new RuntimeException("Not empty optional");
			}
					
			return new AxonConstraintHandlerBundle<>(); 
		});
		
		var handleCommand = mock(CheckedFunction.class);
		var handler = createHandlerAndProperties(false);
		testSubject.gatherNecessaryHandlers(testcommandMsg.getCommandName(), handler, null);
		testSubject.gatherNecessaryHandlers(testEntitycommandMsg.getCommandName(), handler, null);

		assertDoesNotThrow(() -> testSubject.preEnforceCommandDisruptor(testcommandMsg, handleCommand));
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_gatherNecessaryHandlers_then_DoneSuccessfull_for_Disruptor() {
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestCommand());
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			var aggregateList = (Optional<List<Method>>) invocation.getArguments()[3];
			if (aggregateList.isEmpty() || aggregateList.get().size() != 1) {
				throw new RuntimeException("Empty optional or wrong count of aggregate methods");
			}
			var entity = (Optional<Object>) invocation.getArguments()[4];
			if (entity.isPresent()) {
				throw new RuntimeException("Not empty entity optional");
			}
			var entityList = (Optional<List<Method>>) invocation.getArguments()[5];
			if (entityList.isPresent()) {
				throw new RuntimeException("Not empty optional");
			}
					
			return new AxonConstraintHandlerBundle<>(); 
		});
		
		var handleCommand = mock(CheckedFunction.class);
		var handler = createHandlerAndPropertiesForDisruptor(false);
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, null);		

		assertDoesNotThrow(() -> testSubject.preEnforceCommandDisruptor(message, handleCommand));
	}
	
	@Test
	@SuppressWarnings("unchecked")
	public void when_gatherNecessaryHandlers_then_DoneSuccessfully_with_HandlerClassLoop() {
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		var message = new GenericCommandMessage<>(
				new CommandPolicyEnforcementPointTests.TestEntityCommand());
		
		when(constraintHandlerService.createCommandBundle(any(), any(), any(), any(), any(), any())).thenAnswer(invocation -> {
			var aggregateList = (Optional<List<Method>>) invocation.getArguments()[4];
			if (aggregateList.isEmpty() || aggregateList.get().size() != 1) {
				throw new RuntimeException("Empty optional or wrong count of aggregate methods");
			}
			var entity = (Optional<Object>) invocation.getArguments()[5];
			if (entity.isPresent()) {
				throw new RuntimeException("Not empty entity optional");
			}
			var entityList = (Optional<List<Method>>) invocation.getArguments()[6];
			if (entityList.isPresent()) {
				throw new RuntimeException("Not empty optional");
			}
					
			return new AxonConstraintHandlerBundle<>(); 
		});
		
		var handleCommand = mock(CheckedFunction.class);
		var handler = spy(createHandlerAndProperties(false));
		testSubject.gatherNecessaryHandlers(message.getCommandName(), handler, null);
	
		assertDoesNotThrow(() -> testSubject.preEnforceCommand(message, handleCommand));
	}
	
	@Test
	public void when_gatherNecessaryHandlers_then_Exception() {
		assertThrows(UnsupportedOperationException.class,
				() -> testSubject.gatherNecessaryHandlers(null, null, null));
	}

	// end of tests
	// Utils

	private Repository<?> createRepositoryMock() {
		var aggregate = new AggregateWrapper();
		var lockMock = mock(Lock.class);
		var lockAwareAggregateSpy = spy(new LockAwareAggregate<>(aggregate, lockMock));
		when(lockAwareAggregateSpy.getWrappedAggregate()).thenReturn(aggregate);
		
		var repositorySpy = spy(TestRepository.builder(TestAggregate.class).build());
		doReturn(lockAwareAggregateSpy).when(repositorySpy).load(anyString());
		return repositorySpy;
	}
	
	private Repository<?> createDisruptorRepositoryMock() {
		var aggregate = new AggregateWrapper();
		
		TestDisruptorRepository<TestAggregate> repoSpy = spy(
				TestDisruptorRepository.builderDisruptor(TestAggregate.class).build());
		doReturn(aggregate).when(repoSpy).load(anyString());
		return repoSpy;
	}

	private CommandTargetResolver createCommandTargetResolverMock() {
		var resolverMock = mock(CommandTargetResolver.class);
		when(resolverMock.resolveTarget(any())).thenReturn(new VersionedAggregateIdentifier("1", 1L));
		return resolverMock;
	}

	private List<MessageHandler<CommandMessage<?>>> creatHandler(boolean useConstructor)
			throws NoSuchMethodException, SecurityException {
		if (!useConstructor) {
			var method = TestAggregate.class.getDeclaredMethod("method", TestCommand.class);
			var entityMethod = TestEntity.class.getDeclaredMethod("handle", TestEntityCommand.class);

			var methodDelegate = new AnnotatedMessageHandlingMember<TestAggregate>(method, CommandMessage.class,
					TestCommand.class, new DefaultParameterResolverFactory());
			var entityMethodDelegate = new AnnotatedMessageHandlingMember<TestEntity>(entityMethod, CommandMessage.class,
					TestEntityCommand.class, new DefaultParameterResolverFactory());
			var entityDelegate = new EntityCommandHandler(entityMethodDelegate);
			
			List<MessageHandler<CommandMessage<?>>> handlers = List.of(
					new AggregateCommandHandler(methodDelegate, msg -> msg),
					new AggregateCommandHandler(entityDelegate, msg -> msg));
			return handlers;
		} else {
			var constructor = TestAggregate.class.getConstructor(TestCommand.class);
			var methodDelegate = new AnnotatedMessageHandlingMember<TestAggregate>(constructor, CommandMessage.class,
					TestCommand.class, new DefaultParameterResolverFactory());
			List<MessageHandler<CommandMessage<?>>> handlers = List
					.of(new AggregateCommandHandler(methodDelegate, msg -> msg));
			return handlers;
		}
	}

	private StubCommandHandler createHandlerAndProperties(boolean useConstructor) {
		var handler = new StubCommandHandler();
		handler.repository = createRepositoryMock();
		handler.commandTargetResolver = createCommandTargetResolverMock();
		try {
			handler.handlers = creatHandler(useConstructor);
		} catch (Exception e) {
			fail("Did not expect exception");
		}
		return handler;
	}
	
	private StubCommandHandler createHandlerAndPropertiesForDisruptor(boolean useConstructor) {
		var handler = new StubCommandHandler();
		handler.repository = createDisruptorRepositoryMock();
		handler.commandTargetResolver = createCommandTargetResolverMock();
		try {
			handler.handlers = creatHandler(useConstructor);
		} catch (Exception e) {
			fail("Did not expect exception");
		}
		return handler;
	}

	@Aggregate
	private static class TestAggregate {
		
		@AggregateIdentifier
        private String identifier;
		
		@AggregateMember
		private TestEntity entity;
		@AggregateMember
		private List<TestEntity> entityList;
		@AggregateMember
		private Map<String, TestEntity> entityMap;
		
		@PreEnforce
		public TestAggregate() {
			
		}
		
		@PreEnforce
		public TestAggregate(TestCommand cmd) {
		}

		@PreEnforce
		@CommandHandler
		public void method(TestCommand cmd) {
		}

		@PreEnforce
		@CommandHandler
		public String methodWithValue() {
			return "TestValue";
		}
		
		@ConstraintHandler("aggregate")
		public void handleConstraint() {
			
		}
	}
	
	public static class TestEntity {
		@EntityId
		String entityId;
		
		@PreEnforce
		@CommandHandler
		public void handle(TestEntityCommand command) {
			
		}
		
		@ConstraintHandler("entity")
		public void handleConstraint() {
			
		}
	}

	private static class AggregateWrapper extends AnnotatedAggregate<TestAggregate> {
		AggregateWrapper() {
			super(new TestAggregate(), null, null);
		}
	}

	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
	private static class TestCommand {
	}
	@JsonAutoDetect(fieldVisibility = JsonAutoDetect.Visibility.ANY)
	private static class TestEntityCommand {
	}

	@SuppressWarnings("unused")
	private static class StubCommandHandler implements MessageHandler<CommandMessage<?>> {
		private Repository<?> repository;
		private CommandTargetResolver commandTargetResolver;
		private List<MessageHandler<CommandMessage<?>>> handlers;

		@Override
		public Object handle(CommandMessage<?> message) throws Exception {
			return handlers.get(0).handle(message);
		}
	}

	private static class AggregateCommandHandler implements MessageHandler<CommandMessage<?>> {

		@SuppressWarnings("unused")
		private final MessageHandlingMember<?> handler;
		private final MessageHandler<CommandMessage<?>> delegate;

		public AggregateCommandHandler(MessageHandlingMember<?> handler, MessageHandler<CommandMessage<?>> delegate) {
			this.handler = handler;
			this.delegate = delegate;
		}

		@Override
		public Object handle(CommandMessage<?> command) throws Exception {
			return delegate.handle(command);
		}
	}
	
	private static class EntityCommandHandler extends ChildForwardingCommandMessageHandlingMember<TestAggregate, TestEntity> {

		@SuppressWarnings("unused")
		private final BiFunction<CommandMessage<?>, TestAggregate, TestEntity> childEntityResolver = (c, a) -> new TestEntity();
		
		public EntityCommandHandler(MessageHandlingMember<? super TestEntity> childHandler) {
			super(null, childHandler, null);
		}
	}

	private static class TestRepository<T>
			extends AbstractRepository<T, org.axonframework.modelling.command.Aggregate<T>> {

		protected TestRepository(Builder<T> builder) {
			super(builder);
		}

		@Override
		protected org.axonframework.modelling.command.Aggregate<T> doCreateNew(Callable<T> factoryMethod)
				 {
			return null;
		}

		@Override
		protected void doSave(org.axonframework.modelling.command.Aggregate<T> aggregate) {
		}

		@Override
		protected org.axonframework.modelling.command.Aggregate<T> doLoad(String aggregateIdentifier,
				Long expectedVersion) {
			return null;
		}

		@Override
		protected void doDelete(org.axonframework.modelling.command.Aggregate<T> aggregate) {
		}

		public static <T> TestRepositoryBuilder<T> builder(Class<T> aggregateType) {
			return new TestRepositoryBuilder<>(aggregateType);
		}

		static class TestRepositoryBuilder<T> extends Builder<T> {

			protected TestRepositoryBuilder(Class<T> aggregateType) {
				super(aggregateType);
			}

			public TestRepository<T> build() {
				return new TestRepository<>(this);
			}
		}

	}
	
	private static class TestDisruptorRepository<T> extends TestRepository<T> {
		@SuppressWarnings("unused")
		private final Class<?> type;
		
		protected TestDisruptorRepository(TestDisruptorRepositoryBuilder<T> builder) {
			super(builder);
			this.type = builder.aggregateType;
		}
		
		public static <T> TestDisruptorRepositoryBuilder<T> builderDisruptor(Class<T> aggregateType) {
			return new TestDisruptorRepositoryBuilder<>(aggregateType);
		}

		static class TestDisruptorRepositoryBuilder<T> extends Builder<T> {

			private final Class<?> aggregateType;
			
			protected TestDisruptorRepositoryBuilder(Class<T> aggregateType) {
				super(aggregateType);
				this.aggregateType = aggregateType;
			}

			public TestDisruptorRepository<T> build() {
				return new TestDisruptorRepository<>(this);
			}
			
		}
	}
}
