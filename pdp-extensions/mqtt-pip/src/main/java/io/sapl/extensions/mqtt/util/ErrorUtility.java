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
package io.sapl.extensions.mqtt.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.hivemq.client.internal.mqtt.exceptions.MqttClientStateExceptions;
import com.hivemq.client.mqtt.exceptions.ConnectionClosedException;
import com.hivemq.client.mqtt.exceptions.ConnectionFailedException;
import com.hivemq.client.mqtt.exceptions.MqttClientStateException;
import com.hivemq.client.mqtt.exceptions.MqttSessionExpiredException;
import com.hivemq.client.mqtt.mqtt5.exceptions.Mqtt5DisconnectException;
import com.hivemq.client.mqtt.mqtt5.message.Mqtt5MessageType;
import io.sapl.api.model.Value;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Sinks;
import reactor.util.retry.Retry;
import reactor.util.retry.RetryBackoffSpec;

import java.time.Duration;
import java.util.NoSuchElementException;

import static io.sapl.extensions.mqtt.util.ConfigUtility.getConfigValueOrDefault;

/**
 * This utility class provides functions for error handling including retry
 * specifics for the mqtt message flux.
 */
@Slf4j
@UtilityClass
public class ErrorUtility {

    /**
     * The reference for the status in the configuration whether to emit a value at
     * retry or not.
     */
    public static final String   ENVIRONMENT_EMIT_AT_RETRY         = "emitAtRetry";
    static final String          ENVIRONMENT_ERROR_RETRY_ATTEMPTS  = "errorRetryAttempts";
    private static final String  ENVIRONMENT_MIN_ERROR_RETRY_DELAY = "minErrorRetryDelay";
    private static final String  ENVIRONMENT_MAX_ERROR_RETRY_DELAY = "maxErrorRetryDelay";
    private static final boolean DEFAULT_EMIT_AT_RETRY             = true;                // if true then an undefined
                                                                                          // value will be emitted at
                                                                                          // retry
    private static final long    ERROR_RETRY_ATTEMPTS              = 10000000;
    private static final long    MIN_ERROR_RETRY_DELAY             = 5000;                // in milliseconds
    private static final long    MAX_ERROR_RETRY_DELAY             = 10000;               // in milliseconds

    /**
     * Build the {@link RetryBackoffSpec} of the provided configurations.
     *
     * @param pipMqttClientConfig the pdp configuration
     * @return returns the build {@link RetryBackoffSpec}
     */
    public static RetryBackoffSpec getRetrySpec(JsonNode pipMqttClientConfig) {
        return Retry.backoff(
                getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_ERROR_RETRY_ATTEMPTS, ERROR_RETRY_ATTEMPTS),
                Duration.ofMillis(getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_MIN_ERROR_RETRY_DELAY,
                        MIN_ERROR_RETRY_DELAY)))
                .maxBackoff(Duration.ofMillis(getConfigValueOrDefault(pipMqttClientConfig,
                        ENVIRONMENT_MAX_ERROR_RETRY_DELAY, MAX_ERROR_RETRY_DELAY)))
                .transientErrors(true) // the retry spec will be reset after each successful retry
                .filter(errors -> !(errors instanceof NoSuchElementException)).doBeforeRetry(retrySignal -> {
                    long retryNumber = retrySignal.totalRetriesInARow() + 1;
                    log.debug("Trying to reestablish connection and subscription. Retry number "
                            + "since the last value was emitted: {}", retryNumber);
                });
    }

    /**
     * If enabled emit a value when retrying.
     *
     * @param pipMqttClientConfig the provided pdp configuration
     * @param emitterUndefined the emitter necessary to emit downstream
     * @param retrySignal containing specifics about the retry
     */
    public static void emitValueOnRetry(JsonNode pipMqttClientConfig, Sinks.Many<Value> emitterUndefined,
            Retry.RetrySignal retrySignal) {
        boolean isUndefinedAtRetryEnabled = getConfigValueOrDefault(pipMqttClientConfig, ENVIRONMENT_EMIT_AT_RETRY,
                DEFAULT_EMIT_AT_RETRY);
        long    retryNumber               = retrySignal.totalRetriesInARow() + 1;
        if (isUndefinedAtRetryEnabled && retryNumber == 1) {
            emitterUndefined.tryEmitNext(Value.UNDEFINED);
        }
    }

    /**
     * Evaluates the {@link Throwable} whether the broker config hash has to be
     * removed from the client cache or not.
     *
     * @param throwable the {@link Throwable} to evaluate
     * @return returns true if the broker config hash has to be removed from the
     * client cache
     */
    public static boolean isErrorRelevantToRemoveClientCache(Throwable throwable) {
        return throwable instanceof ConnectionClosedException || throwable instanceof ConnectionFailedException
                || (throwable instanceof MqttClientStateException
                        && throwable.getMessage().equals(MqttClientStateExceptions.notConnected().getMessage()))
                || throwable instanceof MqttSessionExpiredException;
    }

    /**
     * Evaluates whether the client caused the disconnect from the broker or not.
     *
     * @param throwable the {@link Throwable} to evaluate
     * @return returns true if the client caused the disconnect
     */
    public static boolean isClientCausedDisconnect(Throwable throwable) {
        return throwable.getCause() instanceof Mqtt5DisconnectException
                && throwable.getCause().getMessage().contains("Client sent " + Mqtt5MessageType.DISCONNECT);
    }
}
