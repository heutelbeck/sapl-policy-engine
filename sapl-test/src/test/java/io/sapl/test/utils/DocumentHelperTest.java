package io.sapl.test.utils;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.SAPLInterpreter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.mockito.MockedStatic;

class DocumentHelperTest {

    private MockedStatic<ClasspathHelper> classpathHelperMockedStatic;
    private MockedStatic<Files> filesMockedStatic;

    private SAPLInterpreter saplInterpreterMock;

    @BeforeEach
    void setUp() {
        classpathHelperMockedStatic = mockStatic(ClasspathHelper.class);
        filesMockedStatic = mockStatic(Files.class, Answers.CALLS_REAL_METHODS);
        saplInterpreterMock = mock(SAPLInterpreter.class);
    }

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
        classpathHelperMockedStatic.when(() -> ClasspathHelper.findPathOnClasspath(any(), eq("file.sapl"))).thenReturn(pathMock);
        filesMockedStatic.when(() ->Files.readString(pathMock)).thenReturn("fancySaplString");

        final var saplMock = mock(SAPL.class);
        when(saplInterpreterMock.parse("fancySaplString")).thenReturn(saplMock);

        final var result = DocumentHelper.readSaplDocument("file", saplInterpreterMock);

        assertEquals(saplMock, result);
    }

    @Test
    void readSaplDocument_doesNotAppendSaplFileExtensionTwice() {
        final var pathMock = mock(Path.class);
        classpathHelperMockedStatic.when(() -> ClasspathHelper.findPathOnClasspath(any(), eq("file.sapl"))).thenReturn(pathMock);
        filesMockedStatic.when(() ->Files.readString(pathMock)).thenReturn("fancySaplString");

        final var saplMock = mock(SAPL.class);
        when(saplInterpreterMock.parse("fancySaplString")).thenReturn(saplMock);

        final var result = DocumentHelper.readSaplDocument("file.sapl", saplInterpreterMock);

        assertEquals(saplMock, result);
    }

    @Test
    void readSaplDocument_propagatesIOExceptionWhenFileCanNotBeRead() {
        final var pathMock = mock(Path.class);
        classpathHelperMockedStatic.when(() -> ClasspathHelper.findPathOnClasspath(any(), eq("file.sapl"))).thenReturn(pathMock);
        filesMockedStatic.when(() -> Files.readString(pathMock)).thenThrow(new IOException("where is my filesystem?"));

        final var saplMock = mock(SAPL.class);
        when(saplInterpreterMock.parse("fancySaplString")).thenReturn(saplMock);

        final var exception = assertThrows(RuntimeException.class, () -> DocumentHelper.readSaplDocument("file.sapl", saplInterpreterMock));

        final var cause = exception.getCause();

        assertInstanceOf(IOException.class, cause);
        assertEquals("where is my filesystem?", cause.getMessage());
    }

    @Test
    void readSaplDocument_throwsExceptionWhenFileCanNotBeRead() {
        final var pathMock = mock(Path.class);
        classpathHelperMockedStatic.when(() -> ClasspathHelper.findPathOnClasspath(any(), eq("file.sapl"))).thenReturn(pathMock);
        filesMockedStatic.when(() -> Files.readString(pathMock)).thenThrow(new SecurityException("security breach"));

        final var saplMock = mock(SAPL.class);
        when(saplInterpreterMock.parse("fancySaplString")).thenReturn(saplMock);

        final var exception = assertThrows(SecurityException.class, () -> DocumentHelper.readSaplDocument("file.sapl", saplInterpreterMock));

        assertEquals("security breach", exception.getMessage());
    }
}