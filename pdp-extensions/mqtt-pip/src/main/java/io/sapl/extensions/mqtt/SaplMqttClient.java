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

import com.hivemq.client.mqtt.MqttClientSslConfig;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedContext;
import com.hivemq.client.mqtt.lifecycle.MqttClientDisconnectedListener;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.Mqtt5ClientBuilder;
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

import javax.net.ssl.TrustManagerFactory;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Set;
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
    /** Configuration key enabling TLS for the broker connection. */
    public static final String   ENVIRONMENT_TLS                = "tls";
    /** Configuration key for a PKCS12/JKS trust store used to verify the broker. */
    public static final String   ENVIRONMENT_TLS_TRUST_STORE    = "tlsTrustStore";
    /** Configuration key for the trust store password. */
    public static final String   ENVIRONMENT_TLS_TRUST_STORE_PW = "tlsTrustStorePassword";
    private static final String  ENVIRONMENT_MQTT_PIP_CONFIG    = "mqttPipConfig";
    private static final String  ENVIRONMENT_USERNAME           = "username";
    private static final String  ENVIRONMENT_BROKER_CONFIG_NAME = "name";
    private static final String  ENVIRONMENT_QOS                = "defaultQos";
    private static final String  ENVIRONMENT_MAX_PAYLOAD_SIZE   = "maxPayloadSize";
    private static final String  DEFAULT_CLIENT_ID              = "mqtt_pip";
    private static final String  DEFAULT_USERNAME               = "";
    private static final String  DEFAULT_BROKER_ADDRESS         = "localhost";
    private static final int     DEFAULT_BROKER_PORT            = 1883;
    private static final int     DEFAULT_QOS                    = 0;
    private static final int     DEFAULT_MAX_PAYLOAD_SIZE       = 1_048_576;
    private static final String  SECRETS_MQTT                   = "mqtt";
    private static final String  SECRETS_PASSWORD               = "password";
    static final boolean         DEFAULT_EMIT_AT_RETRY          = false;
    private static final boolean DEFAULT_TLS                    = false;
    private static final String  DEFAULT_TLS_TRUST_STORE        = "";

    private static final Duration CONNECT_TIMEOUT     = Duration.ofSeconds(10L);
    private static final Duration SUBSCRIBE_TIMEOUT   = Duration.ofSeconds(5L);
    private static final Duration UNSUBSCRIBE_TIMEOUT = Duration.ofSeconds(5L);
    private static final Duration DISCONNECT_TIMEOUT  = Duration.ofSeconds(5L);

    private static final String ERROR_INLINE_BROKER_CONFIG_NOT_ALLOWED = "A policy may only select an mqtt broker by name or use the default. Supplying an inline broker configuration object is not permitted.";
    private static final String ERROR_INVALID_QOS                      = "Invalid MQTT QoS: must be 0, 1, or 2.";
    private static final String ERROR_MQTT_CONNECT_FAILED              = "Failed to connect or subscribe to MQTT broker: %s";
    private static final String ERROR_PAYLOAD_TOO_LARGE                = "MQTT message exceeded the configured limit of %d bytes.";
    private static final String WARN_INSECURE_CREDENTIALS              = "MQTT broker credentials are being transmitted over an "
            + "unencrypted connection (tls=false). Enable 'tls' whenever credentials are configured.";

    // Keyed on the broker configuration node itself (content-based equals), not
    // on its 32-bit hashCode, so two distinct broker configs whose hashes
    // collide cannot share a client (which would route to the wrong broker with
    // the wrong credentials).
    static final ConcurrentHashMap<JsonNode, MqttClientValues> MQTT_CLIENT_CACHE = new ConcurrentHashMap<>();

    // Guards the single runtime warning about credentials being sent over an
    // unencrypted connection, so the insecure transport is observable once
    // without flooding the log on every connection.
    private static final AtomicBoolean INSECURE_CREDENTIALS_WARNED = new AtomicBoolean(false);

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
            // A policy may only select a broker by name or use the default. An inline
            // broker object would let it pair an operator secret with a policy-chosen host.
            if (mqttPipConfigVal instanceof ObjectValue) {
                return Streams.error(ERROR_INLINE_BROKER_CONFIG_NOT_ALLOWED);
            }
            val variables    = ctx.variables();
            val pdpSecrets   = ctx.pdpSecrets();
            val pipConfig    = resolvePipConfig(variables);
            val brokerConfig = getMqttBrokerConfig(pipConfig, mqttPipConfigVal);
            val effectiveQos = getQos(
                    qos != null ? qos : Value.of(getConfigValueOrDefault(pipConfig, ENVIRONMENT_QOS, DEFAULT_QOS)));
            // A policy-supplied QoS outside 0..2 yields a null MqttQos; fail with
            // an error value instead of opening a subscription that would later
            // throw on the worker thread and hang the consumer.
            if (effectiveQos == null) {
                return Streams.error(ERROR_INVALID_QOS);
            }
            val filters               = topicFilters(topic);
            val defaultResponseConfig = getDefaultResponseConfig(pipConfig, mqttPipConfigVal);
            val defaultValue          = getDefaultValue(defaultResponseConfig);
            val timeoutMs             = defaultResponseConfig.getDefaultResponseTimeout();
            val emitAtRetry           = getConfigValueOrDefault(pipConfig, ENVIRONMENT_EMIT_AT_RETRY,
                    DEFAULT_EMIT_AT_RETRY);

            return openSubscription(brokerConfig, pipConfig, pdpSecrets, filters, effectiveQos, defaultValue, timeoutMs,
                    emitAtRetry);
        } catch (RuntimeException e) {
            return Streams.error(messageOf(e));
        }
    }

    private Stream<Value> openSubscription(ObjectNode brokerConfig, JsonNode pipConfig, ObjectValue pdpSecrets,
            List<MqttTopicFilter> filters, MqttQos qos, Value defaultValue, long timeoutMs, boolean emitAtRetry) {
        val maxPayloadBytes = getConfigValueOrDefault(pipConfig, ENVIRONMENT_MAX_PAYLOAD_SIZE,
                DEFAULT_MAX_PAYLOAD_SIZE);
        // Build the candidate client (including any blocking trust-store I/O)
        // outside the cache's atomic region, so the ConcurrentHashMap bin lock is
        // never held across filesystem I/O or client construction.
        val candidate = buildClientValues(brokerConfig, pipConfig, pdpSecrets);
        return Streams.fromCallback((emit, complete) -> {
            // Register this subscriber atomically: insert the prebuilt candidate
            // only if absent, otherwise reuse the existing client and discard the
            // surplus. The atomic region does only the cheap count increment, so a
            // concurrent close() cannot evict between lookup and increment.
            val cached = registerSubscriber(brokerConfig, candidate);

            val firstMessage = new AtomicBoolean(false);
            val closed       = new AtomicBoolean(false);
            val teardownDone = new AtomicBoolean(false);
            val ctx          = new SubscriptionContext(cached, cached.getMqttAsyncClient(), filters, qos, closed,
                    maxPayloadBytes, ConcurrentHashMap.newKeySet());

            val onDisconnect = registerDisconnectListener(cached, emitAtRetry, closed, emit);
            val timerCancel  = scheduleDefaultResponse(timeoutMs, defaultValue, firstMessage, closed, emit);

            Thread.startVirtualThread(() -> connectAndSubscribe(ctx, firstMessage, emit, complete,
                    () -> teardown(brokerConfig, ctx, timerCancel, onDisconnect, teardownDone)));

            return () -> teardown(brokerConfig, ctx, timerCancel, onDisconnect, teardownDone);
        });
    }

    record SubscriptionContext(
            MqttClientValues cached,
            Mqtt5AsyncClient client,
            List<MqttTopicFilter> filters,
            MqttQos qos,
            AtomicBoolean closed,
            int maxPayloadBytes,
            Set<MqttTopicFilter> subscribedFilters) {}

    private static void connectAndSubscribe(SubscriptionContext ctx, AtomicBoolean firstMessage, Consumer<Value> emit,
            Runnable complete, Runnable onFailure) {
        try {
            ctx.client.connect().get(CONNECT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
            for (val filter : ctx.filters) {
                if (ctx.closed.get()) {
                    return;
                }
                // Subscribe on the broker and count the topic as one critical
                // section, so a concurrent unsubscribe on the same shared client
                // cannot apply between the subscribe and the count update and
                // leave this subscriber counted but unsubscribed. The count is
                // updated only after the subscribe succeeds, so a failed subscribe
                // leaves no phantom topic-subscriber count.
                ctx.cached.subscribeTopicAtomically(filter.toString(),
                        () -> ctx.client.subscribeWith().topicFilter(filter).qos(ctx.qos).callback(
                                publish -> deliverPublish(publish, ctx.closed, firstMessage, emit, ctx.maxPayloadBytes))
                                .send().get(SUBSCRIBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
                // Record the filter only after the subscribe-and-count critical
                // section succeeds, so teardown decrements and unsubscribes
                // exactly the filters this subscriber actually subscribed, never
                // the ones a partial failure left unsubscribed.
                ctx.subscribedFilters.add(filter);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            onFailure.run();
        } catch (ExecutionException | TimeoutException | RuntimeException e) {
            // Tear the subscriber down before completing so a connect/subscribe
            // failure (or any unexpected worker-thread error) cannot leave the
            // subscriber count incremented and the client cached and reconnecting.
            emit.accept(Value.error(ERROR_MQTT_CONNECT_FAILED.formatted(messageOf(e))));
            onFailure.run();
            complete.run();
        }
    }

    private static void deliverPublish(Mqtt5Publish publish, AtomicBoolean closed, AtomicBoolean firstMessage,
            Consumer<Value> emit, int maxPayloadBytes) {
        if (closed.get()) {
            return;
        }
        firstMessage.set(true);
        emit.accept(decodePublish(publish, maxPayloadBytes));
    }

    // Teardown for both the stream close callback and the connect/subscribe
    // failure path: unsubscribes exactly the filters this subscriber actually
    // subscribed (a partial-failure subscriber may have subscribed a subset, or
    // none), then releases its broker reference. The shared done flag makes the
    // two callers idempotent, so a failure-path teardown and a later close
    // callback never double-decrement or skip cleanup.
    void teardown(JsonNode brokerKey, SubscriptionContext ctx, Cancellable timerCancel, Runnable onDisconnect,
            AtomicBoolean done) {
        if (!done.compareAndSet(false, true)) {
            return;
        }
        cancelSubscriptionResources(ctx, timerCancel, onDisconnect);
        for (val filter : ctx.subscribedFilters) {
            // Decrement the count and, when the last subscriber leaves, unsubscribe
            // on the broker as one critical section guarded by the same lock the
            // subscribe path uses, so the count and the broker subscription state
            // change atomically per topic.
            ctx.cached.unsubscribeTopicAtomically(filter.toString(), () -> unsubscribeQuietly(ctx.client, filter));
        }
        evictBrokerSubscriber(brokerKey);
    }

    private static void cancelSubscriptionResources(SubscriptionContext ctx, Cancellable timerCancel,
            Runnable onDisconnect) {
        ctx.closed.set(true);
        timerCancel.cancel();
        if (onDisconnect != null) {
            ctx.cached.getOnDisconnectCallbacks().remove(onDisconnect);
        }
    }

    // Registers one broker reference against a prebuilt candidate client. The
    // candidate is inserted only if no entry exists yet; otherwise the existing
    // shared client is reused and the surplus candidate (never connected) is
    // discarded. The atomic map operation does only the count increment, never
    // client construction or trust-store I/O.
    static MqttClientValues registerSubscriber(JsonNode brokerKey, MqttClientValues candidate) {
        return MQTT_CLIENT_CACHE.compute(brokerKey, (key, existing) -> {
            val values = existing != null ? existing : candidate;
            values.incrementBrokerSubscribers();
            return values;
        });
    }

    // Releases one broker reference. The decrement and the eviction decision run
    // inside a single atomic map operation so a concurrent open() cannot attach
    // to an entry being removed; the client is disconnected outside the lock.
    private void evictBrokerSubscriber(JsonNode brokerKey) {
        val toDisconnect = new MqttClientValues[1];
        MQTT_CLIENT_CACHE.compute(brokerKey, (key, existing) -> {
            if (existing == null) {
                return null;
            }
            if (existing.decrementBrokerSubscribers() <= 0) {
                toDisconnect[0] = existing;
                return null;
            }
            return existing;
        });
        if (toDisconnect[0] != null) {
            disconnectQuietly(toDisconnect[0], toDisconnect[0].getMqttAsyncClient());
        }
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

        Mqtt5ClientBuilder builder = Mqtt5Client.builder().identifier(clientId)
                .serverAddress(InetSocketAddress.createUnresolved(host, port)).automaticReconnectWithDefaultConfig()
                .addDisconnectedListener(disconnectedListener);

        val tlsEnabled = getConfigValueOrDefault(brokerConfig, ENVIRONMENT_TLS,
                getConfigValueOrDefault(pipConfig, ENVIRONMENT_TLS, DEFAULT_TLS));
        if (tlsEnabled) {
            builder = applyTls(builder, brokerConfig, pipConfig);
        }

        val auth = buildAuth(brokerConfig, pdpSecrets);
        if (carriesCredentialsOverPlaintext(tlsEnabled, auth)
                && INSECURE_CREDENTIALS_WARNED.compareAndSet(false, true)) {
            log.warn(WARN_INSECURE_CREDENTIALS);
        }
        return builder.simpleAuth(auth).buildAsync();
    }

    // True when credentials (a username or a password) are present but the
    // connection is unencrypted, so the credentials would be sent in cleartext.
    static boolean carriesCredentialsOverPlaintext(boolean tlsEnabled, Mqtt5SimpleAuth auth) {
        if (tlsEnabled) {
            return false;
        }
        val hasUsername = auth.getUsername().map(u -> !u.toString().isEmpty()).orElse(false);
        val hasPassword = auth.getPassword().map(p -> p.remaining() > 0).orElse(false);
        return hasUsername || hasPassword;
    }

    private static Mqtt5ClientBuilder applyTls(Mqtt5ClientBuilder builder, JsonNode brokerConfig, JsonNode pipConfig) {
        val trustStorePath = getConfigValueOrDefault(brokerConfig, ENVIRONMENT_TLS_TRUST_STORE,
                getConfigValueOrDefault(pipConfig, ENVIRONMENT_TLS_TRUST_STORE, DEFAULT_TLS_TRUST_STORE));
        if (trustStorePath.isBlank()) {
            // No explicit trust store: verify the broker against the platform
            // default trust material (public CAs).
            return builder.sslWithDefaultConfig();
        }
        val trustStorePassword = getConfigValueOrDefault(brokerConfig, ENVIRONMENT_TLS_TRUST_STORE_PW,
                getConfigValueOrDefault(pipConfig, ENVIRONMENT_TLS_TRUST_STORE_PW, ""));
        return builder.sslConfig(MqttClientSslConfig.builder()
                .trustManagerFactory(trustManagerFactory(trustStorePath, trustStorePassword)).build());
    }

    private static TrustManagerFactory trustManagerFactory(String trustStorePath, String password) {
        try {
            val trustStore = KeyStore.getInstance(KeyStore.getDefaultType());
            try (InputStream in = Files.newInputStream(Path.of(trustStorePath))) {
                trustStore.load(in, password.isEmpty() ? null : password.toCharArray());
            }
            val factory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            factory.init(trustStore);
            return factory;
        } catch (GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Failed to load MQTT TLS trust store from " + trustStorePath, e);
        }
    }

    static Mqtt5SimpleAuth buildAuth(JsonNode brokerConfig, ObjectValue pdpSecrets) {
        // Credentials are sourced exclusively from the resolved secrets object.
        // A password is never read from the (potentially policy-controlled)
        // broker configuration, so an inline config cannot inject credentials.
        val mqttSecrets = resolveMqttSecrets(brokerConfig, pdpSecrets);
        val username    = mqttSecrets.get(ENVIRONMENT_USERNAME) instanceof TextValue(var u) ? u : DEFAULT_USERNAME;
        val password    = mqttSecrets.get(SECRETS_PASSWORD) instanceof TextValue(var p)
                ? p.getBytes(StandardCharsets.UTF_8)
                : new byte[0];
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

    static Value decodePublish(Mqtt5Publish publishMessage, int maxPayloadBytes) {
        if (publishMessage.getPayloadAsBytes().length > maxPayloadBytes) {
            return Value.error(ERROR_PAYLOAD_TOO_LARGE.formatted(maxPayloadBytes));
        }
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
