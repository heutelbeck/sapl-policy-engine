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
package io.sapl.springdatacommon.sapl.handlers;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.springdatacommon.handlers.LoggingConstraintHandlerProvider;

class LoggingConstraintHandlerProviderTests {

    LoggingConstraintHandlerProvider loggingConstraintHandlerProvider = new LoggingConstraintHandlerProvider();

    static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void when_constraintIsResponsible_then_returnTrue() throws JsonProcessingException {
        // GIVEN
        var constraint = MAPPER.readTree("""
                      		{
                  "id": "log",
                  "message": "You are using SAPL for protection of database."
                }
                      		""");

        // WHEN
        var actual = loggingConstraintHandlerProvider.isResponsible(constraint);

        // THEN
        assertTrue(actual);
    }

    @Test
    void when_constraintHasWrongMessageKey_then_returnFalse() throws JsonProcessingException {
        // GIVEN
        var constraint = MAPPER.readTree("""
                      		{
                  "id": "log",
                  "messageTest": "You are using SAPL for protection of database."
                }
                      		""");

        // WHEN
        var actual = loggingConstraintHandlerProvider.isResponsible(constraint);

        // THEN
        assertFalse(actual);
    }

    @Test
    void when_constraintHasWrongLogKey_then_returnFalse() throws JsonProcessingException {
        // GIVEN
        var constraint = MAPPER.readTree("""
                      		{
                  "id": "logTest",
                  "message": "You are using SAPL for protection of database."
                }
                      		""");

        // WHEN
        var actual = loggingConstraintHandlerProvider.isResponsible(constraint);

        // THEN
        assertFalse(actual);
    }

    @Test
    void when_constraintIsNull_then_returnFalse() {
        // GIVEN

        // WHEN
        var actual = loggingConstraintHandlerProvider.isResponsible(null);

        // THEN
        assertFalse(actual);
    }

    @Test
    void when_constraintIsResponsible_then_returnFalse() throws JsonProcessingException {
        // GIVEN
        var constraintNotValid = MAPPER.readTree("""
                      		{
                  "idTest": "log",
                  "message": "You are using SAPL for protection of database."
                }
                      		""");

        // WHEN
        var actual = loggingConstraintHandlerProvider.isResponsible(constraintNotValid);

        // THEN
        assertFalse(actual);
    }

    @Test
    void when_constraintIsResponsible_then_getHandler() throws JsonProcessingException {
        // GIVEN
        var constraint = MAPPER.readTree("""
                 		{
                  "id": "log",
                  "message": "You are using SAPL for protection of database."
                }
                			""");
        // WHEN
        var actual = loggingConstraintHandlerProvider.getHandler(constraint);

        // THEN
        assertTrue(Runnable.class.isInstance(actual));
        actual.run();
    }
}
