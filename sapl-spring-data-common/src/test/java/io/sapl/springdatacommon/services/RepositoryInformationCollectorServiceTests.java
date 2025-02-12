/*
 * Copyright (C) 2017-2025 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.springdatacommon.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.springframework.core.ResolvableType;
import org.springframework.data.repository.core.CrudMethods;
import org.springframework.data.repository.core.RepositoryInformation;
import org.springframework.data.repository.core.support.RepositoryFragment;
import org.springframework.data.util.Streamable;
import org.springframework.data.util.TypeInformation;

import io.sapl.springdatacommon.database.Person;

class RepositoryInformationCollectorServiceTests {

    @Mock
    private static TypeInformation<?> typeInfo;

    @Test
    void when_add_then_addNewRepositoryInformation() {
        // GIVEN
        final var repositoryInformationMock = mock(RepositoryInformation.class);
        final var service                   = new RepositoryInformationCollectorService();

        // WHEN
        service.add(repositoryInformationMock);
        final var repositories = service.getRepositories();

        // THEN
        assertEquals(1, repositories.size());
        assertEquals(repositoryInformationMock, repositories.iterator().next());
    }

    @Test
    void when_getRepositoryByName_then_getRepositoryByName() {
        final var person  = new RepositoryInformationImpl();
        final var service = new RepositoryInformationCollectorService();

        service.add(person);

        assertEquals(person, service.getRepositoryByName("io.sapl.springdatacommon.database.Person"));
    }

    private static class RepositoryInformationImpl implements RepositoryInformation {

        @Override
        public TypeInformation<?> getIdTypeInformation() {
            return typeInfo;
        }

        @Override
        public TypeInformation<?> getDomainTypeInformation() {
            return typeInfo;
        }

        @Override
        public Class<?> getRepositoryInterface() {
            return Person.class;
        }

        @Override
        public TypeInformation<?> getReturnType(Method method) {
            return typeInfo;
        }

        @Override
        public Class<?> getReturnedDomainClass(Method method) {
            return ResolvableType.class;
        }

        @Override
        public CrudMethods getCrudMethods() {
            return null;
        }

        @Override
        public boolean isPagingRepository() {
            return false;
        }

        @Override
        public Set<Class<?>> getAlternativeDomainTypes() {
            return Set.of();
        }

        @Override
        public boolean isReactiveRepository() {
            return false;
        }

        @Override
        public Set<RepositoryFragment<?>> getFragments() {
            return Set.of();
        }

        @Override
        public boolean isBaseClassMethod(Method method) {
            return false;
        }

        @Override
        public boolean isCustomMethod(Method method) {
            return false;
        }

        @Override
        public boolean isQueryMethod(Method method) {
            return false;
        }

        @Override
        public Streamable<Method> getQueryMethods() {
            return Streamable.of();
        }

        @Override
        public Class<?> getRepositoryBaseClass() {
            return ResolvableType.class;
        }

        @Override
        public Method getTargetClassMethod(Method method) {
            return method;
        }

    }

}
