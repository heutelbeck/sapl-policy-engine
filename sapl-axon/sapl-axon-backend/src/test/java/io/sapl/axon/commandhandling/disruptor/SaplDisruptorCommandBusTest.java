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

import static io.sapl.axon.commandhandling.disruptor.utils.AssertUtils.assertWithin;
import static java.util.Collections.singletonList;
import static org.axonframework.commandhandling.GenericCommandMessage.asCommandMessage;
import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.axonframework.commandhandling.CommandCallback;
import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.CommandResultMessage;
import org.axonframework.commandhandling.DuplicateCommandHandlerResolver;
import org.axonframework.commandhandling.NoHandlerForCommandException;
import org.axonframework.common.AxonConfigurationException;
import org.axonframework.common.Registration;
import org.axonframework.common.caching.NoCache;
import org.axonframework.common.transaction.TransactionManager;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.disruptor.commandhandling.AggregateStateCorruptedException;
import org.axonframework.eventhandling.DomainEventMessage;
import org.axonframework.eventhandling.EventMessage;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventhandling.TrackingEventStream;
import org.axonframework.eventhandling.TrackingToken;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.InterceptorChain;
import org.axonframework.messaging.MessageDispatchInterceptor;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MessageHandlerInterceptor;
import org.axonframework.messaging.annotation.AnnotatedMessageHandlingMember;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.DefaultParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.MessageHandlerInvocationException;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.RollbackConfiguration;
import org.axonframework.messaging.unitofwork.UnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.AnnotationCommandTargetResolver;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.TargetAggregateIdentifier;
import org.axonframework.modelling.command.VersionedAggregateIdentifier;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.saga.SagaScopeDescriptor;
import org.axonframework.monitoring.MessageMonitor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.SleepingWaitStrategy;
import com.lmax.disruptor.dsl.ProducerType;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.sapl.axon.commandhandling.CommandPolicyEnforcementPoint;
import io.sapl.axon.commandhandling.disruptor.utils.MockException;
import io.sapl.axon.commandhandling.disruptor.utils.SomethingDoneEvent;
import io.sapl.axon.utilities.CheckedFunction;
import io.sapl.spring.method.metadata.PreEnforce;

public class SaplDisruptorCommandBusTest {
	private static final int COMMAND_COUNT = 100; // origin: 100 * 1000;
    private static final String COMMAND_RETURN_VALUE = "dummyVal";

    private static AtomicInteger messageHandlingCounter;

    private StubHandler stubHandler;
    private InMemoryEventStore eventStore;
    private String aggregateIdentifier;
    private ParameterResolverFactory parameterResolverFactory;
    private CommandPolicyEnforcementPoint pep;

    
    private SaplDisruptorCommandBus testSubject;
	
    @BeforeEach
    void setUp() {
        var logger = (Logger) LoggerFactory.getLogger("ROOT");
        logger.setLevel(Level.ERROR);
        aggregateIdentifier = UUID.randomUUID().toString();
        stubHandler = new StubHandler();
        eventStore = new InMemoryEventStore();
        eventStore.publish(singletonList(
                new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 0, new StubDomainEvent())
        ));
        parameterResolverFactory = spy(ClasspathParameterResolverFactory.forClass(SaplDisruptorCommandBusTest.class));
        messageHandlingCounter = new AtomicInteger(0);
        
        pep = createPolicyEnforcementPointWithPermittingPdp();
        
