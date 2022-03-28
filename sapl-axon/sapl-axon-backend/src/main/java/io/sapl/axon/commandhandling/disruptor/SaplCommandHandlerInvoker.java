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

import static org.axonframework.commandhandling.GenericCommandResultMessage.asCommandResultMessage;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.common.Assert;
import org.axonframework.common.caching.Cache;
import org.axonframework.disruptor.commandhandling.CommandHandlingEntry;
import org.axonframework.eventsourcing.AggregateCacheEntry;
import org.axonframework.eventsourcing.AggregateFactory;
import org.axonframework.eventsourcing.EventSourcedAggregate;
import org.axonframework.eventsourcing.SnapshotTrigger;
import org.axonframework.eventsourcing.SnapshotTriggerDefinition;
import org.axonframework.eventsourcing.eventstore.DomainEventStream;
import org.axonframework.eventsourcing.eventstore.EventStore;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.ScopeDescriptor;
import org.axonframework.messaging.annotation.ClasspathHandlerDefinition;
import org.axonframework.messaging.annotation.HandlerDefinition;
import org.axonframework.messaging.annotation.ParameterResolverFactory;
import org.axonframework.messaging.unitofwork.CurrentUnitOfWork;
import org.axonframework.modelling.command.Aggregate;
import org.axonframework.modelling.command.AggregateNotFoundException;
import org.axonframework.modelling.command.AggregateScopeDescriptor;
import org.axonframework.modelling.command.ConflictingAggregateVersionException;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.RepositoryProvider;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregateMetaModelFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lmax.disruptor.EventHandler;
import com.lmax.disruptor.LifecycleAware;

import io.sapl.axon.commandhandling.CommandPolicyEnforcementPoint;
import io.sapl.axon.utilities.CheckedFunction;

/**
 * Component of the DisruptorCommandBus that invokes the command handler. The execution is done within a Unit Of Work.
 * If an aggregate has been pre-loaded, it is set to the ThreadLocal.
 *
 */
public class SaplCommandHandlerInvoker implements EventHandler<CommandHandlingEntry>, LifecycleAware {

	private static final Logger logger = LoggerFactory.getLogger(SaplCommandHandlerInvoker.class);
    private static final ThreadLocal<SaplCommandHandlerInvoker> CURRENT_INVOKER = new ThreadLocal<>();

    @SuppressWarnings("rawtypes") // Different types in repository because of possibly different aggregates
	private final Map<Class<?>, DisruptorHandlerInvokerRepository> repositories = new ConcurrentHashMap<>();
    private final Cache cache;
    private final int segmentId;
    
    private final CommandPolicyEnforcementPoint pep;
    /**
     * Returns the Repository instance for Aggregate with given {@code typeIdentifier} used by the
     * CommandHandlerInvoker that is running on the current thread.
     * <p>
     * Calling this method from any other thread will return {@code null}.
     *
     * @param type The type of aggregate
     * @param <T>  The type of aggregate
     * @return the repository instance for aggregate of given type
     */
    @SuppressWarnings("unchecked") // Different types in repository because of possibly different aggregates
    public static <T> DisruptorHandlerInvokerRepository<T> getRepository(Class<?> type) {
        final SaplCommandHandlerInvoker invoker = CURRENT_INVOKER.get();
        Assert.state(invoker != null,
                     () -> "The repositories of a DisruptorCommandBus are only available in the invoker thread");
        return invoker.repositories.get(type);
    }

    /**
     * Create an aggregate invoker instance for the given {@code segment} and {@code cache}.
     *
     * @param cache     The cache temporarily storing aggregate instances
     * @param segmentId The id of the segment this invoker should handle
     */
    public SaplCommandHandlerInvoker(Cache cache, int segmentId, CommandPolicyEnforcementPoint pep) {
        this.cache = cache;
        this.segmentId = segmentId;
        this.pep = pep;
    }

    @Override
    public void onEvent(CommandHandlingEntry entry, long sequence, boolean endOfBatch) {
        if (entry.isRecoverEntry()) {
            removeEntry(entry.getAggregateIdentifier());
        } else if (entry.getInvokerId() == segmentId) {
            entry.start();
            // The policy enforcement point is only called for commands  
            if (
            		Objects.isNull(entry.getMessage()) || 
            		!(
            				entry.getMessage() instanceof CommandMessage
            				)) {
            	runNormal(entry);
            } else {
            	callPepAndProcess(entry);
            }
        }
    }

