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

package io.sapl.axon.constraints;

import static io.sapl.api.interpreter.Val.JSON;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.axonframework.commandhandling.CommandMessage;
import org.axonframework.commandhandling.GenericCommandMessage;
import org.axonframework.messaging.GenericMessage;
import org.axonframework.messaging.Message;
import org.axonframework.messaging.MetaData;
import org.axonframework.messaging.responsetypes.ResponseType;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.modelling.command.AggregateIdentifier;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.spring.stereotype.Aggregate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.security.access.AccessDeniedException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;

import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.axon.annotations.ConstraintHandler;
import io.sapl.spring.constraints.api.ConsumerConstraintHandlerProvider;
import io.sapl.spring.constraints.api.HasPriority;
import io.sapl.spring.constraints.api.MappingConstraintHandlerProvider;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Value;

class ConstraintHandlerServiceTests {

    private final static JsonNode CONSTRAINT;
    private final static ArrayNode ONE_CONSTRAINT;
    private List<AxonRunnableConstraintHandlerProvider> globalSimpleRunnableProviders;

    private List<MappingConstraintHandlerProvider<?>> globalMappingProviders;
    private List<ConsumerConstraintHandlerProvider<?>> globalConsumerProviders;

    private List<MetaDataSupplierConstraintHandlerProvider<?>> globalAddMetaDataProvider;
    private List<MessagePayloadMappingConstraintHandlerProvider<?>> globalMessageMappingProviders;
    private List<MessageConsumerConstraintHandlerProvider<?, ? extends Message<?>>> globalMessageConsumerProviders;

    static {
        CONSTRAINT = JSON.textNode("a constraint");
        ONE_CONSTRAINT = JSON.arrayNode();
        ONE_CONSTRAINT.add(CONSTRAINT);
    }


    // factory
    private ConstraintHandlerService createConstraintHandlerService() {
        return new ConstraintHandlerService(globalSimpleRunnableProviders, globalMappingProviders,
                globalConsumerProviders, globalAddMetaDataProvider, globalMessageMappingProviders,
                globalMessageConsumerProviders);
    }

    @Aggregate
    @NoArgsConstructor
    private static class TestAggregateWithRootHandlers {
    	
    	@AggregateIdentifier
        private String identifier;
    	
        @ConstraintHandler("#constraint.asText().equals('a constraint')")
        public void responsibleHandler() {
            // do something to handle constraint
        }

        @ConstraintHandler("#constraint.asText().equals('not the provided constraint')")
        public void notResponsibleHandler() {
            // do something to handle constraint
        }
    }

    private final List<Method> handlerMethodsOftestAggregateWithRootHandlers = Arrays.stream(TestAggregateWithRootHandlers.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());

    private static class AggregateMemberWithHandlers {
        @ConstraintHandler("#constraint.asText().equals('a constraint')")
        public void responsibleHandler() {
            // do something to handle constraint
        }
    }

    private final List<Method> handlerMethodsOfAggregateMemberWithHandlers = Arrays.stream(AggregateMemberWithHandlers.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());


    @Value
    private static class TestQuery {
        int offset;
        int limit;
    }

    private final ResponseType<Object> testResponseType = ResponseTypes.instanceOf(Object.class);
    private final TestQuery testQuery = new TestQuery(66, 88);
    private final QueryMessage<TestQuery, Object> testQueryMessage = new GenericQueryMessage<>(testQuery,
            testResponseType);

    @Value
    private static class TestCommand {
        int offset;
        int limit;
    }

    private final TestCommand testCommand = new TestCommand(77, 99);
    private final CommandMessage<TestCommand> testCommandMessage = GenericCommandMessage.asCommandMessage(testCommand);
    private final Message<?> testMessage = GenericMessage.asMessage(testCommand);

    @BeforeEach
    void setUp() {
        globalConsumerProviders = new ArrayList<>();
        globalMessageConsumerProviders = new ArrayList<>();
        globalSimpleRunnableProviders = new ArrayList<>();
        globalMappingProviders = new ArrayList<>();
        globalAddMetaDataProvider = new ArrayList<>();
        globalMessageMappingProviders = new ArrayList<>();
    }


