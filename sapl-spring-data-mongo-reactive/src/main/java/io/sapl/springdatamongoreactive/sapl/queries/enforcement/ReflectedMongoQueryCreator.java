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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import lombok.SneakyThrows;

class ReflectedMongoQueryCreator {

    private static final String MONGO_QUERY_CREATOR_NAME = "org.springframework.data.mongodb.repository.query.MongoQueryCreator";
    Class<?>                    mongoQueryCreator;

    @SneakyThrows
    protected ReflectedMongoQueryCreator() {
        this.mongoQueryCreator = Class.forName(MONGO_QUERY_CREATOR_NAME);
    }

    protected Constructor<?> getDeclaredConstructor(Class<?> class1, Class<?> class2, Class<?> class3, Class<?> class4)
            throws InvocationTargetException {
        Constructor<?> constructor = null;
        try {
            constructor = mongoQueryCreator.getDeclaredConstructor(class1, class2, class3, class4);
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InvocationTargetException(e.getCause());
        }
        return constructor;
    }

}
