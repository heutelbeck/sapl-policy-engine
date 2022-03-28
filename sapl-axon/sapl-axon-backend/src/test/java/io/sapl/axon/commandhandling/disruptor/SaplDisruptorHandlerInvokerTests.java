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

import static org.axonframework.modelling.command.AggregateLifecycle.apply;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.lang.reflect.Parameter;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.axonframework.commandhandling.CommandHandler;
import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.caching.Cache;
import org.axonframework.deadline.DeadlineMessage;
import org.axonframework.deadline.GenericDeadlineMessage;
import org.axonframework.deadline.annotation.DeadlineHandler;
import org.axonframework.disruptor.commandhandling.CommandHandlingEntry;
import org.axonframework.eventhandling.GenericDomainEventMessage;
import org.axonframework.eventsourcing.AggregateCacheEntry;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.EventSourcingHandler;
import org.axonframework.eventsourcing.GenericAggregateFactory;
import org.axonframework.eventsourcing.SnapshotTrigger;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.annotation.ClasspathParameterResolverFactory;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.ConflictingAggregateVersionException;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.axonframework.modelling.saga.SagaScopeDescriptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import io.sapl.axon.commandhandling.CommandPolicyEnforcementPoint;
import io.sapl.axon.utilities.CheckedFunction;
import io.sapl.spring.method.metadata.PreEnforce;

public class SaplDisruptorHandlerInvokerTests {
	private SaplCommandHandlerInvoker testSubject;
    private EventStore mockEventStore;
    private Cache mockCache;
    private CommandHandlingEntry commandHandlingEntry;
    private String aggregateIdentifier;
    private CommandMessage<String> mockCommandMessage;
    private MessageHandler<CommandMessage<?>> mockCommandHandler;
    private SnapshotTriggerDefinition snapshotTriggerDefinition;
    private SnapshotTrigger mockTrigger;

    private static AtomicInteger messageHandlingCounter;
    
    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        var logger = (Logger) LoggerFactory.getLogger("ROOT");
        logger.setLevel(Level.ERROR);
        mockEventStore = mock(EventStore.class);
        mockCache = mock(Cache.class);
        doAnswer(invocation -> {
            // attempt to serialize whatever is being added to the cache
            try {
                new ObjectOutputStream(new ByteArrayOutputStream()).writeObject(invocation.getArguments()[1]);
            } catch (Exception e) {
                fail("Attempt to add a non-serializable instance to the cache: " + invocation.getArgument(1));
            }
            return null;
        }).when(mockCache).put(anyString(), any());
        var cpep = mock(CommandPolicyEnforcementPoint.class);
		try {
			when(cpep.preEnforceCommandDisruptor(any(CommandMessage.class), any())).thenReturn("ok");
		} catch (Exception e) {
			fail("Did not expect exception: " + e.getMessage());
		}
        
        
        testSubject = new SaplCommandHandlerInvoker(mockCache, 0, cpep);
        aggregateIdentifier = "mockAggregate";
        mockCommandMessage = mock(CommandMessage.class);
        mockCommandHandler = mock(MessageHandler.class); 
		
        when(mockCommandMessage.getMetaData()).thenReturn(new MetaData(Map.of("aggregateIdentifier", aggregateIdentifier)));
        when(mockCommandMessage.getPayload()).thenReturn("command");
        when(mockCommandMessage.getPayloadType()).thenReturn(String.class);
        when(mockCommandMessage.andMetaData(any())).thenReturn(mockCommandMessage);
        when(mockCommandMessage.getCommandName()).thenReturn("commandName");

