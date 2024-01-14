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
package io.sapl.springdatar2dbc.sapl;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.data.r2dbc.core.R2dbcEntityTemplate;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.r2dbc.core.DatabaseClient.GenericExecuteSpec;
import org.springframework.r2dbc.core.FetchSpec;

public class R2dbcEntityTemplateExecutorTest {

    private final static String QUERY = "SELECT * FROM person WHERE firstname = 'Melinda'";

    @Test
    @SuppressWarnings("unchecked") // mocking of generic type
    void when_executeQuery_then_callR2dbcEntityTemplateFetch() {
        // GIVEN
        var dbClientMock            = mock(DatabaseClient.class);
        var fetchSpecMock           = mock(FetchSpec.class);
        var genericExecuteSpecMock  = mock(GenericExecuteSpec.class);
        var r2dbcEntityTemplateMock = mock(R2dbcEntityTemplate.class);

        when(r2dbcEntityTemplateMock.getDatabaseClient()).thenReturn(dbClientMock);
        when(dbClientMock.sql(anyString())).thenReturn(genericExecuteSpecMock);
        when(genericExecuteSpecMock.fetch()).thenReturn(fetchSpecMock);

        var r2dbcEntityTemplateExecutor = new R2dbcEntityTemplateExecutor(r2dbcEntityTemplateMock);

        // WHEN
        r2dbcEntityTemplateExecutor.executeQuery(QUERY);

        // THEN
        verify(r2dbcEntityTemplateMock, times(1)).getDatabaseClient();
        verify(dbClientMock, times(1)).sql(QUERY);
        verify(genericExecuteSpecMock, times(1)).fetch();
        verify(fetchSpecMock, times(1)).all();
    }
}
