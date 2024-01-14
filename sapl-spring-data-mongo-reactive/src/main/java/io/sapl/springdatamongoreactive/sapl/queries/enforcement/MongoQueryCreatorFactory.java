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

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Iterator;

import org.springframework.data.mapping.context.MappingContext;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.mapping.MongoPersistentEntity;
import org.springframework.data.mongodb.core.mapping.MongoPersistentProperty;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.mongodb.repository.query.MongoParametersParameterAccessor;
import org.springframework.data.mongodb.repository.query.MongoQueryMethod;
import org.springframework.data.projection.ProjectionFactory;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.data.repository.query.parser.Part;
import org.springframework.data.repository.query.parser.PartTree;
import org.springframework.util.ReflectionUtils;

import lombok.SneakyThrows;

/**
 * This class has the sole task of creating a valid instance of the
 * MongoQueryCreator class from
 * {@link org.springframework.data.mongodb.repository.query} which is needed to
 * use certain methods of spring data for the query creation.
 */
public class MongoQueryCreatorFactory {
    private final Class<?>              repository;
    private final ReactiveMongoTemplate reactiveMongoTemplate;

    private ProjectionFactory                                                           projectionFactory;
    private ConvertingParameterAccessor                                                 convertingParameterAccessor;
    private MappingContext<? extends MongoPersistentEntity<?>, MongoPersistentProperty> mappingContext;
    private final ReflectedMongoQueryCreator                                            mongoQueryCreator;
    private final ReflectedMongoQueryCreatorMethods                                     reflectedMongoQueryCreatorMethods;

    public MongoQueryCreatorFactory(Class<?> repository, ReactiveMongoTemplate reactiveMongoTemplate) {
        this.repository                        = repository;
        this.reactiveMongoTemplate             = reactiveMongoTemplate;
        this.mongoQueryCreator                 = new ReflectedMongoQueryCreator();
        this.reflectedMongoQueryCreatorMethods = new ReflectedMongoQueryCreatorMethods();
    }

    /**
     * Creates an instance of the MongoQueryCreator class.
     *
     * @param partTree         is the {@link PartTree} of the repository method.
     * @param repositoryMethod is the original repository method.
     * @param args             are the parameters of the method.
     */
    @SneakyThrows
    public void createInstance(PartTree partTree, Method repositoryMethod, Object[] args) {
        var mongoQueryCreatorInstance = createMongoQueryCreatorInstance(partTree, repositoryMethod, args);
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

    /**
     * Returns the created convertingParameterAccessor.
     *
     * @return the convertingParameterAccessor.
     */
    public ConvertingParameterAccessor getConvertingParameterAccessor() {
        return this.convertingParameterAccessor;
    }

    /**
     * This method creates an instance of the MongoQueryMethod class which is needed
     * for the creation of a
     *
     * @param method is the repository method.
     * @return a new MongoQueryMethod instance.
     * @see org.springframework.data.repository.query.ParameterAccessor This is
     *      necessary because spring data always derives the parameters from the
     *      original method. However, the goal here is to manipulate a method and
     *      its parameters enclosed. To still use the functionality of Spring Data
     *      to create the query, you have to do some trickery.
     */
    private MongoQueryMethod getMongoQueryMethod(Method method) {
        return new MongoQueryMethod(method, AbstractRepositoryMetadata.getMetadata(repository), projectionFactory,
                mappingContext);
    }

    /**
     * Creates the constructor for the later instance of the MongoQueryCreator
     * class.
     *
     * @return constructor of the MongoQueryCreator class.
     */
    private Constructor<?> createMongoQueryCreatorConstructor() throws InvocationTargetException {
        Constructor<?> constructor = mongoQueryCreator.getDeclaredConstructor(PartTree.class,
                ConvertingParameterAccessor.class, MappingContext.class, boolean.class);
        ReflectionUtils.makeAccessible(constructor);
        return constructor;
    }

    /**
     * Initializes variables that are necessary for further processing of the query
     * and Creates an instance of the MongoQueryCreator class. *
     *
     * @param partTree   is the {@link PartTree} of the repository method.
     * @param method     is the original repository method.
     * @param parameters are the parameters of the method.
     * @return new instance of MongoQueryCreator.
     */
    @SneakyThrows
    private Object createMongoQueryCreatorInstance(PartTree partTree, Method method, Object[] parameters) {
        var converter = reactiveMongoTemplate.getConverter();
        this.mappingContext    = converter.getMappingContext();
        this.projectionFactory = converter.getProjectionFactory();

        var mongoQueryMethod = getMongoQueryMethod(method);
        var accessor         = new MongoParametersParameterAccessor(mongoQueryMethod, parameters);
        var constructor      = createMongoQueryCreatorConstructor();
        this.convertingParameterAccessor = new ConvertingParameterAccessor(converter, accessor);

        return constructor.newInstance(partTree, convertingParameterAccessor, mappingContext, Boolean.FALSE);
    }
}
