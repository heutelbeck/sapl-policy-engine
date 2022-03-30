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

import java.lang.reflect.Constructor;
import java.lang.reflect.Executable;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.messaging.MessageHandler;
import org.axonframework.messaging.annotation.MessageHandlingMember;
import org.axonframework.modelling.command.AggregateMember;
import org.axonframework.modelling.command.CommandTargetResolver;
import org.axonframework.modelling.command.LockAwareAggregate;
import org.axonframework.modelling.command.Repository;
import org.axonframework.modelling.command.inspection.AggregateModel;
import org.axonframework.modelling.command.inspection.AnnotatedAggregate;
import org.axonframework.modelling.command.inspection.ChildForwardingCommandMessageHandlingMember;
import org.springframework.context.ApplicationContext;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.axon.annotations.ConstraintHandler;
import io.sapl.axon.constraints.ConstraintHandlerService;
import io.sapl.axon.utilities.AuthorizationSubscriptionBuilderService;
import io.sapl.axon.utilities.CheckedBiFunction;
import io.sapl.axon.utilities.CheckedFunction;
import io.sapl.axon.utilities.Constants;
import io.sapl.spring.method.metadata.PreEnforce;
import lombok.RequiredArgsConstructor;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;


/** 
 * The CommandPolicyEnforcementPoint (PEP) intercepts actions taken by users within an
 * application. The PEP obtains a decision from the PolicyDecisionPoint and lets
 * either the application process or denies access. According to the Command
 * Pattern of Axon IQ the PEP has different Methods to handle Command Messages.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CommandPolicyEnforcementPoint {

    private final PolicyDecisionPoint pdp;
    private final AuthorizationSubscriptionBuilderService authorizationSubscriptionBuilderService;
    private final ConstraintHandlerService constraintHandlerService;
    private static final String DENY = "Denied by PDP";

    private final ConcurrentMap<String, MessageHandlingMember<?>> commandHandlerDelegate = new ConcurrentHashMap<>();
    private final AtomicReference<CommandTargetResolver> commandTargetResolver = new AtomicReference<>();
    private final ConcurrentMap<String, Repository<?>> repositoryForCommand = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, BiFunction<CommandMessage<?>, Object, ?>> childEntityResolverForCommand = new ConcurrentHashMap<>();
	private final ConcurrentMap<String, Class<?>> aggregateTypeForCommand = new ConcurrentHashMap<>();
	
	private final ConcurrentMap<String, ConstraintMethodHolder> constraintMethodForAggregateOrEntity = new ConcurrentHashMap<>();

	private final ApplicationContext applicationContext;
    
    /**
     * Executed to get the Authorization Decision from the Policy Decision Point in
     * a synchronous (blocking) way. It takes the message, aggregate, delegate (the
     * command handler) and the handleCommand and returns the applied command with
     * executed constraints.
     *
     * @param <C>           PayloadType of the command
     * @param <A>           AggregateType
     * @param <R>           expected ResultType of the handled Command
     * @param message       Representation of a Message, containing a Payload and
     *                      MetaData
     * @param aggregate     Object, which contains state and methods to alter that
     *                      state (it could be an Entity)
     * @param delegate      Command handler, needed to get optionally set action
     * @param handleCommand the call super.handle() of the
     *                      WrappedMessageHandlingMember, transferred in a BiFunction
     * @return the result after commandHandling and constraintHandling
     * @throws Exception	on error
     */
	@SuppressWarnings("unchecked") // return type cast 
	public <C, A, R> Object preEnforceCommandBlocking(CommandMessage<C> message, A aggregate,
			MessageHandlingMember<?> delegate, CheckedBiFunction<CommandMessage<C>, A, R> handleCommand) throws Exception {
		var commandName = message.getCommandName();
        var authorizationSubscription = authorizationSubscriptionBuilderService
                .constructAuthorizationSubscriptionForCommand(message, aggregate, delegate);
        var authorizationDecision = pdp.decide(authorizationSubscription).blockFirst();
        checkPermission(authorizationDecision);
        if (!constraintMethodForAggregateOrEntity.containsKey(commandName)) {
        	if (aggregate != null) {
        		aggregateTypeForCommand.put(commandName, aggregate.getClass());
        	} else {
        		// in case of constructor
				aggregateTypeForCommand.put(commandName, delegate.unwrap(Executable.class).get().getDeclaringClass());
        	}
        	// an Entity is handled like an Aggregate
        	tryReadConstraintHandlerAnnotationsFromAggregateClass(commandName);
        }
        // get entity object if present
        var methodHolder = constraintMethodForAggregateOrEntity.get(commandName);
		var listAggregateConstraintMethod = getAggregateConstraintMethods(methodHolder);

        var executable = delegate.unwrap(Executable.class).orElseThrow();
        Class<R> returnType = null;
        
        if (executable instanceof Constructor) {
            var constructor = delegate.unwrap(Constructor.class).get();
            returnType = (Class<R>) constructor.getDeclaringClass();
        } else {
            var delegateMethod = delegate.unwrap(Method.class).orElseThrow();
            returnType = (Class<R>) delegateMethod.getReturnType();
           
        }
		var bundle = constraintHandlerService.createCommandBundle(
				authorizationDecision, message, returnType, listAggregateConstraintMethod, Optional.empty(),
				Optional.empty());
        var mappedCommandMessage = bundle.executeMessageHandlers(message);
        // Scope of AggregateLifecycle is active (Events in methods possible)
        bundle.invokeAggregateConstraintHandlerMethods(aggregate, Optional.empty(), message, applicationContext);

        var result = handleCommand.apply(mappedCommandMessage, aggregate);

        return bundle.executeResultHandlerProvider(result);
    }

    /**
     * Is executed for command-messages from the SaplDisruptorCommandBus to get the
     * Authorization Decision from the Policy Decision Point. It gets the message and the 
     * handleCommand-Function and calls the handleDecision Method to get 
     * the Decision from the Policy Decision Point and to handle Constraints.
     *
     * @param <C>           PayloadType of the command
     * @param <R>           Return type
     * @param message       Representation of a Message, containing a pay-load and
     *                      MetaData
     * @param handleCommand	Function that executes the Command (function may be throw exception)
     * @return the result after commandHandling and constraintHandling
     * @throws Exception	on error
     */
	public <C, R> R preEnforceCommandDisruptor(CommandMessage<C> message,
			CheckedFunction<CommandMessage<?>, R> handleCommand) throws Exception {
		return handleDecision(message, handleCommand);
	}

    /**
     * Is executed for command-messages from the {@link SaplCommandBus} to get the
     * Authorization Decision from the Policy Decision Point. It gets the message and the 
     * handleCommand-Function and calls the handleDecision Method to get the Decision 
     * from the Policy Decision Point and to handle Constraints.
     *
     * @param <C>           PayloadType of the command
     * @param <R>           Return type
     * @param message       Representation of a Message, containing a Payload and
     *                      MetaData
     * @param handleCommand Function that executes the Command (function may be throw exception)
     * @return the result after commandHandling and constraintHandling
     */
	public <C, R> Callable<R> preEnforceCommand(CommandMessage<C> message,
			CheckedFunction<CommandMessage<?>, R> handleCommand) {
		return () -> handleDecision(message, handleCommand);

	}

    private <C, R> R handleDecision(CommandMessage<C> message,
                                            CheckedFunction<CommandMessage<?>, R> handleCommand) throws Exception {
    	var commandName = message.getCommandName();
    	var delegate = commandHandlerDelegate.get(commandName);
    	// in case of no PreEnforce Annotation
    	if (Objects.isNull(delegate)) {
    		return handleCommand.apply(message);
    	}
    	var commandMessageVersionedId = commandTargetResolver.get().resolveTarget(message);
		var commandMessageAggregateId = commandMessageVersionedId.getIdentifier();
		var aggregateOptional = getAggregateFromRepository(commandName, commandMessageAggregateId);
		Object aggregateObject = null; 
		if (aggregateOptional.isPresent()) {
			aggregateObject = aggregateOptional.get().getAggregateRoot();
		}
				
		Map<String, Object> additionalEntries = new HashMap<>();
		additionalEntries.put(Constants.aggregateIdentifier.name(), commandMessageAggregateId);
		additionalEntries.put(Constants.aggregateType.name(), aggregateTypeForCommand.get(commandName));
		
		var commandWithAdditionalMetaData = message.andMetaData(additionalEntries);
		
		var authorizationSubscription = authorizationSubscriptionBuilderService
				.constructAuthorizationSubscriptionForCommand(commandWithAdditionalMetaData, aggregateObject, delegate);

		var authorizationDecision = pdp.decide(authorizationSubscription).next().block();

		checkPermission(authorizationDecision);

		// get return type 
		var returnType = getReturnTypeFromExecutable(delegate);
		// get entity object if present
		var entity = getEntityIfPresent(commandWithAdditionalMetaData, aggregateObject);
		// get constraint methods
		var methodHolder = constraintMethodForAggregateOrEntity.get(commandName);
		var listEntityConstraintMethod = getEntityConstraintMethods(entity, methodHolder);
		var listAggregateConstraintMethod = getAggregateConstraintMethods(methodHolder);
		
		// create bundle
		@SuppressWarnings("unchecked") // necessary return type cast 
		var bundle = constraintHandlerService.createCommandBundle(authorizationDecision, commandWithAdditionalMetaData,
				(Class<R>) returnType, listAggregateConstraintMethod, entity,
				listEntityConstraintMethod);
		var mappedCommandMessage = bundle.executeMessageHandlers(commandWithAdditionalMetaData);
		// invoke constraint methods via AnnotatedAggregate.invoke(Function) (Events in methods possible)
		aggregateOptional.ifPresent(aggregate -> aggregate.invoke(aggregateRoot -> {
			bundle.invokeAggregateConstraintHandlerMethods(aggregateRoot, entity, message, applicationContext);
			return aggregateRoot;
		}));

		var result = handleCommand.apply(mappedCommandMessage);

		return bundle.executeResultHandlerProvider(result);

    }

    private void checkPermission(AuthorizationDecision authorizationDecision) {
        if (authorizationDecision == null || (authorizationDecision.getDecision() != Decision.PERMIT))
            throw new AccessDeniedException(DENY);
    }
    
    private Optional<AnnotatedAggregate<?>> getAggregateFromRepository(String commandName, String commandMessageAggregateId) {
		var repository = repositoryForCommand.get(commandName);
		Optional<AnnotatedAggregate<?>> annotatedAggregateOptional = Optional.empty();
		try {
			var repositoryAggregate = repository.load(commandMessageAggregateId);
			AnnotatedAggregate<?> annotatedAggregate;
			if (repositoryAggregate instanceof LockAwareAggregate) {
				// load LockAwareAggregate via repository
				var lockAwareAggregate = (LockAwareAggregate<?, ?>) repositoryAggregate;
				// get wrapped annotated aggregate
				annotatedAggregate = (AnnotatedAggregate<?>) lockAwareAggregate.getWrappedAggregate();
			} else {
				// load AnnotatedAggregate via repository (Disruptor)
				annotatedAggregate = (AnnotatedAggregate<?>) repositoryAggregate;
			}
			annotatedAggregateOptional = Optional.of(annotatedAggregate);
		} catch (Exception exception) {
			// if exception than there is no aggregate maybe it is an aggregate creation command
			log.debug("No Aggregate because: {}", exception.getMessage());
		}
		
		return annotatedAggregateOptional;
	}
    
    private Class<?> getReturnTypeFromExecutable(MessageHandlingMember<?> delegate) {
    	var executable = delegate.unwrap(Executable.class).orElseThrow();
    	Class<?> returnType;
		if (executable instanceof Constructor) {
			var constructor = (Constructor<?>) executable;
			returnType = constructor.getDeclaringClass();
		} else {
			var delegateMethod = (Method) executable;
			returnType = delegateMethod.getReturnType();
		}
		return returnType;
	}
    
    private Optional<Object> getEntityIfPresent(CommandMessage<?> message, Object aggregateRoot) {
		var childEntityResolver = childEntityResolverForCommand.get(message.getCommandName());
		if (childEntityResolver != null) {
			return Optional.ofNullable(childEntityResolver.apply(message, aggregateRoot));
		}
		return Optional.empty();
    }
    
	private Optional<List<Method>> getEntityConstraintMethods(Optional<Object> entityOpt,
			ConstraintMethodHolder methodHolder) {
		if (entityOpt.isPresent()) {
			var entity = entityOpt.get();
			return methodHolder.getMapEntityToConstraintMethod().entrySet().stream()
					.filter(e -> e.getKey().equals(entity.getClass().getName())).map(Map.Entry::getValue).findFirst();
		}
		return Optional.empty();
    }
    
    private Optional<List<Method>> getAggregateConstraintMethods(ConstraintMethodHolder methodHolder) {
    	Optional<List<Method>> returnOptional = Optional.empty();
    	if (!methodHolder.getAggregateConstraintMethodList().isEmpty()) {
    		returnOptional = Optional.of(methodHolder.getAggregateConstraintMethodList());
    	}
    	return returnOptional;
    }

    /**
	 * Gathers and saves {@link Repository}, {@link  CommandTargetResolver} (if not present), AggregateType,
	 * ChildEntityResolver, CommandHandlerDelegates (Aggregate and Entity) from {@link MessageHandler}.
	 * 
	 * @param commandName command name
	 * @param commandHandler command message handler
	 * @param commandTargetResolver a target resolver
	 */
	public void gatherNecessaryHandlers(String commandName, MessageHandler<? super CommandMessage<?>> commandHandler,
				CommandTargetResolver commandTargetResolver) {
		try {
			// gather Repository
			var messageHandlerClass = gatherRepositoryFromMessageHandlerAndReturnMessageHandlerClass(commandName, commandHandler);
			
			// set or gather CommandTargetResolver
			if (this.commandTargetResolver.get() == null)
				if (commandTargetResolver == null) {
					gatherCommandTargetResolver(commandHandler, messageHandlerClass);
				} else {
					// in the DisruptorCommandBus is already a CommandTagretResolver present
					this.commandTargetResolver.set(commandTargetResolver);
				}
			
			// get aggregate type via repository
			gatherAggregateType(commandName);
			// save delegate from command handler
			gatherCommandHandlingDelegates(commandName, commandHandler, messageHandlerClass);
			// save constraint handler methods form aggregate and entity (AggregateMember), if present 
			tryReadConstraintHandlerAnnotationsFromAggregateClass(commandName);
		} catch (Exception e) {
			log.error("Error on reading necessary handlers: {}", e.getMessage());
			// unchecked exception to exit process without caller exception handling
			throw new UnsupportedOperationException("Error on reading necessary handlers: ", e);
		}
	}

	private Class<?> gatherRepositoryFromMessageHandlerAndReturnMessageHandlerClass(String commandName,
			MessageHandler<? super CommandMessage<?>> commandMessageHandler)
			throws IllegalAccessException {
		Class<?> messageHandlerClass = commandMessageHandler.getClass();
		Field repositoryField = null;
		while (repositoryField == null) {
			try {
				repositoryField = messageHandlerClass.getDeclaredField("repository");
			} catch (NoSuchFieldException | SecurityException e) {
				messageHandlerClass = messageHandlerClass.getSuperclass();
			}
		}
		setFieldAccessible(repositoryField);
		var repository = (Repository<?>) repositoryField.get(commandMessageHandler);
		repositoryForCommand.put(commandName, repository);
		
		return messageHandlerClass;
	}
	
	private void gatherCommandTargetResolver(MessageHandler<? super CommandMessage<?>> commandMessageHandler,
			Class<?> messageHandlerClass) throws NoSuchFieldException, IllegalAccessException {
		// only set the CommandTargetResolver if it is empty
		if (commandTargetResolver.get() == null) {
			var fieldCommandTargetResolver = messageHandlerClass.getDeclaredField("commandTargetResolver");
			setFieldAccessible(fieldCommandTargetResolver);
			commandTargetResolver.set((CommandTargetResolver) fieldCommandTargetResolver.get(commandMessageHandler));
		}
	}
	
	private void gatherAggregateType(String commandName) throws IllegalAccessException {
		var repository = repositoryForCommand.get(commandName);
		Class<?> repositoryClass = repository.getClass();
		Field aggreagteModelField = null;
		while (aggreagteModelField == null) {
			try {
				// repository has a field named "aggregateModel" and is type of "AggregateModel<?>"
				aggreagteModelField = repositoryClass.getDeclaredField("aggregateModel");
				setFieldAccessible(aggreagteModelField);
				var aggragteModel = (AggregateModel<?>) aggreagteModelField.get(repository);
				aggregateTypeForCommand.put(commandName, aggragteModel.entityClass());
				
			} catch (NoSuchFieldException | SecurityException reflectionException) {
				try {
					// for disruptor: repository has a field named "type" and is type of "Class<?>";
					aggreagteModelField = repositoryClass.getDeclaredField("type");
					setFieldAccessible(aggreagteModelField);
					Class<?> aggregateClass = (Class<?>) aggreagteModelField.get(repository);
					aggregateTypeForCommand.put(commandName, aggregateClass);
				} catch (NoSuchFieldException | SecurityException reflection2Exception) {
					repositoryClass = repositoryClass.getSuperclass();
				}
			}
		}
		
	}
	
	private void gatherCommandHandlingDelegates(String commandName,
			MessageHandler<? super CommandMessage<?>> commandMessageHandler, Class<?> messageHandlerClass)
			throws NoSuchFieldException, IllegalAccessException {
		var fieldHandlers = messageHandlerClass.getDeclaredField("handlers");
		setFieldAccessible(fieldHandlers);
		@SuppressWarnings("unchecked") // type of field is known (Axon Framework)
		var listHandlers = (List<MessageHandler<CommandMessage<?>>>) fieldHandlers.get(commandMessageHandler);

		for (MessageHandler<CommandMessage<?>> msgHandler : listHandlers) {
			Field fieldMessageHandlingMember = msgHandler.getClass().getDeclaredField("handler");
			setFieldAccessible(fieldMessageHandlingMember);
			var msgHandlingMember = (MessageHandlingMember<?>) fieldMessageHandlingMember.get(msgHandler);
			
			var optExecutable = msgHandlingMember.unwrap(Executable.class);
			// find correct command via method parameter
			if (optExecutable.isPresent() && optExecutable.get().isAnnotationPresent(PreEnforce.class)) {
				var executable = optExecutable.get();
				for (var parameterClass : executable.getParameterTypes()) {
					if (commandName.equals(parameterClass.getName()) && !commandHandlerDelegate.containsKey(commandName)) {
						commandHandlerDelegate.put(commandName, msgHandlingMember);
						log.debug("Add command '{}' with method handler '{}'", commandName, executable);
						if (msgHandlingMember instanceof ChildForwardingCommandMessageHandlingMember) {
							gatherChildEntityResolver(commandName, msgHandlingMember);
						}						
					}
				}
			}
		}
	}

	private void gatherChildEntityResolver(String commandName, MessageHandlingMember<?> msgHandlingMember)
			throws NoSuchFieldException, IllegalAccessException {
		// detect ChildEntityResolver function (BiFunction<CommandMessage<?>, Object, ?>) 
		var clazzChild = msgHandlingMember.getClass();
		Field childEntityResolverField = clazzChild.getDeclaredField("childEntityResolver");
		setFieldAccessible(childEntityResolverField);
		@SuppressWarnings("unchecked") // type of field is known (Axon Framework) 
		var childEntityResolver = (BiFunction<CommandMessage<?>, Object, ?>) childEntityResolverField.get(msgHandlingMember);
		childEntityResolverForCommand.put(commandName, childEntityResolver);
	}
	
	private void setFieldAccessible(Field field) {
		field.setAccessible(true);
	}

	private void tryReadConstraintHandlerAnnotationsFromAggregateClass(String commandName) {
		Class<?> aggregateClass = aggregateTypeForCommand.get(commandName);
		
		// aggregate already scanned?
		var optHolder = constraintMethodForAggregateOrEntity.values().stream()
				.filter(h -> aggregateClass.getName().equals(h.getAggregateTypeName())).findFirst();
		if (optHolder.isPresent()) {
			// add existing holder for this command
			constraintMethodForAggregateOrEntity.put(commandName, optHolder.get());
			return;
		}
		var allAggregateMethods = aggregateClass.getDeclaredMethods();
		var aggregateConstraintMethods = Arrays.stream(allAggregateMethods)
				.filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
		
		var methodHolder = new ConstraintMethodHolder(aggregateClass.getName(), aggregateConstraintMethods,
				new HashMap<>());
		var allFields = aggregateClass.getDeclaredFields();
		var fields = Arrays.stream(allFields).filter(f -> f.isAnnotationPresent(AggregateMember.class))
				.collect(Collectors.toList());
		
		if (!fields.isEmpty()) {
			for (var field : fields) {
				// get entity type
				Class<?> entityType;
				if (List.class.equals(field.getType()) || Set.class.equals(field.getType())) {
					var parameterizedType = (ParameterizedType) field.getGenericType();
					// index: 0 = only one list/set entry type
					entityType = (Class<?>) parameterizedType.getActualTypeArguments()[0];
				} else if (Map.class.equals(field.getType())) {
					var parameterizedType = (ParameterizedType) field.getGenericType();
					// index: 0 = key type, 1 = value type
					entityType = (Class<?>) parameterizedType.getActualTypeArguments()[1];
				} else {
					entityType = field.getType();
				}
				
				// get entity constraint handler methods
				var allEntityMethods = entityType.getDeclaredMethods();
				var entityMethods = Arrays.stream(allEntityMethods)
						.filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
				
				if (!entityMethods.isEmpty()) {
					methodHolder.getMapEntityToConstraintMethod().put(entityType.getName(), entityMethods);
				}
			}
		}
		constraintMethodForAggregateOrEntity.put(commandName, methodHolder);
	}
	
	@RequiredArgsConstructor
	@Value
	private static class ConstraintMethodHolder {
		String aggregateTypeName;
		List<Method> aggregateConstraintMethodList;
		Map<String, List<Method>> mapEntityToConstraintMethod;
	}
    
}
