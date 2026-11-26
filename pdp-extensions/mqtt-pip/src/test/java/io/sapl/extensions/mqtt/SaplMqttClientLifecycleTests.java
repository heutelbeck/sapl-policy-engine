/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.extensions.mqtt;

import static io.sapl.api.model.ValueJsonMarshaller.json;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Answers.RETURNS_SELF;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient.Mqtt5SubscribeAndCallbackBuilder;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5UnsubscribeBuilder;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.unsuback.Mqtt5UnsubAck;

import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Poll;
import io.sapl.api.model.Value;
import io.sapl.api.stream.Cancellable;
import lombok.val;

@Timeout(10)
@ExtendWith(MockitoExtension.class)
@DisplayName("MQTT shared connection lifetime")
class SaplMqttClientLifecycleTests {

    @Mock(answer = RETURNS_SELF)
    private Mqtt5ClientBuilder                                                      builder;
    @Mock
    private Mqtt5AsyncClient                                                        async;
    @Mock
    private Mqtt5ConnAck                                                            acknowledgement;
    @Mock(answer = RETURNS_SELF)
    private Mqtt5SubscribeAndCallbackBuilder.Start.Complete                         subscription;
    @Mock
    private Mqtt5SubscribeAndCallbackBuilder.Call.Ex                                callbackBuilder;
    @Mock
    private Mqtt5UnsubscribeBuilder.Send.Start<CompletableFuture<Mqtt5UnsubAck>>    unsubscribeStart;
    @Mock
    private Mqtt5UnsubscribeBuilder.Send.Complete<CompletableFuture<Mqtt5UnsubAck>> unsubscribe;

    private record Observations(
            int liveClients,
            int disconnectsWhileSecondReaderLives,
            Value secondValue,
            Poll<Value> closedFirstReader,
            int finalClients,
            int finalDisconnects) {}

