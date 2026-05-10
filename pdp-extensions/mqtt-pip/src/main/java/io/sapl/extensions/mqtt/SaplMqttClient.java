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

import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import io.sapl.api.attributes.AttributeAccessContext;
import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.TextValue;
import io.sapl.api.model.UndefinedValue;
import io.sapl.api.model.Value;
import io.sapl.api.model.ValueJsonMarshaller;
import io.sapl.api.stream.Cancellable;
import io.sapl.api.stream.RealTimeScheduler;
import io.sapl.api.stream.Stream;
import io.sapl.api.stream.Streams;
import io.sapl.api.stream.TimeScheduler;
import io.sapl.extensions.mqtt.util.DefaultResponseConfig;
import io.sapl.extensions.mqtt.util.MqttClientValues;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

import java.io.Closeable;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import static io.sapl.extensions.mqtt.util.ConfigUtility.getClientId;
import static io.sapl.extensions.mqtt.util.ConfigUtility.getConfigValueOrDefault;
import static io.sapl.extensions.mqtt.util.ConfigUtility.getMqttBrokerConfig;
import static io.sapl.extensions.mqtt.util.ConfigUtility.getPassword;
import static io.sapl.extensions.mqtt.util.ConfigUtility.getQos;
import static io.sapl.extensions.mqtt.util.DefaultResponseUtility.getDefaultResponseConfig;
import static io.sapl.extensions.mqtt.util.DefaultResponseUtility.getDefaultValue;
import static io.sapl.extensions.mqtt.util.PayloadFormatUtility.convertBytesToArrayValue;
import static io.sapl.extensions.mqtt.util.PayloadFormatUtility.getContentType;
import static io.sapl.extensions.mqtt.util.PayloadFormatUtility.getPayloadFormatIndicator;
import static io.sapl.extensions.mqtt.util.PayloadFormatUtility.getValueOfJson;
import static io.sapl.extensions.mqtt.util.PayloadFormatUtility.isValidUtf8String;
import static io.sapl.extensions.mqtt.util.SubscriptionUtility.topicFilters;

/**
 * Subscribes to MQTT topics on a HiveMQ MQTT 5 broker and exposes the
 * incoming publish messages as a {@link Stream}{@code <Value>}.
 * Connections are reference-counted per broker configuration so that
 * multiple PIP invocations against the same broker share one client.
 * Reconnect on disconnect is delegated to HiveMQ's built-in
 * automatic-reconnect (default settings).
 */
@Slf4j
@RequiredArgsConstructor
public class SaplMqttClient implements Closeable {

    /** Configuration key for the MQTT client identifier. */
    public static final String   ENVIRONMENT_CLIENT_ID          = "clientId";
    /** Configuration key for the MQTT broker host. */
    public static final String   ENVIRONMENT_BROKER_ADDRESS     = "brokerAddress";
    /** Configuration key for the MQTT broker port. */
    public static final String   ENVIRONMENT_BROKER_PORT        = "brokerPort";
    /** Configuration key controlling sentinel emission on reconnect. */
    public static final String   ENVIRONMENT_EMIT_AT_RETRY      = "emitAtRetry";
    private static final String  ENVIRONMENT_MQTT_PIP_CONFIG    = "mqttPipConfig";
    private static final String  ENVIRONMENT_USERNAME           = "username";
    private static final String  ENVIRONMENT_BROKER_CONFIG_NAME = "name";
    private static final String  ENVIRONMENT_QOS                = "defaultQos";
    private static final String  DEFAULT_CLIENT_ID              = "mqtt_pip";
    private static final String  DEFAULT_USERNAME               = "";
    private static final String  DEFAULT_BROKER_ADDRESS         = "localhost";
    private static final int     DEFAULT_BROKER_PORT            = 1883;
    private static final int     DEFAULT_QOS                    = 0;
    private static final String  SECRETS_MQTT                   = "mqtt";
    private static final String  SECRETS_PASSWORD               = "password";
    private static final boolean DEFAULT_EMIT_AT_RETRY          = true;

    private static final Duration CONNECT_TIMEOUT     = Duration.ofSeconds(10L);
    private static final Duration SUBSCRIBE_TIMEOUT   = Duration.ofSeconds(5L);
    private static final Duration UNSUBSCRIBE_TIMEOUT = Duration.ofSeconds(5L);
    private static final Duration DISCONNECT_TIMEOUT  = Duration.ofSeconds(5L);

