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
package io.sapl.springdatamongoreactive.sapl.querytypes.methodnameenforcement;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.query.ConvertingParameterAccessor;
import org.springframework.data.mongodb.repository.query.MongoParametersParameterAccessor;
import org.springframework.data.mongodb.repository.query.MongoQueryMethod;
import org.springframework.data.repository.core.support.AbstractRepositoryMetadata;
import org.springframework.data.repository.query.parser.PartTree;

import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.MongoDbRepositoryTest;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;

@SpringBootTest
class MongoQueryCreatorFactoryTest {

    @Autowired
    private MongoDbRepositoryTest repositoryTest;

    @SpyBean
    private ReactiveMongoTemplate reactiveMongoTemplateSpy;

    MockedStatic<AbstractRepositoryMetadata> abstractRepositoryMetadataMockedStatic;

    @BeforeEach
    public void beforeEach() {
        abstractRepositoryMetadataMockedStatic = mockStatic(AbstractRepositoryMetadata.class);
    }

    @AfterEach
    public void cleanup() {
        abstractRepositoryMetadataMockedStatic.close();
    }

    @Test
    void when_mongoQueryCreatorInstanceCanBeCreated_then_createInstance() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var partTree                  = new PartTree("findAllByFirstnameAndAgeBefore", TestUser.class);
        var args                      = mongoMethodInvocationTest.getArguments();
        var method                    = mongoMethodInvocationTest.getMethod();

