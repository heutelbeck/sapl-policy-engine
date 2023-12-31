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
package io.sapl.springdatar2dbc.sapl;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.PersonWithoutTableAnnotation;
import io.sapl.springdatar2dbc.database.Role;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

class QueryManipulationExecutorTest {

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    R2dbcEntityTemplate r2dbcEntityTemplateMock;

    Map<String, Object> userHashMap = Map.of("1", new Person(123, "Cathrin", "Second", 32, Role.ADMIN, Boolean.TRUE));

    @Test
    void when_r2dbcEntityTemplateWasFoundAndQueryContainsWHERE_then_executeQuery() {
        // GIVEN
        var beanFactoryMock = mock(BeanFactory.class);
        var query           = "SELECT * FROM person WHERE firstname = 'Melinda'";
        var cathrin         = new Person(123, "Cathrin", "Second", 32, Role.ADMIN, Boolean.TRUE);
        var userHashMap     = new HashMap<String, Object>();
        userHashMap.put("1", cathrin);

        try (MockedConstruction<R2dbcEntityTemplateExecutor> r2dbcEntityTemplateExecutorMockedConstruction = Mockito
                .mockConstruction(R2dbcEntityTemplateExecutor.class)) {

            // WHEN
            when(beanFactoryMock.getBean(R2dbcEntityTemplate.class)).thenReturn(r2dbcEntityTemplateMock);

            QueryManipulationExecutor queryManipulationExecutor   = new QueryManipulationExecutor(beanFactoryMock);
            var                       r2dbcEntityTemplateExecutor = r2dbcEntityTemplateExecutorMockedConstruction
                    .constructed().get(0);

            when(r2dbcEntityTemplateExecutor.executeQuery(query)).thenReturn(Flux.just(userHashMap));

            var result = queryManipulationExecutor.execute(query, Person.class);

            // THEN
            StepVerifier.create(result).expectNext(userHashMap).verifyComplete();

            Mockito.verify(r2dbcEntityTemplateExecutor, times(1)).executeQuery(query);
        }
    }

    @Test
    void when_r2dbcEntityTemplateWasFound_then_executeQuery() {
        // GIVEN
        var query           = "firstname = 'Malinda'";
        var completeQuery   = "SELECT * FROM person WHERE " + query;
        var beanFactoryMock = mock(BeanFactory.class);

        try (MockedConstruction<R2dbcEntityTemplateExecutor> r2dbcEntityTemplateExecutorMockedConstruction = Mockito
                .mockConstruction(R2dbcEntityTemplateExecutor.class)) {

            // WHEN
            when(beanFactoryMock.getBean(R2dbcEntityTemplate.class)).thenReturn(r2dbcEntityTemplateMock);

            QueryManipulationExecutor queryManipulationExecutor   = new QueryManipulationExecutor(beanFactoryMock);
            var                       r2dbcEntityTemplateExecutor = r2dbcEntityTemplateExecutorMockedConstruction
                    .constructed().get(0);

            when(r2dbcEntityTemplateExecutor.executeQuery(completeQuery)).thenReturn(Flux.just(userHashMap));

            var result = queryManipulationExecutor.execute(query, Person.class);

            // THEN
            StepVerifier.create(result).expectNext(userHashMap).verifyComplete();

            Mockito.verify(r2dbcEntityTemplateExecutor, times(1)).executeQuery(completeQuery);
        }
    }

    @Test
    void when_r2dbcEntityTemplateWasFoundAndPersonHasNoAtTableAnnotation_then_executeQuery() {
        // GIVEN
        var query           = "firstname = 'Malinda'";
        var completeQuery   = "SELECT * FROM PersonWithoutTableAnnotation WHERE " + query;
        var beanFactoryMock = mock(BeanFactory.class);

        try (MockedConstruction<R2dbcEntityTemplateExecutor> r2dbcEntityTemplateExecutorMockedConstruction = Mockito
                .mockConstruction(R2dbcEntityTemplateExecutor.class)) {

            // WHEN
            when(beanFactoryMock.getBean(R2dbcEntityTemplate.class)).thenReturn(r2dbcEntityTemplateMock);

            QueryManipulationExecutor queryManipulationExecutor   = new QueryManipulationExecutor(beanFactoryMock);
            var                       r2dbcEntityTemplateExecutor = r2dbcEntityTemplateExecutorMockedConstruction
                    .constructed().get(0);

            when(r2dbcEntityTemplateExecutor.executeQuery(completeQuery)).thenReturn(Flux.just(userHashMap));

            var result = queryManipulationExecutor.execute(query, PersonWithoutTableAnnotation.class);

            // THEN
            StepVerifier.create(result).expectNext(userHashMap).verifyComplete();

            Mockito.verify(r2dbcEntityTemplateExecutor, times(1)).executeQuery(completeQuery);
        }
    }
}
