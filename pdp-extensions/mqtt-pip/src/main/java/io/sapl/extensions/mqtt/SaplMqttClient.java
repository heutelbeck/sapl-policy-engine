/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttTopicFilter;
import com.hivemq.client.mqtt.exceptions.MqttClientStateException;
import com.hivemq.client.mqtt.mqtt5.Mqtt5Client;
import com.hivemq.client.mqtt.mqtt5.message.auth.Mqtt5SimpleAuth;
import com.hivemq.client.mqtt.mqtt5.message.connect.connack.Mqtt5ConnAck;
import com.hivemq.client.mqtt.mqtt5.message.publish.Mqtt5Publish;
import com.hivemq.client.mqtt.mqtt5.message.unsubscribe.Mqtt5Unsubscribe;
import com.hivemq.client.mqtt.mqtt5.reactor.Mqtt5ReactorClient;
import io.sapl.api.model.*;
import io.sapl.extensions.mqtt.util.DefaultResponseConfig;
import io.sapl.extensions.mqtt.util.ErrorUtility;
import io.sapl.extensions.mqtt.util.MqttClientValues;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.util.function.Tuple2;
import reactor.util.function.Tuple4;
import reactor.util.function.Tuples;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static io.sapl.extensions.mqtt.util.ConfigUtility.*;
import static io.sapl.extensions.mqtt.util.DefaultResponseUtility.getDefaultResponseConfig;
import static io.sapl.extensions.mqtt.util.DefaultResponseUtility.getDefaultValue;
import static io.sapl.extensions.mqtt.util.ErrorUtility.emitValueOnRetry;
import static io.sapl.extensions.mqtt.util.ErrorUtility.getRetrySpec;
import static io.sapl.extensions.mqtt.util.PayloadFormatUtility.*;
import static io.sapl.extensions.mqtt.util.SubscriptionUtility.addSubscriptionsCountToSubscriptionList;
import static io.sapl.extensions.mqtt.util.SubscriptionUtility.buildTopicSubscription;

/**
 * This mqtt client allows the user to receive mqtt messages of subscribed
 * topics from a mqtt broker.
 */
@Slf4j
public class SaplMqttClient {

    /**
     * The reference for the client id in configurations.
     */
    public static final String  ENVIRONMENT_CLIENT_ID       = "clientId";
    /**
     * The reference for the broker address in configurations.
     */
    public static final String  ENVIRONMENT_BROKER_ADDRESS  = "brokerAddress";
    /**
     * The reference for the broker port in configurations.
     */
    public static final String  ENVIRONMENT_BROKER_PORT     = "brokerPort";
    private static final String ENVIRONMENT_MQTT_PIP_CONFIG = "mqttPipConfig";
    private static final String ENVIRONMENT_USERNAME        = "username";
    private static final String ENVIRONMENT_QOS             = "defaultQos";
    private static final String DEFAULT_CLIENT_ID           = "mqtt_pip";
    private static final String DEFAULT_USERNAME            = "";
    private static final String DEFAULT_BROKER_ADDRESS      = "localhost";
    private static final int    DEFAULT_BROKER_PORT         = 1883;
    private static final int    DEFAULT_QOS                 = 0;              // AT_MOST_ONCE

    static final ConcurrentHashMap<Integer, MqttClientValues>   MQTT_CLIENT_CACHE             = new ConcurrentHashMap<>();
    static final ConcurrentHashMap<UUID, DefaultResponseConfig> DEFAULT_RESPONSE_CONFIG_CACHE = new ConcurrentHashMap<>();

    /**
     * This method returns a reactive stream of mqtt messages of one or many
     * subscribed topics.
     *
     * @param topic A string or array of topic(s) for subscription.
     * @param variables The configuration specified in the PDP configuration file.
     * @return A {@link Flux} of messages of the subscribed topic(s).
     */
    public Flux<Value> buildSaplMqttMessageFlux(Value topic, Map<String, Value> variables) {
        return buildSaplMqttMessageFlux(topic, variables, null, Value.UNDEFINED);
    }

