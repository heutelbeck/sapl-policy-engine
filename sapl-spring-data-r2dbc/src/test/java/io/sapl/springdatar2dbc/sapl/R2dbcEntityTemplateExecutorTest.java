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

/*import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.Mockito;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;

import io.sapl.springdatar2dbc.database.Person;
import io.sapl.springdatar2dbc.database.Role;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;*/

public class R2dbcEntityTemplateExecutorTest {
    /*
     * Map<String, Object> userHashMap = Map.of("1", new Person(123, "Cathrin",
     * "Second", 32, Role.ADMIN, Boolean.TRUE)); String query =
     * "SELECT * FROM person WHERE firstname = 'Melinda'";
     *
     * @Test void when_executeQuery_then_callR2dbcEntityTemplateFetch() {
     *
     * // GIVEN var r2dbcEntityTemplateMock = mock(R2dbcEntityTemplate.class,
     * Answers.RETURNS_DEEP_STUBS); var r2dbcEntityTemplateExecutor = new
     * R2dbcEntityTemplateExecutor(r2dbcEntityTemplateMock);
     *
     * // WHEN
     * when(r2dbcEntityTemplateMock.getDatabaseClient().sql(query).fetch().all()).
     * thenReturn(Flux.just(userHashMap));
     *
     * var result = r2dbcEntityTemplateExecutor.executeQuery(query);
     *
     * // THEN StepVerifier.create(result).expectNext(userHashMap).verifyComplete();
     *
     * Mockito.verify(r2dbcEntityTemplateMock.getDatabaseClient(),
     * times(1)).sql(query);
     *
     * }
     */
    // -> NullPointer Cannot invoke "[Ljava.lang.Class;.clone()" because
    // "<local2>.parameterTypes" is null
}
