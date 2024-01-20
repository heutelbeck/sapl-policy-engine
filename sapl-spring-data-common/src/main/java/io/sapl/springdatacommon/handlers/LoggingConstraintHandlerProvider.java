/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatacommon.handlers;

import com.fasterxml.jackson.databind.JsonNode;

import lombok.extern.slf4j.Slf4j;

/**
 * This class represents the logging constraint handler provider.
 */
@Slf4j
public class LoggingConstraintHandlerProvider {

    private static final String MESSAGE = "message";

    /**
     * Checks if an obligation of a {@link io.sapl.api.pdp.Decision} is responsible
     * and can be applied.
     *
     * @param constraint is an obligation of a {@link io.sapl.api.pdp.Decision}
     * @return true if the obligation can be applied.
     */
    public boolean isResponsible(JsonNode constraint) {
        if (constraint == null) {
            return false;
        }
        return constraint.has(MESSAGE) && constraint.has("id") && "log".equals(constraint.get("id").asText());
    }

    /**
     * Get the handler to be able to use it.
     *
     * @param constraint is an obligation of a {@link io.sapl.api.pdp.Decision}
     * @return a {@link Runnable}
     */
    public Runnable getHandler(JsonNode constraint) {
        return () -> {
            if (constraint != null && constraint.has(MESSAGE)) {
                var message = constraint.findValue(MESSAGE).asText();
                log.info(message);
            }
        };
    }

}
