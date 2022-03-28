# SAPL Axon Backend Integration
This package provides integration into the Axon Backend. It provides a SAPL specific Axon elements such as a SAPL QueryBus und SAPL CommandBus.
Furthermore, will this integration provide the functionality to handle SAPL specific constraints.

-----------------

## Command Handling
Commands are used in the Axon framework to change the state of the application.
The Axon command messages can be enforced with the SAPL integration. There are 2 possible integrations provided in this package. Either to do the enforcement in the CommandBus or as a blocking variant in the CommandHandlerEnhancer. 

For more general information on AxonIQ site: 
- [Commands](https://docs.axoniq.io/reference-guide/v/master/axon-framework/messaging-concepts#commands)
- [Command Bus](https://docs.axoniq.io/reference-guide/axon-framework/axon-framework-commands/infrastructure)
- [Handler Enhancers](https://docs.axoniq.io/reference-guide/v/master/appendices/message-handler-tuning/handler-enhancers)

### Configuration for CommandBus

In this section there are 3 possibilities to configure the CommandBus to work together with the SAPL integration. 

In case of using a [local CommandBus](https://docs.axoniq.io/reference-guide/v/master/axon-framework/axon-framework-commands/infrastructure#the-command-bus-local) the SAPLCommandBus can be configured to work with the Axon framework.

For this provide the CommandBus in Configuration:

```java
// SaplCommandBus 
@Bean(destroyMethod = "shutdown") // destroyMethod for ExecutorService usage
public CommandBus provideSaplCommandBus(CommandPolicyEnforcementPoint pep) {
	return SaplCommandBus.builder().policyEnforcementPoint(pep).build();
}
```

In case the functionality of the Axon DisruptorCommandBus will be used there is an extension available to allow the SAPL enforcement also in the DisruptorCommandBus. For this the SAPLDisruptorCommandBus shall be configured:


```java
// SaplDisruptorCommandBus    
@Bean(destroyMethod = "stop")  // destroyMethod for ExecutorService usage
public CommandBus provideSaplDisruptorCommandBus(CommandPolicyEnforcementPoint pep) {
	return SaplDisruptorCommandBus bus = SaplDisruptorCommandBus.builderSapl().pep(pep).build();
}
```

In case of using an AxonServer the @Qualifier("localSegment") needs to be added to the Bean-Annotation.

```java
// SaplCommandBus 
@Qualifier("localSegment")
@Bean(destroyMethod = "shutdown") // destroyMethod for ExecutorService usage
public CommandBus provideSaplCommandBus(CommandPolicyEnforcementPoint pep) {
	return SaplCommandBus.builder().policyEnforcementPoint(pep).build();
}
```

### Configuration for CommandHandlerEnhancer
When using a custom CommandBus or not needing the asynchronous handling of the SAPLCommandBus, there is the possibility to use a SAPL HandlerEnhancer and HandlingMember instead.
For this the integration will provide a default SAPL integration. 
The integration provides an abstract implementation of a MessageHandlingMember that delegates Commands to a wrapped MessageHandlingMember. Inside this abstract implementation the CommandPolicyEnforcementPoint is called to handle the authorization of the commands. `PreEnforce` annotation on the CommandHandler will be enforced and the Constraints will be handled before and after CommandHandling. Extend this class to provide additional functionality to the delegate member or use the default implementation provided in the integration.

To use this functionality the HandlerEnhancer needs to be configured:

```java
@Bean
public HandlerEnhancerDefinition registerCommandHandlerEnhancer(CommandPolicyEnforcementPoint pep) {
        return new DefaultSAPLCommandHandlerEnhancer(pep);
}
```

### Using SAPL on Command enforcement
For enforcing commands the CommandHandler needs to be annotated with the SAPL `PreEnforce` annotation. When the CommandHandler is annotated the command will be enforced using a CommandPolicyEnforcementPoint and the constraints are handled. The annotations allow the usage of SpEL for the parameter `action` and `resource` for building the authorization subscription. In `action` the context of the message and in `resource`the context of the aggregate can be used to build the information for the authorization subscription.
By default, the authorization subscription of a command contains of the following information:
- subject: extracted from the annotation or using the subject metadata set in the SAPL Axon Client integration
- action: SpEL in context of the message and additionally `name`, `message` and `metadata` of the CommandMessage
- resource: SpEL in context of the aggregate and additionally `aggregateType` and `aggregateIdentifier`
- environment: extracted from the annotation

#### PreEnforce

A PreEnforce Annotations enforces the given Policy before the method, which should handle the Command is called.
ConstraintHandlers are applied to the command sent. The method which should handle this Command is called and the Result is used for ConstraintHandling.
The final result is then returned and processed like any Result of a `CommandMessage`.
For more Information about ConstraintHandling refer to Section [Constraint Handling](#constraint-handling)

#### Annotation example for Command
```java
@PreEnforce
@CommandHandler
public void handle(CommandMessage command) {
    apply("Commands handled by this method are PREENFORCED");
}
```

-----------------

## Query Handling

Queries sent within the Axon Framework typical are just executed without considering who sent the Query.</br>
This integration of SAPL into the Axon Framework enables the use of SAPL to enforce access-control policies without much code.</br>
For more Information about the used Tech Stacks refer to these links

<li><a href="https://sapl.io/">SAPL</a></li>
<li><a href="https://axoniq.io/">Axon</a></li>
<li><a href="https://docs.axoniq.io/reference-guide/axon-framework/queries/query-handlers">Axon Queryhandling</a></li>

Methods in the Projection which are annotated with `@QueryHandler` are considered as potential methods to be called, when a Query is received.</br>
These Methods can be Annotated with SAPL specific Annotations to additionally enforce access-control policies on Queries, which are handled by the targeted Method.</br>
Depending on the type of Query, different Annotations are used to determine how these Queries are enforced.</br>
All possible Annotations are:

<li>@PreEnforce</li>
<li>@PostEnforce</li>
<li>@EnforceTillDenied</li>
<li>@EnforceDropWhileDenied</li>
<li>@EnforceRecoverableIfDenied</li>

Additionally, to the 2 types `Query` and `SubscriptionQuery` included in Axon, a 3rd type `RecoverableSubscriptionQuery` can be received.</br>
For more information about the RecoverableSubscription refer to the Client Package.

### SingleQuery Handling

When sending a Single Query the Annotations @PreEnforce and @PostEnforce are considered to determine how and when policies should be enforced.</br>
[PreEnforce](#preenforce) enforces access-control policies before the Method annotated with `@QueryHandler` is called and after the call on the received Result.</br>
[PostEnforce](#postenforce) enforces access-control policies after the Method annotated with `@QueryHandler` is called on the received Result.</br>
The Difference is the time, at which the `AuthorizationSubscription` is created and sent to the `PolicyDecisionPoint` and which Parameters are included in the inquiry.</br>
Both Enforcements can be combined.

#### PreEnforce

A PreEnforce Annotations enforces the given Policy before the Query is handled.
ConstraintHandlers are applied to the sent `QueryMessage` in 3 steps.
<li>MessageConstraintHandler</li>
<li>MessageConsumerHandler</li>
<li>RunnableConsumerHandler with Signal PRE_HANDLE or PRE_AND_POST_HANDLE</li>
<br>
After this phase the method which should handle this Query is called and the Result is used for further ConstraintHandling.</br>
Additional ConstraintHandler are called in the following Order.
<li>ResultMappingHandler</li>
<li>ResultConsumerMapper</li>
<li>RunnableConsumerHandler with Signal POST_HANDLE or PRE_AND_POST_HANDLE</li>


The final result is then returned and processed like any Result of a `QueryMessage`.
Up to this stage it is possible, that the access is denied because an obligation can't be handled.
For more Information about ConstraintHandling refer to Section [Constraint Handling](#constraint-handling)
Information about Obligations can be found in the <a href="https://sapl.io/">SAPL Documentation</a>

#### PostEnforce

PostEnforce enforces Policies after the method, which handles the Query is executed and a result is already present.</br>
The result is handled like it would be handled in the 2nd part of PreEnforce,
the difference to PreEnforce is, that the Result of the Query is taken into consideration in the Decision of the `PolicyDecisionPoint`.</br>
If @PreEnforce and @PostEnforce are used together, the result returned from PreEnforce is used for PostEnforce.</br>
PreEnforce and PostEnforce can be combined and PostEnforce will be enforced after PreEnforce has completed and all Constraints are handled.

#### Annotation example for single Queries

```Java
public Class QueryProjection(){
    
@PreEnforce
@QueryHandler
public String preEnforcedQuery(QueryType query){
        return "Querys handled by this method are PREENFORCED";
        }

@PostEnforce
@QueryHandler
public String postEnforcedQuery(QueryType query){
        return "Queries handled by this method are POSTENFORCED";
        }

@PreEnforce
@PostEnforce
@QueryHandler
public String preAndPostEnforcedQuery(QueryType query){
        return "Queries handled by this method are PREENFORCED and POSTENFORCED";
        }
}
```
### SubscriptionQuery Handling

When a `SubscriptionQuery` is handled, the Annotations `@PreEnforce` and `@PostenForce` are ignored and
only the Annotations `@EnforceTillDenied`, `@EnforceDropWhileDenied` and `@EnforceRecoverableIfDenied` are taken into
consideration on how this query is handled.</br>
In contrast to the Annotations @PreEnforce and @PostEnforce for single Queries, only one of the 3
possible Annotations for a SubscriptionQuery can be present.</br>
Combining Annotations for SubscriptionQuery will result in an Exception.

All the Annotations for a SubscriptionQuery are working like @PreEnforce for the initialQuery,
but differ in the behaviour, what happens, when the access is denied at any point.</br>

A SubscriptionQuery is divided into an initialQuery and a Flux on which are updates for this Query published.</br>
To Prevent Clients from ignoring the initialQuery and only expect updates, the result of the initialQuery has to be used
in order to receive updates, otherwise all updates for this SubscriptionQuery are dropped and not published.</br>

Every SubscriptionQuery will be registered in the `QueryPolicyEnforcementPoint`, because the `PolicyDecisionPoint` returns a Flux of Decisions and
the current Decision for a SubscriptionQuery can change at any point.


#### EnforceTillDenied

When a Query is annotated with @EnforceTillDenied the updateFlux is cancelled, when the access is denied at any Point.</br>
This behaviour will result in canceling the SubscriptionQuery, when the access is denied for the initialQuery.

#### EnforceDropWhileDenied

A QueryHandler annotated with @EnforceDropWhileDenied won't cancel the updateFlux, when the access is denied.</br>
When the access is denied for the initialQuery and the decisionFlux has not completed, every new Decision is checked for a Permit.</br>
When a Permit is received the initialQuery will be handled and the updateFlux will be registered in the QueryUpdateEmitter, otherwise
the handling of the initialQuery is paused until the next Decision is received or the DecisionFlux completes.

When the Decision changes, the current Decision is changed in the `QueryPolicyEnforcementPoint`. </br>
Before an update is published the current Decision has to be handled and access denied will result in the update not being published.
Every Update is handled like a result and constraints have to be handled. The Constraints which need to be handled are in the following order:</br>
<li>ResultMappingHandler</li>
<li>ResultConsumerMapper</li>
<li>RunnableConsumerHandler with Signal POST_HANDLE or PRE_AND_POST_HANDLE</li>
<br>

### RecoverableSubscriptionQuery Handling

`RecoverableSubscriptions` are handled the same way as `SubscriptionQuerys` when the method annotated with @QueryHandler is annotated with
@EnforceTillDenied or @EnforceDropWhileDenied, but are handled different, when updates are emitted and the Annotation EnforceRecoverableWhileDenied
is used.</br>

`RecoverableSubscriptions` can be used to continue the updateFlux when an error of type RecoverableExceptions is published
and the QueryHandler is annotated with @EnforceRecoverableIfDenied. Errors of this type are consumed at Client-side


#### EnforceRecoverableIfDenied

This Annotation is like @EnforceDropWhileDenied until the initialQuery is handled and `SubscriptionQuery` or `RecoverableSubscriptionQuery` are
registered in the QueryUpdateEmitter.</br>
When updates are published and access is denied a `RecoverableException` is thrown, which will be handled depending on what kind of SubscriptionQuery is registered.</br>
A `RecoverableSubscriptionQuery` has added a Key to the Metadata of the original `SubscriptionQuery` and the metadata will be checked for the Key.</br>
If the Key which signals, that this was a `RecoverableSubscriptionQuery` is present, a `RecoverableResponse` will be created and emitted as normal `SubscriptionQueryUpdateMessage`
the RecoverableResponse will be handled from the Client, which uses a SAPLQueryGateway to be able to send a `RecoverableSubscriptionQuery`.</br>
If the Key is not present the `RecoverableException` will be treated as any other Exception, resulting in the emitting of an error and closing the updateFlux.

#### Annotation example for Subscription Queries

```Java
public Class QueryProjection(){

@PreEnforce
@QueryHandler
@EnforceTillDenied
public String EnforcedTillDeniedSubscriptionQuery(QueryType query){
        return"Single Querys handled by this method are PREENFORCED and SubscriptionQueries are EnforcedTillDenied";
        }

@PostEnforce
@QueryHandler
@EnforceDropWhileDenied
public String postEnforcedSubscriptionQuery(QueryType query){
        return"Queries handled by this method are POSTENFORCED and SubscriptionQueries are EnforcedDropWhileDenied";
        }

@PreEnforce
@PostEnforce
@QueryHandler
@EnforceRecoverableIfDenied
public String RecoverableIfDeniedEnforcedSubscriptionQuery(QueryType query){
        return"Queries handled by this method are PREENFORCED and POSTENFORCED and SubscriptionQueries are EnforcedRecoverableIfDenied";
        }
        }
```

### AbstractSaplQueryHandlingMember

A `WrappedMessageHandlingMember` which handles the enforcement of Queries, this Class can be extended to implement other `WrappedMessageHandlingMembers`,
but you have to make sure that `QueryMessages` are handled by a Handler, which extends a `AbstractSaplQueryHandlingMember`.

### SaplQueryUpdateEmitter

The `SaplQueryUpdateEmitter` needs to be used in order to use SAPL for SubscriptionQueries.</br>
updates which are by default sent to each registered `SubscriptionQuery` are enforced depending on the current Decision for each registered
`SubscriptionQuery`, the according Annotation and if it is a `SubscriptionQuery` or a `RecoverableSubscriptionQuery`.

### SaplQueryBus

The `SaplQueryBus` extends the `SimpleQueryBus` by determining if a received `SubscriptionQuery` is a `RecoverableSubscriptionQuery`.
If a RecoverableSubscriptionQuery was sent, it is converted to a standard `SubscriptionQuery`, while preserving
the knowledge, that updates for this `SubscriptionQuery` should be published as a `RecoverableResponse`.

<br>

### Query Configuration

In order to use SAPL in your Axon Project for Query handling, you need to configure your Project to use 3 Components.
First you need to register a HandlerEnhancer in your Configuration, which extends the AbstractSaplQueryHandlingMember

```Java
@Bean
public HandlerEnhancerDefinition registerQueryHandlerEnhancer(QueryPolicyEnforcementPoint pep) {
return new DefaultSAPLQueryHandlerEnhancer(pep);
}
```

The HandlerEnhancer is already capable to enforce all types of Annotations for Single Queries.</br>
To use SAPL also for your `SubscriptionQueries` you need to use the provided `SaplQueryBus` and `SAPLQueryUpdateEmitter`

The QueryBus of your Project needs to be either a `SaplQueryBus`, or an `AxonServerQueryBus` with a `SaplQueryBus` as localsegment.

```Java
@Bean
public SaplQueryUpdateEmitter registerQueryUpdateEmitter(QueryPolicyEnforcementPoint policyEnforcementPoint) {
return SaplQueryUpdateEmitter.builder()
    .policyEnforcementPoint(policyEnforcementPoint)
    .build();
}

@Bean
public QueryBus registerQueryBus(SaplQueryUpdateEmitter queryUpdateEmitter) {
        SaplQueryBus saplQueryBus = SaplQueryBus.builder()
        .queryUpdateEmitter(queryUpdateEmitter)
        .build();
        return saplQueryBus;
        }
```

-----------------

## Constraint Handling
SAPL-Engine defines two kinds of so-called constraints, obligations and advices. Both are being optionally added to the 
AuthorizationDecision returned by the PDP.
Obligations must be handled during the message handling enforcement otherwise access is denied. Advices on the other 
hand should be handled but if not access will be granted anyway.

Constraint handlers are responsible for handling of these constraints. These handlers are either:
- methods in aggregate root classes or aggregate members annotated with the provided ConstraintHandler annotation, or
- functions provided by so-called ConstraintHandlerProviders

### ConstraintHandler methods in aggregate objects:

```java

@ConstraintHandler("#constraint.asText().equals('a constraint')")
public void handleConstraint(SomeCommand command, JsonNode constraint, MetaData metaData, CommandBus commandBus ) {
    // do something to handle the command, aggregate's state is available for handling
}
```
Responsibility for a combination of constraint and command can be specified via an expression as attribute value.
The expression gets evaluated via SpEl and both command and constraint are provided as variables (#constraint and #commandMessage).
If an empty string is provided constraint handler is considered always responsible.

Due to their location within aggregate objects these handlers provide access to the aggregate's state during constraint 
handling without breaking encapsulation.

Argument injection works similar to the constraint handler methods in aggregates to ensure consistency:
- highest priority has the constraint which will be injected for each parameter of type JsonNode
- other resolved parameter types are: MetaData, CommandMessage and the command payload (only as first parameter)
- additionally, Beans are also available for injection
Parameter resolution is provided by the default Axon DefaultParameterResolver and SpringBeanParameterResolverFactory. 
For more information see detailed implementations of both classes.


### ConstraintHandlers provided by ConstraintHandlerProviders:
ConstraintHandlerProviders are classes implementing one of the following interfaces:
- AxonRunnableConstraintHandlerProvider: 
  - returns a Runnable
  - point of execution during message handling can be specified
- MessageConsumerConstraintHandlerProvider: consumes message before message handling
- MessagePayloadMappingConstraintHandlerProvider: maps message payload before message handling
- MetaDataSupplierConstraintHandlerProvider: used to append message metaData before message handling
- MappingConstraintHandlerProvider: used for mapping the message handling result
- ConsumerConstraintHandlerProvider: used for consumption of message handling result

```java

@Service
@RequiredArgsConstructor
public class LogDataForPharmaceuticalStudyConstraintHandlerProvider implements
        MessageConsumerConstraintHandlerProvider<MedicalRecordAPI.UpdateMedicalRecordCommand, CommandMessage<MedicalRecordAPI.UpdateMedicalRecordCommand>> {

  private final EventBus eventBus;

  @Override
  public Consumer<CommandMessage<MedicalRecordAPI.UpdateMedicalRecordCommand>> getHandler(JsonNode constraint) {
    return this::logDataForStudy;
  }

  private void logDataForStudy(CommandMessage<?> commandMessage) {
    eventBus.publish(GenericEventMessage.asEventMessage(new MedicalRecordAPI.AuditingEvent(commandMessage)));
  }

  @Override
  public Class<MedicalRecordAPI.UpdateMedicalRecordCommand> getSupportedMessagePayloadType() {
    return MedicalRecordAPI.UpdateMedicalRecordCommand.class;
  }

  @Override
  @SuppressWarnings("rawtypes")
  public Class<CommandMessage> getSupportedMessageType() {
    return CommandMessage.class;
  }

  @Override
  public boolean isResponsible(JsonNode constraint) {
    return constraint != null && constraint.has("study");
  }
}

```

All of these HandlerProvider interfaces implement a certain subset of other interfaces that are used for fine-grained control:
- HasPriority: handlers within are executed in order within a group of similar HandlerProviders according priority
- MessagePayloadTypeSupport: Does MessageConsumer support the given MessagePayloadType?
- TypeSupport: Does e.g. MappingConstraintHandlerProvider support the given MessagePayloadType?
- Responsible: Is ConstraintHandlerProvider responsible for handling a given constraint? Returns a boolean indicating responsibility?


All classes implementing the above-mentioned interfaces must be annotated with @Service to be discovered and injected into the ConstraintHandlerService.

### ConstraintHandlerService
The ConstraintHandlerService is used by the Command- and QueryPolicyEnforcementPoint by default. So no additional configuration is required.
In case an obligation cannot be handled (either because there is no responsible handler for the given obligation or the 
handler fails during execution) an AccessDeniedException is thrown.

Furthermore, the ConstraintHandlerService handles transformed resources returned within the AuthorizationDecision. 
On the command side the aggregate is sent to the PDP as resource. 
To prevent undesired changes within the aggregate an AccessDeniedException is thrown if PDP returns a transformed resource.
On the QuerySide transformation is possible and supported.

### Constraint Handling stages and applicable HandlerProvider Types


|             |                          | Command Side                                                                                                  | Query Side                                                     |
|-------------|--------------------------|---------------------------------------------------------------------------------------------------------------|----------------------------------------------------------------|
| PreEnforce  | before handler execution | Handlers within AggregateRoot/AggregateMember, AxonRunnable, MetaDataSupplier, MessageConsumer, MessageMapper | AxonRunnable, MetaDataSupplier, MessageConsumer, MessageMapper |
|             | after handler execution  | AxonRunnable, (Result-)Consumer, (Resul-)Mapper                                                               | AxonRunnable, (Result-)Consumer, (Result-)Mapper               |
| PostEnforce |                          | -                                                                                                             | AxonRunnable, (Result-)Consumer, (Result-)Mapper               |

-----------------

### AxonConstraintHandlerBundle
This class acts as a container for responsible constraint handlers and provides methods to execute these methods during
message enforcement at the applicable stages.
