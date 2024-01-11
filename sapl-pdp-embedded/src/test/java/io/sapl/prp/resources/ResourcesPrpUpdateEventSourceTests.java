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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.CleanupMode;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.util.JarCreator;
import io.sapl.util.JarUtil;

class ResourcesPrpUpdateEventSourceTests {

    static final DefaultSAPLInterpreter DEFAULT_SAPL_INTERPRETER = new DefaultSAPLInterpreter();

    @Test
    void test_guard_clauses() {
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource("", null));
        assertThrows(NullPointerException.class,
                () -> new ResourcesPrpUpdateEventSource(null, DEFAULT_SAPL_INTERPRETER));
        assertThrows(NullPointerException.class,
                () -> new ResourcesPrpUpdateEventSource(null, mock(SAPLInterpreter.class)));

        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, "", null));
        assertThrows(NullPointerException.class,
                () -> new ResourcesPrpUpdateEventSource(null, null, mock(SAPLInterpreter.class)));
        var thisClass = this.getClass();
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(thisClass, null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(thisClass, "", null));
    }

    @Test
    void readPoliciesFromDirectory() throws InitializationException {
        var source = new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER);
        var update = source.getUpdates().blockFirst();
        assertThat(update, notNullValue());
        assertThat(update.getUpdates().length, is(3));
        assertThrows(RuntimeException.class,
                () -> new ResourcesPrpUpdateEventSource("/NON-EXISTING-PATH", DEFAULT_SAPL_INTERPRETER));
        assertThrows(PolicyEvaluationException.class,
                () -> new ResourcesPrpUpdateEventSource("/it/invalid", DEFAULT_SAPL_INTERPRETER));
        source.dispose();
    }

    @Test
    void ifFileIOError_exceptionPropagates() throws IOException {
        var failingInputStream = mock(InputStream.class);
        when(failingInputStream.read(any(), anyInt(), anyInt())).thenThrow(new IOException("to be expected"));

        try (MockedStatic<Channels> mock = mockStatic(Channels.class, CALLS_REAL_METHODS)) {
            mock.when(() -> Channels.newInputStream(any(ReadableByteChannel.class))).thenReturn(failingInputStream);
            assertThrows(InitializationException.class,
                    () -> new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER));
        }
    }

    @Test
    void ifExecutedInJar_thenLoadDocumentsFromJar(@TempDir(cleanup = CleanupMode.ALWAYS) Path tempDir)
            throws InitializationException, IOException {
        var url = JarCreator.createPoliciesInJar("!/policies", tempDir);
        try (MockedStatic<JarUtil> mock = mockStatic(JarUtil.class, CALLS_REAL_METHODS)) {
            mock.when(() -> JarUtil.inferUrlOfResourcesPath(any(), any())).thenReturn(url);

            var source = new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER);
            var update = source.getUpdates().blockFirst();

            assertThat(update, notNullValue());
            assertThat(update.getUpdates().length, is(2));
        }
    }

}