        commandHandlingEntry = new CommandHandlingEntry();
        commandHandlingEntry.reset(mockCommandMessage, mockCommandHandler, 0, 0, null,
                                   Collections.emptyList(),
                                   Collections.emptyList());
        mockTrigger = mock(SnapshotTrigger.class);
        snapshotTriggerDefinition = mock(SnapshotTriggerDefinition.class);
        when(snapshotTriggerDefinition.prepareTrigger(any())).thenReturn(mockTrigger);
        messageHandlingCounter = new AtomicInteger(0);
    }

    @Test
    void when_SendCommand_then_Successfully() {
    	commandHandlingEntry = spy(new CommandHandlingEntry());
    	commandHandlingEntry.reset(mockCommandMessage, mockCommandHandler, 0, 0, null,
                Collections.emptyList(),
                Collections.emptyList());
    	testSubject.onEvent(commandHandlingEntry, 0, false);
    	verify(commandHandlingEntry, times(1)).start();
    }
    
    
	@Test
	@SuppressWarnings("unchecked")
    void when_SendCommand_then_MappedMessage() throws Exception {
    	commandHandlingEntry = spy(new CommandHandlingEntry());
    	commandHandlingEntry.reset(mockCommandMessage, mockCommandHandler, 0, 0, null,
                Collections.emptyList(),
                Collections.emptyList());
    	var cpep = mock(CommandPolicyEnforcementPoint.class);
    	ArgumentCaptor<CheckedFunction<CommandMessage<?>, ?>> handleCommandtMessageCaptor = ArgumentCaptor
				.forClass(CheckedFunction.class);

 		when(cpep.preEnforceCommandDisruptor(any(CommandMessage.class), any())).thenReturn("ok");
        
        testSubject = new SaplCommandHandlerInvoker(mockCache, 0, cpep);
    	testSubject.onEvent(commandHandlingEntry, 0, false);
    	
		verify(cpep).preEnforceCommandDisruptor(any(CommandMessage.class), handleCommandtMessageCaptor.capture());
    	handleCommandtMessageCaptor.getValue().apply(mock(CommandMessage.class));
    	
    	verify(commandHandlingEntry, times(1)).start();
    }
    
    @Test
    void when_CreateRepositoryWithParameterResolverFactory_then_CallSuccessfully() {
    	ParameterResolverFactory parameterResolverFactory =
                spy(ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        testSubject.createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                     snapshotTriggerDefinition, parameterResolverFactory);

        // The StubAggregate has three 'handle()' functions, hence verifying this 3 times
        verify(parameterResolverFactory, times(4)).createInstance(
                argThat(item -> List.of("method","handle").contains(item.getName())), isA(Parameter[].class), anyInt()
        );
        verifyNoMoreInteractions(parameterResolverFactory);
    }
    
    @Test
	void when_CreateRepositoryWithRepositoryProvider_then_NotNull() throws Exception {
		var repositoryProviderMock = mock(RepositoryProvider.class);
		final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				repositoryProviderMock, new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class));
		assertNotNull(repository);
	}
    
    @Test
	void when_CreateRepositoryWithHandlerDefinition_then_NotNull() throws Exception {
		var repositoryProviderMock = mock(RepositoryProvider.class);
		var handlerDefinitionMock = mock(HandlerDefinition.class);
		final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				repositoryProviderMock, new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class), handlerDefinitionMock);
		assertNotNull(repository);
	}
    
    @Test
    void when_OnStart_then_GetRepository_Successfully() {
    	testSubject.onStart();
    	
    	assertNull(SaplCommandHandlerInvoker.getRepository(StubAggregate.class));
    }
    
    @Test
    void when_OnShutdown_then_throwsException() {
    	testSubject.onShutdown();
    	
    	 Exception exception = assertThrows(Exception.class, () -> SaplCommandHandlerInvoker.getRepository(StubAggregate.class));
    	 assertEquals("The repositories of a DisruptorCommandBus are only available in the invoker thread", exception.getMessage());
    }
    
    @Test
    void when_RepositoryLoadVersion_then_NotNull() {
    	final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class));
		when(mockEventStore.readEvents(any())).thenReturn(DomainEventStream
				.of(new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 1, aggregateIdentifier)));
    	
		commandHandlingEntry.start();
    	assertNotNull(repository.load(aggregateIdentifier, 1L));
    }
    
    @Test
    void when_RepositoryLoadVersion_then_ConflictingAggregateVersionException() {
    	final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class));
		when(mockEventStore.readEvents(any())).thenReturn(DomainEventStream
				.of(new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 1, aggregateIdentifier)));
    	
		commandHandlingEntry.start();
		assertThrows(ConflictingAggregateVersionException.class, () -> repository.load(aggregateIdentifier, -1L));
    }
    
    @Test
    void when_RepositoryLoad_then_Notnull() {
    	final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class));
		when(mockEventStore.readEvents(any())).thenReturn(DomainEventStream
				.of(new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 1, aggregateIdentifier)));
    	
		commandHandlingEntry.start();
    	assertNotNull(repository.load(aggregateIdentifier));
    }
    
    @Test
    void when_RepositoryLoadFromCache_then_NotNull() {
    	final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class));
		when(mockEventStore.readEvents(any())).thenReturn(DomainEventStream
				.of(new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 1, aggregateIdentifier)));
    	
		when(mockCache.get(aggregateIdentifier)).thenReturn(new AggregateCacheEntry<>(
              EventSourcedAggregate.initialize(new StubAggregate(aggregateIdentifier),
              AnnotatedAggregateMetaModelFactory.inspectAggregate(StubAggregate.class),
              mockEventStore, mockTrigger)));
		commandHandlingEntry.start();
    	assertNotNull(repository.load(aggregateIdentifier));
    }
    
    @Test
    void when_RepositoryNewInstance_then_NotNull() throws Exception {
    	final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class));
		when(mockEventStore.readEvents(any())).thenReturn(DomainEventStream
				.of(new GenericDomainEventMessage<>("StubAggregate", aggregateIdentifier, 1, aggregateIdentifier)));
    	
		commandHandlingEntry.start();
    	assertNotNull(repository.newInstance(() -> new StubAggregate(aggregateIdentifier)));
    }
    
    @Test
    void when_RepositoryLoadOrCreate_then_NotNull() throws Exception {
    	final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class));
		when(mockEventStore.readEvents(any())).thenReturn(DomainEventStream.empty());
    	
		commandHandlingEntry.start();
    	assertNotNull(repository.loadOrCreate("wrongId", () -> new StubAggregate(aggregateIdentifier)));
    }
    
    @Test
    void when_RepositoryLoadOrCreate_then_Exception() throws Exception {
    	final Repository<StubAggregate> repository = testSubject.createRepository(mockEventStore,
				new GenericAggregateFactory<>(StubAggregate.class), snapshotTriggerDefinition,
				ClasspathParameterResolverFactory.forClass(StubAggregate.class));
		when(mockEventStore.readEvents(any())).thenThrow(new RuntimeException("Test"));
    	
		commandHandlingEntry.start();
    	assertThrows(RuntimeException.class, () -> repository.loadOrCreate("wrongId", () -> new StubAggregate(aggregateIdentifier)));
    }
   
    @Test
    void when_RemoveEntry_then_Successfully() throws Exception {
    	testSubject
              .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                snapshotTriggerDefinition,
                                ClasspathParameterResolverFactory.forClass(StubAggregate.class));
    	    	
        commandHandlingEntry.resetAsRecoverEntry(aggregateIdentifier);
        
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).remove(aggregateIdentifier);
    }

    @Test
    void when_CacheEntryInvalidatedOnRecoveryEntry_then_Successfully() {
        commandHandlingEntry.resetAsRecoverEntry(aggregateIdentifier);
        testSubject.onEvent(commandHandlingEntry, 0, true);

        verify(mockCache).remove(aggregateIdentifier);
        verify(mockEventStore, never()).readEvents(eq(aggregateIdentifier));
    }

    @Test
    void when_CreateRepository_then_ReturnsSameInstanceOnSecondInvocation() {
        final Repository<StubAggregate> repository1 = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        final Repository<StubAggregate> repository2 = testSubject
                .createRepository(mockEventStore, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertSame(repository1, repository2);
    }
    
    @Test
    void when_CreateRepository_and_WithoutHandlerDefinition_then_ReturnsSameInstanceOnSecondInvocation() {
    	var repositoryProviderMock = mock(RepositoryProvider.class);
    	final Repository<StubAggregate> repository1 = testSubject
                .createRepository(mockEventStore, repositoryProviderMock, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        final Repository<StubAggregate> repository2 = testSubject
                .createRepository(mockEventStore, repositoryProviderMock, new GenericAggregateFactory<>(StubAggregate.class),
                                  snapshotTriggerDefinition,
                                  ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertSame(repository1, repository2);
    }

    @Test
    void when_ReposityCanResolve_then_ReturnsTrueForMatchingAggregateDescriptor() {
        Repository<StubAggregate> testRepository =
                testSubject.createRepository(mockEventStore,
                                             new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition,
                                             ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertTrue(testRepository.canResolve(new AggregateScopeDescriptor(
                StubAggregate.class.getSimpleName(), "some-identifier")
        ));
    }

    @Test
    void when_ReposityCanResolve_then_ReturnsFalseNonAggregateScopeDescriptorImplementation() {
        Repository<StubAggregate> testRepository =
                testSubject.createRepository(mockEventStore,
                                             new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition,
                                             ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertFalse(testRepository.canResolve(new SagaScopeDescriptor("some-saga-type", "some-identifier")));
    }

    @Test
    void when_ReposityCanResolve_then_ReturnsFalseForNonMatchingAggregateType() {
        Repository<StubAggregate> testRepository =
                testSubject.createRepository(mockEventStore,
                                             new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition,
                                             ClasspathParameterResolverFactory.forClass(StubAggregate.class));

        assertFalse(testRepository.canResolve(
                new AggregateScopeDescriptor("other-non-matching-type", "some-identifier")
        ));
    }

    @Test
    void when_RepositySendDeliversMessageAtDescribedAggregateInstance_then_CallSuccessfully() throws Exception {
        String testAggregateId = "some-identifier";
        DeadlineMessage<DeadlinePayload> testMsg =
                GenericDeadlineMessage.asDeadlineMessage("deadline-name", new DeadlinePayload(), Instant.now());
        AggregateScopeDescriptor testDescriptor =
                new AggregateScopeDescriptor(StubAggregate.class.getSimpleName(), testAggregateId);

        Repository<StubAggregate> testRepository =
                testSubject.createRepository(mockEventStore,
                                             new GenericAggregateFactory<>(StubAggregate.class),
                                             snapshotTriggerDefinition,
                                             ClasspathParameterResolverFactory.forClass(StubAggregate.class));
        when(mockEventStore.readEvents(any()))
                .thenReturn(DomainEventStream.of(new GenericDomainEventMessage<>(
                        StubAggregate.class.getSimpleName(), testAggregateId, 0, testAggregateId
                )));

        commandHandlingEntry.start();
        try {
            testRepository.send(testMsg, testDescriptor);
        } finally {
            commandHandlingEntry.pause();
        }

        assertEquals(1, messageHandlingCounter.get());
    }

    private static class FailingPayload {

    }

    private static class DeadlinePayload {

    }

    @org.axonframework.spring.stereotype.Aggregate
    public static class StubAggregate implements Serializable {
		private static final long serialVersionUID = 1L;

		@AggregateIdentifier
        private String id;

        public StubAggregate() {
        }

        public StubAggregate(String id) {
            this.id = id;
        }

        public void doSomething() {
            apply(id);
        }

        @DeadlineHandler
        public void handle(FailingPayload deadline) {
            throw new IllegalArgumentException();
        }

        @DeadlineHandler
        public void handle(DeadlinePayload deadline) {
            messageHandlingCounter.getAndIncrement();
        }

        @EventSourcingHandler
        public void handle(String id) {
            this.id = id;
        }
        
        @PreEnforce
		@CommandHandler
		public void method(String param) {
			assertNotNull(param);
		}
    }
}