    @Test
    void when_noConstraints_then_AccessIsGranted() {
        var decision = AuthorizationDecision.PERMIT;
        var service = createConstraintHandlerService();
        assertDoesNotThrow(() -> service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                 java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
    }


    @Test
    void when_resourceInDecisionOnCommandSide_then_AccessIsDenied() {
        var decision = AuthorizationDecision.PERMIT.withResource(JSON.textNode("a transformed aggregate"));
        var service = createConstraintHandlerService();
        assertThrows(AccessDeniedException.class,
                () -> service.createCommandBundle(decision, testCommandMessage, Integer.class,
                        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
    }

    @Test
    void when_noHandlers_but_obligation_then_AccessIsDenied() {
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        assertThrows(AccessDeniedException.class,
                () -> service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                        java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
    }

    @Test
    void when_noHandlers_but_queryWithObligation_then_AccessIsDenied() {

        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();

        Exception exception = assertThrows(AccessDeniedException.class,
                () -> service.createQueryBundle(decision, testMessage, testResponseType));
        assertEquals("No handler found for obligation: a constraint", exception.getMessage());
    }

    @Test
    void when_noHandlers_and_Advice_then_AccessIsGranted() {
        var decision = AuthorizationDecision.PERMIT.withAdvice(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        assertDoesNotThrow(() -> service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
    }

    @Test
    void when_obligation_and_RunnableHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        var failingProvider = spy(new AxonRunnableConstraintHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept() {
            }

            @Override
            public Signal getSignal() {
                return Signal.PRE_HANDLE;
            }
        });
        globalSimpleRunnableProviders.add(failingProvider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertDoesNotThrow(() -> bundle.executeMessageHandlers(testCommandMessage));
    }

    @Test
    void when_obligation_and_RunnableHandlerIsResponsibleWithPreAndPostSignal_andSucceeds_then_AccessIsGranted() {
        var failingProvider = spy(new AxonRunnableConstraintHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept() {
            }

            @Override
            public Signal getSignal() {
                return Signal.PRE_AND_POST_HANDLE;
            }
        });
        globalSimpleRunnableProviders.add(failingProvider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertDoesNotThrow(() -> bundle.executeMessageHandlers(testCommandMessage));
    }

    @Test
    void when_obligation_and_RunnableHandlerIsResponsible_andFails_then_AccessIsDenied() {
        var failingProvider = spy(new AxonRunnableConstraintHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept() {
                throw new RuntimeException();
            }

            @Override
            public Signal getSignal() {
                return Signal.PRE_HANDLE;
            }
        });
        globalSimpleRunnableProviders.add(failingProvider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertThrows(AccessDeniedException.class, () -> bundle.executeMessageHandlers(
                testCommandMessage));
    }

    @Test
    void when_advice_and_RunnableHandlerIsResponsible_andFails_then_AccessIsGranted() {
        var failingProvider = spy(new AxonRunnableConstraintHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept() {
                throw new RuntimeException();
            }

            @Override
            public Signal getSignal() {
                return Signal.PRE_HANDLE;
            }
        });
        globalSimpleRunnableProviders.add(failingProvider);
        var decision = AuthorizationDecision.PERMIT.withAdvice(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());

        assertDoesNotThrow(() -> bundle.executeMessageHandlers(
                testCommandMessage));
    }

    @Test
    void when_queryWithAdvice_and_RunnableHandlerIsResponsible_andFails_then_AccessIsGranted() {
        var failingProvider = spy(new AxonRunnableConstraintHandlerProvider() {
            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept() {
                throw new RuntimeException();
            }

            @Override
            public Signal getSignal() {
                return Signal.PRE_HANDLE;
            }
        });
        globalSimpleRunnableProviders.add(failingProvider);
        var decision = AuthorizationDecision.PERMIT.withAdvice(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        assertDoesNotThrow(() -> service.createQueryBundle(decision, testQueryMessage, testResponseType));
    }

    @Test
    void when_obligation_and_RunnableHandlersAreResponsible_andSucceed_then_AccessIsGranted_and_handlersCalledInOrder() {
        var highPriorityProvider = spy(new AxonRunnableConstraintHandlerProvider() {
            @Override
            public int getPriority() {
                return 1;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept() {
                // do something
            }

            @Override
            public Signal getSignal() {
                return Signal.PRE_HANDLE;
            }
        });

        var lowPriorityProvider = spy(new AxonRunnableConstraintHandlerProvider() {
            @Override
            public int getPriority() {
                return 100;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Runnable getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept() {
                // do something
            }

            @Override
            public Signal getSignal() {
                return Signal.PRE_HANDLE;
            }
        });
        globalSimpleRunnableProviders.add(lowPriorityProvider);
        globalSimpleRunnableProviders.add(highPriorityProvider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        bundle.executeMessageHandlers(testCommandMessage);
        InOrder inOrder = inOrder(highPriorityProvider, lowPriorityProvider);
        inOrder.verify(highPriorityProvider).accept();
        inOrder.verify(lowPriorityProvider).accept();
    }


    @Test
    void when_obligation_and_MetaDataSupplierIsResponsible_andSucceeds_then_AccessIsGranted() {
        var expectedMetaData = Map.of("key1", "key1Value", "key2", "key2Value");

        var provider = spy(new MetaDataSupplierConstraintHandlerProvider<TestCommand>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<? extends Message> getSupportedMessageType() {
                return CommandMessage.class;
            }

            @Override
            public Class<TestCommand> getSupportedMessagePayloadType() {
                return TestCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Supplier<Map<String, ?>> getMetaDataSupplier(JsonNode constraint) {
                return this::supply;
            }

            public Map<String, ?> supply() {
                return expectedMetaData;
            }
        });
        globalAddMetaDataProvider.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertDoesNotThrow(() -> bundle.executeMessageHandlers(testCommandMessage));

    }

    @Test
    void when_obligation_and_MetaDataSupplierIsResponsible_andFails_then_AccessIsDenied() {
        var provider = spy(new MetaDataSupplierConstraintHandlerProvider<TestCommand>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<CommandMessage> getSupportedMessageType() {
                return CommandMessage.class;
            }

            @Override
            public Class<TestCommand> getSupportedMessagePayloadType() {
                return TestCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Supplier<Map<String, ?>> getMetaDataSupplier(JsonNode constraint) {
                return this::supply;
            }

            public Map<String, ?> supply() {
                throw new RuntimeException();
            }
        });

        globalAddMetaDataProvider.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();

        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertThrows(AccessDeniedException.class,
                () -> bundle.executeMessageHandlers(testCommandMessage));
    }

    @Test
    void when_advice_and_MetaDataSupplierIsResponsible_andFails_then_AccessIsGranted() {
        var provider = spy(new MetaDataSupplierConstraintHandlerProvider<TestCommand>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<CommandMessage> getSupportedMessageType() {
                return CommandMessage.class;
            }

            @Override
            public int compareTo(HasPriority other) {
                return 0;
            }

            @Override
            public Class<TestCommand> getSupportedMessagePayloadType() {
                return TestCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Supplier<Map<String, ?>> getMetaDataSupplier(JsonNode constraint) {
                return this::supply;
            }

            public Map<String, ?> supply() {
                throw new RuntimeException();
            }
        });

        globalAddMetaDataProvider.add(provider);
        var decision = AuthorizationDecision.PERMIT.withAdvice(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertDoesNotThrow(() -> bundle.executeMessageHandlers(testCommandMessage));
    }

    @Test
    void when_queryWithObligation_and_MetaDataSupplierIsResponsible_andFails_then_AccessIsDenied() {
        var provider = spy(new MetaDataSupplierConstraintHandlerProvider<TestQuery>() {

            @Override
            public Supplier<Map<String, ?>> getMetaDataSupplier(JsonNode constraint) {
                return this::supply;
            }

            @SuppressWarnings("rawtypes")
			@Override
            public Class<QueryMessage> getSupportedMessageType() {
                return QueryMessage.class;
            }

            @Override
            public Class<TestQuery> getSupportedMessagePayloadType() {
                return TestQuery.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            public Map<String, ?> supply() {
                throw new RuntimeException();
            }
        });

        globalAddMetaDataProvider.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createQueryBundle(decision, testQueryMessage, testResponseType);
        assertThrows(AccessDeniedException.class, () -> bundle.executeMessageHandlers(testQueryMessage));
    }

    @Test
    void when_MetaDataSupplierIsResponsible_MetaDataIsAdded() {
        var expectedMetaData = Map.of("key1", "key1Value", "key2", "key2Value");

        var provider = spy(new MetaDataSupplierConstraintHandlerProvider<TestCommand>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<? extends Message> getSupportedMessageType() {
                return CommandMessage.class;
            }

            @Override
            public Class<TestCommand> getSupportedMessagePayloadType() {
                return TestCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Supplier<Map<String, ?>> getMetaDataSupplier(JsonNode constraint) {
                return this::supply;
            }

            public Map<String, ?> supply() {
                return expectedMetaData;
            }
        });
        globalAddMetaDataProvider.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        var mappedMessage = bundle.executeMessageHandlers(testCommandMessage);
        assertEquals(expectedMetaData, mappedMessage.getMetaData());
    }

    @Test
    void when_obligation_and_messageConsumerHandlerIsResponsible_andSucceeds_then_AccessIsGranted() {
        var provider = spy(new MessageConsumerConstraintHandlerProvider<TestCommand, CommandMessage<TestCommand>>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<CommandMessage> getSupportedMessageType() {
                return CommandMessage.class;
            }

            @Override
            public Class<TestCommand> getSupportedMessagePayloadType() {
                return TestCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Consumer<CommandMessage<TestCommand>> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Message<?> commandMessage) { // do something with the commandMessage e.g. logging
            }
        });

        globalMessageConsumerProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        assertDoesNotThrow(() -> service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
    }

    @Test
    void when_obligation_and_messageConsumerHandlerIsResponsible_andFails_then_AccessIsDenied() {
        var provider = spy(new MessageConsumerConstraintHandlerProvider<TestCommand, CommandMessage<TestCommand>>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<CommandMessage> getSupportedMessageType() {
                return CommandMessage.class;
            }

            @Override
            public Class<TestCommand> getSupportedMessagePayloadType() {
                return TestCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Consumer<CommandMessage<TestCommand>> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Message<?> commandMessage) {
                throw new ArithmeticException();
            }
        });

        globalMessageConsumerProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertThrows(AccessDeniedException.class, () -> bundle.executeMessageHandlers(testCommandMessage));
    }

    @Test
    void when_advice_and_messageConsumerHandlerIsResponsible_andFails_then_AccessIsGranted() {
        var provider = spy(new MessageConsumerConstraintHandlerProvider<TestCommand, CommandMessage<TestCommand>>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<CommandMessage> getSupportedMessageType() {
                return CommandMessage.class;
            }

            @Override
            public Class<TestCommand> getSupportedMessagePayloadType() {
                return TestCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Consumer<CommandMessage<TestCommand>> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Message<?> commandMessage) {
                throw new ArithmeticException();
            }
        });

        globalMessageConsumerProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withAdvice(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        assertDoesNotThrow(() -> service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
    }

    @Test
    void when_queryWithObligation_and_MessageConsumerConstraintHandlerProviderIsResponsible_andFails_then_AccessDenied() {
        var provider = spy(new MessageConsumerConstraintHandlerProvider<TestQuery, QueryMessage<TestQuery, ?>>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<QueryMessage> getSupportedMessageType() {
                return QueryMessage.class;
            }

            @Override
            public Class<TestQuery> getSupportedMessagePayloadType() {
                return TestQuery.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            public void accept(Message<?> queryMessage) {
                throw new RuntimeException();
            }

            @Override
            public Consumer<QueryMessage<TestQuery, ?>> getHandler(JsonNode constraint) {
                return this::accept;
            }
        });

        globalMessageConsumerProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createQueryBundle(decision, testQueryMessage, testResponseType);
        assertThrows(AccessDeniedException.class, () -> bundle.executeMessageHandlers(testQueryMessage));
    }


    @Test
    void when_messageMappingHandler_isInvoked_withCommandMessage_andSucceeds_MessageIsMapped() {
        var provider = spy(new MessagePayloadMappingConstraintHandlerProvider<TestCommand>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<Message> getSupportedMessageType() {
                return Message.class;
            }

            @Override
            public Class<TestCommand> getSupportedMessagePayloadType() {
                return TestCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Function<TestCommand, TestCommand> getHandler(JsonNode constraint) {
                return this::map;
            }

            public TestCommand map(TestCommand command) {
                try {
                    Field field = TestCommand.class.getDeclaredField("limit");
                    field.setAccessible(true);
                    field.setInt(command, 111);
                } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException
                        | SecurityException e) {
                    //
                }
                return command;
            }
        });

        globalMessageMappingProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        var mappedMessage = bundle.executeMessageHandlers(testCommandMessage);

        verify(provider, times(1)).map(testCommand);
        assertEquals((mappedMessage.getPayload()).getLimit(), 111);
    }

    @Test
    void when_messageMappingHandler_isInvoked_withQueryMessage_andSucceeds_MessageIsMapped() {
        var provider = spy(new MessagePayloadMappingConstraintHandlerProvider<TestQuery>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<Message> getSupportedMessageType() {
                return Message.class;
            }

            @Override
            public Class<TestQuery> getSupportedMessagePayloadType() {
                return TestQuery.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Function<TestQuery, TestQuery> getHandler(JsonNode constraint) {
                return this::map;
            }

            public TestQuery map(TestQuery query) {
                try {
                    Field field = TestQuery.class.getDeclaredField("limit");
                    field.setAccessible(true);
                    field.setInt(query, 111);
                } catch (IllegalArgumentException | IllegalAccessException | NoSuchFieldException
                        | SecurityException e) {
                    //
                }
                return query;
            }
        });

        globalMessageMappingProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createQueryBundle(decision, testQueryMessage, testResponseType);
        var mappedMessage = bundle.executeMessageHandlers(testQueryMessage);

        verify(provider, times(1)).map(testQuery);
        assertEquals((mappedMessage.getPayload()).getLimit(), 111);
    }

    @Test
    void when_messageMappingHandler_isResponsible_butNotSupportedPayloadType_itIsNotConsidered() {
        class SomeOtherCommand {

        }

        var provider = spy(new MessagePayloadMappingConstraintHandlerProvider<SomeOtherCommand>() {

            @SuppressWarnings("rawtypes")
            @Override
            public Class<Message> getSupportedMessageType() {
                return Message.class;
            }

            @Override
            public Class<SomeOtherCommand> getSupportedMessagePayloadType() {
                return SomeOtherCommand.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Function<SomeOtherCommand, SomeOtherCommand> getHandler(JsonNode constraint) {
                return this::map;
            }

            public SomeOtherCommand map(SomeOtherCommand command) {
                return command;
            }
        });

        globalMessageMappingProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        assertThrows(AccessDeniedException.class, () -> service.createCommandBundle(decision, testCommandMessage, testResponseType.getClass(),
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty()));
        verify(provider, times(0)).getHandler(any());
    }



    @Test
    void when_queryWithObligation_and_MessagePayloadMappingConstraintHandlerProviderIsResponsible_andFails_then_AccessDenied() {
        var provider = spy(new MessagePayloadMappingConstraintHandlerProvider<TestQuery>() {

            @SuppressWarnings("rawtypes")
			@Override
            public Class<QueryMessage> getSupportedMessageType() {
                return QueryMessage.class;
            }

            @Override
            public Class<TestQuery> getSupportedMessagePayloadType() {
                return TestQuery.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            public TestQuery map(TestQuery query) {
                throw new RuntimeException();
            }

            @Override
            public Function<TestQuery, TestQuery> getHandler(JsonNode constraint) {
                return this::map;
            }
        });

        globalMessageMappingProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createQueryBundle(decision, testQueryMessage, testResponseType);
        assertThrows(AccessDeniedException.class, () -> bundle.executeMessageHandlers(testQueryMessage));
    }


    @Test
    void when_obligation_andResultConsumerConstraintHandlerProviderIsResponsible_andSucceeds_AccessIsGranted() {
        var provider = spy(new ConsumerConstraintHandlerProvider<Integer>() {

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Consumer<Integer> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Integer result) { // do something with the result e.g. logging
            }
        });

        globalConsumerProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        int result = 1;
        var bundle = service.createCommandBundle(decision, testCommandMessage, Integer.class,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertDoesNotThrow(() -> bundle.executeResultHandlerProvider(result));
    }

    @Test
    void when_obligation_andResultConsumerConstraintHandlerProviderIsResponsible_andFails_AccessIsDenied() {
        var provider = spy(new ConsumerConstraintHandlerProvider<Integer>() {

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Consumer<Integer> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Integer result) {
                throw new ArithmeticException();
            }
        });

        globalConsumerProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        int result = 1;
        var bundle = service.createCommandBundle(decision, testCommandMessage, Integer.class,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertThrows(AccessDeniedException.class, () -> bundle.executeResultHandlerProvider(result));
    }

    @Test
    void when_advice_andResultConsumerConstraintHandlerProviderIsResponsible_andFails_AccessIsGranted() {
        var provider = spy(new ConsumerConstraintHandlerProvider<Integer>() {

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return true;
            }

            @Override
            public Consumer<Integer> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Integer result) {
                throw new ArithmeticException();
            }
        });

        globalConsumerProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withAdvice(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        int result = 1;
        var bundle = service.createCommandBundle(decision, testCommandMessage, Integer.class,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertDoesNotThrow(() -> bundle.executeResultHandlerProvider(result));
    }


    @Test
    void when_resultConsumerConstraintHandlerProviderIsResponsible_itIsInvoked_with_commandResult() {
        var provider = spy(new ConsumerConstraintHandlerProvider<Integer>() {

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Consumer<Integer> getHandler(JsonNode constraint) {
                return this::accept;
            }

            public void accept(Integer result) { // do something with the result e.g. logging
            }
        });

        globalConsumerProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        int result = 1;
        var bundle = service.createCommandBundle(decision, testCommandMessage, Integer.class,
                java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        bundle.executeResultHandlerProvider(result);
        verify(provider, times(1)).accept(result);
    }


    @Test
    void when_obligation_andResultMappingConstraintHandlerProvider_isResponsible_andSucceeds_accessIsGranted() {
        var provider = spy(new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Function<Integer, Integer> getHandler(JsonNode constraint) {
                return this::apply;
            }

            public Integer apply(Integer result) { // do something with the result e.g. logging
                return result + 1;
            }
        });

        globalMappingProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testMessage,
                Integer.class, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertDoesNotThrow(() -> bundle.executeResultHandlerProvider(1));
    }

    @Test
    void when_obligation_andResultMappingConstraintHandlerProvider_isResponsible_andFails_accessIsDenied() {
        var provider = spy(new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Function<Integer, Integer> getHandler(JsonNode constraint) {
                return this::apply;
            }

            public Integer apply(Integer result) {
                throw new ArithmeticException();
            }
        });

        globalMappingProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testMessage,
                Integer.class, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertThrows(AccessDeniedException.class, () -> bundle.executeResultHandlerProvider(1));
    }

    @Test
    void when_advice_andResultMappingConstraintHandlerProvider_isResponsible_andFails_accessIsGranted() {
        var provider = spy(new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Function<Integer, Integer> getHandler(JsonNode constraint) {
                return this::apply;
            }

            public Integer apply(Integer result) {
                throw new ArithmeticException();
            }
        });

        globalMappingProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withAdvice(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var bundle = service.createCommandBundle(decision, testMessage,
                Integer.class, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());
        assertDoesNotThrow(() -> bundle.executeResultHandlerProvider(1));
    }

    @Test
    void when_resultMappingConstraintHandlerProviderIsResponsible_resultIsMappedAccordingly() {
        var provider = spy(new MappingConstraintHandlerProvider<Integer>() {

            @Override
            public Class<Integer> getSupportedType() {
                return Integer.class;
            }

            @Override
            public boolean isResponsible(JsonNode constraint) {
                return constraint.asText().equals("a constraint");
            }

            @Override
            public Function<Integer, Integer> getHandler(JsonNode constraint) {
                return this::apply;
            }

            public Integer apply(Integer result) { // do something with the result e.g. logging
                return result + 1;
            }
        });

        globalMappingProviders.add(provider);
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var result = 1;
        var bundle = service.createCommandBundle(decision, testMessage,
                Integer.class, java.util.Optional.empty(), java.util.Optional.empty(), java.util.Optional.empty());

        var resultMapped = bundle.executeResultHandlerProvider(result);
        verify(provider, times(1)).apply(1);
        assertEquals(2, resultMapped);
    }


    @Test
    void when_aggregateConstraintHandlerAnnotation_isEmptyString_itIsConsideredResponsible() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithRootHandler {
            @AggregateIdentifier
            private String id;
            @ConstraintHandler("   ")
            public void responsibleHandler() {
                // do something to handle constraint
            }
        }

        List<Method> handlerMethods = Arrays.stream(TestAggregateWithRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();

        assertDoesNotThrow(() -> service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                Optional.of(handlerMethods), java.util.Optional.empty(),
                java.util.Optional.of(Collections.emptyList())));
    }

    @Test
    void nonResponsibleHandlerProvider_withinAggregateRoot_isNotUsed() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithNoResponsibleRootHandler {
            @AggregateIdentifier
            private String id;
            @ConstraintHandler("#constraint.asText().equals('not the provided constraint')")
            public void notResponsibleHandler() {
                // do something to handle constraint
            }
        }
        List<Method> handlerMethods = Arrays.stream(TestAggregateWithNoResponsibleRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();

        assertThrows(AccessDeniedException.class,
                () -> service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                        Optional.of(handlerMethods), java.util.Optional.empty(),
                        java.util.Optional.of(Collections.emptyList())));
    }

    @Test
    void when_aggregateConstraintHandlerAnnotationUsesNonProvidedVariable_exceptionIsCaughtAndConsideredNotResponsible() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithNoResponsibleRootHandler {
            @AggregateIdentifier
            private String id;
            @ConstraintHandler("#someNonAvailableVariable")
            public void notResponsibleHandler() {
            }
        }
        List<Method> handlerMethods = Arrays.stream(TestAggregateWithNoResponsibleRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();

        assertThrows(AccessDeniedException.class,
                () -> service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                        Optional.of(handlerMethods), java.util.Optional.empty(),
                        java.util.Optional.of(Collections.emptyList())));
    }

    @Test
    void when_aggregateConstraintHandlerAnnotationUsesRootContext_exceptionIsCaughtAndConsideredNotResponsible() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithNoResponsibleRootHandler {
            @AggregateIdentifier
            private String id;
            @ConstraintHandler("asText()")
            public void notResponsibleHandler() {
            }
        }
        List<Method> handlerMethods = Arrays.stream(TestAggregateWithNoResponsibleRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();

        assertThrows(AccessDeniedException.class,
                () -> service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                        Optional.of(handlerMethods), java.util.Optional.empty(),
                        java.util.Optional.of(Collections.emptyList())));
    }

    @Test
    void responsibleHandlerProvider_withinAggregateRoot_isAddedToBundle() {
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();

        Optional<Object> entity = Optional.of(new AggregateMemberWithHandlers());

        assertDoesNotThrow(
                () -> service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                        java.util.Optional.of(Collections.emptyList()), entity,
                        Optional.of(handlerMethodsOfAggregateMemberWithHandlers))
        );
    }

    @Test
    void responsibleHandlerProvider_withinAggregateMember_isAddedToBundle() {
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();

        assertDoesNotThrow(
                () -> service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                        Optional.of(handlerMethodsOftestAggregateWithRootHandlers), java.util.Optional.empty(),
                        java.util.Optional.of(Collections.emptyList()))
        );
    }

    @Test
    void when_obligation_and_AggregateRootHandlerMethodIsResponsible_andSucceeds_then_AccessIsGranted() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithRootHandler {
            @AggregateIdentifier
            private String id;

            @ConstraintHandler("#constraint.asText().equals('a constraint')")
            public void responsibleHandler() {
                // do something to handle constraint
            }
        }

        List<Method> handlerMethods = Arrays.stream(TestAggregateWithRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var aggregate = new TestAggregateWithRootHandler();

        var bundle = service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                Optional.of(handlerMethods), java.util.Optional.empty(),
                java.util.Optional.of(Collections.emptyList()));
        assertDoesNotThrow(
                () -> bundle.invokeAggregateConstraintHandlerMethods(aggregate, java.util.Optional.empty(), testMessage, null)
        );
    }

    @Test
    void when_obligation_and_AggregateRootHandlerMethodIsResponsible_andFails_then_AccessIsDenied() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithFailingRootHandler {
            @AggregateIdentifier
            private String id;
            @ConstraintHandler("#constraint.asText().equals('a constraint')")
            public void responsibleHandler() {
                // do something to handle constraint
                throw new ArithmeticException();
            }
        }

        List<Method> handlerMethods = Arrays.stream(TestAggregateWithFailingRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var aggregate = new TestAggregateWithFailingRootHandler();

        var bundle = service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                Optional.of(handlerMethods), java.util.Optional.empty(),
                java.util.Optional.of(Collections.emptyList()));
        assertThrows(AccessDeniedException.class,
                () -> bundle.invokeAggregateConstraintHandlerMethods(aggregate, java.util.Optional.empty(), testMessage, null)
        );
    }

    @Test
    void when_advice_and_AggregateRootHandlerMethodIsResponsible_andFails_then_AccessIsGranted() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithFailingRootHandler {
            @AggregateIdentifier
            private String id;
            @ConstraintHandler("#constraint.asText().equals('a constraint')")
            public void responsibleHandler() {
                // do something to handle constraint
                throw new ArithmeticException();
            }
        }

        List<Method> handlerMethods = Arrays.stream(TestAggregateWithFailingRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withAdvice(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var aggregate = new TestAggregateWithFailingRootHandler();

        var bundle = service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                Optional.of(handlerMethods), java.util.Optional.empty(),
                java.util.Optional.of(Collections.emptyList()));
        assertDoesNotThrow(
                () -> bundle.invokeAggregateConstraintHandlerMethods(aggregate, java.util.Optional.empty(), testMessage, null)
        );
    }

    @Test
    void when_aggregateRootHandlerMethodIsResponsible_andNeedsParameters_then_ParametersAreResolved_and_AccessIsGranted() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithRootHandler {
            @AggregateIdentifier
            private String id;
            @ConstraintHandler("#constraint.asText().equals('a constraint')")
            public void responsibleHandler( TestCommand command, MetaData metaData, JsonNode constraint) {
                // do something to handle constraint
            }
        }

        Map<String, String> metaData = new HashMap<>();
        List<Method> handlerMethods = Arrays.stream(TestAggregateWithRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var aggregate = spy(new TestAggregateWithRootHandler());
        var testMessage = GenericMessage.asMessage(testCommand).withMetaData(metaData);
        var bundle = service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                Optional.of(handlerMethods), java.util.Optional.empty(),
                java.util.Optional.of(Collections.emptyList()));

        assertDoesNotThrow(
                () -> bundle.invokeAggregateConstraintHandlerMethods(aggregate, java.util.Optional.empty(), testMessage, null)
        );
        verify(aggregate, times(1)).responsibleHandler(testCommand, testMessage.getMetaData(), CONSTRAINT);
    }

    @Test
    void when_aggregateRootHandlerMethodIsResponsible_andNeedsParameters_andIfOneCannotBeResolved_then_AccessIsDenied() {
        class NotResolvable {
            private void doSomething() {}
        }
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithRootHandler {
            @AggregateIdentifier
            private String id;

            @ConstraintHandler("#constraint.asText().equals('a constraint')")
            public void responsibleHandler(TestCommand command, NotResolvable notResolvable) {
                // do something to handle constraint
                notResolvable.doSomething();
            }
        }

        Map<String, String> metaData = new HashMap<>();
        List<Method> handlerMethods = Arrays.stream(TestAggregateWithRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var aggregate = spy(new TestAggregateWithRootHandler());
        var testMessage = GenericMessage.asMessage(testCommand).withMetaData(metaData);
        var bundle = service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                Optional.of(handlerMethods), java.util.Optional.empty(),
                java.util.Optional.of(Collections.emptyList()));

        assertThrows(AccessDeniedException.class,
                () -> bundle.invokeAggregateConstraintHandlerMethods(aggregate, java.util.Optional.empty(), testMessage, null)
        );
    }

    @Test
    void when_aggregateRootHandlerMethodIsResponsible_andNeedsNonResolvableParameter_then_AccessIsDenied() {
        class SomeOtherClass {
        }
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithRootHandler {
            @AggregateIdentifier
            private String id;
            @ConstraintHandler("#constraint.asText().equals('a constraint')")
            public void responsibleHandler(SomeOtherClass foo) {
                // do something to handle constraint
            }
        }

        List<Method> handlerMethods = Arrays.stream(TestAggregateWithRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var aggregate = new TestAggregateWithRootHandler();
        var bundle = service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                Optional.of(handlerMethods), java.util.Optional.empty(),
                java.util.Optional.of(Collections.emptyList()));

        assertThrows(AccessDeniedException.class,
                () -> bundle.invokeAggregateConstraintHandlerMethods(aggregate, java.util.Optional.empty(), testMessage, null)
        );
    }

    @Test
    void when_obligation_and_AggregateMemberHandlerMethodIsResponsible_andSucceeds_then_AccessIsGranted() {
        @Aggregate
        @NoArgsConstructor
        class TestAggregateWithoutRootHandler {
            @AggregateIdentifier
            private String id;

        }

        @Getter
        class TestAggregateMemberWithHandler {
            private String id;
            @ConstraintHandler("#constraint.asText().equals('a constraint')")
            public void responsibleHandler() {
                // do something to handle constraint
            }
        }

        List<Method> handlerMethods = Arrays.stream(TestAggregateWithoutRootHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        List<Method> entityMethods = Arrays.stream(TestAggregateMemberWithHandler.class.getDeclaredMethods()).filter(m -> m.isAnnotationPresent(ConstraintHandler.class)).collect(Collectors.toList());
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var aggregate = new TestAggregateWithoutRootHandler();
        var entity = spy(new TestAggregateMemberWithHandler());

        var bundle = service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                Optional.of(handlerMethods), Optional.of(entity),
                Optional.of(entityMethods));
        assertDoesNotThrow(
                () -> bundle.invokeAggregateConstraintHandlerMethods(aggregate, Optional.of(entity), testMessage, null)
        );
        verify(entity, times(1)).responsibleHandler();
    }

    @Test
    void when_obligation_and_noAggregateConstraintHandlerMethods_andNoAggregateMemberConstraintHandlerMethods_then_AccessIsDenied() {
        class TestAggregateMemberWithoutHandler {

        }

        Optional<List<Method>> handlerMethods = Optional.empty();
        Optional <List<Method>> entityMethods = Optional.empty();
        var decision = AuthorizationDecision.PERMIT.withObligations(ONE_CONSTRAINT);
        var service = createConstraintHandlerService();
        var entity = spy(new TestAggregateMemberWithoutHandler());

        assertThrows(AccessDeniedException.class, () -> service.createCommandBundle(decision, testMessage, testResponseType.getClass(),
                handlerMethods, Optional.of(entity), entityMethods));
    }
}