        testSubject = SaplDisruptorCommandBus.builderSapl()
        		.policyEnforcementPoint(pep)
        		.coolingDownPeriod(1000)
                .commandTargetResolver(AnnotationCommandTargetResolver.builder().build())
                .build();
    }

    @AfterEach
    void tearDown() {
        testSubject.stop();
    }
  
    @Test
    void when_subscribe_then_successfully() {
        var registration = testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        
        assertNotNull(registration);
        
        assertDoesNotThrow(registration::cancel);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void when_subscribe_with_duplicateCommandHandlerResolver_then_successfully() {
    	var duplicateCommandHandlerResolverMock = mock(DuplicateCommandHandlerResolver.class);
      
        testSubject = SaplDisruptorCommandBus.builderSapl()
        		.policyEnforcementPoint(pep)
                .duplicateCommandHandlerResolver(duplicateCommandHandlerResolverMock)
                .build();
        
        // Subscribe the initial handler
        var registration1 = testSubject.subscribe(String.class.getName(), mock(MessageHandler.class));
        // Then, subscribe a duplicate
        var registration2 = testSubject.subscribe(String.class.getName(), mock(MessageHandler.class));

        verify(duplicateCommandHandlerResolverMock, times(1)).resolve(any(), any(), any());
        
        assertDoesNotThrow(registration1::cancel);
        assertDoesNotThrow(registration2::cancel);
    }
    
    @Test
    void when_createRepository_then_successfully() {
		var snapshotTriggerDefinition = mock(SnapshotTriggerDefinition.class);
		var handlerDefinition = mock(HandlerDefinition.class);
		var repositoryProvider = mock(RepositoryProvider.class);
		var repository = testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class),
				snapshotTriggerDefinition, parameterResolverFactory, handlerDefinition, repositoryProvider);

		assertNotNull(repository);
    }
    
    @Test
    @SuppressWarnings("unchecked")
    void when_registerDispatchInterceptor_then_successfully() {
    	var testHandler =  mock(StubHandler.class);
       
		// DispatchInterceptor
		MessageDispatchInterceptor<CommandMessage<?>> mockDispatchInterceptor1 = mock(MessageDispatchInterceptor.class);
        final MessageDispatchInterceptor<CommandMessage<?>> mockDispatchInterceptor2 = mock(MessageDispatchInterceptor.class);
		when(mockDispatchInterceptor1.handle(isA(CommandMessage.class)))
				.thenAnswer(invocation -> invocation.getArguments()[0]);
		when(mockDispatchInterceptor2.handle(isA(CommandMessage.class)))
				.thenAnswer(invocation -> invocation.getArguments()[0]);

		var dispatchInterceptorRegistration1 = testSubject.registerDispatchInterceptor(mockDispatchInterceptor1);
        var dispatchInterceptorRegistration2 = testSubject.registerDispatchInterceptor(mockDispatchInterceptor2);

        testSubject.subscribe(String.class.getName(), testHandler);
        testSubject.dispatch(asCommandMessage("Hi there!"));
        
        InOrder inOrder = inOrder(mockDispatchInterceptor1, mockDispatchInterceptor2);
        inOrder.verify(mockDispatchInterceptor1).handle(isA(CommandMessage.class));
        inOrder.verify(mockDispatchInterceptor2).handle(isA(CommandMessage.class));
		
		// test close Registration
        assertDoesNotThrow(dispatchInterceptorRegistration1::close);
        assertDoesNotThrow(dispatchInterceptorRegistration2::close);
    	
    }
   
	@Test
	@SuppressWarnings({ "rawtypes", "unchecked" })
    void when_registerHandlerInterceptor_then_successfully() throws Exception {
    	var testHandler =  mock(StubHandler.class);
        when(testHandler.canHandle(any())).thenReturn(true);
        when(testHandler.handle(any(CommandMessage.class))).thenReturn(COMMAND_RETURN_VALUE);
		testHandler.repository = creatRepositoryMock();
        testHandler.handlers = createHandlers(String.class);
        testHandler.commandTargetResolver = createCommandTargetResolverMock();
    	
        try {
			when(pep.preEnforceCommandDisruptor(any(), any())).thenAnswer(invocation -> {
				var msg = (CommandMessage) invocation.getArgument(0);
				var handleCommand = (CheckedFunction<CommandMessage<?>, Object>)invocation.getArgument(1);
				return handleCommand.apply(msg);
			});
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
        
		testSubject = SaplDisruptorCommandBus.builderSapl()
				.policyEnforcementPoint(pep)
				.commandTargetResolver(testHandler.commandTargetResolver)
				.build();
             
        // HandlerInterceptor
    	MessageHandlerInterceptor<CommandMessage<?>> mockHandlerInterceptor1 = mock(MessageHandlerInterceptor.class);
        final MessageHandlerInterceptor<CommandMessage<?>> mockHandlerInterceptor2 = mock(MessageHandlerInterceptor.class);
        when(mockHandlerInterceptor1.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> mockHandlerInterceptor2.handle(
                        (UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0],
                        (InterceptorChain) invocation.getArguments()[1]));
        when(mockHandlerInterceptor2.handle(isA(UnitOfWork.class), isA(InterceptorChain.class)))
                .thenAnswer(invocation -> testHandler
                        .handle(((UnitOfWork<CommandMessage<?>>) invocation.getArguments()[0]).getMessage()));

        var handlerInterceptorRegistration1 = testSubject.registerHandlerInterceptor(mockHandlerInterceptor1);
        var handlerInterceptorRegistration2 = testSubject.registerHandlerInterceptor(mockHandlerInterceptor2);

        testSubject.subscribe(String.class.getName(), testHandler);
        var invocationCounter = new AtomicInteger(0);
        testSubject.dispatch(asCommandMessage("Hi there!"),
                             (commandMessage, commandResultMessage) -> {
                                invocationCounter.incrementAndGet();
                            	 if (commandResultMessage.isExceptional()) {
                                     Throwable cause = commandResultMessage.exceptionResult();
                                     throw new RuntimeException("Unexpected exception", cause);
                                 }
                                 assertEquals(COMMAND_RETURN_VALUE, commandResultMessage.getPayload());
                             });
        await(invocationCounter);

        InOrder inOrder = inOrder(mockHandlerInterceptor1, mockHandlerInterceptor2);
        inOrder.verify(mockHandlerInterceptor1).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		inOrder.verify(mockHandlerInterceptor2).handle(isA(UnitOfWork.class), isA(InterceptorChain.class));
		
		// test close Registration
		assertDoesNotThrow(handlerInterceptorRegistration1::close);
		assertDoesNotThrow(handlerInterceptorRegistration2::close);
    }
    
    @Test
    void when_stop_then_successfully() {
    	assertDoesNotThrow(() -> testSubject.stop());
    }
    
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void when_Repository_NewInstance_then_CreateAggregate() {
        eventStore.storedEvents.clear();
        CommandTargetResolver resolverMock = createCommandTargetResolverMock();
        
        try {
			when(pep.preEnforceCommandDisruptor(any(), any())).thenAnswer(invocation -> {
				var msg = (CommandMessage) invocation.getArgument(0);
				var handleCommand = (CheckedFunction<CommandMessage<?>, Object>)invocation.getArgument(1);
				return handleCommand.apply(msg);
			});
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
        
        testSubject = SaplDisruptorCommandBus.builderSapl()
        		.policyEnforcementPoint(pep)
        		.commandTargetResolver(resolverMock)
	            .bufferSize(8)
	            .producerType(ProducerType.SINGLE)
	            .waitStrategy(new SleepingWaitStrategy())
	            .invokerThreadCount(2)
	            .publisherThreadCount(3)
	            .build();
        
        
        stubHandler.handlers = createHandlers(StubCommand.class, CreateCommand.class, ErrorCommand.class);
        stubHandler.commandTargetResolver = resolverMock;
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );
        
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(ErrorCommand.class.getName(), stubHandler);
       

        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage<?> lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

        // we expect  one event from aggregate constructor
        assertEquals(1, lastEvent.getSequenceNumber());
        assertEquals(aggregateIdentifier, lastEvent.getAggregateIdentifier());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void when_Repository_loadOrCreate_then_AggregateWithPreviousAggregate() {
        eventStore.storedEvents.clear();
        CommandTargetResolver resolverMock = createCommandTargetResolverMock();
        
        try {
			when(pep.preEnforceCommandDisruptor(any(), any())).thenAnswer(invocation -> {
				var msg = (CommandMessage) invocation.getArgument(0);
				var handleCommand = (CheckedFunction<CommandMessage<?>, Object>)invocation.getArgument(1);
				return handleCommand.apply(msg);
			});
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
        
        testSubject = SaplDisruptorCommandBus.builderSapl()
        		.policyEnforcementPoint(pep)
        		.commandTargetResolver(resolverMock)
                .bufferSize(8)
                .producerType(ProducerType.SINGLE)
                .waitStrategy(new SleepingWaitStrategy())
                .invokerThreadCount(2)
                .publisherThreadCount(3)
                .build();

        stubHandler.handlers = createHandlers(CreateCommand.class, CreateOrUpdateCommand.class);
        stubHandler.commandTargetResolver = resolverMock;
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateOrUpdateCommand.class.getName(), stubHandler);

        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new CreateOrUpdateCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage<?> lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

        // we expect 2 events, 1 from aggregate constructor, 1 from doSomething method invocation
        assertEquals(2, lastEvent.getSequenceNumber());
        assertEquals(aggregateIdentifier, lastEvent.getAggregateIdentifier());
    }


    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void when_Repository_loadOrCreate_then_AggregateWithoutPreviousAggregate() {
        eventStore.storedEvents.clear();
        
        CommandTargetResolver resolverMock = createCommandTargetResolverMock();
        
		try {
			when(pep.preEnforceCommandDisruptor(any(), any())).thenAnswer(invocation -> {
				var msg = (CommandMessage) invocation.getArgument(0);
				var handleCommand = (CheckedFunction<CommandMessage<?>, Object>) invocation.getArgument(1);
				return handleCommand.apply(msg);
			});
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
        
        testSubject = SaplDisruptorCommandBus.builderSapl()
        		.policyEnforcementPoint(pep)
        		.commandTargetResolver(resolverMock)
                .bufferSize(8)
                .producerType(ProducerType.SINGLE)
                .waitStrategy(new SleepingWaitStrategy())
                .invokerThreadCount(2)
                .publisherThreadCount(3)
                .build();

        stubHandler.handlers = createHandlers(CreateOrUpdateCommand.class);
        stubHandler.commandTargetResolver = resolverMock;
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );
        
        testSubject.subscribe(CreateOrUpdateCommand.class.getName(), stubHandler);
       
        testSubject.dispatch(asCommandMessage(new CreateOrUpdateCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage<?> lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

        // we expect  one event from aggregate constructor
        assertEquals(1, lastEvent.getSequenceNumber());
        assertEquals(aggregateIdentifier, lastEvent.getAggregateIdentifier());
    }
    
    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void when_Repository_loadVersion_then_AggregateWithoutPreviousAggregate() {
        eventStore.storedEvents.clear();
        
        CommandTargetResolver resolverMock = createCommandTargetResolverMock();
        
		try {
			when(pep.preEnforceCommandDisruptor(any(), any())).thenAnswer(invocation -> {
				var msg = (CommandMessage) invocation.getArgument(0);
				var handleCommand = (CheckedFunction<CommandMessage<?>, Object>) invocation.getArgument(1);
				return handleCommand.apply(msg);
			});
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
        
        testSubject = SaplDisruptorCommandBus.builderSapl()
        		.policyEnforcementPoint(pep)
        		.commandTargetResolver(resolverMock)
	            .bufferSize(8)
	            .producerType(ProducerType.SINGLE)
	            .waitStrategy(new SleepingWaitStrategy())
	            .invokerThreadCount(2)
	            .publisherThreadCount(3)
	            .build();

        stubHandler.handlers = createHandlers(CreateCommand.class, LoadVersionCommand.class);
        stubHandler.commandTargetResolver = resolverMock;
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(LoadVersionCommand.class.getName(), stubHandler);
       
        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new LoadVersionCommand(aggregateIdentifier)));

        testSubject.stop();

        DomainEventMessage<?> lastEvent = eventStore.storedEvents.get(aggregateIdentifier);

        // we expect 2 events, 1 from aggregate constructor, one from doSomething method invocation
        assertEquals(2, lastEvent.getSequenceNumber());
        assertEquals(aggregateIdentifier, lastEvent.getAggregateIdentifier());
    }

    @Test
    @SuppressWarnings({ "rawtypes", "unchecked" })
    void when_MessageMonitoring_then_succuessfully() {
        eventStore.storedEvents.clear();
        final AtomicLong successCounter = new AtomicLong();
        final AtomicLong failureCounter = new AtomicLong();
        final AtomicLong ignoredCounter = new AtomicLong();

        String aggregateIdentifier2 = UUID.randomUUID().toString();
        var resolverMock = new CommandTargetResolver() {
			@Override
			public VersionedAggregateIdentifier resolveTarget(CommandMessage<?> command) {
				if (((StubCommand)command.getPayload()).aggregateIdentifier.equals(aggregateIdentifier)) {
					return new VersionedAggregateIdentifier(aggregateIdentifier, 1L);
				}
				return new VersionedAggregateIdentifier(aggregateIdentifier2, 1L);
			}
		};
        
		try {
			when(pep.preEnforceCommandDisruptor(any(), any())).thenAnswer(invocation -> {
				var msg = (CommandMessage) invocation.getArgument(0);
				var handleCommand = (CheckedFunction<CommandMessage<?>, Object>) invocation.getArgument(1);
				return handleCommand.apply(msg);
			});
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
		
        testSubject = SaplDisruptorCommandBus.builderSapl()
        		.policyEnforcementPoint(pep)
        		.commandTargetResolver(resolverMock)
                .bufferSize(8)
                .messageMonitor(msg -> new MessageMonitor.MonitorCallback() {
                     @Override
                     public void reportSuccess() {
                         successCounter.incrementAndGet();
                     }

                     @Override
                     public void reportFailure(Throwable cause) {
                         failureCounter.incrementAndGet();
                     }

                     @Override
                     public void reportIgnored() {
                         ignoredCounter.incrementAndGet();
                     }
                 })
                .build();
        
        stubHandler.handlers = createHandlers(StubCommand.class, CreateCommand.class, ErrorCommand.class);
        stubHandler.commandTargetResolver = resolverMock;
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );
        
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);
        testSubject.subscribe(CreateCommand.class.getName(), stubHandler);
        testSubject.subscribe(ErrorCommand.class.getName(), stubHandler);
        
   
        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new ErrorCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier)));

        testSubject.dispatch(asCommandMessage(new CreateCommand(aggregateIdentifier2)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier2)));
        testSubject.dispatch(asCommandMessage(new ErrorCommand(aggregateIdentifier2)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier2)));
        testSubject.dispatch(asCommandMessage(new StubCommand(aggregateIdentifier2)));

        //noinspection unchecked
        CommandCallback<Object, Object> callback = mock(CommandCallback.class);
        testSubject.dispatch(asCommandMessage(new UnknownCommand(aggregateIdentifier2)), callback);

        testSubject.stop();

        assertEquals(8, successCounter.get());
        assertEquals(3, failureCounter.get());
        assertEquals(0, ignoredCounter.get());
        //noinspection unchecked
        ArgumentCaptor<CommandResultMessage<Object>> commandResultMessageCaptor =
                ArgumentCaptor.forClass(CommandResultMessage.class);
        verify(callback).onResult(any(), commandResultMessageCaptor.capture());
        assertTrue(commandResultMessageCaptor.getValue().isExceptional());
        assertEquals(NoHandlerForCommandException.class,
                     commandResultMessageCaptor.getValue().exceptionResult().getClass());
    }

    @Test
    void when_stop_then_CommandRejectedAfterShutdown() {
    	CommandTargetResolver resolverMock = createCommandTargetResolverMock();
    	
		testSubject = SaplDisruptorCommandBus.builderSapl()
				.policyEnforcementPoint(pep)
				.commandTargetResolver(resolverMock)
				.build();
        stubHandler.handlers = createHandlers(StubCommand.class);
        stubHandler.commandTargetResolver = resolverMock;
        stubHandler.setRepository(
                testSubject.createRepository(eventStore, new GenericAggregateFactory<>(StubAggregate.class))
        );
        
        testSubject.subscribe(StubCommand.class.getName(), stubHandler);

        testSubject.stop();
        assertThrows(IllegalStateException.class, () -> testSubject.dispatch(asCommandMessage(new Object())));
    }

    @Test
    void when_Repositotry_CanResolve_then_ReturnsTrueForMatchingAggregateDescriptor() {
        testSubject = SaplDisruptorCommandBus.builderSapl().policyEnforcementPoint(pep).build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertTrue(testRepository.canResolve(new AggregateScopeDescriptor(
                StubAggregate.class.getSimpleName(), aggregateIdentifier)
        ));
    }

    @Test
    void when_Repositotry_CanResolve_then_ReturnsFalseNonAggregateScopeDescriptorImplementation() {
        testSubject = SaplDisruptorCommandBus.builderSapl().policyEnforcementPoint(pep).build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertFalse(testRepository.canResolve(new SagaScopeDescriptor("some-saga-type", aggregateIdentifier)));
    }

    @Test
    void when_Repositotry_CanResolve_then_eturnsFalseForNonMatchingAggregateType() {
    	testSubject = SaplDisruptorCommandBus.builderSapl().policyEnforcementPoint(pep).build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertFalse(testRepository.canResolve(new AggregateScopeDescriptor(
                "other-non-matching-type", aggregateIdentifier
        )));
    }

    @Test
    void when_Repositotry_Send_then_DeliversMessageAtDescribedAggregateInstance() throws Exception {
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), aggregateIdentifier);

        testSubject = SaplDisruptorCommandBus.builderSapl().policyEnforcementPoint(pep).build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);

        assertEquals(1, messageHandlingCounter.get());
    }
    
    @Test
    void when_Repositotry_Send_then_DeliversMessageAtDescribedAggregateInstanceManyInvokers() throws Exception {
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), aggregateIdentifier);

		testSubject = SaplDisruptorCommandBus.builderSapl()
				.policyEnforcementPoint(pep)
				.invokerThreadCount(2)
				.publisherThreadCount(2).build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);

        assertEquals(1, messageHandlingCounter.get());
    }
    
    @Test
    void when_Repositotry_Send_then_DeliversMessageAtDescribedAggregateInstanceNotResolve() throws Exception {
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(String.class.getSimpleName(), 1);

        testSubject = SaplDisruptorCommandBus.builderSapl().policyEnforcementPoint(pep).build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);

        assertEquals(0, messageHandlingCounter.get());
    }

    @Test
    void when_Repositotry_Send_then_ThrowsMessageHandlerInvocationExceptionIfHandleFails() {
        DeadlineMessage<FailingEvent> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new FailingEvent(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), aggregateIdentifier);

        testSubject = SaplDisruptorCommandBus.builderSapl().policyEnforcementPoint(pep).build();
        Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        assertThrows(MessageHandlerInvocationException.class, () -> testRepository.send(testMsg, testDescriptor));
    }

    @Test
    void when_Repositotry_Send_then_FailsSilentlyOnAggregateNotFoundException() throws Exception {
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), "some-other-aggregate-id");

		testSubject = SaplDisruptorCommandBus.builderSapl().build();
				
		Repository<StubAggregate> testRepository = testSubject.createRepository(
                eventStore, new GenericAggregateFactory<>(StubAggregate.class), parameterResolverFactory
        );

        testRepository.send(testMsg, testDescriptor);

        assertEquals(0, messageHandlingCounter.get());
    }

    @Test
    void when_dispatch_then_CommandIsRescheduledForCorruptAggregateState() {
        int expectedNumberOfInvocations = 1;
        AtomicInteger invocationCounter = new AtomicInteger(0);

        CommandMessage<String> testCommand = asCommandMessage("some-command");
        var testHandler =  mock(StubHandler.class);

        testHandler.repository = creatRepositoryMock();
        testHandler.handlers = createHandlers(String.class);
        testHandler.commandTargetResolver = createCommandTargetResolverMock();
        
		try {
			when(pep.preEnforceCommandDisruptor(any(), any())).thenThrow(AggregateStateCorruptedException.class)
					.thenReturn(COMMAND_RETURN_VALUE);
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
        
		testSubject = SaplDisruptorCommandBus.builderSapl()
				.policyEnforcementPoint(pep)
				.commandTargetResolver(testHandler.commandTargetResolver)
				.rescheduleCommandsOnCorruptState(true)
				.build();
        testSubject.subscribe(String.class.getName(), testHandler);

        testSubject.dispatch(testCommand, (command, result) -> {
            invocationCounter.incrementAndGet();
            assertFalse(result.isExceptional());
            assertEquals(COMMAND_RETURN_VALUE, result.getPayload());
        });
        assertWithin(2000, TimeUnit.MILLISECONDS,
                     () -> assertEquals(expectedNumberOfInvocations, invocationCounter.get()));
    }

    @Test
    void when_dispatch_then_CommandIsNotRescheduledForCorruptAggregateState() {
        int expectedNumberOfInvocations = 1;
        AtomicInteger invocationCounter = new AtomicInteger(0);

        CommandMessage<String> testCommand = asCommandMessage("some-command");
        var testHandler =  mock(StubHandler.class);

        testHandler.repository = creatRepositoryMock();
        testHandler.handlers = createHandlers(String.class);
        testHandler.commandTargetResolver = createCommandTargetResolverMock();
        
		try {
			when(pep.preEnforceCommandDisruptor(any(), any())).thenThrow(AggregateStateCorruptedException.class)
					.thenReturn(COMMAND_RETURN_VALUE);
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
        
		testSubject = SaplDisruptorCommandBus.builderSapl()
				.policyEnforcementPoint(pep)
				.commandTargetResolver(testHandler.commandTargetResolver)
				.rescheduleCommandsOnCorruptState(false)
				.build();
        testSubject.subscribe(String.class.getName(), testHandler);

        testSubject.dispatch(testCommand, (command, result) -> {
            invocationCounter.incrementAndGet();
            assertTrue(result.isExceptional());
            assertEquals(AggregateStateCorruptedException.class, result.exceptionResult().getClass());
        });
        assertWithin(2500, TimeUnit.MILLISECONDS,
                     () -> assertEquals(expectedNumberOfInvocations, invocationCounter.get()));
    }

    @Test
    void when_BuildWithZeroOrNegativeCoolingDownPeriod_then_ThrowsAxonConfigurationException() {
        SaplDisruptorCommandBus.Builder builderTestSubject = SaplDisruptorCommandBus.builderSapl();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.coolingDownPeriod(0));

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.coolingDownPeriod(-1));
    }

    @Test
    void when_BuildWithNullCommandTargetResolver_then_ThrowsAxonConfigurationException() {
        SaplDisruptorCommandBus.Builder builderTestSubject = SaplDisruptorCommandBus.builderSapl();

        assertThrows(AxonConfigurationException.class, () -> builderTestSubject.commandTargetResolver(null));
    }
    
    @Test
    void when_BuildWithOtherParameters_then_successfully() {
    	
    	assertDoesNotThrow(() -> SaplDisruptorCommandBus.builderSapl()
    			.cache(NoCache.INSTANCE)
    			.dispatchInterceptors(Collections.emptyList())
    			.executor(mock(Executor.class))
    			.invokerInterceptors(Collections.emptyList())
    			.publisherInterceptors(Collections.emptyList())
    			.rollbackConfiguration(mock(RollbackConfiguration.class))
    			.transactionManager(mock(TransactionManager.class)));
    }
    
    // Utils
    
    private CommandPolicyEnforcementPoint createPolicyEnforcementPointWithPermittingPdp() {
        var cpep = mock(CommandPolicyEnforcementPoint.class);
		try {
			when(cpep.preEnforceCommandDisruptor(any(), any())).thenReturn(COMMAND_RETURN_VALUE);
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
		doNothing().when(cpep).gatherNecessaryHandlers(any(), any(), any());
		return cpep;
	}
	
    @SuppressWarnings("unchecked")
	private Repository<StubAggregate> creatRepositoryMock() {
		AggregateWrapper aggregate = new AggregateWrapper();
		Repository<StubAggregate> repoMock = mock(Repository.class);
		when(repoMock.load(anyString())).thenReturn(aggregate);
		return repoMock;
	}
	
	private CommandTargetResolver createCommandTargetResolverMock()	{
		var resolverMock = mock(CommandTargetResolver.class);
		when(resolverMock.resolveTarget(any())).thenReturn(new VersionedAggregateIdentifier(aggregateIdentifier, 1L));
		return resolverMock;
	}

	private List<MessageHandler<CommandMessage<?>>> createHandlers(Class<?>... clazzes) {
		List<MessageHandler<CommandMessage<?>>> handlers = new LinkedList<>();
		try {
			for (Class<?> clazz : clazzes) {
				Method m = StubAggregate.class.getDeclaredMethod("fakeMethod", clazz);
				var methodDelegate = new AnnotatedMessageHandlingMember<StubAggregate>(m, CommandMessage.class, clazz,
						new DefaultParameterResolverFactory());
				handlers.add(new AggregateCommandHandler(methodDelegate));
			}
		} catch (Exception e) {
			fail("Did not expect exception");
		}
		return handlers;
	}

	private void await(AtomicInteger run) {
		assertWithin(2000, TimeUnit.MILLISECONDS, () -> assertEquals(1, run.get()));
	}

	private static class DeadlinePayload {

    }
	
    @org.axonframework.spring.stereotype.Aggregate
    private static class StubAggregate {

        @AggregateIdentifier
        private String identifier;

        private StubAggregate(String identifier) {
            this.identifier = identifier;
            apply(new SomethingDoneEvent());
        }

        public StubAggregate() {
        }

        @SuppressWarnings("unused") 
        public String getIdentifier() {
            return identifier;
        }

        @CommandHandler
        public void doSomething() {
            apply(new SomethingDoneEvent());
        }

        public void createFailingEvent() {
            apply(new FailingEvent());
        }

        @DeadlineHandler
        public void handle(FailingEvent deadline) {
            throw new IllegalArgumentException();
        }

        @DeadlineHandler
        public void handle(DeadlinePayload deadline) {
            messageHandlingCounter.getAndIncrement();
        }

        @EventSourcingHandler
        protected void handle(EventMessage<?> event) {
            identifier = ((DomainEventMessage<?>) event).getAggregateIdentifier();
        }
        
        @PreEnforce
        public void fakeMethod(String param) {	
        }
        
        @PreEnforce
        public void fakeMethod(StubCommand stubCommand) {
        }
        
        @PreEnforce
        public void fakeMethod(CreateCommand createCommand) {
        }
        
        @PreEnforce
        public void fakeMethod(CreateOrUpdateCommand createOrUpdateCommand) {
        }
        
        @PreEnforce
        public void fakeMethod(UnknownCommand unknownCommand) {
        }
        
        @PreEnforce
        public void fakeMethod(ErrorCommand errorCommand) {
        }
        
        @PreEnforce
        public void fakeMethod(ExceptionCommand exceptionCommand) {
        }
        @PreEnforce
        public void fakeMethod(LoadVersionCommand loadCommand) {
        }
    }

    private static class InMemoryEventStore implements EventStore {

        private final Map<String, DomainEventMessage<?>> storedEvents = new ConcurrentHashMap<>();
        private final CountDownLatch countDownLatch = new CountDownLatch((int) (COMMAND_COUNT + 1L));

        @Override
        public DomainEventStream readEvents(String aggregateIdentifier) {
            DomainEventMessage<?> message = storedEvents.get(aggregateIdentifier);
            return message == null ? DomainEventStream.empty() : DomainEventStream.of(message);
        }

        @Override
        public void publish(List<? extends EventMessage<?>> events) {
            if (events == null || events.isEmpty()) {
                return;
            }
            String key = ((DomainEventMessage<?>) events.get(0)).getAggregateIdentifier();
            DomainEventMessage<?> lastEvent = null;
            for (EventMessage<?> event : events) {
                countDownLatch.countDown();
                lastEvent = (DomainEventMessage<?>) event;
                if (FailingEvent.class.isAssignableFrom(lastEvent.getPayloadType())) {
                    throw new MockException("This is a failing event. EventStore refuses to store that");
                }
            }
            storedEvents.put(key, lastEvent);
        }

        @Override
        public TrackingEventStream openStream(TrackingToken trackingToken) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration subscribe(Consumer<List<? extends EventMessage<?>>> eventProcessor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Registration registerDispatchInterceptor(
                MessageDispatchInterceptor<? super EventMessage<?>> dispatchInterceptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void storeSnapshot(DomainEventMessage<?> snapshot) {
        }
    }

    private static class StubCommand {

        @TargetAggregateIdentifier
        private final Object aggregateIdentifier;

        public StubCommand(Object aggregateIdentifier) {
            this.aggregateIdentifier = aggregateIdentifier;
        }

        public String getAggregateIdentifier() {
            return aggregateIdentifier.toString();
        }
    }

    private static class ErrorCommand extends StubCommand {

        public ErrorCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class ExceptionCommand extends StubCommand {

        private final Exception exception;

        public ExceptionCommand(Object aggregateIdentifier, Exception exception) {
            super(aggregateIdentifier);
            this.exception = exception;
        }

        public Exception getException() {
            return exception;
        }
    }

    private static class CreateCommand extends StubCommand {

        public CreateCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class CreateOrUpdateCommand extends StubCommand {

        public CreateOrUpdateCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class UnknownCommand extends StubCommand {

        public UnknownCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }
    
    private static class LoadVersionCommand extends StubCommand {

        public LoadVersionCommand(Object aggregateIdentifier) {
            super(aggregateIdentifier);
        }
    }

    private static class StubHandler implements MessageHandler<CommandMessage<?>> {

        private Repository<StubAggregate> repository;
        private CommandTargetResolver commandTargetResolver;
        @SuppressWarnings("unused") 
        private List<MessageHandler<CommandMessage<?>>> handlers;
        
        private StubHandler() {
        }

        @Override
        public Object handle(CommandMessage<?> command) throws Exception {
        	
        	if (command.getPayload() instanceof String) {
        		return command.getPayload();
        	}
            StubCommand payload = (StubCommand) command.getPayload();
            if (ExceptionCommand.class.isAssignableFrom(command.getPayloadType())) {
                throw ((ExceptionCommand) command.getPayload()).getException();
            } else if (CreateCommand.class.isAssignableFrom(command.getPayloadType())) {
                repository.newInstance(() -> new StubAggregate(payload.getAggregateIdentifier()))
                          .execute(StubAggregate::doSomething);
            } else if ((CreateOrUpdateCommand.class.isAssignableFrom(command.getPayloadType()))) {
                repository.loadOrCreate(payload.getAggregateIdentifier(),
                                 () -> new StubAggregate(payload.getAggregateIdentifier()))
                          .execute(StubAggregate::doSomething);
			} else if ((LoadVersionCommand.class.isAssignableFrom(command.getPayloadType()))) {
				repository.load(payload.getAggregateIdentifier(), 1L)
						.execute(StubAggregate::doSomething);
			} else {
                Aggregate<StubAggregate> aggregate = repository.load(payload.getAggregateIdentifier());
                if (ErrorCommand.class.isAssignableFrom(command.getPayloadType())) {
                    aggregate.execute(StubAggregate::createFailingEvent);
                } else {
                    aggregate.execute(StubAggregate::doSomething);
                }
            }

            return COMMAND_RETURN_VALUE;
        }

        public void setRepository(Repository<StubAggregate> repository) {
            this.repository = repository;
        }
        
        @Override
        public boolean canHandle(CommandMessage<?> message) {
        	return true;
        }

    }

    private static class StubDomainEvent {

    }

    private static class FailingEvent {

    }
	
    private static class AggregateCommandHandler implements MessageHandler<CommandMessage<?>> {
    	@SuppressWarnings("unused") 
        private final MessageHandlingMember<?> handler;

        public AggregateCommandHandler(MessageHandlingMember<?> handler) {
            this.handler = handler;
        }

        @Override
        public Object handle(CommandMessage<?> command) {
            return command;
        }
    }
    
    private static class AggregateWrapper extends AnnotatedAggregate<StubAggregate> {
    	AggregateWrapper() {
			super(new StubAggregate(), null, null);
		}
	}
}
