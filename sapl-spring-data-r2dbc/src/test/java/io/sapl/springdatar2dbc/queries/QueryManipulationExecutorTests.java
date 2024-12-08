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
package io.sapl.springdatar2dbc.queries;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.PersonWithoutAnnotation;
import reactor.core.publisher.Flux;

@ExtendWith(MockitoExtension.class)
class QueryManipulationExecutorTests {

    @Mock
    ObjectProvider<SqlQueryExecutor> objectProviderSqlQueryExecutorMock;

    SqlQueryExecutor sqlQueryExecutorMock = mock(SqlQueryExecutor.class);

    @Test
    void when_execute_then_returnFluxOfObjects1() {
        // GIVEN
        final var query            = "SELECT firstname, age FROM XXXXX WHERE age > 22";
        final var queryTransformed = "SELECT firstname, age FROM person WHERE age > 22";
        final var flux             = Flux.just(new Person(1, "Juni", 22, true));

        final var queryManipulationExecutor = new QueryManipulationExecutor(objectProviderSqlQueryExecutorMock);

        // WHEN
        lenient().when(objectProviderSqlQueryExecutorMock.getObject()).thenReturn(sqlQueryExecutorMock);
        when(sqlQueryExecutorMock.executeQuery(anyString(), eq(Person.class))).thenReturn(flux);

        queryManipulationExecutor.execute(query, Person.class);

        // THEN
        verify(sqlQueryExecutorMock, times(1)).executeQuery(queryTransformed, Person.class);
    }

    @Test
    void when_execute_then_returnFluxOfObjects2() {
        // GIVEN
        final var query = "SELECT firstname, age FROM person WHERE age > 22";
        final var flux  = Flux.just(new Person(1, "Juni", 22, true));

        final var queryManipulationExecutor = new QueryManipulationExecutor(objectProviderSqlQueryExecutorMock);

        // WHEN
        lenient().when(objectProviderSqlQueryExecutorMock.getObject()).thenReturn(sqlQueryExecutorMock);
        when(sqlQueryExecutorMock.executeQuery(anyString(), eq(Person.class))).thenReturn(flux);

        queryManipulationExecutor.execute(query, Person.class);

        // THEN
        verify(sqlQueryExecutorMock, times(1)).executeQuery(query, Person.class);
    }

    @Test
    void when_execute_then_returnFluxOfObjects3() {
        // GIVEN
        final var query            = "SELECT firstname, age FROM XXXXX WHERE age > 22";
        final var queryTransformed = "SELECT firstname, age FROM PersonWithoutAnnotation WHERE age > 22";
        final var flux             = Flux.just(new PersonWithoutAnnotation(1, "Juni", 22, true));

        final var queryManipulationExecutor = new QueryManipulationExecutor(objectProviderSqlQueryExecutorMock);

        // WHEN
        lenient().when(objectProviderSqlQueryExecutorMock.getObject()).thenReturn(sqlQueryExecutorMock);
        when(sqlQueryExecutorMock.executeQuery(anyString(), eq(PersonWithoutAnnotation.class))).thenReturn(flux);

        queryManipulationExecutor.execute(query, PersonWithoutAnnotation.class);

        // THEN
        verify(sqlQueryExecutorMock, times(1)).executeQuery(queryTransformed, PersonWithoutAnnotation.class);
    }

}
