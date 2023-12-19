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

import io.sapl.springdatar2dbc.database.Role;
import org.junit.jupiter.api.Test;
import org.junit.platform.commons.util.ReflectionUtils;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import io.sapl.springdatar2dbc.database.Person;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class QueryManipulationExecutorTest {

//    @Test
//    void when_r2dbcEntityTemplateWasFoundAndQueryContainsWHERE_then_executeQuery() {
//        // GIVEN
//        var beanFactoryMock         = mock(BeanFactory.class);
//        var r2dbcEntityTemplateMock = mock(R2dbcEntityTemplate.class, RETURNS_DEEP_STUBS);
//        var query                   = "SELECT * FROM person";
//        var cathrin                 = new Person(123, "Cathrin", "Second", 32, Role.ADMIN, Boolean.TRUE);
//        var userHashMap             = new HashMap<String, Object>();
//        userHashMap.put("1", cathrin);
//
//        // WHEN
//        when(beanFactoryMock.getBean(R2dbcEntityTemplate.class)).thenReturn(r2dbcEntityTemplateMock);
//        when(r2dbcEntityTemplateMock.getDatabaseClient().sql(anyString()).fetch().all()).thenReturn(Flux.just(userHashMap));
//
//        var result = QueryManipulationExecutor.execute(query, beanFactoryMock, Person.class);
//
//        // THEN
//        StepVerifier.create(result)
//                .expectNext(userHashMap)
//                .verifyComplete();
//
//        Mockito.verify(r2dbcEntityTemplateMock.getDataAccessStrategy().getTableName(eq(Person.class)), times(1)).getReference();
//        Mockito.verify(r2dbcEntityTemplateMock.getDatabaseClient().sql(anyString()).fetch(), times(1)).all();
//    }
//
//    @Test
//    void when_r2dbcEntityTemplateWasFound_then_executeQuery() {
//        // GIVEN
//        var beanFactoryMock         = mock(BeanFactory.class);
//        var r2dbcEntityTemplateMock = mock(R2dbcEntityTemplate.class, RETURNS_DEEP_STUBS);
//        var query                   = "SELECT * FROM person WHERE";
//        var cathrin                 = new Person(123, "Cathrin", "Second", 32, Role.ADMIN, Boolean.TRUE);
//        var userHashMap             = new HashMap<String, Object>();
//        userHashMap.put("1", cathrin);
//
//        // WHEN
//        when(beanFactoryMock.getBean(R2dbcEntityTemplate.class)).thenReturn(r2dbcEntityTemplateMock);
//        when(r2dbcEntityTemplateMock.getDatabaseClient().sql(anyString()).fetch().all()).thenReturn(Flux.just(userHashMap));
//
//        var result = QueryManipulationExecutor.execute(query, beanFactoryMock, Person.class);
//
//        // THEN
//        StepVerifier.create(result)
//                .expectNext(userHashMap)
//                .verifyComplete();
//
//        Mockito.verify(r2dbcEntityTemplateMock.getDataAccessStrategy().getTableName(eq(Person.class)), never()).getReference();
//        Mockito.verify(r2dbcEntityTemplateMock.getDatabaseClient().sql(anyString()).fetch(), times(1)).all();
//    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = QueryManipulationExecutor.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }
}
