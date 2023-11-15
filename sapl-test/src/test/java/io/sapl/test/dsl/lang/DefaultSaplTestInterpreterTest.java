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
import io.sapl.test.Helper;
import io.sapl.test.SaplTestException;
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
import org.eclipse.emf.ecore.EObject;
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

    private final MockedStatic<SAPLTestStandaloneSetup> saplTestStandaloneSetupMockedStatic = mockStatic(SAPLTestStandaloneSetup.class);
    private final MockedStatic<URI> uriMockedStatic = mockStatic(URI.class);
    private final MockedStatic<IOUtils> ioUtilsMockedStatic = mockStatic(IOUtils.class);

    private DefaultSaplTestInterpreter defaultSaplTestInterpreter;

    @Mock
    private Injector injectorMock;

    @BeforeEach
    void setUp() {
        defaultSaplTestInterpreter = new DefaultSaplTestInterpreter();
        saplTestStandaloneSetupMockedStatic.when(SAPLTestStandaloneSetup::doSetupAndGetInjector).thenReturn(injectorMock);
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
        final var loadOptions = Collections.emptyMap();
        final var resourceMock = mockResourceCreation(loadOptions);

        final var inputStreamMock = mock(InputStream.class);

        doThrow(new IOException("loading failed")).when(resourceMock).load(inputStreamMock, loadOptions);

        final var result = assertThrows(SaplTestException.class, () -> defaultSaplTestInterpreter.loadAsResource(inputStreamMock));

        assertEquals("loading failed", result.getCause().getMessage());
    }

    @Test
    void loadAsResource_loadingThrowsWrappedException_throwsSaplTestException() throws IOException {
        final var resourceMock = mockResourceCreation(Collections.emptyMap());

        final var inputStreamMock = mock(InputStream.class);
        final var loadOptions = Collections.emptyMap();

        doThrow(new WrappedException(new Exception("loading failed"))).when(resourceMock).load(inputStreamMock, loadOptions);

        final var result = assertThrows(SaplTestException.class, () -> defaultSaplTestInterpreter.loadAsResource(inputStreamMock));

        assertEquals("loading failed", result.getCause().getCause().getMessage());
    }

    @Test
    void loadAsResource_withResourceWithErrors_throwsSaplTestException() {
        final var resourceMock = mockResourceCreation(Collections.emptyMap());

        final var inputStreamMock = mock(InputStream.class);

        final var resourceErrorMock = mock(Resource.Diagnostic.class);

        final var errors = Helper.mockEList(List.of(resourceErrorMock));

        when(resourceMock.getErrors()).thenReturn(errors);

        final var result = assertThrows(SaplTestException.class, () -> defaultSaplTestInterpreter.loadAsResource(inputStreamMock));

        assertEquals("Input is not a valid test definition", result.getMessage());
    }

    @Test
    void loadAsResource_withValidResource_returnsSAPLTest() {
        final var resourceMock = mockResourceCreation(Collections.emptyMap());

        final var inputStreamMock = mock(InputStream.class);

        final var errors = Helper.mockEList(List.<Resource.Diagnostic>of());

        when(resourceMock.getErrors()).thenReturn(errors);

        final var saplTestMock = mock(SAPLTest.class);
        final var contents = Helper.mockEList(List.<EObject>of(saplTestMock));
        when(resourceMock.getContents()).thenReturn(contents);

        final var result = defaultSaplTestInterpreter.loadAsResource(inputStreamMock);

        assertEquals(saplTestMock, result);
    }

    @Test
    void loadAsResource_withStringInputAndValidResource_returnsSAPLTest() throws IOException {
        final var loadOptions = Collections.emptyMap();
        final var resourceMock = mockResourceCreation(loadOptions);

        final var errors = Helper.mockEList(List.<Resource.Diagnostic>of());

        when(resourceMock.getErrors()).thenReturn(errors);

        final var saplTestMock = mock(SAPLTest.class);
        final var contents = Helper.mockEList(List.<EObject>of(saplTestMock));
        when(resourceMock.getContents()).thenReturn(contents);

        final var inputStreamMock = mock(InputStream.class);
        ioUtilsMockedStatic.when(() -> IOUtils.toInputStream("foo", StandardCharsets.UTF_8)).thenReturn(inputStreamMock);

        final var result = defaultSaplTestInterpreter.loadAsResource("foo");

        assertEquals(saplTestMock, result);
        verify(resourceMock, times(1)).load(inputStreamMock, loadOptions);
    }
}