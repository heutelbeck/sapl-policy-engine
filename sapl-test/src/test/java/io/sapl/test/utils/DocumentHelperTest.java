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
package io.sapl.test.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.prp.Document;

@ExtendWith(MockitoExtension.class)
class DocumentHelperTests {
    private final MockedStatic<ClasspathHelper> classpathHelperMockedStatic = mockStatic(ClasspathHelper.class);
    private final MockedStatic<Files>           filesMockedStatic           = mockStatic(Files.class,
            Answers.CALLS_REAL_METHODS);
    @Mock
    private SAPLInterpreter                     saplInterpreterMock;

    @AfterEach
    void tearDown() {
        classpathHelperMockedStatic.close();
        filesMockedStatic.close();
    }

    @Test
    void readSaplDocument_returnsNullForNullDocumentName() {
        final var result = DocumentHelper.readSaplDocument(null, saplInterpreterMock);

        assertNull(result);
    }

    @Test
    void readSaplDocument_returnsNullForEmptyDocumentName() {
        final var result = DocumentHelper.readSaplDocument("", saplInterpreterMock);

        assertNull(result);
    }

    @Test
    void readSaplDocument_appendsSaplFileExtensionWhenMissing() {
        final var pathMock = mock(Path.class);
        classpathHelperMockedStatic.when(() -> ClasspathHelper.findPathOnClasspath(any(), eq("file.sapl")))
                .thenReturn(pathMock);
        filesMockedStatic.when(() -> Files.readString(pathMock)).thenReturn("fancySaplString");

        final var saplMock = mock(Document.class);
        when(saplInterpreterMock.parseDocument("fancySaplString")).thenReturn(saplMock);

        final var result = DocumentHelper.readSaplDocument("file", saplInterpreterMock);

        assertEquals(saplMock, result);
    }

    @Test
    void readSaplDocument_doesNotAppendSaplFileExtensionTwice() {
        final var pathMock = mock(Path.class);
        classpathHelperMockedStatic.when(() -> ClasspathHelper.findPathOnClasspath(any(), eq("file.sapl")))
                .thenReturn(pathMock);
        filesMockedStatic.when(() -> Files.readString(pathMock)).thenReturn("fancySaplString");

        final var saplMock = mock(Document.class);
        when(saplInterpreterMock.parseDocument("fancySaplString")).thenReturn(saplMock);

        final var result = DocumentHelper.readSaplDocument("file.sapl", saplInterpreterMock);

        assertEquals(saplMock, result);
    }

    @Test
    void readSaplDocument_propagatesIOExceptionWhenFileCanNotBeRead() {
        final var pathMock = mock(Path.class);
        classpathHelperMockedStatic.when(() -> ClasspathHelper.findPathOnClasspath(any(), eq("file.sapl")))
                .thenReturn(pathMock);
        filesMockedStatic.when(() -> Files.readString(pathMock)).thenThrow(new IOException("where is my filesystem?"));

        final var exception = assertThrows(IOException.class,
                () -> DocumentHelper.readSaplDocument("file.sapl", saplInterpreterMock));

        assertInstanceOf(IOException.class, exception);
        assertEquals("where is my filesystem?", exception.getMessage());
    }

    @Test
    void readSaplDocument_throwsExceptionWhenFileCanNotBeRead() {
        final var pathMock = mock(Path.class);
        classpathHelperMockedStatic.when(() -> ClasspathHelper.findPathOnClasspath(any(), eq("file.sapl")))
                .thenReturn(pathMock);
        filesMockedStatic.when(() -> Files.readString(pathMock)).thenThrow(new SecurityException("security breach"));

        final var exception = assertThrows(SecurityException.class,
                () -> DocumentHelper.readSaplDocument("file.sapl", saplInterpreterMock));

        assertEquals("security breach", exception.getMessage());
    }

    @Test
    void readSaplDocumentFromInputString_handlesNullInput_returnsNull() {
        final var result = DocumentHelper.readSaplDocumentFromInputString(null, saplInterpreterMock);

        assertNull(result);
    }

    @Test
    void readSaplDocumentFromInputString_handlesEmptyInput_returnsNull() {
        final var result = DocumentHelper.readSaplDocumentFromInputString("", saplInterpreterMock);

        assertNull(result);
    }

    @Test
    void readSaplDocumentFromInputString_parsesValidInput_returnsSAPL() {
        final var saplMock = mock(Document.class);
        when(saplInterpreterMock.parseDocument("foo")).thenReturn(saplMock);

        final var result = DocumentHelper.readSaplDocumentFromInputString("foo", saplInterpreterMock);

        assertEquals(saplMock, result);
    }
}
