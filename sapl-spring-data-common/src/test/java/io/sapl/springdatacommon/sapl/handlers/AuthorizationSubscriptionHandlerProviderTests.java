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
package io.sapl.springdatacommon.sapl.handlers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;

import io.sapl.springdatacommon.sapl.Enforce;
import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.boot.test.context.SpringBootTest;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.springdatacommon.database.R2dbcMethodInvocation;
import io.sapl.springdatacommon.database.R2dbcPersonRepository;
import io.sapl.springdatacommon.database.TestClass;
import io.sapl.springdatacommon.handlers.AuthorizationSubscriptionHandlerProvider;
import io.sapl.springdatacommon.handlers.EnforceAnnotationHandler;

@SpringBootTest(classes = AuthorizationSubscriptionHandlerProvider.class)
class AuthorizationSubscriptionHandlerProviderTests {

    private final AuthorizationSubscription generalProtectionR2dbcPersonRepository   = AuthorizationSubscription
            .of("general", "general_protection_reactive_r2dbc_repository", "resource", "test");
    private final AuthorizationSubscription subjectAndEnvAuthSub                     = AuthorizationSubscription
            .of("method", "", "", "test");
    private final AuthorizationSubscription actionAndResourceAuthSub                 = AuthorizationSubscription.of("",
            "find_all_by_firstname_reactive_r2dbc_repository", "resource", "");
    private final MethodInvocation          methodInvocation                         = new R2dbcMethodInvocation(
            "findAllByFirstname", new ArrayList<>(List.of(String.class)), null, null);
    private final MethodInvocation          methodInvocationWithoutEnforceAnnotation = new R2dbcMethodInvocation(
            "findAllByAge", new ArrayList<>(List.of(int.class)), null, null);

    @Mock
    BeanFactory beanFactoryMock;

    @Mock
    Enforce enforceAnnotationMock;

    @Mock
    EnforceAnnotationHandler enforceAnnotationHandlerMock;

    @Test
    void when_classIsNoRepository_then_throwException() {
        // GIVEN
        var authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(beanFactoryMock);

        // WHEN
        assertThrows(IllegalArgumentException.class, () -> authorizationSubscriptionHandlerProvider
                .getAuthSub(TestClass.class, methodInvocation, enforceAnnotationMock));

        // THEN
        verify(enforceAnnotationHandlerMock, times(0)).enforceAnnotation(any(MethodInvocation.class),
                any(Enforce.class));
        verify(beanFactoryMock, times(0)).getBean(anyString());
    }

    @Test
    void when_annotationIsAvailableButNotCompleteAndBeanIsAvailable1_then_getAuthSub() {
        // GIVEN
        var methodInvocationForTesting = new R2dbcMethodInvocation("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);
        var correctAuthSub             = AuthorizationSubscription.of("method",
                "find_all_by_firstname_reactive_r2dbc_repository", "resource", "test");
        var annotationAuthSub          = AuthorizationSubscription.of("",
                "find_all_by_firstname_reactive_r2dbc_repository", "resource", "");

        // WHEN
        try (MockedConstruction<EnforceAnnotationHandler> enforceAnnotationHandlerMockConstruction = mockConstruction(
                EnforceAnnotationHandler.class)) {
            var authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(
                    beanFactoryMock);
            var enforceAnnotationHandlerMock             = enforceAnnotationHandlerMockConstruction.constructed()
                    .get(0);

            when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class), any(Enforce.class)))
                    .thenReturn(annotationAuthSub);
            when(beanFactoryMock.getBean("findAllByFirstnameR2dbcPersonRepository")).thenReturn(subjectAndEnvAuthSub);

            var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(R2dbcPersonRepository.class,
                    methodInvocationForTesting, enforceAnnotationMock);

            // THEN
            compareTwoAuthSubs(correctAuthSub, resultAuthSub);

            verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(methodInvocationForTesting,
                    enforceAnnotationMock);
            verify(beanFactoryMock, times(1)).getBean("findAllByFirstnameR2dbcPersonRepository");
        }
    }

    @Test
    void when_annotationIsAvailableButNotCompleteAndBeanIsAvailable2_then_getAuthSub() {
        // GIVEN
        var methodInvocationForTesting = new R2dbcMethodInvocation("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), null, null);
        var correctAuthSub             = AuthorizationSubscription.of("",
                "find_all_by_firstname_reactive_r2dbc_repository", "resource", "environment");
        var annotationAuthSub          = AuthorizationSubscription.of("",
                "find_all_by_firstname_reactive_r2dbc_repository", "", "environment");

        // WHEN
        try (MockedConstruction<EnforceAnnotationHandler> enforceAnnotationHandlerMockConstruction = mockConstruction(
                EnforceAnnotationHandler.class)) {
            var authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(
                    beanFactoryMock);
            var enforceAnnotationHandlerMock             = enforceAnnotationHandlerMockConstruction.constructed()
                    .get(0);

            when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class), any(Enforce.class)))
                    .thenReturn(annotationAuthSub);
            when(beanFactoryMock.getBean("findAllByFirstnameR2dbcPersonRepository"))
                    .thenReturn(actionAndResourceAuthSub);

            var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(R2dbcPersonRepository.class,
                    methodInvocationForTesting, enforceAnnotationMock);

            // THEN
            compareTwoAuthSubs(correctAuthSub, resultAuthSub);

            verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(methodInvocationForTesting,
                    enforceAnnotationMock);
            verify(beanFactoryMock, times(1)).getBean("findAllByFirstnameR2dbcPersonRepository");
        }
    }

    @Test
    void when_annotationIsAvailableButNotCompleteAndBeanIsAvailable3_then_getAuthSub() {
        // GIVEN
        var correctAuthSub    = AuthorizationSubscription.of("method",
                "find_all_by_firstname_reactive_r2dbc_repository", "resource", "test");
        var annotationAuthSub = AuthorizationSubscription.of("method", "", "", "test");

        // WHEN
        try (MockedConstruction<EnforceAnnotationHandler> enforceAnnotationHandlerMockConstruction = mockConstruction(
                EnforceAnnotationHandler.class)) {
            var authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(
                    beanFactoryMock);
            var enforceAnnotationHandlerMock             = enforceAnnotationHandlerMockConstruction.constructed()
                    .get(0);

            when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class), any(Enforce.class)))
                    .thenReturn(annotationAuthSub);
            when(beanFactoryMock.getBean("findAllByFirstnameR2dbcPersonRepository"))
                    .thenReturn(actionAndResourceAuthSub);
            var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(R2dbcPersonRepository.class,
                    methodInvocation, enforceAnnotationMock);

            // THEN
            compareTwoAuthSubs(correctAuthSub, resultAuthSub);
            verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class),
                    any(Enforce.class));
            verify(beanFactoryMock, times(1)).getBean(anyString());
        }
    }

    @Test
    void when_annotationIsAvailableAndAuthSubIsComplete_then_getAuthSub() {
        // GIVEN
        var correctAuthSub = AuthorizationSubscription.of("method", "find_all_by_firstname_reactive_r2dbc_repository",
                "resource", "test");

        // WHEN
        try (MockedConstruction<EnforceAnnotationHandler> enforceAnnotationHandlerMockConstruction = mockConstruction(
                EnforceAnnotationHandler.class)) {
            var authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(
                    beanFactoryMock);
            var enforceAnnotationHandlerMock             = enforceAnnotationHandlerMockConstruction.constructed()
                    .get(0);

            when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class), any(Enforce.class)))
                    .thenReturn(correctAuthSub);
            var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(R2dbcPersonRepository.class,
                    methodInvocation, enforceAnnotationMock);

            // THEN
            compareTwoAuthSubs(correctAuthSub, resultAuthSub);
            verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class),
                    any(Enforce.class));
            verify(beanFactoryMock, times(0)).getBean(anyString());
        }
    }

    @Test
    void when_noAnnotationButSpecificBeanForRepositoryIsAvailable_then_getAuthSub() {
        // GIVEN
        var correctAuthSub = AuthorizationSubscription.of("method", "", "", "test");

        // WHEN
        try (MockedConstruction<EnforceAnnotationHandler> enforceAnnotationHandlerMockConstruction = mockConstruction(
                EnforceAnnotationHandler.class)) {
            var authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(
                    beanFactoryMock);
            var enforceAnnotationHandlerMock             = enforceAnnotationHandlerMockConstruction.constructed()
                    .get(0);

            when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class), any(Enforce.class)))
                    .thenReturn(null);
            when(beanFactoryMock.getBean("findAllByAgeR2dbcPersonRepository")).thenReturn(subjectAndEnvAuthSub);

            var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(R2dbcPersonRepository.class,
                    methodInvocationWithoutEnforceAnnotation, enforceAnnotationMock);

            // THEN
            compareTwoAuthSubs(correctAuthSub, resultAuthSub);
            verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class),
                    any(Enforce.class));
            verify(beanFactoryMock, times(1)).getBean(anyString());
        }
    }

    @Test
    void when_noAnnotationButBeanForMethodIsAvailable_then_getAuthSub() {
        // GIVEN
        var correctAuthSub = AuthorizationSubscription.of("general", "general_protection_reactive_r2dbc_repository",
                "resource", "test");

        // WHEN
        try (MockedConstruction<EnforceAnnotationHandler> enforceAnnotationHandlerMockConstruction = mockConstruction(
                EnforceAnnotationHandler.class)) {
            var authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(
                    beanFactoryMock);
            var enforceAnnotationHandlerMock             = enforceAnnotationHandlerMockConstruction.constructed()
                    .get(0);

            when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class), any(Enforce.class)))
                    .thenReturn(null);
            when(beanFactoryMock.getBean(anyString())).thenAnswer(ans -> {
                if (ans.getArgument(0).equals("findAllByFirstnameR2dbcPersonRepository")) {
                    throw new NoSuchBeanDefinitionException("No such bean.");
                }

                if (ans.getArgument(0).equals("generalProtectionR2dbcPersonRepository")) {
                    return generalProtectionR2dbcPersonRepository;
                }

                return null;
            });
            var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(R2dbcPersonRepository.class,
                    methodInvocation, enforceAnnotationMock);

            // THEN
            compareTwoAuthSubs(correctAuthSub, resultAuthSub);
            verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class),
                    any(Enforce.class));
            verify(beanFactoryMock, times(2)).getBean(anyString());
        }
    }

    @Test
    void when_noAnnotationAnNoBeanForMethodIsAvailable_then_returnNull() {
        // GIVEN

        // WHEN
        try (MockedConstruction<EnforceAnnotationHandler> enforceAnnotationHandlerMockConstruction = mockConstruction(
                EnforceAnnotationHandler.class)) {
            var authorizationSubscriptionHandlerProvider = new AuthorizationSubscriptionHandlerProvider(
                    beanFactoryMock);
            var enforceAnnotationHandlerMock             = enforceAnnotationHandlerMockConstruction.constructed()
                    .get(0);

            when(enforceAnnotationHandlerMock.enforceAnnotation(any(MethodInvocation.class), any(Enforce.class)))
                    .thenReturn(null);
            when(beanFactoryMock.getBean(anyString())).thenAnswer(ans -> {
                if (ans.getArgument(0).equals("findAllByFirstnameR2dbcPersonRepository")) {
                    throw new NoSuchBeanDefinitionException("No such bean.");
                }

                if (ans.getArgument(0).equals("generalProtectionR2dbcPersonRepository")) {
                    throw new NoSuchBeanDefinitionException("No such bean.");
                }

                return null;
            });
            var resultAuthSub = authorizationSubscriptionHandlerProvider.getAuthSub(R2dbcPersonRepository.class,
                    methodInvocation, enforceAnnotationMock);

            // THEN

            assertNull(resultAuthSub);
            verify(enforceAnnotationHandlerMock, times(1)).enforceAnnotation(any(MethodInvocation.class),
                    any(Enforce.class));
            verify(beanFactoryMock, times(2)).getBean(anyString());
        }
    }

    private void compareTwoAuthSubs(AuthorizationSubscription first, AuthorizationSubscription second) {
        assertEquals(first.getSubject(), second.getSubject());
        assertEquals(first.getAction(), second.getAction());
        assertEquals(first.getResource(), second.getResource());
        assertEquals(first.getEnvironment(), second.getEnvironment());
    }
}
