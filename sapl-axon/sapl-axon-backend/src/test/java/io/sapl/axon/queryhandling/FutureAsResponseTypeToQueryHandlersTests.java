/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.sapl.axon.queryhandling;

import static java.util.Arrays.asList;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import org.axonframework.messaging.Message;
import org.axonframework.messaging.responsetypes.ResponseTypes;
import org.axonframework.queryhandling.GenericQueryMessage;
import org.axonframework.queryhandling.QueryHandler;
import org.axonframework.queryhandling.QueryMessage;
import org.axonframework.queryhandling.annotation.AnnotationQueryHandlerAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

/**
 * Tests for different types of queries hitting query handlers with Future as a response type.
 *
 */
class FutureAsResponseTypeToQueryHandlersTests {

    private static final int FUTURE_RESOLVING_TIMEOUT = 500;

    private final SaplQueryBus queryBus = SaplQueryBus.builder().queryUpdateEmitter(mock(SaplQueryUpdateEmitter.class)).build();
    private final MyQueryHandler myQueryHandler = new MyQueryHandler();
    private final AnnotationQueryHandlerAdapter<MyQueryHandler> annotationQueryHandlerAdapter = new AnnotationQueryHandlerAdapter<>(
            myQueryHandler);

    @BeforeEach
    void setUp() {
        var logger = (Logger) LoggerFactory.getLogger("ROOT");
        logger.setLevel(Level.ERROR);
        annotationQueryHandlerAdapter.subscribe(queryBus);
    }

    @Test
    void testQueryWithMultipleResponses() throws ExecutionException, InterruptedException {
        QueryMessage<String, List<String>> queryMessage = new GenericQueryMessage<>(
                "criteria", "myQueryWithMultipleResponses", ResponseTypes.multipleInstancesOf(String.class));

        List<String> response = queryBus.query(queryMessage).get().getPayload();

        assertEquals(asList("Response1", "Response2"), response);
    }

    @Test
    void testQueryWithSingleResponse() throws ExecutionException, InterruptedException {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>(
                "criteria", "myQueryWithSingleResponse", ResponseTypes.instanceOf(String.class));

        String response = queryBus.query(queryMessage).get().getPayload();

        assertEquals("Response", response);
    }

    @Test
    void testScatterGatherQueryWithMultipleResponses() {
        QueryMessage<String, List<String>> queryMessage = new GenericQueryMessage<>(
                "criteria", "myQueryWithMultipleResponses", ResponseTypes.multipleInstancesOf(String.class));

        List<String> response = queryBus
                .scatterGather(queryMessage, FUTURE_RESOLVING_TIMEOUT * 2, TimeUnit.MILLISECONDS)
                .map(Message::getPayload)
                .flatMap(Collection::stream)
                .collect(Collectors.toList());

        assertEquals(asList("Response1", "Response2"), response);
    }

    @Test
    void testScatterGatherQueryWithSingleResponse() {
        QueryMessage<String, String> queryMessage = new GenericQueryMessage<>(
                "criteria", "myQueryWithSingleResponse", ResponseTypes.instanceOf(String.class));

        String response = queryBus
                .scatterGather(queryMessage, FUTURE_RESOLVING_TIMEOUT + 100, TimeUnit.MILLISECONDS)
                .map(Message::getPayload)
                .findFirst()
                .orElse(null);

        assertEquals("Response", response);
    }

    @Test
    void testFutureQueryWithMultipleResponses() throws ExecutionException, InterruptedException {
        QueryMessage<String, List<String>> queryMessage = new GenericQueryMessage<>(
                "criteria", "myQueryFutureWithMultipleResponses", ResponseTypes.multipleInstancesOf(String.class));

        List<String> result = queryBus.query(queryMessage).get().getPayload();

        assertEquals(asList("Response1", "Response2"), result);
    }

    @Test
    void testFutureScatterGatherQueryWithMultipleResponses() {
        QueryMessage<String, List<String>> queryMessage = new GenericQueryMessage<>(
                "criteria", "myQueryFutureWithMultipleResponses", ResponseTypes.multipleInstancesOf(String.class));

        List<String> result = queryBus
                .scatterGather(queryMessage, FUTURE_RESOLVING_TIMEOUT + 100, TimeUnit.MILLISECONDS)
                .map(Message::getPayload)
                .findFirst()
                .orElse(null);

        assertEquals(asList("Response1", "Response2"), result);
    }

    private static class MyQueryHandler {

        private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

        @QueryHandler(queryName = "myQueryWithMultipleResponses")
        public CompletableFuture<List<String>> queryHandler1(String criteria) {
            CompletableFuture<List<String>> completableFuture = new CompletableFuture<>();
            executor.schedule(() -> completableFuture.complete(asList("Response1", "Response2")),
                              FUTURE_RESOLVING_TIMEOUT,
                              TimeUnit.MILLISECONDS);
            return completableFuture;
        }

        @QueryHandler(queryName = "myQueryWithSingleResponse")
        public Future<String> queryHandler2(String criteria) {
            return executor.schedule(() -> "Response",
                                     FUTURE_RESOLVING_TIMEOUT,
                                     TimeUnit.MILLISECONDS);
        }

        @QueryHandler(queryName = "myQueryFutureWithMultipleResponses")
        public Future<List<String>> queryHandler3(String criteria) {
            return executor.schedule(() -> asList("Response1", "Response2"),
                                     FUTURE_RESOLVING_TIMEOUT,
                                     TimeUnit.MILLISECONDS);
        }
    }
}
