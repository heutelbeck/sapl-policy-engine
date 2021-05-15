package io.sapl.prp.resources;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.util.JarPathUtil;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class ResourcesPrpUpdateEventSourceTest {

    static final DefaultSAPLInterpreter DEFAULT_SAPL_INTERPRETER = new DefaultSAPLInterpreter();

    @Test
    void do_stuff() {
        assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null));

        var source = new ResourcesPrpUpdateEventSource("", DEFAULT_SAPL_INTERPRETER);
        assertThat(source, notNullValue());

        source = new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER);
        assertThat(source, notNullValue());

        source.dispose();
    }

    @Test
    void readPoliciesFromDirectory() {
        var source = new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER);
        var update = source.getUpdates().blockFirst();

        assertThat(update, notNullValue());
        assertThat(update.getUpdates().length, is(2));

        assertThrows(RuntimeException.class, () -> new ResourcesPrpUpdateEventSource("/NON-EXISTING-PATH", DEFAULT_SAPL_INTERPRETER));

        assertThrows(PolicyEvaluationException.class, () -> new ResourcesPrpUpdateEventSource("/it/invalid", DEFAULT_SAPL_INTERPRETER));


    }

    @Test
    void test_read_config_from_jar() throws Exception {
        var url = Paths.get("src/test/resources/policies_in_jar.jar!/policies").toUri().toURL();
        val jarPathElements = url.toString().split("!");

        var source = new ResourcesPrpUpdateEventSource("", DEFAULT_SAPL_INTERPRETER);

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(jarPathElements[0].substring("file:".length()));

            var config = source.readPoliciesFromJar(url);

            assertThat(config, notNullValue());
        }
    }

    @Test
    void test_read_missing_config_from_jar() throws Exception {
        var url = Paths.get("src/test/resources/policies_in_jar.jar!/missing_folder").toUri().toURL();
        val jarPathElements = url.toString().split("!");

        var source = new ResourcesPrpUpdateEventSource("", DEFAULT_SAPL_INTERPRETER);

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(jarPathElements[0].substring("file:".length()));

            var config = source.readPoliciesFromJar(url);

            assertThat(config, notNullValue());
        }
    }

    @Test
    void throw_exception_when_initialized_with_resource_that_cannot_be_found() {
        assertThrows(RuntimeException.class, () ->
                new ResourcesPrpUpdateEventSource("kljsdfklösöfdjs/dklsjdsaöfs", DEFAULT_SAPL_INTERPRETER));
    }

    @Test
    void propagate_exception_caught_while_reading_policies_from_directory() throws Exception {
        var urlMock = mock(URL.class);
        when(urlMock.toURI()).thenThrow(new URISyntaxException("", ""));

        var source = new ResourcesPrpUpdateEventSource("", DEFAULT_SAPL_INTERPRETER);

        assertThrows(RuntimeException.class, () -> source.readPoliciesFromDirectory(urlMock));
    }

    @Test
    void propagate_exception_caught_while_reading_policies_from_jar() throws Exception {

        val url = Paths.get("src/test/resources/policies_in_jar.jar!/policies").toUri().toURL();
        val jarPathElements = url.toString().split("!");
        val jarFilePath = jarPathElements[0].substring("file:".length());
        val zipFile = new ZipFile(jarFilePath);

        var source = new ResourcesPrpUpdateEventSource("", DEFAULT_SAPL_INTERPRETER);

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(jarFilePath);


            try (MockedConstruction<ZipFile> mocked = Mockito.mockConstruction(ZipFile.class,
                    (mockZipFile, context) -> {
                        doThrow(new IOException()).when(mockZipFile).getInputStream(any());
                        doReturn(zipFile.entries()).when(mockZipFile).entries();
                    })) {

                assertThrows(RuntimeException.class, () -> source.readPoliciesFromJar(url));
            }

        }
    }


}
