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
package io.sapl.spring.data.utils;

import io.sapl.spring.data.database.MongoReactiveMethodInvocation;
import io.sapl.spring.data.database.R2dbcMethodInvocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AnnotationUtilitiesTests {

    @Test
    void when_hasAnnotationQueryReactiveMongo_then_returnTrue() {
        // GIVEN
        final var methodInvocation = new MongoReactiveMethodInvocation("findAllUsersTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        final var method           = methodInvocation.getMethod();

        // WHEN
        final var result = AnnotationUtilities.hasAnnotationQueryReactiveMongo(method);

        // THEN
        assertTrue(result);
    }

    @Test
    void when_hasAnnotationQueryReactiveMongo_then_returnFalsee() {
        // GIVEN
        final var methodInvocation = new MongoReactiveMethodInvocation("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        final var method           = methodInvocation.getMethod();

        // WHEN
        final var result = AnnotationUtilities.hasAnnotationQueryReactiveMongo(method);

        // THEN
        assertFalse(result);
    }

    @Test
    void when_hasAnnotationQueryR2dbc_then_returnTrue() {
        // GIVEN
        final var methodInvocation = new R2dbcMethodInvocation("findAllUsersTest", new ArrayList<>(List.of()),
                new ArrayList<>(List.of()), null);
        final var method           = methodInvocation.getMethod();

        // WHEN
        final var result = AnnotationUtilities.hasAnnotationQueryR2dbc(method);

        // THEN
        assertTrue(result);
    }

    @Test
    void when_hasAnnotationQueryR2dbc_then_returnFalse() {
        // GIVEN
        final var methodInvocation = new R2dbcMethodInvocation("findByAge", new ArrayList<>(List.of(int.class)),
                new ArrayList<>(List.of(123)), null);
        final var method           = methodInvocation.getMethod();

        // WHEN
        final var result = AnnotationUtilities.hasAnnotationQueryR2dbc(method);

        // THEN
        assertFalse(result);
    }

    @Test
    void when_hasAnnotationQueryEnforce_then_returnTrue() {
        // GIVEN
        final var methodInvocation = new R2dbcMethodInvocation("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        final var method           = methodInvocation.getMethod();

        // WHEN
        final var result = AnnotationUtilities.hasAnnotationQueryEnforce(method);

        // THEN
        assertTrue(result);
    }

    @Test
    void when_hasAnnotationQueryEnforce_then_returnFalsee() {
        // GIVEN
        final var methodInvocation = new R2dbcMethodInvocation("findAllUsersTest", new ArrayList<>(List.of()),
                new ArrayList<>(List.of()), null);
        final var method           = methodInvocation.getMethod();

        // WHEN
        final var result = AnnotationUtilities.hasAnnotationQueryEnforce(method);

        // THEN
        assertFalse(result);
    }

}
