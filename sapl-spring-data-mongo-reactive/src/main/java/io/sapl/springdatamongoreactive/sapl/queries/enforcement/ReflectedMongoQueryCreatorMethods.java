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

import lombok.SneakyThrows;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Method;
import java.util.Iterator;

public class ReflectedMongoQueryCreatorMethods {

    private Object mongoQueryCreatorInstance;
    private Method createMethod;
    private Method andMethod;
    private Method orMethod;

    @SneakyThrows // throws NoSuchMethodException, SecurityException
    public void initializeMethods(Object mongoQueryCreatorInstance) {
        this.mongoQueryCreatorInstance = mongoQueryCreatorInstance;

        this.createMethod = mongoQueryCreatorInstance.getClass().getDeclaredMethod("create", Part.class,
                Iterator.class);
        this.andMethod    = mongoQueryCreatorInstance.getClass().getDeclaredMethod("and", Part.class, Criteria.class,
                Iterator.class);
        this.orMethod     = mongoQueryCreatorInstance.getClass().getDeclaredMethod("or", Criteria.class,
                Criteria.class);

        ReflectionUtils.makeAccessible(this.createMethod);
        ReflectionUtils.makeAccessible(this.andMethod);
        ReflectionUtils.makeAccessible(this.orMethod);
    }

    /**
     * Fetches the original method named 'create' of the class named
     * MongoQueryCreator by using reflection and invokes it.
     * <p>
     * Creates a new atomic instance of the criteria object.
     *
     * @param part     is a part of a PartTree.
     * @param iterator is an iterator built from all parameters.
     * @return a new {@link Criteria}.
     */
    @SneakyThrows // throws IllegalAccessException, IllegalArgumentException,
                  // InvocationTargetException
    public Criteria create(Part part, Iterator<Object> iterator) {
        return (Criteria) createMethod.invoke(this.mongoQueryCreatorInstance, part, iterator);
    }

    /**
     * Fetches the original method named 'and' of the class named MongoQueryCreator
     * by using reflection and invokes it.
     * <p>
     * Creates a new criteria object from the given part and and-concatenates it to
     * the given base criteria.
     *
     * @param part     is a part of a PartTree.
     * @param base     is the current base criteria.
     * @param iterator is an iterator built from all parameters.
     * @return a new {@link Criteria}.
     */
    @SneakyThrows // throws IllegalAccessException, IllegalArgumentException,
                  // InvocationTargetException
    public Criteria and(Part part, Criteria base, Iterator<Object> iterator) {
        return (Criteria) andMethod.invoke(this.mongoQueryCreatorInstance, part, base, iterator);
    }

    /**
     * Fetches the original method named 'or' of the class named MongoQueryCreator
     * by using reflection and invokes it.
     * <p>
     * Or-concatenates the given base criteria to the given new criteria.
     *
     * @param base     is the current base criteria.
     * @param criteria is an iterator built from all parameters.
     * @return a new {@link Criteria}.
     */
    @SneakyThrows // throws IllegalAccessException, IllegalArgumentException,
                  // InvocationTargetException
    public Criteria or(Criteria base, Criteria criteria) {
        return (Criteria) orMethod.invoke(this.mongoQueryCreatorInstance, base, criteria);
    }

}
