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
package io.sapl.springdatamongoreactive.sapl.queries.enforcement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;
import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.repository.query.parser.PartTree;

import io.sapl.springdatamongoreactive.sapl.utils.Utilities;

class ReflectedMongoQueryCreatorConstructorTests {

    @Test
    void when_getDeclaredConstructorGetsWrongArguments_then_throwInvocationTargetException()
            throws InvocationTargetException {
        // GIVEN
        var reflectedMongoQueryCreator = new ReflectedMongoQueryCreatorConstructor(Utilities.MONGO_QUERY_CREATOR_NAME);

        // WHEN
        Constructor<?> constructor = reflectedMongoQueryCreator.getDeclaredConstructor(PartTree.class,
                ConvertingParameterAccessor.class, MappingContext.class, boolean.class);

        // THEN
        assertNotNull(constructor);
    }

    @Test
    void when_getDeclaredConstructor_then_returnRealMongoQueryCreatorConstructor() {
        // GIVEN
        var reflectedMongoQueryCreator = new ReflectedMongoQueryCreatorConstructor(Utilities.MONGO_QUERY_CREATOR_NAME);

        // WHEN

        // THEN
        assertThrows(InvocationTargetException.class, () -> reflectedMongoQueryCreator
                .getDeclaredConstructor(String.class, String.class, String.class, String.class));
    }

    @Test
    void when_pathToMongoQueryCreatorNotExists_then_throwClassNotFoundException() {
        // GIVEN

        // WHEN

        // THEN
        assertThrows(ClassNotFoundException.class, () -> new ReflectedMongoQueryCreatorConstructor("path.not.found"));
    }

}
