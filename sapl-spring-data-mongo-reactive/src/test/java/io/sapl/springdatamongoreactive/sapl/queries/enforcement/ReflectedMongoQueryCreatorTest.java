/*
 * Copyright (C) 2017-2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import java.lang.reflect.InvocationTargetException;

import org.junit.jupiter.api.Test;

class ReflectedMongoQueryCreatorTest {

    @Test
    void when_getDeclaredConstructor_then_returnRealMongoQueryCreatorConstructor() {
        // GIVEN
        var reflectedMongoQueryCreator = new ReflectedMongoQueryCreator();

        // WHEN

        // THEN
        assertThrows(InvocationTargetException.class, () -> reflectedMongoQueryCreator
                .getDeclaredConstructor(String.class, String.class, String.class, String.class));

    }

    @Test
    void when_constructorOfReflectedMongoQueryCreator_then_throwClassNotFoundException()
            throws NoSuchFieldException, SecurityException {
        // GIVEN
        setMongoQueryCreatorName("notValidClassName");

        // WHEN

        // THEN
        assertThrows(ClassNotFoundException.class, () -> new ReflectedMongoQueryCreator());

        setMongoQueryCreatorName("org.springframework.data.mongodb.repository.query.MongoQueryCreator");
    }

    private void setMongoQueryCreatorName(String className) throws NoSuchFieldException, SecurityException {
        var field = ReflectedMongoQueryCreator.class.getDeclaredField("MONGO_QUERY_CREATOR_NAME");
        field.setAccessible(true);
        try {
            field.set(field, className);
        } catch (IllegalArgumentException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }

}