    @ValueSource(booleans = { true, false })
    @ParameterizedTest(name = "owner closes during connection establishment: {0}")
    @DisplayName("Closing the first reader leaves the shared connection with its remaining reader")
    void whenFirstReaderClosesThenSecondReaderKeepsTheSharedConnection(boolean closeDuringConnect) throws Exception {
        val connection  = new CompletableFuture<Mqtt5ConnAck>();
        val connecting  = new CountDownLatch(1);
        val subscribed  = new CountDownLatch(closeDuringConnect ? 1 : 2);
        val workers     = ConcurrentHashMap.<Thread>newKeySet();
        val callbacks   = new CopyOnWriteArrayList<Consumer<Mqtt5Publish>>();
        val disconnects = new AtomicInteger();
        when(builder.buildAsync()).thenReturn(async);
        when(async.connect()).thenAnswer(call -> {
            workers.add(Thread.currentThread());
            connecting.countDown();
            return connection;
        });
        when(async.subscribeWith()).thenReturn(subscription);
        doAnswer(call -> {
            callbacks.add(call.getArgument(0));
            return callbackBuilder;
        }).when(subscription).callback(any());
        when(callbackBuilder.send()).thenAnswer(call -> {
            workers.add(Thread.currentThread());
            subscribed.countDown();
            return CompletableFuture.completedFuture(null);
        });
        when(async.unsubscribeWith()).thenReturn(unsubscribeStart);
        when(unsubscribeStart.addTopicFilter(any(MqttTopicFilter.class))).thenReturn(unsubscribe);
        when(unsubscribe.send()).thenReturn(CompletableFuture.completedFuture(null));
        when(async.disconnect()).thenAnswer(call -> {
            disconnects.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
        val variables = (ObjectValue) json("""
                {"mqtt":{"defaultResponseTimeout":0,"brokers":{"default":{"brokerAddress":"in-memory.invalid"}}}}
                """);
        val context   = new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val clock     = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        try (val factory = mockStatic(Mqtt5Client.class);
                val client = new SaplMqttClient(clock, (at, action) -> Cancellable.NOOP)) {
            factory.when(Mqtt5Client::builder).thenReturn(builder);
            val pip = new MqttPolicyInformationPoint(client);
            try (val first = pip.messages(Value.of("room/status"), context);
                    val second = pip.messages(Value.of("room/status"), context)) {
                await(connecting);
                if (closeDuringConnect) {
                    first.close();
                }
                connection.complete(acknowledgement);
                await(subscribed);
                join(workers);
                if (!closeDuringConnect) {
                    first.close();
                }
                val liveClients       = client.cache().size();
                val activeDisconnects = disconnects.get();
                val publish           = Mqtt5Publish.builder().topic("room/status")
                        .payload("later".getBytes(StandardCharsets.UTF_8)).build();
                callbacks.forEach(callback -> callback.accept(publish));
                val secondValue = second.awaitNext(Duration.ofSeconds(1));
                val closedFirst = first.tryNext();
                second.close();
                assertThat(new Observations(liveClients, activeDisconnects, secondValue, closedFirst,
                        client.cache().size(), disconnects.get()))
                        .isEqualTo(new Observations(1, 0, Value.of("later"), Poll.done(), 0, 1));
            } finally {
                connection.complete(acknowledgement);
            }
        }
    }

    private record TopicRelease(
            int liveClients,
            int disconnectsWhileSecondReaderLives,
            List<String> releasedWhileSecondReaderLives,
            Value secondValue,
            Poll<Value> closedFirstReader,
            int finalClients,
            int finalDisconnects,
            List<String> finalReleasedTopics) {}

    @ValueSource(booleans = { true, false })
    @ParameterizedTest(name = "first reader closes before subscribe acknowledgement: {0}")
    @DisplayName("Closing a reader releases its topic even when the subscribe acknowledgement is pending")
    void whenFirstReaderClosesAroundSubscribeThenOnlyItsTopicIsReleased(boolean closeBeforeAcknowledgement)
            throws Exception {
        val pendingSubscription = new CompletableFuture<Mqtt5SubAck>();
        val firstSubscribing    = new CountDownLatch(1);
        val subscribing         = new CountDownLatch(2);
        val workers             = ConcurrentHashMap.<Thread>newKeySet();
        val callbacks           = new CopyOnWriteArrayList<Consumer<Mqtt5Publish>>();
        val releasedTopics      = new CopyOnWriteArrayList<String>();
        val subscribeCalls      = new AtomicInteger();
        val disconnects         = new AtomicInteger();
        when(builder.buildAsync()).thenReturn(async);
        when(async.connect()).thenReturn(CompletableFuture.completedFuture(acknowledgement));
        when(async.subscribeWith()).thenReturn(subscription);
        doAnswer(call -> {
            callbacks.add(call.getArgument(0));
            return callbackBuilder;
        }).when(subscription).callback(any());
        when(callbackBuilder.send()).thenAnswer(call -> {
            workers.add(Thread.currentThread());
            subscribing.countDown();
            if (subscribeCalls.getAndIncrement() == 0) {
                firstSubscribing.countDown();
                return pendingSubscription;
            }
            return CompletableFuture.completedFuture(null);
        });
        when(async.unsubscribeWith()).thenReturn(unsubscribeStart);
        when(unsubscribeStart.addTopicFilter(any(MqttTopicFilter.class))).thenAnswer(call -> {
            releasedTopics.add(call.getArgument(0).toString());
            return unsubscribe;
        });
        when(unsubscribe.send()).thenReturn(CompletableFuture.completedFuture(null));
        when(async.disconnect()).thenAnswer(call -> {
            disconnects.incrementAndGet();
            return CompletableFuture.completedFuture(null);
        });
        val variables = (ObjectValue) json("""
                {"mqtt":{"defaultResponseTimeout":0,"brokers":{"default":{"brokerAddress":"in-memory.invalid"}}}}
                """);
        val context   = new AttributeAccessContext(variables, Value.EMPTY_OBJECT, Value.EMPTY_OBJECT);
        val clock     = Clock.fixed(Instant.EPOCH, ZoneOffset.UTC);
        try (val factory = mockStatic(Mqtt5Client.class);
                val client = new SaplMqttClient(clock, (at, action) -> Cancellable.NOOP)) {
            factory.when(Mqtt5Client::builder).thenReturn(builder);
            val pip = new MqttPolicyInformationPoint(client);
            try (val first = pip.messages(Value.of("room/status"), context)) {
                await(firstSubscribing);
                try (val second = pip.messages(Value.of("room/other"), context)) {
                    if (closeBeforeAcknowledgement) {
                        first.close();
                    }
                    pendingSubscription.complete(null);
                    await(subscribing);
                    join(workers);
                    if (!closeBeforeAcknowledgement) {
                        first.close();
                    }
                    val liveClients       = client.cache().size();
                    val activeDisconnects = disconnects.get();
                    val released          = List.copyOf(releasedTopics);
                    val publish           = Mqtt5Publish.builder().topic("room/other")
                            .payload("ready".getBytes(StandardCharsets.UTF_8)).build();
                    callbacks.get(1).accept(publish);
                    val secondValue = second.awaitNext(Duration.ofSeconds(1));
                    val closedFirst = first.tryNext();
                    second.close();
                    assertThat(new TopicRelease(liveClients, activeDisconnects, released, secondValue, closedFirst,
                            client.cache().size(), disconnects.get(), List.copyOf(releasedTopics)))
                            .isEqualTo(new TopicRelease(1, 0, List.of("room/status"), Value.of("ready"), Poll.done(), 0,
                                    1, List.of("room/status", "room/other")));
                }
            } finally {
                pendingSubscription.complete(null);
                join(workers);
            }
        }
    }

    private static void await(CountDownLatch phase) throws InterruptedException {
        if (!phase.await(5, TimeUnit.SECONDS)) {
            throw new AssertionError("The ordinary client phase did not finish.");
        }
    }

    private static void join(Set<Thread> workers) throws InterruptedException {
        for (val worker : workers) {
            if (!worker.join(Duration.ofSeconds(5))) {
                throw new AssertionError("The ordinary subscription worker did not finish.");
            }
        }
    }
}