    /**
     * This method returns a reactive stream of mqtt messages of one or many
     * subscribed topics.
     *
     * @param topic A string or array of topic(s) for subscription.
     * @param variables The configuration specified in the PDP configuration file.
     * @param qos A {@link Flux} of the quality of service level of the mqtt
     * subscription to the broker. Possible values: 0, 1, 2. This
     * variable may be null.
     * @return A {@link Flux} of messages of the subscribed topic(s).
     */
    public Flux<Value> buildSaplMqttMessageFlux(Value topic, Map<String, Value> variables, Value qos) {
        return buildSaplMqttMessageFlux(topic, variables, qos, Value.UNDEFINED);
    }

    /**
     * This method returns a reactive stream of mqtt messages of one or many
     * subscribed topics.
     *
     * @param topic A string or array of topic(s) for subscription.
     * @param variables The configuration specified in the PDP configuration
     * file.
     * @param qos A {@link Flux} of the quality of service level of the
     * mqtt subscription to the broker. Possible values: 0, 1,
     * 2. This variable can be null.
     * @param mqttPipConfig An {@link com.fasterxml.jackson.databind.node.ArrayNode}
     * of {@link com.fasterxml.jackson.databind.node.ObjectNode}s
     * or only a single {@link ObjectNode} containing
     * configurations for the pip as a mqtt client. Each
     * {@link ObjectNode} specifies the configuration of a
     * single mqtt client. Therefore, it is possible for the pip
     * to build multiple mqtt clients, that is the pip can
     * subscribe to topics by different brokers. This variable
     * may be null.
     * @return A {@link Flux} of messages of the subscribed topic(s).
     */
    public Flux<Value> buildSaplMqttMessageFlux(Value topic, Map<String, Value> variables, Value qos,
            Value mqttPipConfig) {
        try {
            JsonNode pipMqttClientConfig = null;
            if (variables != null) {
                var pipMqttClientConfigVal = variables.get(ENVIRONMENT_MQTT_PIP_CONFIG);
                if (pipMqttClientConfigVal != null && !(pipMqttClientConfigVal instanceof UndefinedValue)) {
                    pipMqttClientConfig = ValueJsonMarshaller.toJsonNode(pipMqttClientConfigVal);
                }
            }
            var messageFlux = buildMqttMessageFlux(topic, qos, mqttPipConfig, pipMqttClientConfig);
            return addDefaultValueToMessageFlux(pipMqttClientConfig, mqttPipConfig, messageFlux)
                    .onErrorResume(error -> {
                        log.debug("An error occurred on the sapl mqtt message flux: {}", error.getMessage());
                        return Flux.just(Value.error(error.getMessage()));
                    });
        } catch (Exception e) {
            log.debug("An exception occurred while building the mqtt message flux: {}", e.getMessage());
            return Flux.just(Value.error("Failed to build stream of messages."));
        }
    }

    private Flux<Value> buildMqttMessageFlux(Value topic, Value qos, Value mqttPipConfig,
            JsonNode pipMqttClientConfig) {
        Sinks.Many<Value> emitterUndefined = Sinks.many().multicast().directAllOrNothing();

        var mqttMessageFlux = buildFluxOfConfigParams(qos, mqttPipConfig, pipMqttClientConfig)
                .map(params -> getConnectionAndSubscription(topic, pipMqttClientConfig, params))
                .switchMap(this::connectAndSubscribe).map(this::getValueFromMqttPublishMessage).share()
                .retryWhen(getRetrySpec(pipMqttClientConfig).doBeforeRetry(
                        retrySignal -> emitValueOnRetry(pipMqttClientConfig, emitterUndefined, retrySignal)));

        return Flux.merge(mqttMessageFlux, emitterUndefined.asFlux());
    }

