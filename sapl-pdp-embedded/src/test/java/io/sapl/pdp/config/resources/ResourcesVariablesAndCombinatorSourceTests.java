package io.sapl.pdp.config.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.grammar.sapl.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.util.JarPathUtil;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import lombok.val;
import reactor.core.publisher.Flux;

class ResourcesVariablesAndCombinatorSourceTests {

    @Test
    void test_guard_clauses() {
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource("", null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, mock(ObjectMapper.class)));

        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, "", null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, null,  mock(ObjectMapper.class)));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(this.getClass(), null, null));
        assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(this.getClass(), "", null));

    }

    @Test
    void loadExistingConfigTest() {
        var configProvider = new ResourcesVariablesAndCombinatorSource();
        var algo = configProvider.getCombiningAlgorithm().blockFirst();
        var variables = configProvider.getVariables().blockFirst();
        configProvider.dispose();

        assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
        assertThat(variables.get().size(), is(3));
    }


    @Test
    void return_default_config_for_missing_configuration_file() {
        var configProvider = new ResourcesVariablesAndCombinatorSource("");
        var algo = configProvider.getCombiningAlgorithm().blockFirst();
        var variables = configProvider.getVariables().blockFirst();
        configProvider.dispose();

        assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
        assertThat(variables.get().size(), is(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void return_empty_optional_for_exception_during_config_load() throws Exception {
        try (MockedConstruction<ObjectMapper> mocked = Mockito.mockConstruction(ObjectMapper.class,
                (mock, context) -> {
                    doThrow(new IOException()).when(mock).readValue(any(File.class), any(Class.class));
                })) {

            assertThrows(Exception.class, () -> new ResourcesVariablesAndCombinatorSource("/policies"));
        }
    }

    @Test
    void test_process_watcher_event() {
        try (MockedStatic<FileMonitorUtil> mock = mockStatic(FileMonitorUtil.class)) {
            mock.when(() -> FileMonitorUtil.monitorDirectory(any(), any()))
                    .thenReturn(Flux.just(new FileCreatedEvent(null), new FileDeletedEvent(null)));


            var configProvider = new ResourcesVariablesAndCombinatorSource("/policies");
            var algo = configProvider.getCombiningAlgorithm().blockLast();
            configProvider.getVariables().blockFirst();
            configProvider.dispose();


            assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
        }
    }

    @Test
    void test_read_config_from_jar() throws Exception {
        URI uri = ClassLoader.getSystemResource("policies_in_jar.jar").toURI();
        String pathToJar = Paths.get(uri).toString();
        String pathToJarWithDirectory = pathToJar + "!" + File.separator + "policies";
        val url = Paths.get(pathToJarWithDirectory).toUri().toURL();

        var source = new ResourcesVariablesAndCombinatorSource("");

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(pathToJar);

            var config = source.readConfigFromJar(url);

            assertThat(config, notNullValue());
        }
    }

    @Test
    void test_read_missing_config_from_jar() throws Exception {
        URI uri = ClassLoader.getSystemResource("policies_in_jar.jar").toURI();
        String pathToJar = Paths.get(uri).toString();
        String pathToJarWithDirectory = pathToJar + "!" + "missing_folder";
        val url = Paths.get(pathToJarWithDirectory).toUri().toURL();

        var source = new ResourcesVariablesAndCombinatorSource("");

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(pathToJar);

            var config = source.readConfigFromJar(url);

            assertThat(config, notNullValue());
        }
    }

    @Test
    void throw_exception_when_initialized_with_resource_that_cannot_be_found() {
        assertThrows(RuntimeException.class, () -> new ResourcesVariablesAndCombinatorSource("kljsdfklösöfdjs/dklsjdsaöfs"));
    }

    @Test
    void propagate_exception_caught_while_reading_config_from_directory() throws Exception {
        var urlMock = mock(URL.class);
        when(urlMock.toURI()).thenThrow(new URISyntaxException("", ""));

        var source = new ResourcesVariablesAndCombinatorSource("");
        RuntimeException exception = assertThrows(RuntimeException.class, () -> source.readConfigFromDirectory(urlMock));
        assertThat(exception.getCause() instanceof URISyntaxException, is(true));

        try (MockedStatic<Files> mock = mockStatic(Files.class)) {
            mock.when(() -> Files.newDirectoryStream(any(), anyString())).thenThrow(new IOException());

            URI uri = ClassLoader.getSystemResource("policies").toURI();
            String pathToDir = Paths.get(uri).toString();
            val url = Paths.get(pathToDir).toUri().toURL();
            exception = assertThrows(RuntimeException.class, () -> source.readConfigFromDirectory(url));
            assertThat(exception.getCause() instanceof IOException, is(true));
        }
    }

    @Test
    void propagate_exception_caught_while_reading_config_from_jar() throws Exception {
        URI uri = ClassLoader.getSystemResource("policies_in_jar.jar").toURI();
        String pathToJar = Paths.get(uri).toString();
        String pathToJarWithDirectory = pathToJar + "!" + File.separator + "policies";
        val url = Paths.get(pathToJarWithDirectory).toUri().toURL();

        val zipFile = new ZipFile(pathToJar);

        val source = new ResourcesVariablesAndCombinatorSource("");

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(pathToJar);

            try (MockedConstruction<ZipFile> mocked = Mockito.mockConstruction(ZipFile.class,
                    (mockZipFile, context) -> {
                        doThrow(new IOException()).when(mockZipFile).getInputStream(any());
                        doReturn(zipFile.entries()).when(mockZipFile).entries();
                    })) {

                assertThrows(RuntimeException.class, () -> source.readConfigFromJar(url));
            }
        }
        
        zipFile.close();
    }


}
