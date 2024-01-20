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
package io.sapl.prp.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.PrpUpdateEvent.Type;

class ResourcesPrpUpdateEventSourceTests {

    static final DefaultSAPLInterpreter DEFAULT_SAPL_INTERPRETER = new DefaultSAPLInterpreter();

    @Test
    void test_guard_clauses() {
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null));
        assertThrows(NullPointerException.class,
                () -> new ResourcesPrpUpdateEventSource(null, DEFAULT_SAPL_INTERPRETER));
        assertThrows(NullPointerException.class,
                () -> new ResourcesPrpUpdateEventSource(null, mock(SAPLInterpreter.class)));

        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource("", null));
        assertThrows(NullPointerException.class,
                () -> new ResourcesPrpUpdateEventSource(null, mock(SAPLInterpreter.class)));
    }

    @Test
    void readPoliciesFromDirectory() throws InitializationException {
        var source = new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER);
        var update = source.getUpdates().blockFirst();
        assertThat(update, notNullValue());
        assertThat(update.getUpdates().length, is(3));

        source = new ResourcesPrpUpdateEventSource("/NON-EXISTING-PATH", DEFAULT_SAPL_INTERPRETER);
        update = source.getUpdates().blockFirst();
        assertThat(update, notNullValue());
        assertThat(update.getUpdates().length, is(0));

        source = new ResourcesPrpUpdateEventSource("/it/invalid", DEFAULT_SAPL_INTERPRETER);
        update = source.getUpdates().blockFirst();
        assertThat(update, notNullValue());
        assertThat(update.getUpdates().length, is(1));
        assertThat(update.getUpdates()[0].getType(), is(Type.INCONSISTENT));
    }

}