    private Flux<Value> addDefaultValueToMessageFlux(JsonNode pipMqttClientConfig, Value mqttPipConfig,
            Flux<Value> messageFlux) {
        var messageFluxUuid = UUID.randomUUID();
        return Flux
                .merge(messageFlux, Mono.just(mqttPipConfig)
                        .map(pipConfigParams -> determineDefaultResponse(messageFluxUuid, pipMqttClientConfig,
                                pipConfigParams))
                        .delayUntil(v -> Mono.just(v)
                                .delayElement(Duration.ofMillis(DEFAULT_RESPONSE_CONFIG_CACHE.get(messageFluxUuid)
                                        .getDefaultResponseTimeout())))
                        .takeUntilOther(messageFlux))
                .doFinally(signalType -> DEFAULT_RESPONSE_CONFIG_CACHE.remove(messageFluxUuid));
    }

    private Value determineDefaultResponse(UUID messageFluxUuid, JsonNode pipMqttClientConfig, Value pipConfigParams) {
        var defaultResponseConfig = getDefaultResponseConfig(pipMqttClientConfig, pipConfigParams);
        DEFAULT_RESPONSE_CONFIG_CACHE.put(messageFluxUuid, defaultResponseConfig);
        return getDefaultValue(defaultResponseConfig);
    }

    private Value getValueFromMqttPublishMessage(Mqtt5Publish publishMessage) {
        var payloadFormatIndicator = getPayloadFormatIndicator(publishMessage);
        var contentType            = getContentType(publishMessage);

        if (publishMessage.getPayloadFormatIndicator().isEmpty()
                && isValidUtf8String(publishMessage.getPayloadAsBytes())) {
            return Value.of(new String(publishMessage.getPayloadAsBytes(), StandardCharsets.UTF_8));
        } else if (payloadFormatIndicator == 1) {
            if ("application/json".equals(contentType)) {
                return getValueOfJson(publishMessage);
            } else {
                return Value.of(new String(publishMessage.getPayloadAsBytes(), StandardCharsets.UTF_8));
            }
        }

        return convertBytesToArrayValue(publishMessage.getPayloadAsBytes());
    }