    private static final String ERROR_MQTT_CONNECT_FAILED = "Failed to connect or subscribe to MQTT broker: %s";

    static final ConcurrentHashMap<Integer, MqttClientValues> MQTT_CLIENT_CACHE = new ConcurrentHashMap<>();

    private final Clock         clock;
    private final TimeScheduler scheduler;

    public Stream<Value> buildSaplMqttMessageStream(Value topic, AttributeAccessContext ctx) {
        return buildSaplMqttMessageStream(topic, ctx, null, Value.UNDEFINED);
    }

    public Stream<Value> buildSaplMqttMessageStream(Value topic, AttributeAccessContext ctx, Value qos) {
        return buildSaplMqttMessageStream(topic, ctx, qos, Value.UNDEFINED);
    }

    public Stream<Value> buildSaplMqttMessageStream(Value topic, AttributeAccessContext ctx, Value qos,
            Value mqttPipConfigVal) {
        try {
            val variables             = ctx.variables();
            val pdpSecrets            = ctx.pdpSecrets();
            val pipConfig             = resolvePipConfig(variables);
            val brokerConfig          = getMqttBrokerConfig(pipConfig, mqttPipConfigVal);
            val effectiveQos          = getQos(
                    qos != null ? qos : Value.of(getConfigValueOrDefault(pipConfig, ENVIRONMENT_QOS, DEFAULT_QOS)));
            val filters               = topicFilters(topic);
            val defaultResponseConfig = getDefaultResponseConfig(pipConfig, mqttPipConfigVal);
            val defaultValue          = getDefaultValue(defaultResponseConfig);
            val timeoutMs             = defaultResponseConfig.getDefaultResponseTimeout();
            val emitAtRetry           = getConfigValueOrDefault(pipConfig, ENVIRONMENT_EMIT_AT_RETRY,
                    DEFAULT_EMIT_AT_RETRY);

            return openSubscription(brokerConfig.hashCode(), brokerConfig, pipConfig, pdpSecrets, filters, effectiveQos,
                    defaultValue, timeoutMs, emitAtRetry);
        } catch (RuntimeException e) {
            return Streams.error(messageOf(e));
        }
    }

    private Stream<Value> openSubscription(int brokerHash, ObjectNode brokerConfig, JsonNode pipConfig,
            ObjectValue pdpSecrets, List<MqttTopicFilter> filters, MqttQos qos, Value defaultValue, long timeoutMs,
            boolean emitAtRetry) {
        return Streams.fromCallback((emit, complete) -> {
            val cached = MQTT_CLIENT_CACHE.computeIfAbsent(brokerHash,
                    h -> buildClientValues(brokerConfig, pipConfig, pdpSecrets));
            cached.incrementBrokerSubscribers();

            val firstMessage = new AtomicBoolean(false);
            val closed       = new AtomicBoolean(false);
            val ctx          = new SubscriptionContext(cached, cached.getMqttAsyncClient(), filters, qos, closed);

            val onDisconnect = registerDisconnectListener(cached, emitAtRetry, closed, emit);
            val timerCancel  = scheduleDefaultResponse(timeoutMs, defaultValue, firstMessage, closed, emit);

            Thread.startVirtualThread(() -> connectAndSubscribe(ctx, firstMessage, emit, complete));

            return buildCloseCallback(brokerHash, ctx, timerCancel, onDisconnect);
        });
    }

    private record SubscriptionContext(
            MqttClientValues cached,
            Mqtt5AsyncClient client,
            List<MqttTopicFilter> filters,
            MqttQos qos,
            AtomicBoolean closed) {}

