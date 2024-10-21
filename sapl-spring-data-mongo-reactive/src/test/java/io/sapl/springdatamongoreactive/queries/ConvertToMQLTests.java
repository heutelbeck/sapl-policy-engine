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
package io.sapl.springdatamongoreactive.queries;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.aopalliance.intercept.MethodInvocation;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.BasicQuery;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class ConvertToMQLTests {

    MethodInvocation methodInvocationMock = mock(MethodInvocation.class);
    BasicQuery       queryMock            = mock(BasicQuery.class);

    @Test
    void when_createPageable_then_createPageableWithSortObjectsASC() {
        // GIVEN
        final var sortDocument = new Document("age", 0);
        final var sortObject   = Sort.by(Sort.Direction.ASC, "firstname");
        final var objects      = new Object[] { "Juni", sortObject };
        final var sort         = Sort.by(Sort.Direction.DESC, "age");

        // WHEN
        when(queryMock.getSortObject()).thenReturn(sortDocument);
        when(methodInvocationMock.getArguments()).thenReturn(objects);

        final var result = ConvertToMQL.createPageable(methodInvocationMock, queryMock);

        // THEN
        assertEquals(PageRequest.of(0, 2147483647, sort.and(sortObject)), result);
    }

    @Test
    void when_createPageable_then_createPageableWithPagableObjectsASC() {
        // GIVEN
        final var pageableObject = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "firstname"));
        final var sortDocument   = new Document();
        final var objects        = new Object[] { "Juni", pageableObject };

        // WHEN
        when(queryMock.getSortObject()).thenReturn(sortDocument);
        when(methodInvocationMock.getArguments()).thenReturn(objects);

        final var result = ConvertToMQL.createPageable(methodInvocationMock, queryMock);

        // THEN
        assertEquals(pageableObject, result);
    }

    @Test
    void when_createPageable_then_createPageableWithPagableObjectAndSortObjectASC() {
        // GIVEN
        final var pageableObject = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "firstname"));
        final var sortDocument   = new Document("age", 0);
        final var objects        = new Object[] { "Juni", pageableObject };
        final var sort           = Sort.by(Sort.Direction.DESC, "age");
        final var expected       = PageRequest.of(pageableObject.getPageNumber(), pageableObject.getPageSize(),
                sort.and(pageableObject.getSort()));

        // WHEN
        when(queryMock.getSortObject()).thenReturn(sortDocument);
        when(methodInvocationMock.getArguments()).thenReturn(objects);

        final var result = ConvertToMQL.createPageable(methodInvocationMock, queryMock);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_createPageable_then_createPageableWithPagableObjectAndSortIsEmpty() {
        // GIVEN
        final var pageableObject = PageRequest.of(0, 10, Sort.by(Sort.Direction.ASC, "firstname"));
        final var sortDocument   = new Document();
        final var objects        = new Object[] { "Juni", pageableObject };
        final var expected       = PageRequest.of(pageableObject.getPageNumber(), pageableObject.getPageSize(),
                pageableObject.getSort());

        // WHEN
        when(queryMock.getSortObject()).thenReturn(sortDocument);
        when(methodInvocationMock.getArguments()).thenReturn(objects);

        final var result = ConvertToMQL.createPageable(methodInvocationMock, queryMock);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_createPageable_then_createUnsortedPageableBecauseSortIsEmptyAndPageableIsNull() {
        // GIVEN
        final var sortDocument = new Document();
        final var objects      = new Object[] { "Juni" };
        final var expected     = Pageable.unpaged();

        // WHEN
        when(queryMock.getSortObject()).thenReturn(sortDocument);
        when(methodInvocationMock.getArguments()).thenReturn(objects);

        final var result = ConvertToMQL.createPageable(methodInvocationMock, queryMock);

        // THEN
        assertEquals(expected, result);
    }

    @Test
    void when_createPageable_then_createPageableWithSortObjectsDESC() {
        // GIVEN
        final var sortDocument = new Document("age", 1);
        final var sortObject   = Sort.by(Sort.Direction.DESC, "firstname");
        final var objects      = new Object[] { "Juni", sortObject };

        final var sort = Sort.by(Sort.Direction.ASC, "age");

        // WHEN
        when(queryMock.getSortObject()).thenReturn(sortDocument);
        when(methodInvocationMock.getArguments()).thenReturn(objects);

        final var result = ConvertToMQL.createPageable(methodInvocationMock, queryMock);

        // THEN
        assertEquals(PageRequest.of(0, 2147483647, sort.and(sortObject)), result);
    }

    @Test
    void when_createPageable_then_queryDoesNotContainSortObject() {
        // GIVEN
        final var sortDocument = new Document();
        final var sortObject   = Sort.by(Sort.Direction.ASC, "firstname");
        final var objects      = new Object[] { "Juni", sortObject };

        // WHEN
        when(queryMock.getSortObject()).thenReturn(sortDocument);
        when(methodInvocationMock.getArguments()).thenReturn(objects);

        final var result = ConvertToMQL.createPageable(methodInvocationMock, queryMock);

        // THEN
        assertEquals(PageRequest.of(0, 2147483647, sortObject), result);
    }

    @Test
    void when_createPageable_then_argumentsNotContainSortObject() {
        // GIVEN
        final var sortDocument = new Document("age", 0);
        final var sortObject   = Sort.by(Sort.Direction.DESC, "age");
        final var objects      = new Object[] { "Juni" };

        // WHEN
        when(queryMock.getSortObject()).thenReturn(sortDocument);
        when(methodInvocationMock.getArguments()).thenReturn(objects);

        final var result = ConvertToMQL.createPageable(methodInvocationMock, queryMock);

        // THEN
        assertEquals(PageRequest.of(0, 2147483647, sortObject), result);
    }

}
