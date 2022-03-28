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


import static io.sapl.axon.commandhandling.disruptor.utils.AssertUtils.assertWithin;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolution;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.commandhandling.callbacks.NoOpCallback;
import org.axonframework.common.Registration;
import org.axonframework.common.lock.Lock;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.eventsourcing.EventSourcingRepository;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.correlation.MessageOriginProvider;
import org.axonframework.messaging.interceptors.CorrelationDataInterceptor;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.RollbackConfigurationType;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.AbstractRepository;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.LockAwareAggregate;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.VersionedAggregateIdentifier;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.monitoring.MessageMonitor;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.ObjectMapper;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.constraints.AxonConstraintHandlerBundle;
import io.sapl.axon.constraints.ConstraintHandlerService;
import io.sapl.axon.utilities.AuthorizationSubscriptionBuilderService;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.NoArgsConstructor;
import reactor.core.publisher.Flux;


/**
 * Test class validating the {@link SaplCommandBus}.
 *
 * template from Axon Framework
 */
public class SaplCommandBusTests {

	private static final String AGGREGATEIDENTIFIER = "id";
	
	private SaplCommandBus testSubject;
	
	private PolicyDecisionPoint pdp;
	private CommandPolicyEnforcementPoint pep;
	private final ObjectMapper mapper = new ObjectMapper();
	
    @BeforeEach
    void setUp() {
		var logger = (Logger) LoggerFactory.getLogger("ROOT");
		logger.setLevel(Level.ERROR);
    	pdp = mock(PolicyDecisionPoint.class);
		when(pdp.decide(any(AuthorizationSubscription.class))).thenReturn(Flux.just(AuthorizationDecision.PERMIT));
		var authSubscriptionService = new AuthorizationSubscriptionBuilderService(mapper);
		var constraintHandlerService = mock(ConstraintHandlerService.class);

		when(constraintHandlerService.createCommandBundle(any(),any(),any(),any(), any(), any())).thenReturn(new AxonConstraintHandlerBundle<>());

		pep = new CommandPolicyEnforcementPoint(pdp, authSubscriptionService, constraintHandlerService, null);
		
    	testSubject = SaplCommandBus.builder().policyEnforcementPoint(pep).build();
    }

	@AfterEach
	void tearDown() {
		while (CurrentUnitOfWork.isStarted()) {
			CurrentUnitOfWork.get().rollback();
		}
	}

    @Test
    void testDispatchCommandHandlerSubscribed() {
    	var handler = new StubCommandHandler();
    	setHandlerProperties(handler, message -> message);
        testSubject.subscribe(String.class.getName(), handler);
		testSubject.dispatch(asCommandMessage("Say hi!"),
				(CommandCallback<String, CommandMessage<String>>) (command, commandResultMessage) -> {
					if (commandResultMessage.isExceptional()) {
						commandResultMessage.optionalExceptionResult().ifPresent(Throwable::printStackTrace);
						fail("Did not expect exception");
					}
					assertEquals("Say hi!", commandResultMessage.getPayload().getPayload());
				});
    }

    @Test
    void testDispatchCommandImplicitUnitOfWorkIsCommittedOnReturnValue() {
    	var invocationCounter = new AtomicInteger(0);
    	final AtomicReference<UnitOfWork<?>> unitOfWork = new AtomicReference<>();
        var handler = new StubCommandHandler();
		setHandlerProperties(handler, command -> {
			unitOfWork.set(CurrentUnitOfWork.get());
			assertTrue(CurrentUnitOfWork.isStarted());
			assertNotNull(CurrentUnitOfWork.get());
			return command;
		});

		testSubject.subscribe(String.class.getName(), handler);
		testSubject.dispatch(asCommandMessage("Say hi!"),
				(CommandCallback<String, CommandMessage<String>>) (commandMessage, commandResultMessage) -> {
					invocationCounter.incrementAndGet();
					if (commandResultMessage.isExceptional()) {
						commandResultMessage.optionalExceptionResult().ifPresent(Throwable::printStackTrace);
						fail("Did not expect exception");
					}
					assertEquals("Say hi!", commandResultMessage.getPayload().getPayload());
				});
       
        await(invocationCounter);
        assertFalse(CurrentUnitOfWork.isStarted());
        assertFalse(unitOfWork.get().isRolledBack());       
        assertFalse(unitOfWork.get().isActive());
        
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }
    
