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

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.repository.query.parser.PartTree;
import io.sapl.springdatamongoreactive.sapl.database.MethodInvocationForTesting;
import io.sapl.springdatamongoreactive.sapl.database.TestUser;

class ReflectedMongoQueryCreatorMethodsTest {

    @Test
    void when_instanceIsMongoQueryCreator_then_initializeMethods() {
        // GIVEN
        var mongoMethodInvocationTest = new MethodInvocationForTesting("findAllByFirstnameAndAgeBefore",
                new ArrayList<>(List.of(String.class, int.class)), new ArrayList<>(List.of("Aaron", 22)), null);
        var partTree                  = new PartTree("findAllByFirstnameAndAgeBefore", TestUser.class);
        var part                      = partTree.getParts().toList().get(0);
        var args                      = mongoMethodInvocationTest.getArguments();
        var argsAsIterator            = Arrays.stream(args).iterator();
        var criteria1                 = Criteria.where("age").lt(40);
        var criteria1and2             = criteria1.and("id").is(10);

        var mongoQueryCreatorFactoryHelperSpy = spy(MongoQueryCreatorFactoryHelper.class);
        var reflectedMongoQueryCreatorMethods = new ReflectedMongoQueryCreatorMethods();

        // WHEN
        reflectedMongoQueryCreatorMethods.initializeMethods(mongoQueryCreatorFactoryHelperSpy);
        reflectedMongoQueryCreatorMethods.create(part, argsAsIterator);
        reflectedMongoQueryCreatorMethods.and(part, criteria1, argsAsIterator);
        reflectedMongoQueryCreatorMethods.or(criteria1, criteria1and2);

        // THEN
        verify(mongoQueryCreatorFactoryHelperSpy, times(1)).create(part, argsAsIterator);
        verify(mongoQueryCreatorFactoryHelperSpy, times(1)).and(part, criteria1, argsAsIterator);
        verify(mongoQueryCreatorFactoryHelperSpy, times(1)).or(criteria1, criteria1and2);
    }

    @Test
    void when_instanceIsNotMongoQueryCreator_then_throwMethodNotFoundException() {
        // GIVEN
        var testUser                          = new TestUser();
        var reflectedMongoQueryCreatorMethods = new ReflectedMongoQueryCreatorMethods();

        // WHEN

        // THEN
        assertThrows(NoSuchMethodException.class, () -> reflectedMongoQueryCreatorMethods.initializeMethods(testUser));
    }

}
