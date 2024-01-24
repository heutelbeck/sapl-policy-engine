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

import java.lang.reflect.Method;
import java.util.Iterator;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;

/**
 * This class has the sole task of creating a valid instance of the
 * MongoQueryCreator class from
 * {@link org.springframework.data.mongodb.repository.query} which is needed to
 * use certain methods of spring data for the query creation.
 */
public class MongoQueryCreatorFactory {

    private final ReflectedMongoQueryCreatorMethods reflectedMongoQueryCreatorMethods;

    private final ReflectedMongoQueryCreatorInstantiator mongoQueryCreatorInstantiator;

    public MongoQueryCreatorFactory(Class<?> repository, ReactiveMongoTemplate reactiveMongoTemplate) {
        this.reflectedMongoQueryCreatorMethods = new ReflectedMongoQueryCreatorMethods();
        this.mongoQueryCreatorInstantiator     = new ReflectedMongoQueryCreatorInstantiator(reactiveMongoTemplate,
                repository);
    }

    /**
     * Creates an instance of the MongoQueryCreator class.
     *
     * @param partTree         is the {@link PartTree} of the repository method.
     * @param repositoryMethod is the original repository method.
     * @param args             are the parameters of the method.
     */
    public void createInstance(PartTree partTree, Method repositoryMethod, Object[] args) {
        var mongoQueryCreatorInstance = mongoQueryCreatorInstantiator.createMongoQueryCreatorInstance(partTree,
                repositoryMethod, args);
        this.reflectedMongoQueryCreatorMethods.initializeMethods(mongoQueryCreatorInstance);
    }

    public Criteria create(Part part, Iterator<Object> iterator) {
        return this.reflectedMongoQueryCreatorMethods.create(part, iterator);
    }

    public Criteria and(Part part, Criteria base, Iterator<Object> iterator) {
        return this.reflectedMongoQueryCreatorMethods.and(part, base, iterator);
    }

    public Criteria or(Criteria base, Criteria criteria) {
        return this.reflectedMongoQueryCreatorMethods.or(base, criteria);
    }

    public ConvertingParameterAccessor getConvertingParameterAccessor() {
        return this.mongoQueryCreatorInstantiator.getConvertingParameterAccessor();
    }

}
