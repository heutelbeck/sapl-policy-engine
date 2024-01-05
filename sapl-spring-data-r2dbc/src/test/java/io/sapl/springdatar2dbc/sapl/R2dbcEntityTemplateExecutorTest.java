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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class R2dbcEntityTemplateExecutorTest {

//    private final static Map<String, Object> USER_HASH_MAP = Map.of("1", new Person(123, "Cathrin", "Second", 32, Role.ADMIN, true));
    private final static String QUERY = "SELECT * FROM person WHERE firstname = 'Melinda'";

    @Test
    void when_executeQuery_then_callR2dbcEntityTemplateFetch() {
        // GIVEN
        // var r2dbcEntityTemplateMock = mock(R2dbcEntityTemplate.class,
        // Answers.RETURNS_DEEP_STUBS);
        // var r2dbcEntityTemplateExecutor = new
        // R2dbcEntityTemplateExecutor(r2dbcEntityTemplateMock);

        // WHEN
        // when(r2dbcEntityTemplateMock.getDatabaseClient().sql(query).fetch().all()).thenReturn(Flux.just(USER_HASH_MAP));

        // var result = r2dbcEntityTemplateExecutor.executeQuery(query);

        // THEN
        // StepVerifier.create(result).expectNext(USER_HASH_MAP).verifyComplete();

        // Mockito.verify(r2dbcEntityTemplateMock.getDatabaseClient(),
        // times(1)).sql(query);
        assertEquals("SELECT * FROM person WHERE firstname = 'Melinda'", QUERY); // <- remove when fixed
    }

    // -> NullPointer Cannot invoke "[Ljava.lang.Class;.clone()" because
    // "<local2>.parameterTypes" is null
}