	@Test
    void testDispatchCommandImplicitUnitOfWorkIsCommittedOnReturnValueWithExecutor() {
		var invocationCounter = new AtomicInteger(0);
		var executorService = Executors.newFixedThreadPool(1);
		testSubject = SaplCommandBus.builder().executor(executorService).policyEnforcementPoint(pep).build();

		final AtomicReference<UnitOfWork<?>> unitOfWork = new AtomicReference<>();
		var handler = new StubCommandHandler();
		setHandlerProperties(handler, command -> {
			unitOfWork.set(CurrentUnitOfWork.get());
			assertTrue(CurrentUnitOfWork.isStarted());
			assertNotNull(CurrentUnitOfWork.get());
			return command;
		});
       
		testSubject.subscribe(String.class.getName(), handler);
		testSubject.dispatch(asCommandMessage("Say hi!"),
				(CommandCallback<String, CommandMessage<String>>) (commandMessage, commandResultMessage) -> {
					invocationCounter.incrementAndGet();
					if (commandResultMessage.isExceptional()) {
						commandResultMessage.optionalExceptionResult().ifPresent(Throwable::printStackTrace);
						fail("Did not expect exception");
					}
					assertEquals("Say hi!", commandResultMessage.getPayload().getPayload());
				});
       
        await(invocationCounter);
        assertFalse(CurrentUnitOfWork.isStarted());
        assertFalse(unitOfWork.get().isRolledBack());       
        assertFalse(unitOfWork.get().isActive());
        
        executorService.shutdown();
        
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

	@Test
	@SuppressWarnings("unchecked")
	void testFireAndForgetUsesDefaultCallback() {
		CommandCallback<Object, Object> mockCallback = mock(CommandCallback.class);
		testSubject = SaplCommandBus.builder().defaultCommandCallback(mockCallback).build();

		CommandMessage<Object> command = asCommandMessage("command");
		testSubject.dispatch(command, NoOpCallback.INSTANCE);
		verify(mockCallback, never()).onResult(any(), any());

		testSubject.dispatch(command);
		verify(mockCallback).onResult(eq(command), any());
		
		verify(pdp, never()).decide(any(AuthorizationSubscription.class));
	}

    @Test
    void testDispatchCommandImplicitUnitOfWorkIsRolledBackOnException() {
        final AtomicReference<UnitOfWork<?>> unitOfWork = new AtomicReference<>();
        var handler = new StubCommandHandler();
        setHandlerProperties(handler, command -> {
			unitOfWork.set(CurrentUnitOfWork.get());
			assertTrue(CurrentUnitOfWork.isStarted());
			assertNotNull(CurrentUnitOfWork.get());
			throw new RuntimeException();
		});
		var invocationCounter = new AtomicInteger(0);
		testSubject.subscribe(String.class.getName(), handler);
		testSubject.dispatch(asCommandMessage("Say hi!"), (commandMessage, commandResultMessage) -> {
			invocationCounter.incrementAndGet();
			if (commandResultMessage.isExceptional()) {
				Throwable cause = commandResultMessage.exceptionResult();
				assertEquals(RuntimeException.class, cause.getClass());
			} else {
				fail("Expected exception");
			}
		});
        
        await(invocationCounter);
        
        assertFalse(CurrentUnitOfWork.isStarted());
        assertTrue(unitOfWork.get().isRolledBack());
        
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

	@Test
	void testDispatchCommandUnitOfWorkIsCommittedOnCheckedException() {
		final AtomicReference<UnitOfWork<?>> unitOfWork = new AtomicReference<>();
		var handler = new StubCommandHandler();
		setHandlerProperties(handler, command -> {
			unitOfWork.set(CurrentUnitOfWork.get());
			unitOfWork.set(CurrentUnitOfWork.get());
			throw new Exception();
		});
		testSubject.subscribe(String.class.getName(), handler);

		testSubject.setRollbackConfiguration(RollbackConfigurationType.UNCHECKED_EXCEPTIONS);
		var invocationCounter = new AtomicInteger(0);
		testSubject.dispatch(asCommandMessage("Say hi!"), (commandMessage, commandResultMessage) -> {
			invocationCounter.incrementAndGet();
			if (commandResultMessage.isExceptional()) {
				var cause = commandResultMessage.exceptionResult();
				assertEquals(Exception.class, cause.getClass());
			} else {
				fail("Expected exception");
			}
		});

		await(invocationCounter);
		assertFalse(unitOfWork.get().isActive());
		assertFalse(unitOfWork.get().isRolledBack());
		
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
	}

	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void testDispatchCommandNoHandlerSubscribed() {
		CommandMessage<Object> command = asCommandMessage("test");
		CommandCallback callback = mock(CommandCallback.class);
		testSubject.dispatch(command, callback);
		ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor = ArgumentCaptor
				.forClass(CommandResultMessage.class);
		verify(callback).onResult(eq(command), commandResultMessageCaptor.capture());
		assertTrue(commandResultMessageCaptor.getValue().isExceptional());
		assertEquals(NoHandlerForCommandException.class,
				commandResultMessageCaptor.getValue().exceptionResult().getClass());
	}

	
	@Test
	@SuppressWarnings({"unchecked", "rawtypes"})
	void testDispatchCommandHandlerUnsubscribed() {
		var commandHandler = new StubCommandHandler();
		setHandlerProperties(commandHandler, message -> message);
		Registration subscription = testSubject.subscribe(String.class.getName(), commandHandler);
		subscription.close();
		CommandMessage<Object> command = asCommandMessage("Say hi!");
		CommandCallback callback = mock(CommandCallback.class);
		testSubject.dispatch(command, callback);
		ArgumentCaptor<CommandResultMessage> commandResultMessageCaptor = ArgumentCaptor
				.forClass(CommandResultMessage.class);
		verify(callback).onResult(eq(command), commandResultMessageCaptor.capture());
		assertTrue(commandResultMessageCaptor.getValue().isExceptional());
		assertEquals(NoHandlerForCommandException.class,
		         commandResultMessageCaptor.getValue().exceptionResult().getClass());
		
		verify(pdp, never()).decide(any(AuthorizationSubscription.class));
    }

	@Test
	@SuppressWarnings("unchecked")
	void testDispatchCommandNoHandlerSubscribedCallsMonitorCallbackIgnored() throws InterruptedException {
		final CountDownLatch countDownLatch = new CountDownLatch(1);
		MessageMonitor<? super CommandMessage<?>> messageMonitor = (message) -> new MessageMonitor.MonitorCallback() {
			@Override
			public void reportSuccess() {
				fail("Expected #reportFailure");
			}

			@Override
			public void reportFailure(Throwable cause) {
				countDownLatch.countDown();
			}

			@Override
			public void reportIgnored() {
				fail("Expected #reportFailure");
			}
		};

		testSubject = SaplCommandBus.builder().messageMonitor(messageMonitor).build();

		try {
			testSubject.dispatch(asCommandMessage("test"), mock(CommandCallback.class));
		} catch (NoHandlerForCommandException expected) {
			// ignore
		}

		assertTrue(countDownLatch.await(10, TimeUnit.SECONDS));
		
		verify(pdp, never()).decide(any(AuthorizationSubscription.class));
	}
    

    @Test
    @SuppressWarnings({"unchecked"})
    void testDispatchInterceptorCommandHandledSuccessfully() {
		var commandHandler = new StubCommandHandler();
		setHandlerProperties(commandHandler, command -> "Hi there!");
    	
		// DispatchInterceptor
    	MessageDispatchInterceptor<CommandMessage<?>> mockDispatchInterceptor1 = mock(MessageDispatchInterceptor.class);
        final MessageDispatchInterceptor<CommandMessage<?>> mockDispatchInterceptor2 = mock(MessageDispatchInterceptor.class);
		when(mockDispatchInterceptor1.handle(isA(CommandMessage.class)))
				.thenAnswer(invocation -> invocation.getArguments()[0]);
		when(mockDispatchInterceptor2.handle(isA(CommandMessage.class)))
				.thenAnswer(invocation -> invocation.getArguments()[0]);
        var dispatchInterceptorRegistration1 = testSubject.registerDispatchInterceptor(mockDispatchInterceptor1);
        var dispatchInterceptorRegistration2 = testSubject.registerDispatchInterceptor(mockDispatchInterceptor2);
        
		testSubject.subscribe(String.class.getName(), commandHandler);
		var invocationCounter = new AtomicInteger(0);
		testSubject.dispatch(asCommandMessage("Hi there!"), (commandMessage, commandResultMessage) -> {
			invocationCounter.incrementAndGet();
			if (commandResultMessage.isExceptional()) {
				Throwable cause = commandResultMessage.exceptionResult();
				throw new RuntimeException("Unexpected exception", cause);
			}
			assertEquals("Hi there!", commandResultMessage.getPayload());
		});
		
		await(invocationCounter);
        
        InOrder inOrder = inOrder(mockDispatchInterceptor1, mockDispatchInterceptor2);
        inOrder.verify(mockDispatchInterceptor1).handle(isA(CommandMessage.class));
        inOrder.verify(mockDispatchInterceptor2).handle(isA(CommandMessage.class));
		
		// test close Registration
		dispatchInterceptorRegistration1.close();
		dispatchInterceptorRegistration2.close();
		
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }
    

    @Test
    @SuppressWarnings({"unchecked"})
    void testInterceptorChainCommandHandledSuccessfully() throws Exception {
    	var commandHandler = new StubCommandHandler();
		setHandlerProperties(commandHandler, command -> "Hi there!");

        // HandlerInterceptor
    	MessageHandlerInterceptor<CommandMessage<?>> mockHandlerInterceptor1 = mock(MessageHandlerInterceptor.class);
        final MessageHandlerInterceptor<CommandMessage<?>> mockHandlerInterceptor2 = mock(MessageHandlerInterceptor.class);
        when(mockHandlerInterceptor1.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> mockHandlerInterceptor2.handle(
                        (UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0],
                        (InterceptorChain) invocation.getArguments()[1]));
        when(mockHandlerInterceptor2.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> commandHandler
                        .handle(((UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0]).getMessage()));
        var handlerInterceptorRegistration1 = testSubject.registerHandlerInterceptor(mockHandlerInterceptor1);
        var handlerInterceptorRegistration2 = testSubject.registerHandlerInterceptor(mockHandlerInterceptor2);

        
		testSubject.subscribe(String.class.getName(), commandHandler);
		var invocationCounter = new AtomicInteger(0);
		testSubject.dispatch(asCommandMessage("Hi there!"), (commandMessage, commandResultMessage) -> {
			invocationCounter.incrementAndGet();
			if (commandResultMessage.isExceptional()) {
				Throwable cause = commandResultMessage.exceptionResult();
				throw new RuntimeException("Unexpected exception", cause);
			}
			assertEquals("Hi there!", commandResultMessage.getPayload());
		});
		
        await(invocationCounter);
        
        InOrder inOrder = inOrder(mockHandlerInterceptor1, mockHandlerInterceptor2);
        inOrder.verify(mockHandlerInterceptor1).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		inOrder.verify(mockHandlerInterceptor2).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		
		// test close Registration
		handlerInterceptorRegistration1.close();
		handlerInterceptorRegistration2.close();
		
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInterceptorChainCommandHandlerThrowsException() throws Exception {
    	var commandHandler = new StubCommandHandler();
		setHandlerProperties(commandHandler, command -> {
			throw new RuntimeException("Faking failed command handling");
		});
    	
		testSubject.subscribe(String.class.getName(), commandHandler);
		
		 // HandlerInterceptor
    	MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor1 = mock(MessageHandlerInterceptor.class);
        final MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor2 = mock(MessageHandlerInterceptor.class);
		
		when(mockInterceptor1.handle(isA(UnitOfWork.class), isA(InterceptorChain.class))).thenAnswer(
				invocation -> mockInterceptor2.handle((UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0],
						(InterceptorChain) invocation.getArguments()[1]));
		when(mockInterceptor2.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
				.thenAnswer(invocation -> commandHandler
						.handle(((UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0]).getMessage()));

        testSubject.registerHandlerInterceptor(mockInterceptor1);
        testSubject.registerHandlerInterceptor(mockInterceptor2);

		testSubject.subscribe(String.class.getName(), commandHandler);
		var invocationCounter = new AtomicInteger(0);
		testSubject.dispatch(asCommandMessage("Hi there!"), (commandMessage, commandResultMessage) -> {
			invocationCounter.incrementAndGet();
			if (commandResultMessage.isExceptional()) {
				Throwable cause = commandResultMessage.exceptionResult();
				assertEquals("Faking failed command handling", cause.getMessage());
			} else {
				fail("Expected exception to be thrown");
			}
		});
		
        await(invocationCounter);
        
		InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2);
		inOrder.verify(mockInterceptor1).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		inOrder.verify(mockInterceptor2).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    @SuppressWarnings("unchecked")
    void testInterceptorChainInterceptorThrowsException() throws Exception {
    	 var commandHandler = new StubCommandHandler();
         setHandlerProperties(commandHandler, command -> "Hi there!");
         testSubject.subscribe(String.class.getName(), commandHandler);
    	
    	 // HandlerInterceptor
    	MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor1 = mock(MessageHandlerInterceptor.class,
				"stubName");
		final MessageHandlerInterceptor<CommandMessage<?>> mockInterceptor2 = mock(MessageHandlerInterceptor.class);
		when(mockInterceptor1.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
				.thenAnswer(invocation -> ((InterceptorChain) invocation.getArguments()[1]).proceed());
        testSubject.registerHandlerInterceptor(mockInterceptor1);
        testSubject.registerHandlerInterceptor(mockInterceptor2);
       
        
		RuntimeException someException = new RuntimeException("Mocking");
		doThrow(someException).when(mockInterceptor2).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		var invocationCounter = new AtomicInteger(0);
		testSubject.dispatch(asCommandMessage("Hi there!"), (commandMessage, commandResultMessage) -> {
			invocationCounter.incrementAndGet();
			if (commandResultMessage.isExceptional()) {
				Throwable cause = commandResultMessage.exceptionResult();
				assertEquals("Mocking", cause.getMessage());
			} else {
				fail("Expected exception to be propagated");
			}
		});
		
        await(invocationCounter);
        
		InOrder inOrder = inOrder(mockInterceptor1, mockInterceptor2);
		inOrder.verify(mockInterceptor1).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		inOrder.verify(mockInterceptor2).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		
		verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    void testCommandReplyMessageCorrelationData() {
    	var commandHandler = new StubCommandHandler();
        setHandlerProperties(commandHandler, command -> command.getPayload().toString());
        
        testSubject.subscribe(String.class.getName(), commandHandler);

		testSubject.registerHandlerInterceptor(new CorrelationDataInterceptor<>(new MessageOriginProvider()));
		CommandMessage<String> command = asCommandMessage("Hi");
		testSubject.dispatch(command, (CommandCallback<String, String>) (commandMessage, commandResultMessage) -> {
			if (commandResultMessage.isExceptional()) {
				fail("Command execution should be successful");
			}
			assertEquals(command.getIdentifier(), commandResultMessage.getMetaData().get("traceId"));
			assertEquals(command.getIdentifier(), commandResultMessage.getMetaData().get("correlationId"));
			assertEquals(command.getPayload(), commandResultMessage.getPayload());
			verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
		});
    }

    @Test
    void testDuplicateCommandHandlerResolverSetsTheExpectedHandler() {
    	DuplicateCommandHandlerResolver testDuplicateCommandHandlerResolver = DuplicateCommandHandlerResolution.silentOverride();
		testSubject = SaplCommandBus.builder().policyEnforcementPoint(pep)
				.duplicateCommandHandlerResolver(testDuplicateCommandHandlerResolver).build();

        var initialHandler = new StubCommandHandler();
        var calledInitial = new AtomicBoolean(false);
        setHandlerProperties(initialHandler, message -> {
        	calledInitial.set(true);
        	return message;
        });
        var duplicateHandler = new StubCommandHandler();
        var calledDuplicate = new AtomicBoolean(false);
        setHandlerProperties(duplicateHandler, message -> {
        	calledDuplicate.set(true);
        	return message;	
        });
        CommandMessage<Object> testMessage = asCommandMessage("Say hi!");

        // Subscribe the initial handler
        testSubject.subscribe(String.class.getName(), initialHandler);
        // Then, subscribe a duplicate
        testSubject.subscribe(String.class.getName(), duplicateHandler);

        var invocationCounter = new AtomicInteger(0);
        // And after dispatching a test command, it should be handled by the initial handler
        testSubject.dispatch(testMessage, (commandMessage, commandResultMessage) -> invocationCounter.incrementAndGet());

        await(invocationCounter);
        assertFalse(calledInitial.get());
        assertTrue(calledDuplicate.get());
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }
    
    @Test
    void testBuilderWithRollbackConfigurationAndTransactionManagerAndDuplicateCommandHandlerResolverSuccessful() {
    	var rollbackConfiguration = mock(RollbackConfiguration.class);
    	var transactionManager = mock(TransactionManager.class);
    	var duplicateCommandHandlerResolver = mock(DuplicateCommandHandlerResolver.class);
		SaplCommandBus.builder().rollbackConfiguration(rollbackConfiguration)
				.transactionManager(transactionManager).duplicateCommandHandlerResolver(duplicateCommandHandlerResolver).build();
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void testReadHandlerNoAggregateException_then_readAggregateType() {
		var model = mock(AggregateModel.class);
		when(model.entityClass()).thenReturn(StubAggregate.class);
		var eventStore = mock(EventStore.class);
		var repoMock = spy(EventSourcingRepository.builder(StubAggregate.class).aggregateModel(model).eventStore(eventStore).build());
		doThrow(new RuntimeException("No aggregate!!")).when(repoMock).load(anyString());
		
		var handler = new StubCommandHandler();
		setHandlerProperties(handler, message -> message);
		handler.repository = repoMock;
		
		testSubject.subscribe(String.class.getName(), handler);
		var invocationCounter = new AtomicInteger(0);
		CommandMessage<Object> testMessage = asCommandMessage("Say hi!");
		testSubject.dispatch(testMessage, (commandMessage, commandResultMessage) -> invocationCounter.incrementAndGet());

        await(invocationCounter);
        
        verify(pdp, times(1)).decide(any(AuthorizationSubscription.class));
    }

    @Test
    void testReadHandlerException() {
		Throwable error = assertThrows(Throwable.class,
				() -> testSubject.subscribe(String.class.getName(), command -> command));
		assertEquals(UnsupportedOperationException.class, error.getClass());
    }
    
    @Test
	void testShutdownUsingExecutorService() {
		var executorService = mock(ExecutorService.class);
		testSubject = SaplCommandBus.builder().executor(executorService).build();
		testSubject.shutdown();
		verify(executorService).shutdown();
	}
    
    @Test
	void testShutdownUsingExecutorServicethen_Excepton() throws Exception {
		var executorService = mock(ExecutorService.class);
		when(executorService.awaitTermination(anyLong(), any())).thenThrow(new InterruptedException());
		testSubject = SaplCommandBus.builder().executor(executorService).build();
		testSubject.shutdown();
		verify(executorService).shutdown();
	}
    
	@Test
	void testShutdownUsingExecutor() {
		var executor = mock(Executor.class);
		testSubject = SaplCommandBus.builder().executor(executor).build();
		testSubject.shutdown();
		verify(executor, never()).execute(any(Runnable.class));
	}

	private Repository<?> creatRepositoryMock() {
		AggregateWrapper aggregate = new AggregateWrapper();
		Lock lockMock = mock(Lock.class);
		var lockAwareAggregate = spy(new LockAwareAggregate<>(aggregate, lockMock));
		when(lockAwareAggregate.getWrappedAggregate()).thenReturn(aggregate);
		TestRepository<StubAggregate> repoSpy = spy(TestRepository.builder(StubAggregate.class).build());
		
		doReturn(lockAwareAggregate).when(repoSpy).load(anyString());
		return repoSpy;
	}
	
	private CommandTargetResolver createCommandTargetResolverMock() {
		var resolverMock = mock(CommandTargetResolver.class);
		when(resolverMock.resolveTarget(any())).thenReturn(new VersionedAggregateIdentifier(AGGREGATEIDENTIFIER, 1L));
		return resolverMock;
	}

	private List<MessageHandler<CommandMessage<?>>> creatHandler(MessageHandler<CommandMessage<?>> delegate)
			throws NoSuchMethodException, SecurityException {

		Method m = StubAggregate.class.getDeclaredMethod("method", String.class);
		var methodDelegate = new AnnotatedMessageHandlingMember<StubAggregate>(m, CommandMessage.class, String.class,
				new DefaultParameterResolverFactory());
		List<MessageHandler<CommandMessage<?>>> handlers = List
				.of(new AggregateCommandHandler(methodDelegate, delegate));
		return handlers;
	}

	private void setHandlerProperties(StubCommandHandler handler, MessageHandler<CommandMessage<?>> delegate) {
		handler.repository = creatRepositoryMock();
        handler.commandTargetResolver = createCommandTargetResolverMock();
        try {
        	handler.handlers = creatHandler(delegate);
        } catch (Exception e) {
        	fail("Did not expect exception");
        }
	}
	
	private void await(AtomicInteger invocationCounter) {
		assertWithin(2000, TimeUnit.MILLISECONDS, () -> assertEquals(1, invocationCounter.get()));
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
    
    private static class AggregateWrapper extends AnnotatedAggregate<StubAggregate> {
    	AggregateWrapper() {
			super(new StubAggregate(), null, null);
		}
	}
    
    @Aggregate
    @NoArgsConstructor
    private static class StubAggregate {
		@AggregateIdentifier
    	String id = AGGREGATEIDENTIFIER;
		
		@PreEnforce
		@CommandHandler
		public void method(String param) {
			assertNotNull(param);
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
    
	 private static class TestRepository<T> extends AbstractRepository<T, org.axonframework.modelling.command.Aggregate<T>> {
		protected TestRepository(Builder<T> builder) {
			super(builder);
		}
	
		@Override
		protected org.axonframework.modelling.command.Aggregate<T> doCreateNew(Callable<T> factoryMethod) {
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
		
		public static <T> TestBuilder<T> builder(Class<T> aggregateType) {
			return new TestBuilder<>(aggregateType);
		}
		
		static class TestBuilder<T> extends Builder<T> {

			protected TestBuilder(Class<T> aggregateType) {
				super(aggregateType);
			}
			
			public TestRepository<T> build() {
				return new TestRepository<>(this);
			}
		}
    	
    }
}
