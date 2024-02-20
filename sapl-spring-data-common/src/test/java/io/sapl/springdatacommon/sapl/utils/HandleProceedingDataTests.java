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
package io.sapl.springdatacommon.sapl.utils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.util.ReflectionUtils;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.springdatacommon.database.Person;
import io.sapl.springdatacommon.database.R2dbcMethodInvocation;
import io.sapl.springdatacommon.database.Role;
import io.sapl.springdatacommon.sapl.QueryManipulationEnforcementData;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class HandleProceedingDataTests {

    @Test
    void when_proceedingDataIsTypeOfFlux_then_justProceed() {
        // GIVEN
        final Person malinda = new Person("1", "Malinda", "Perrot", 53, Role.ADMIN, true);
        final Person emerson = new Person("2", "Emerson", "Rowat", 82, Role.USER, false);
        final Person yul     = new Person("3", "Yul", "Barukh", 79, Role.USER, true);

        final Flux<Person> data = Flux.just(malinda, emerson, yul);

        var domainType                = Person.class;
        var beanFactoryMock           = mock(BeanFactory.class);
        var policyDecisionPointMock   = mock(PolicyDecisionPoint.class);
        var authSub                   = AuthorizationSubscription.of("", "permitTest", "");
        var r2dbcMethodInvocationTest = new R2dbcMethodInvocation("findAllByFirstname",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), data);

        var queryManipulationEnforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                beanFactoryMock, domainType, policyDecisionPointMock, authSub);

        // WHEN
        Flux<Person> result = HandleProceedingData.proceed(queryManipulationEnforcementData);

        // THEN
        StepVerifier.create(result).expectNext(malinda).expectNext(emerson).expectNext(yul).verifyComplete();
    }

    @Test
    void when_proceedingDataIsTypeOfMono_then_convertToFlux() {
        // GIVEN
        final Person       malinda    = new Person("1", "Malinda", "Perrot", 53, Role.ADMIN, true);
        final Mono<Person> dataAsMono = Mono.just(malinda);

        var domainType                = Person.class;
        var beanFactoryMock           = mock(BeanFactory.class);
        var policyDecisionPointMock   = mock(PolicyDecisionPoint.class);
        var authSub                   = AuthorizationSubscription.of("", "permitTest", "");
        var r2dbcMethodInvocationTest = new R2dbcMethodInvocation("findUserTest",
                new ArrayList<>(List.of(String.class)), new ArrayList<>(List.of("Cathrin")), dataAsMono);

        var queryManipulationEnforcementData = new QueryManipulationEnforcementData<>(r2dbcMethodInvocationTest,
                beanFactoryMock, domainType, policyDecisionPointMock, authSub);

        // WHEN
        Flux<Person> result = HandleProceedingData.proceed(queryManipulationEnforcementData);

        // THEN
        StepVerifier.create(result).expectNext(malinda).verifyComplete();
    }

    @Test
    void when_classIsStaticUtilityClass_then_instantiateThisTestForCoverageReasonsOfConstructor() {
        assertThrows(InvocationTargetException.class, () -> {
            var constructor = HandleProceedingData.class.getDeclaredConstructor();
            assertTrue(Modifier.isPrivate(constructor.getModifiers()));
            ReflectionUtils.makeAccessible(constructor);
            constructor.newInstance();
        });
    }

}