	private void callPepAndProcess(CommandHandlingEntry entry) {
		CheckedFunction<CommandMessage<?>, Object> handleCommand = (CommandMessage<?> commandMessage) -> {
			if (Objects.nonNull(commandMessage) && !entry.getMessage().equals(commandMessage)) {
				entry.transformMessage(command -> commandMessage);
			}
			return entry.getInvocationInterceptorChain().proceed();
		};
		try {
			Object result = pep.preEnforceCommandDisruptor(entry.getMessage(), handleCommand);
			entry.setResult(asCommandResultMessage(result));
		} catch (Exception throwable) {
			entry.setResult(asCommandResultMessage(throwable));
		} finally {
			entry.pause();
		}
	}
	
	private void runNormal(CommandHandlingEntry entry) {
		try {
			Object result = entry.getInvocationInterceptorChain().proceed();
			entry.setResult(asCommandResultMessage(result));
		} catch (Exception throwable) {
			entry.setResult(asCommandResultMessage(throwable));
		} finally {
			entry.pause();
		}
	}

    /**
     * Create a repository instance for an aggregate created by the given {@code aggregateFactory}. The returning
     * repository must be safe to use by this invoker instance.
     *
     * @param <T>                       The type of aggregate created by the factory
     * @param eventStore                The events store to load and publish events
     * @param aggregateFactory          The factory creating aggregate instances
     * @param snapshotTriggerDefinition The trigger definition for snapshots
     * @param parameterResolverFactory  The factory used to resolve parameters on command handler methods
     * @return A Repository instance for the given aggregate
     */
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              AggregateFactory<T> aggregateFactory,
                                              SnapshotTriggerDefinition snapshotTriggerDefinition,
                                              ParameterResolverFactory parameterResolverFactory) {
        return createRepository(eventStore,
                                null,
                                aggregateFactory,
                                snapshotTriggerDefinition,
                                parameterResolverFactory,
                                ClasspathHandlerDefinition.forClass(aggregateFactory.getAggregateType()));
    }

    /**
     * Create a repository instance for an aggregate created by the given {@code aggregateFactory}. The returning
     * repository must be safe to use by this invoker instance.
     *
     * @param <T>                       The type of aggregate created by the factory
     * @param eventStore                The events store to load and publish events
     * @param repositoryProvider        Provides repositories for specified aggregate types
     * @param aggregateFactory          The factory creating aggregate instances
     * @param snapshotTriggerDefinition The trigger definition for snapshots
     * @param parameterResolverFactory  The factory used to resolve parameters on command handler methods
     * @param handlerDefinition         The handler definition used to create concrete handlers
     * @return A Repository instance for the given aggregate
     */
    @SuppressWarnings("unchecked") // Different types in repository because of possibly different aggregates 
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              RepositoryProvider repositoryProvider,
                                              AggregateFactory<T> aggregateFactory,
                                              SnapshotTriggerDefinition snapshotTriggerDefinition,
                                              ParameterResolverFactory parameterResolverFactory,
                                              HandlerDefinition handlerDefinition) {
        return repositories.computeIfAbsent(
                aggregateFactory.getAggregateType(),
                k -> new DisruptorHandlerInvokerRepository<>(
                        aggregateFactory,
                        cache,
                        eventStore,
                        parameterResolverFactory,
                        handlerDefinition,
                        snapshotTriggerDefinition,
                        repositoryProvider
                ));
    }
    
    /**
     * Create a repository instance for an aggregate created by the given {@code aggregateFactory}. The returning
     * repository must be safe to use by this invoker instance.
     *
     * @param <T>                       The type of aggregate created by the factory
     * @param eventStore                The events store to load and publish events
     * @param repositoryProvider        Provides repositories for specified aggregate types
     * @param aggregateFactory          The factory creating aggregate instances
     * @param snapshotTriggerDefinition The trigger definition for snapshots
     * @param parameterResolverFactory  The factory used to resolve parameters on command handler methods
     * @return A Repository instance for the given aggregate
     */
    @SuppressWarnings("unchecked") // Different types in repository because of possibly different aggregates
    public <T> Repository<T> createRepository(EventStore eventStore,
                                              RepositoryProvider repositoryProvider,
                                              AggregateFactory<T> aggregateFactory,
                                              SnapshotTriggerDefinition snapshotTriggerDefinition,
                                              ParameterResolverFactory parameterResolverFactory) {
        return repositories.computeIfAbsent(
                aggregateFactory.getAggregateType(),
                k -> new DisruptorHandlerInvokerRepository<>(
                        aggregateFactory,
                        cache,
                        eventStore,
                        parameterResolverFactory,
                        snapshotTriggerDefinition,
                        repositoryProvider
                ));
    }

    private void removeEntry(String aggregateIdentifier) {
        for (DisruptorHandlerInvokerRepository<?> repository : repositories.values()) {
            repository.removeFromCache(aggregateIdentifier);
        }
        cache.remove(aggregateIdentifier);
    }

    @Override
    public void onStart() {
        CURRENT_INVOKER.set(this);
    }

    @Override
    public void onShutdown() {
        CURRENT_INVOKER.remove();
    }
    
	 /**
     * Repository implementation that is safe to use by a single CommandHandlerInvoker instance.
     *
     * @param <T> The type of aggregate stored in this repository
     */
    static final class DisruptorHandlerInvokerRepository<T> implements Repository<T> {
		
		 private final EventStore eventStore;
	     private final RepositoryProvider repositoryProvider;
	     private final SnapshotTriggerDefinition snapshotTriggerDefinition;
	     private final AggregateFactory<T> aggregateFactory;
	     private final FirstLevelCache<T> firstLevelCache = new FirstLevelCache<>();
	     private final Cache cache;
	     private final AggregateModel<T> aggregateModel;

	     DisruptorHandlerInvokerRepository(AggregateFactory<T> aggregateFactory,
	                                 Cache cache,
	                                 EventStore eventStore,
	                                 ParameterResolverFactory parameterResolverFactory,
	                                 SnapshotTriggerDefinition snapshotTriggerDefinition,
	                                 RepositoryProvider repositoryProvider) {
	         this.aggregateFactory = aggregateFactory;
	         this.cache = cache;
	         this.eventStore = eventStore;
	         this.snapshotTriggerDefinition = snapshotTriggerDefinition;
	         this.aggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateFactory.getAggregateType(),
	                                                                          parameterResolverFactory);
	         this.repositoryProvider = repositoryProvider;
	     }

	     DisruptorHandlerInvokerRepository(AggregateFactory<T> aggregateFactory, Cache cache, EventStore eventStore,
	                                 ParameterResolverFactory parameterResolverFactory,
	                                 HandlerDefinition handlerDefinition,
	                                 SnapshotTriggerDefinition snapshotTriggerDefinition,
	                                 RepositoryProvider repositoryProvider) {
	         this.aggregateFactory = aggregateFactory;
	         this.cache = cache;
	         this.eventStore = eventStore;
	         this.snapshotTriggerDefinition = snapshotTriggerDefinition;
	         this.aggregateModel = AnnotatedAggregateMetaModelFactory.inspectAggregate(aggregateFactory.getAggregateType(),
	                                                                          parameterResolverFactory,
	                                                                          handlerDefinition);
	         this.repositoryProvider = repositoryProvider;
	     }

	     @Override
	     public Aggregate<T> load(String aggregateIdentifier, Long expectedVersion) {
	         ((CommandHandlingEntry) CurrentUnitOfWork.get()).registerAggregateIdentifier(aggregateIdentifier);
	         Aggregate<T> aggregate = load(aggregateIdentifier);
	         if (expectedVersion != null && aggregate.version() > expectedVersion) {
	             throw new ConflictingAggregateVersionException(aggregateIdentifier, expectedVersion,
	                                                            aggregate.version());
	         }
	         return aggregate;
	     }

	     
	     @Override
	     @SuppressWarnings("unchecked") // False positive the isInstance checks type
	     public Aggregate<T> load(String aggregateIdentifier) {
	         ((CommandHandlingEntry) CurrentUnitOfWork.get()).registerAggregateIdentifier(aggregateIdentifier);
	        
	         EventSourcedAggregate<T> aggregateRoot = firstLevelCache.get(aggregateIdentifier);
	         if (aggregateRoot == null) {
	         	logger.debug("Try to load aggregate from cache.");
	         	Object cachedItem = cache.get(aggregateIdentifier);
	             if (cachedItem instanceof AggregateCacheEntry) {
	                 EventSourcedAggregate<T> cachedAggregate = ((AggregateCacheEntry<T>) cachedItem).recreateAggregate(
	                		 aggregateModel, eventStore, repositoryProvider, snapshotTriggerDefinition
	                 );

	                 aggregateRoot = cachedAggregate.invoke(r -> {
	                     if (aggregateFactory.getAggregateType().isInstance(r)) {
	                         return cachedAggregate;
	                     } else {
	                         return null;
	                     }
	                 });
	                 logger.debug("Aggregate in cache: {} ", aggregateRoot.getAggregateRoot());
	             }
	             
	         }
	         if (aggregateRoot == null) {
	             logger.debug("Aggregate {} not in first level cache, loading fresh one from Event Store",
	                          aggregateIdentifier);
	             DomainEventStream eventStream = eventStore.readEvents(aggregateIdentifier);
	             SnapshotTrigger trigger = snapshotTriggerDefinition.prepareTrigger(aggregateFactory.getAggregateType());
	             if (!eventStream.hasNext()) {
	                 throw new AggregateNotFoundException(aggregateIdentifier,
	                                                      "The aggregate was not found in the event store");
	             }
	             aggregateRoot = EventSourcedAggregate.initialize(aggregateFactory.createAggregateRoot(
	                     aggregateIdentifier, eventStream.peek()), aggregateModel, eventStore, repositoryProvider, trigger
	             );

	             aggregateRoot.initializeState(eventStream);
	             firstLevelCache.put(aggregateRoot.identifierAsString(), aggregateRoot);
	             cache.put(aggregateIdentifier, new AggregateCacheEntry<>(aggregateRoot));
	         }

	         return aggregateRoot;
	     }

	     @Override
		 public Aggregate<T> newInstance(Callable<T> factoryMethod) throws Exception {
			SnapshotTrigger trigger = snapshotTriggerDefinition.prepareTrigger(aggregateFactory.getAggregateType());
			EventSourcedAggregate<T> aggregate = EventSourcedAggregate.initialize(factoryMethod, aggregateModel,
					eventStore, repositoryProvider, trigger);

			// in case of loadOrCreate,
			// identifier is null, therefore this special case is handled in loadOrCreate
			// method
			if (aggregate.identifierAsString() != null) {
				firstLevelCache.put(aggregate.identifierAsString(), aggregate);
				cache.put(aggregate.identifierAsString(), new AggregateCacheEntry<>(aggregate));
			}

			return aggregate;
		 }

	     @Override
	     public Aggregate<T> loadOrCreate(String aggregateIdentifier, Callable<T> factoryMethod) throws Exception {
	         try {
	             return load(aggregateIdentifier);
	         } catch (AggregateNotFoundException ex) {
	             Aggregate<T> newInstance = newInstance(factoryMethod);
	             firstLevelCache.put(aggregateIdentifier, (EventSourcedAggregate<T>) newInstance);
	             cache.put(aggregateIdentifier, new AggregateCacheEntry<>((EventSourcedAggregate<T>) newInstance));
	             
	             return newInstance;
	         } catch (Exception e) {
	             logger.debug("Exception occurred while trying to load/create an aggregate. ", e);
	             throw e;
	         }
	     }

	     void removeFromCache(String aggregateIdentifier) {
	         EventSourcedAggregate<T> removed = firstLevelCache.remove(aggregateIdentifier);
	         if (removed != null) {
	             logger.debug("Aggregate {} removed from first level cache for recovery purposes.",
	                 aggregateIdentifier);
	         }
	     }

	     @Override
	     public void send(Message<?> message, ScopeDescriptor scopeDescription) throws Exception {
	         if (canResolve(scopeDescription)) {
	             String aggregateIdentifier = ((AggregateScopeDescriptor) scopeDescription).getIdentifier().toString();
	             load(aggregateIdentifier).handle(message);
	         }
	     }

	     @Override
	     public boolean canResolve(ScopeDescriptor scopeDescription) {
	         return scopeDescription instanceof AggregateScopeDescriptor
	                 && Objects.equals(aggregateModel.type(), ((AggregateScopeDescriptor) scopeDescription).getType());
	     }
	}
}

