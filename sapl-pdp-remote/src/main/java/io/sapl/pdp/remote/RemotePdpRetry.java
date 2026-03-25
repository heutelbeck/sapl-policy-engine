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
package io.sapl.pdp.remote;

import java.time.Duration;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import reactor.util.retry.Retry;

/**
 * Shared retry and logging utilities for remote PDP clients.
 */
@Slf4j
@UtilityClass
class RemotePdpRetry {

    static final String ERROR_STREAM_RECONNECT  = "PDP streaming connection lost, reconnecting (attempt {})";
    static final String WARN_INSECURE_SSL       = "!!! ATTENTION: do not use insecure sslContext in production !!!";
    static final String WARN_INSECURE_SSL_DELIM = "------------------------------------------------------------------";

    static final int RETRY_ESCALATION_THRESHOLD = 5;

    static Retry createRetrySpec(long maxRetries, int firstBackoffMillis, int maxBackOffMillis) {
        return Retry.backoff(maxRetries, Duration.ofMillis(firstBackoffMillis))
                .maxBackoff(Duration.ofMillis(maxBackOffMillis)).doBeforeRetry(signal -> {
                    val attempt = signal.totalRetries() + 1;
                    if (attempt >= RETRY_ESCALATION_THRESHOLD) {
                        log.error(ERROR_STREAM_RECONNECT, attempt);
                    } else {
                        log.warn(ERROR_STREAM_RECONNECT, attempt);
                    }
                });
    }

    static void logInsecureSslWarning() {
        log.warn(WARN_INSECURE_SSL_DELIM);
        log.warn(WARN_INSECURE_SSL);
        log.warn(WARN_INSECURE_SSL_DELIM);
    }
}