    private static void connectAndSubscribe(SubscriptionContext ctx, AtomicBoolean firstMessage, Consumer<Value> emit,
            Runnable complete) {
        try {
            ctx.client.connect().get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            for (val filter : ctx.filters) {
                if (ctx.closed.get()) {
                    return;
                }
                ctx.cached.incrementTopicSubscribers(filter.toString());
                ctx.client.subscribeWith().topicFilter(filter).qos(ctx.qos)
                        .callback(publish -> deliverPublish(publish, ctx.closed, firstMessage, emit)).send()
                        .get(SUBSCRIBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            emit.accept(Value.error(ERROR_MQTT_CONNECT_FAILED.formatted(messageOf(e))));
            complete.run();
        }
    }

    private static void deliverPublish(Mqtt5Publish publish, AtomicBoolean closed, AtomicBoolean firstMessage,
            Consumer<Value> emit) {
        if (closed.get()) {
            return;
        }
        firstMessage.set(true);
        emit.accept(decodePublish(publish));
    }

    private Runnable buildCloseCallback(int brokerHash, SubscriptionContext ctx, Cancellable timerCancel,
            Runnable onDisconnect) {
        return () -> {
            ctx.closed.set(true);
            timerCancel.cancel();
            if (onDisconnect != null) {
                ctx.cached.getOnDisconnectCallbacks().remove(onDisconnect);
            }
            for (val filter : ctx.filters) {
                if (!ctx.cached.decrementTopicSubscribers(filter.toString())) {
                    unsubscribeQuietly(ctx.client, filter);
                }
            }
            if (ctx.cached.decrementBrokerSubscribers() <= 0) {
                MQTT_CLIENT_CACHE.remove(brokerHash);
                disconnectQuietly(ctx.cached, ctx.client);
            }
        };
    }

    private static void unsubscribeQuietly(Mqtt5AsyncClient client, MqttTopicFilter filter) {
        try {
            client.unsubscribeWith().addTopicFilter(filter).send().get(UNSUBSCRIBE_TIMEOUT.toMillis(),
                    TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.debug("Unsubscribe for topic {} failed: {}", filter, messageOf(e));
        }
    }

    private static void disconnectQuietly(MqttClientValues cached, Mqtt5AsyncClient client) {
        try {
            client.disconnect().get(DISCONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException e) {
            log.debug("Disconnect failed for client {}: {}", cached.getClientId(), messageOf(e));
        }
    }

    private static Runnable registerDisconnectListener(MqttClientValues cached, boolean emitAtRetry,
            AtomicBoolean closed, Consumer<Value> emit) {
        if (!emitAtRetry) {
            return null;
        }
        Runnable callback = () -> {
            if (!closed.get()) {
                emit.accept(Value.UNDEFINED);
            }
        };
        cached.getOnDisconnectCallbacks().add(callback);
        return callback;
    }

    private Cancellable scheduleDefaultResponse(long timeoutMs, Value defaultValue, AtomicBoolean firstMessage,
            AtomicBoolean closed, Consumer<Value> emit) {
        if (timeoutMs <= 0L) {
            return Cancellable.NOOP;
        }
        val deadline = clock.instant().plusMillis(timeoutMs);
        return scheduler.scheduleAt(deadline, () -> {
            if (!closed.get() && firstMessage.compareAndSet(false, true)) {
                emit.accept(defaultValue);
            }
        });
    }

    private MqttClientValues buildClientValues(ObjectNode brokerConfig, JsonNode pipConfig, ObjectValue pdpSecrets) {
        val clientId              = getConfigValueOrDefault(brokerConfig, ENVIRONMENT_CLIENT_ID,
                getConfigValueOrDefault(pipConfig, ENVIRONMENT_CLIENT_ID, DEFAULT_CLIENT_ID + "-" + UUID.randomUUID()));
        val onDisconnectCallbacks = new CopyOnWriteArrayList<Runnable>();
        val client                = buildMqttClient(brokerConfig, pipConfig, pdpSecrets, clientId,
                onDisconnectCallbacks);
        return new MqttClientValues(clientId, client, brokerConfig, onDisconnectCallbacks);
    }

    private Mqtt5AsyncClient buildMqttClient(JsonNode brokerConfig, JsonNode pipConfig, ObjectValue pdpSecrets,
            String clientId, CopyOnWriteArrayList<Runnable> onDisconnectCallbacks) {
        val host = getConfigValueOrDefault(brokerConfig, ENVIRONMENT_BROKER_ADDRESS,
                getConfigValueOrDefault(pipConfig, ENVIRONMENT_BROKER_ADDRESS, DEFAULT_BROKER_ADDRESS));
        val port = getConfigValueOrDefault(brokerConfig, ENVIRONMENT_BROKER_PORT,
                getConfigValueOrDefault(pipConfig, ENVIRONMENT_BROKER_PORT, DEFAULT_BROKER_PORT));

        MqttClientDisconnectedListener disconnectedListener = (MqttClientDisconnectedContext context) -> {
            log.debug("Mqtt client '{}' disconnected: {}", clientId, context.getCause().getMessage());
            for (val cb : onDisconnectCallbacks) {
                try {
                    cb.run();
                } catch (RuntimeException e) {
                    log.debug("Disconnect callback raised: {}", messageOf(e));
                }
            }
        };

        return Mqtt5Client.builder().identifier(clientId).serverAddress(InetSocketAddress.createUnresolved(host, port))
                .automaticReconnectWithDefaultConfig().addDisconnectedListener(disconnectedListener)
                .simpleAuth(buildAuth(brokerConfig, pdpSecrets)).buildAsync();
    }

    private static Mqtt5SimpleAuth buildAuth(JsonNode brokerConfig, ObjectValue pdpSecrets) {
        val    mqttSecrets = resolveMqttSecrets(brokerConfig, pdpSecrets);
        val    username    = mqttSecrets.get(ENVIRONMENT_USERNAME) instanceof TextValue(var u) ? u : DEFAULT_USERNAME;
        val    passwordVal = mqttSecrets.get(SECRETS_PASSWORD);
        byte[] password;
        if (passwordVal instanceof TextValue(var p)) {
            password = p.getBytes(StandardCharsets.UTF_8);
        } else {
            password = getPassword(brokerConfig);
        }
        return Mqtt5SimpleAuth.builder().username(username).password(password).build();
    }

    private static ObjectValue resolveMqttSecrets(JsonNode brokerConfig, ObjectValue pdpSecrets) {
        if (pdpSecrets == null || pdpSecrets.isEmpty()) {
            return Value.EMPTY_OBJECT;
        }
        val mqttSecretsValue = pdpSecrets.get(SECRETS_MQTT);
        if (!(mqttSecretsValue instanceof ObjectValue mqttSecrets)) {
            return Value.EMPTY_OBJECT;
        }
        if (brokerConfig != null && brokerConfig.has(ENVIRONMENT_BROKER_CONFIG_NAME)) {
            val brokerName      = brokerConfig.get(ENVIRONMENT_BROKER_CONFIG_NAME).asString();
            val perBrokerSecret = mqttSecrets.get(brokerName);
            if (perBrokerSecret instanceof ObjectValue perBrokerSecrets) {
                return perBrokerSecrets;
            }
        }
        if (mqttSecrets.containsKey(ENVIRONMENT_USERNAME) || mqttSecrets.containsKey(SECRETS_PASSWORD)) {
            return mqttSecrets;
        }
        return Value.EMPTY_OBJECT;
    }

    private static Value decodePublish(Mqtt5Publish publishMessage) {
        val payloadFormatIndicator = getPayloadFormatIndicator(publishMessage);
        val contentType            = getContentType(publishMessage);
        if (publishMessage.getPayloadFormatIndicator().isEmpty()
                && isValidUtf8String(publishMessage.getPayloadAsBytes())) {
            return Value.of(new String(publishMessage.getPayloadAsBytes(), StandardCharsets.UTF_8));
        }
        if (payloadFormatIndicator == 1) {
            if ("application/json".equals(contentType)) {
                return getValueOfJson(publishMessage);
            }
            return Value.of(new String(publishMessage.getPayloadAsBytes(), StandardCharsets.UTF_8));
        }
        return convertBytesToArrayValue(publishMessage.getPayloadAsBytes());
    }

    private static JsonNode resolvePipConfig(ObjectValue variables) {
        val raw = variables.get(ENVIRONMENT_MQTT_PIP_CONFIG);
        if (raw == null || raw instanceof UndefinedValue) {
            return null;
        }
        return ValueJsonMarshaller.toJsonNode(raw);
    }

    private static String messageOf(Throwable t) {
        return t.getMessage() == null ? t.toString() : t.getMessage();
    }

    @Override
    public void close() {
        MQTT_CLIENT_CACHE.forEach((hash, values) -> {
            try {
                values.getMqttAsyncClient().disconnect().get(DISCONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException e) {
                log.debug("Error disconnecting client {}: {}", values.getClientId(), messageOf(e));
            }
        });
        MQTT_CLIENT_CACHE.clear();
    }

    /**
     * Static factory using the system clock and a real
     * {@link RealTimeScheduler}; intended for production wiring outside of
     * tests.
     */
    public static SaplMqttClient withDefaults() {
        return new SaplMqttClient(Clock.systemUTC(), new RealTimeScheduler(Clock.systemUTC()));
    }
}
