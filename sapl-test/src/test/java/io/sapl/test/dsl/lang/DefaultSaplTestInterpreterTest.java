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

package io.sapl.test.dsl.lang;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.inject.Injector;
import io.sapl.test.SaplTestException;
import io.sapl.test.TestHelper;
import io.sapl.test.grammar.SAPLTestStandaloneSetup;
import io.sapl.test.grammar.sAPLTest.SAPLTest;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.apache.commons.io.IOUtils;
import org.eclipse.emf.common.util.URI;
import org.eclipse.emf.common.util.WrappedException;
import org.eclipse.emf.ecore.resource.Resource;
import org.eclipse.xtext.resource.XtextResourceSet;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultSaplTestInterpreterTest {

    @Mock
    private Injector injectorMock;

    private DefaultSaplTestInterpreter defaultSaplTestInterpreter;

    private final MockedStatic<SAPLTestStandaloneSetup> saplTestStandaloneSetupMockedStatic = mockStatic(
            SAPLTestStandaloneSetup.class);
    private final MockedStatic<URI>                     uriMockedStatic                     = mockStatic(URI.class);
    private final MockedStatic<IOUtils>                 ioUtilsMockedStatic                 = mockStatic(IOUtils.class);

    @BeforeEach
    void setUp() {
        defaultSaplTestInterpreter = new DefaultSaplTestInterpreter();
        saplTestStandaloneSetupMockedStatic.when(SAPLTestStandaloneSetup::doSetupAndGetInjector)
                .thenReturn(injectorMock);
    }

    @AfterEach
    void tearDown() {
        saplTestStandaloneSetupMockedStatic.close();
        uriMockedStatic.close();
        ioUtilsMockedStatic.close();
    }

    private Resource mockResourceCreation(Map<Object, Object> loadOptions) {
        final var xtextResourceSetMock = mock(XtextResourceSet.class);

        when(injectorMock.getInstance(XtextResourceSet.class)).thenReturn(xtextResourceSetMock);

        final var uriMock = mock(URI.class);
        uriMockedStatic.when(() -> URI.createFileURI("test:/test1.sapltest")).thenReturn(uriMock);

        final var resourceMock = mock(Resource.class);

        when(xtextResourceSetMock.createResource(uriMock)).thenReturn(resourceMock);
        when(xtextResourceSetMock.getLoadOptions()).thenReturn(loadOptions);

        return resourceMock;
    }

    @Test
    void loadAsResource_loadingThrowsIOException_throwsSaplTestException() throws IOException {
        final var loadOptions  = Collections.emptyMap();
        final var resourceMock = mockResourceCreation(loadOptions);

        final var inputStreamMock = mock(InputStream.class);

        doThrow(new IOException("loading failed")).when(resourceMock).load(inputStreamMock, loadOptions);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultSaplTestInterpreter.loadAsResource(inputStreamMock));

        assertEquals("loading failed", exception.getCause().getMessage());
    }

    @Test
    void loadAsResource_loadingThrowsWrappedException_throwsSaplTestException() throws IOException {
        final var resourceMock = mockResourceCreation(Collections.emptyMap());

        final var inputStreamMock = mock(InputStream.class);
        final var loadOptions     = Collections.emptyMap();

        doThrow(new WrappedException(new Exception("loading failed"))).when(resourceMock).load(inputStreamMock,
                loadOptions);

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultSaplTestInterpreter.loadAsResource(inputStreamMock));

        assertEquals("loading failed", exception.getCause().getCause().getMessage());
    }

    @Test
    void loadAsResource_withResourceWithErrors_throwsSaplTestException() {
        final var resourceMock = mockResourceCreation(Collections.emptyMap());

        final var inputStreamMock = mock(InputStream.class);

        final var resourceErrorMock = mock(Resource.Diagnostic.class);

        TestHelper.mockEListResult(resourceMock::getErrors, List.of(resourceErrorMock));

        final var exception = assertThrows(SaplTestException.class,
                () -> defaultSaplTestInterpreter.loadAsResource(inputStreamMock));

        assertEquals("Input is not a valid test definition", exception.getMessage());
    }

    @Test
    void loadAsResource_withValidResource_returnsSAPLTest() {
        final var resourceMock = mockResourceCreation(Collections.emptyMap());

        final var inputStreamMock = mock(InputStream.class);

        TestHelper.mockEListResult(resourceMock::getErrors, Collections.emptyList());

        final var saplTestMock = mock(SAPLTest.class);
        TestHelper.mockEListResult(resourceMock::getContents, List.of(saplTestMock));

        final var result = defaultSaplTestInterpreter.loadAsResource(inputStreamMock);

        assertEquals(saplTestMock, result);
    }

    @Test
    void loadAsResource_withStringInputAndValidResource_returnsSAPLTest() throws IOException {
        final var loadOptions  = Collections.emptyMap();
        final var resourceMock = mockResourceCreation(loadOptions);

        TestHelper.mockEListResult(resourceMock::getErrors, Collections.emptyList());

        final var saplTestMock = mock(SAPLTest.class);
        TestHelper.mockEListResult(resourceMock::getContents, List.of(saplTestMock));

        final var inputStreamMock = mock(InputStream.class);
        ioUtilsMockedStatic.when(() -> IOUtils.toInputStream("foo", StandardCharsets.UTF_8))
                .thenReturn(inputStreamMock);

        final var result = defaultSaplTestInterpreter.loadAsResource("foo");

        assertEquals(saplTestMock, result);
        verify(resourceMock, times(1)).load(inputStreamMock, loadOptions);
    }
}