        // WHEN
        try (MockedConstruction<MongoQueryMethod> mongoQueryMethodMockedConstruction = Mockito
                .mockConstruction(MongoQueryMethod.class)) {
            try (MockedConstruction<MongoParametersParameterAccessor> mongoParametersParameterAccessorMockedConstruction = Mockito
                    .mockConstruction(MongoParametersParameterAccessor.class)) {
                try (MockedConstruction<ConvertingParameterAccessor> convertingParameterAccessorMockedConstruction = Mockito
                        .mockConstruction(ConvertingParameterAccessor.class)) {
                    var mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repositoryTest.getClass(),
                            reactiveMongoTemplateSpy);
                    mongoQueryCreatorFactory.createInstance(partTree, method, args);

                    var mongoQueryMethod                 = mongoQueryMethodMockedConstruction.constructed().get(0);
                    var mongoParametersParameterAccessor = mongoParametersParameterAccessorMockedConstruction
                            .constructed().get(0);
                    var convertingParameterAccessor      = convertingParameterAccessorMockedConstruction.constructed()
                            .get(0);

                    // THEN
                    Assertions.assertNotNull(mongoQueryMethod);
                    Assertions.assertNotNull(mongoParametersParameterAccessor);
                    Assertions.assertNotNull(convertingParameterAccessor);
                }
            }
        }

        abstractRepositoryMetadataMockedStatic.verify(() -> AbstractRepositoryMetadata.getMetadata(any(Class.class)),
                times(1));
    }

    @Test
    void when_mongoQueryCreatorInstanceCanBeCreated_then_createCriteria() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Aaron")), null);
        var method                    = mongoMethodInvocationTest.getMethod();
        var partTree                  = new PartTree("findAllByFirstname", TestUser.class);
        var args                      = mongoMethodInvocationTest.getArguments();
        var expected                  = new Query(Criteria.where("firstname").is("Aaron")).with(Sort.by(List.of()));

        // WHEN
        try (MockedConstruction<MongoQueryMethod> mongoQueryMethodMockedConstruction = Mockito
                .mockConstruction(MongoQueryMethod.class)) {
            try (MockedConstruction<MongoParametersParameterAccessor> mongoParametersParameterAccessorMockedConstruction = Mockito
                    .mockConstruction(MongoParametersParameterAccessor.class)) {
                try (MockedConstruction<ConvertingParameterAccessor> convertingParameterAccessorMockedConstruction = Mockito
                        .mockConstruction(ConvertingParameterAccessor.class)) {
                    var mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repositoryTest.getClass(),
                            reactiveMongoTemplateSpy);
                    mongoQueryCreatorFactory.createInstance(partTree, method, args);

                    var part     = partTree.getParts().toList().get(0);
                    var iterator = Arrays.stream(args).iterator();
                    var result   = new Query(mongoQueryCreatorFactory.create(part, iterator));

                    var mongoQueryMethod                 = mongoQueryMethodMockedConstruction.constructed().get(0);
                    var mongoParametersParameterAccessor = mongoParametersParameterAccessorMockedConstruction
                            .constructed().get(0);
                    var convertingParameterAccessor      = convertingParameterAccessorMockedConstruction.constructed()
                            .get(0);

                    // THEN
                    Assertions.assertNotNull(mongoQueryMethod);
                    Assertions.assertNotNull(mongoParametersParameterAccessor);
                    Assertions.assertNotNull(convertingParameterAccessor);
                    Assertions.assertEquals(result, expected);
                }
            }
        }

        abstractRepositoryMetadataMockedStatic.verify(() -> AbstractRepositoryMetadata.getMetadata(any(Class.class)),
                times(1));
    }

    @Test
    void when_mongoQueryCreatorInstanceCanBeCreated_then_andCriteria() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var method                    = mongoMethodInvocationTest.getMethod();
        var partTree                  = new PartTree("findAllByFirstnameAndAgeBefore", TestUser.class);
        var args                      = mongoMethodInvocationTest.getArguments();
        var expected                  = new Query(Criteria.where("firstname").is("Aaron"))
                .addCriteria(Criteria.where("age").lt(22)).with(Sort.by(List.of()));

        // WHEN
        try (MockedConstruction<MongoQueryMethod> mongoQueryMethodMockedConstruction = Mockito
                .mockConstruction(MongoQueryMethod.class)) {
            try (MockedConstruction<MongoParametersParameterAccessor> mongoParametersParameterAccessorMockedConstruction = Mockito
                    .mockConstruction(MongoParametersParameterAccessor.class)) {
                try (MockedConstruction<ConvertingParameterAccessor> convertingParameterAccessorMockedConstruction = Mockito
                        .mockConstruction(ConvertingParameterAccessor.class)) {
                    var mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repositoryTest.getClass(),
                            reactiveMongoTemplateSpy);
                    mongoQueryCreatorFactory.createInstance(partTree, method, args);

                    var part          = partTree.getParts().toList();
                    var iterator      = Arrays.stream(args).iterator();
                    var baseCriteria  = mongoQueryCreatorFactory.create(part.get(0), iterator);
                    var resultWithAnd = new Query(mongoQueryCreatorFactory.and(part.get(1), baseCriteria, iterator));

                    var mongoQueryMethod                 = mongoQueryMethodMockedConstruction.constructed().get(0);
                    var mongoParametersParameterAccessor = mongoParametersParameterAccessorMockedConstruction
                            .constructed().get(0);
                    var convertingParameterAccessor      = convertingParameterAccessorMockedConstruction.constructed()
                            .get(0);

                    // THEN
                    Assertions.assertNotNull(mongoQueryMethod);
                    Assertions.assertNotNull(mongoParametersParameterAccessor);
                    Assertions.assertNotNull(convertingParameterAccessor);
                    Assertions.assertEquals(resultWithAnd.toString(), expected.toString());
                }
            }
        }

        abstractRepositoryMetadataMockedStatic.verify(() -> AbstractRepositoryMetadata.getMetadata(any(Class.class)),
                times(1));
    }

    @Test
    void when_mongoQueryCreatorInstanceCanBeCreated_then_orCriteria() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstnameOrAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var method                    = mongoMethodInvocationTest.getMethod();
        var partTree                  = new PartTree("findAllByFirstnameOrAgeBefore", TestUser.class);
        var args                      = mongoMethodInvocationTest.getArguments();
        var expected                  = "Query: { \"$or\" : [{ \"firstname\" : \"Aaron\"}, { \"age\" : { \"$lt\" : 22}}]}, Fields: {}, Sort: {}";
        var part                      = partTree.getParts().toList();
        var iterator                  = Arrays.stream(args).iterator();

        // WHEN
        try (MockedConstruction<MongoQueryMethod> mongoQueryMethodMockedConstruction = Mockito
                .mockConstruction(MongoQueryMethod.class)) {
            try (MockedConstruction<MongoParametersParameterAccessor> mongoParametersParameterAccessorMockedConstruction = Mockito
                    .mockConstruction(MongoParametersParameterAccessor.class)) {
                try (MockedConstruction<ConvertingParameterAccessor> convertingParameterAccessorMockedConstruction = Mockito
                        .mockConstruction(ConvertingParameterAccessor.class)) {
                    var mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repositoryTest.getClass(),
                            reactiveMongoTemplateSpy);
                    mongoQueryCreatorFactory.createInstance(partTree, method, args);

                    var baseCriteria                        = mongoQueryCreatorFactory.create(part.get(0), iterator);
                    var resultWithAnd                       = mongoQueryCreatorFactory.create(part.get(1), iterator);
                    var resultWithOr                        = new Query(
                            mongoQueryCreatorFactory.or(baseCriteria, resultWithAnd));
                    var expectedConvertingParameterAccessor = mongoQueryCreatorFactory.getConvertingParameterAccessor();
                    var mongoQueryMethod                    = mongoQueryMethodMockedConstruction.constructed().get(0);
                    var mongoParametersParameterAccessor    = mongoParametersParameterAccessorMockedConstruction
                            .constructed().get(0);
                    var convertingParameterAccessor         = convertingParameterAccessorMockedConstruction
                            .constructed().get(0);

                    // THEN
                    Assertions.assertNotNull(mongoQueryMethod);
                    Assertions.assertNotNull(mongoParametersParameterAccessor);
                    Assertions.assertEquals(expectedConvertingParameterAccessor, convertingParameterAccessor);
                    Assertions.assertEquals(expected, resultWithOr.toString());
                }
            }
        }

        abstractRepositoryMetadataMockedStatic.verify(() -> AbstractRepositoryMetadata.getMetadata(any(Class.class)),
                times(1));
    }

    @Test
    void when_mongoQueryCreatorInstanceCanNotBeCreated_then_throwInvocationTargetException() {
        // GIVEN

        // WHEN
        try (MockedConstruction<MongoQueryMethod> mongoQueryMethodMockedConstruction = Mockito
                .mockConstruction(MongoQueryMethod.class)) {
            try (MockedConstruction<MongoParametersParameterAccessor> mongoParametersParameterAccessorMockedConstruction = Mockito
                    .mockConstruction(MongoParametersParameterAccessor.class)) {
                try (MockedConstruction<ConvertingParameterAccessor> convertingParameterAccessorMockedConstruction = Mockito
                        .mockConstruction(ConvertingParameterAccessor.class)) {
                    var mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repositoryTest.getClass(),
                            reactiveMongoTemplateSpy);

                    // THEN
                    Assertions.assertThrows(InvocationTargetException.class,
                            () -> mongoQueryCreatorFactory.createInstance(null, null, null));
                }
            }
        }
    }

    @Test
    void when_mongoQueryCreatorInstanceCanBeCreatedButNotCreateMethodInvoked_then_throwInvocationTargetException() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstnameOrAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var method                    = mongoMethodInvocationTest.getMethod();
        var partTree                  = new PartTree("findAllByFirstnameOrAgeBefore", TestUser.class);
        var args                      = mongoMethodInvocationTest.getArguments();
        var part                      = partTree.getParts().toList();

        // WHEN
        try (MockedConstruction<MongoQueryMethod> mongoQueryMethodMockedConstruction = Mockito
                .mockConstruction(MongoQueryMethod.class)) {
            try (MockedConstruction<MongoParametersParameterAccessor> mongoParametersParameterAccessorMockedConstruction = Mockito
                    .mockConstruction(MongoParametersParameterAccessor.class)) {
                try (MockedConstruction<ConvertingParameterAccessor> convertingParameterAccessorMockedConstruction = Mockito
                        .mockConstruction(ConvertingParameterAccessor.class)) {
                    var mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repositoryTest.getClass(),
                            reactiveMongoTemplateSpy);
                    mongoQueryCreatorFactory.createInstance(partTree, method, args);

                    // THEN
                    Assertions.assertThrows(InvocationTargetException.class,
                            () -> mongoQueryCreatorFactory.create(part.get(0), null));
                }
            }
        }
    }

    @Test
    void when_mongoQueryCreatorInstanceCanBeCreatedButNotAndMethodInvoked_then_throwInvocationTargetException() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstnameOrAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var method                    = mongoMethodInvocationTest.getMethod();
        var partTree                  = new PartTree("findAllByFirstnameOrAgeBefore", TestUser.class);
        var args                      = mongoMethodInvocationTest.getArguments();

        // WHEN
        try (MockedConstruction<MongoQueryMethod> mongoQueryMethodMockedConstruction = Mockito
                .mockConstruction(MongoQueryMethod.class)) {
            try (MockedConstruction<MongoParametersParameterAccessor> mongoParametersParameterAccessorMockedConstruction = Mockito
                    .mockConstruction(MongoParametersParameterAccessor.class)) {
                try (MockedConstruction<ConvertingParameterAccessor> convertingParameterAccessorMockedConstruction = Mockito
                        .mockConstruction(ConvertingParameterAccessor.class)) {
                    var mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repositoryTest.getClass(),
                            reactiveMongoTemplateSpy);
                    mongoQueryCreatorFactory.createInstance(partTree, method, args);

                    // THEN
                    Assertions.assertThrows(InvocationTargetException.class,
                            () -> mongoQueryCreatorFactory.and(null, null, null));
                }
            }
        }
    }

    @Test
    void when_mongoQueryCreatorInstanceCanBeCreatedButNotOrMethodInvoked_then_throwInvocationTargetException() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstnameOrAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var method                    = mongoMethodInvocationTest.getMethod();
        var partTree                  = new PartTree("findAllByFirstnameOrAgeBefore", TestUser.class);
        var args                      = mongoMethodInvocationTest.getArguments();

        // WHEN
        try (MockedConstruction<MongoQueryMethod> mongoQueryMethodMockedConstruction = Mockito
                .mockConstruction(MongoQueryMethod.class)) {
            try (MockedConstruction<MongoParametersParameterAccessor> mongoParametersParameterAccessorMockedConstruction = Mockito
                    .mockConstruction(MongoParametersParameterAccessor.class)) {
                try (MockedConstruction<ConvertingParameterAccessor> convertingParameterAccessorMockedConstruction = Mockito
                        .mockConstruction(ConvertingParameterAccessor.class)) {
                    var mongoQueryCreatorFactory = new MongoQueryCreatorFactory(repositoryTest.getClass(),
                            reactiveMongoTemplateSpy);
                    mongoQueryCreatorFactory.createInstance(partTree, method, args);

                    // THEN
                    Assertions.assertThrows(InvocationTargetException.class,
                            () -> mongoQueryCreatorFactory.or(null, null));
                }
            }
        }
    }
}
