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
package io.sapl.prp;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.core.Is.is;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent.Type;
import io.sapl.prp.PrpUpdateEvent.Update;

class PrpUpdateEventTests {
    private static final SAPLInterpreter INTERPRETER = new DefaultSAPLInterpreter();

    @Test
    void should_return_empty_event_when_initialized_with_null() {
        List<Update> updates = null;
        final var    event   = new PrpUpdateEvent(updates);
        assertThat(event, notNullValue());
    }

    @Test
    void toStringTest() {
        final var document = INTERPRETER.parseDocument("policy \"p\" permit");
        final var empty    = new PrpUpdateEvent.Update(null, null);
        final var valid    = new PrpUpdateEvent.Update(Type.PUBLISH, document);
        assertThat(empty.toString(), is("Update(type=null, documentName=NULL POLICY)"));
        assertThat(valid.toString(), is("Update(type=PUBLISH, documentName='p')"));
    }

}