    private Flux<Tuple2<Value, ObjectNode>> buildFluxOfConfigParams(Value qos, Value mqttPipConfig,
            JsonNode pipMqttClientConfig) {
        Value effectiveQos = qos;
        if (effectiveQos == null) {
            effectiveQos = Value.of(getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_QOS, DEFAULT_QOS));
        }
        var mqttBrokerConfig = getMqttBrokerConfig(pipMqttClientConfig, mqttPipConfig);
        return Flux.just(Tuples.of(effectiveQos, mqttBrokerConfig));
    }

    private Tuple4<Mqtt5ReactorClient, Mono<Mqtt5ConnAck>, Flux<Mqtt5Publish>, Integer> getConnectionAndSubscription(
            Value topic, JsonNode pipMqttClientConfig, Tuple2<Value, ObjectNode> params) {
        var mqttBrokerConfig = params.getT2();
        var brokerConfigHash = mqttBrokerConfig.hashCode();
        var clientValues     = getOrBuildMqttClientValues(mqttBrokerConfig, brokerConfigHash, pipMqttClientConfig);
        var qos              = params.getT1();
        var mqttSubscription = buildMqttSubscription(brokerConfigHash, topic, qos);

        return Tuples.of(clientValues.getMqttReactorClient(), clientValues.getClientConnection(), mqttSubscription,
                brokerConfigHash);
    }

    private Flux<Mqtt5Publish> connectAndSubscribe(
            Tuple4<Mqtt5ReactorClient, Mono<Mqtt5ConnAck>, Flux<Mqtt5Publish>, Integer> buildParams) {
        var clientConnection = buildParams.getT2();
        var mqttSubscription = buildParams.getT3();
        var brokerConfigHash = buildParams.getT4();
        return clientConnection.thenMany(mqttSubscription).doOnError(ErrorUtility::isErrorRelevantToRemoveClientCache,
                throwable -> MQTT_CLIENT_CACHE.remove(brokerConfigHash));
    }

    private Flux<Mqtt5Publish> buildMqttSubscription(int brokerConfigHash, Value topic, Value qos) {
        var topicSubscription = buildTopicSubscription(topic, qos);
        var mqttClientValues  = Objects.requireNonNull(MQTT_CLIENT_CACHE.get(brokerConfigHash));
        var mqttClientReactor = mqttClientValues.getMqttReactorClient();
        return mqttClientReactor.subscribePublishes(topicSubscription).doOnSingle(mqtt5SubAck -> {
            addSubscriptionsCountToSubscriptionList(mqttClientValues, mqtt5SubAck, topicSubscription);
            log.debug("Mqtt client '{}' subscribed to topic(s) '{}' with reason codes: {}",
                    getClientId(mqttClientReactor), topic, mqtt5SubAck.getReasonCodes());
        }).doOnNext(mqtt5Publish -> log.debug("Mqtt client '{}' received message of topic '{}' with QoS '{}'.",
                getClientId(mqttClientReactor), mqtt5Publish.getTopic(), mqtt5Publish.getQos()))
                .onErrorResume(ErrorUtility::isClientCausedDisconnect, throwable -> Mono.empty())
                .doOnCancel(() -> handleMessageFluxCancel(brokerConfigHash, topic));
    }

    private MqttClientValues getOrBuildMqttClientValues(ObjectNode mqttBrokerConfig, int brokerConfigHash,
            JsonNode pipMqttClientConfig) {
        var clientValues = MQTT_CLIENT_CACHE.get(brokerConfigHash);
        if (clientValues == null) {
            clientValues = buildClientValues(mqttBrokerConfig, brokerConfigHash, pipMqttClientConfig);
        }
        return clientValues;
    }

    private MqttClientValues buildClientValues(ObjectNode mqttBrokerConfig, int brokerConfigHash,
            JsonNode pipMqttClientConfig) {
        var clientId             = getConfigValueOrDefault(mqttBrokerConfig, ENVIRONMENT_CLIENT_ID,
                getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_CLIENT_ID, DEFAULT_CLIENT_ID));
        var mqttClientReactor    = buildMqttReactorClient(mqttBrokerConfig, pipMqttClientConfig);
        var mqttClientConnection = buildClientConnection(mqttClientReactor).share();
        var clientValues         = new MqttClientValues(clientId, mqttClientReactor, mqttBrokerConfig,
                mqttClientConnection);
        MQTT_CLIENT_CACHE.put(brokerConfigHash, clientValues);
        return clientValues;
    }

    private Mqtt5ReactorClient buildMqttReactorClient(JsonNode mqttBrokerConfig, JsonNode pipMqttClientConfig) {
        return Mqtt5ReactorClient.from(buildMqttClient(mqttBrokerConfig, pipMqttClientConfig));
    }

    private Mqtt5Client buildMqttClient(JsonNode mqttBrokerConfig, JsonNode pipMqttClientConfig) {
        return MqttClient.builder().useMqttVersion5()
                .identifier(getConfigValueOrDefault(mqttBrokerConfig, ENVIRONMENT_CLIENT_ID,
                        getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_CLIENT_ID, DEFAULT_CLIENT_ID)))
                .serverAddress(
                        InetSocketAddress.createUnresolved(
                                getConfigValueOrDefault(mqttBrokerConfig, ENVIRONMENT_BROKER_ADDRESS,
                                        getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_BROKER_ADDRESS,
                                                DEFAULT_BROKER_ADDRESS)),
                                getConfigValueOrDefault(mqttBrokerConfig, ENVIRONMENT_BROKER_PORT,
                                        getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_BROKER_PORT,
                                                DEFAULT_BROKER_PORT))))
                .simpleAuth(buildAuthn(mqttBrokerConfig)).build();
    }

    private Mono<Mqtt5ConnAck> buildClientConnection(Mqtt5ReactorClient mqttClientReactor) {
        return mqttClientReactor.connect()
                // Register a callback to print a message when receiving the CONNACK message
                .doOnSuccess(mqtt5ConnAck -> log.debug("Successfully established connection for client '{}': {}",
                        getClientId(mqttClientReactor), mqtt5ConnAck.getReasonCode()))
                .doOnError(throwable -> log.debug("Mqtt client '{}' connection failed: {}",
                        getClientId(mqttClientReactor), throwable.getMessage()))
                .ignoreElement();
    }

    private Mqtt5SimpleAuth buildAuthn(JsonNode config) {
        return Mqtt5SimpleAuth.builder()
                .username(getConfigValueOrDefault(config, ENVIRONMENT_USERNAME, DEFAULT_USERNAME))
                .password(getPassword(config)).build();
    }

    private void handleMessageFluxCancel(int brokerConfigHash, Value topic) {
        unsubscribeTopics(brokerConfigHash, topic);
        var mqttClientValuesDisconnect = MQTT_CLIENT_CACHE.get(brokerConfigHash);
        if (mqttClientValuesDisconnect != null && mqttClientValuesDisconnect.isTopicSubscriptionsCountMapEmpty()) {
            disconnectClient(brokerConfigHash, mqttClientValuesDisconnect);
        }
    }

    private void unsubscribeTopics(int brokerConfigHash, Value topics) {
        var mqttClientValues = MQTT_CLIENT_CACHE.get(brokerConfigHash);
        if (mqttClientValues != null) {
            var mqttTopicFilters   = getMqttTopicFiltersToUnsubscribeAndReduceCount(topics, mqttClientValues);
            var unsubscribeMessage = Mqtt5Unsubscribe.builder().addTopicFilters(mqttTopicFilters).build();
            unsubscribeWithMessage(mqttClientValues, unsubscribeMessage);
        }
    }

    private void disconnectClient(int brokerConfigHash, MqttClientValues mqttClientValues) {
        MQTT_CLIENT_CACHE.remove(brokerConfigHash);
        var clientId = mqttClientValues.getClientId();
        mqttClientValues.getMqttReactorClient().disconnect()
                .onErrorResume(MqttClientStateException.class, e -> Mono.empty())
                .doOnSuccess(success -> log.debug("Client '{}' disconnected successfully.", clientId)).subscribe();
    }

    private List<MqttTopicFilter> getMqttTopicFiltersToUnsubscribeAndReduceCount(Value topics,
            MqttClientValues mqttClientValues) {
        var mqttTopicFilters = new LinkedList<MqttTopicFilter>();
        if (topics instanceof ArrayValue arrayTopics) {
            for (Value topicValue : arrayTopics) {
                var topic           = ((TextValue) topicValue).value();
                var isTopicExisting = mqttClientValues.countTopicSubscriptionsCountMapDown(topic);
                if (!isTopicExisting) {
                    mqttTopicFilters.add(MqttTopicFilter.of(topic));
                }
            }
        } else {
            var topic           = ((TextValue) topics).value();
            var isTopicExisting = mqttClientValues.countTopicSubscriptionsCountMapDown(topic);
            if (!isTopicExisting) {
                mqttTopicFilters.add(MqttTopicFilter.of(topic));
            }
        }
        return mqttTopicFilters;
    }

    private void unsubscribeWithMessage(MqttClientValues clientRecord, Mqtt5Unsubscribe unsubscribeMessage) {
        clientRecord.getMqttReactorClient().unsubscribe(unsubscribeMessage)
                .timeout(Duration.ofMillis(10000), Mono.empty()).subscribe();
    }
}
