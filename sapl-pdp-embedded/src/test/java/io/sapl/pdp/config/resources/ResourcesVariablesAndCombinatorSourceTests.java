package io.sapl.pdp.config.resources;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.sapl.grammar.sapl.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.util.JarPathUtil;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import lombok.val;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import reactor.core.publisher.Flux;

import java.io.File;
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

//@Disabled
class ResourcesVariablesAndCombinatorSourceTests {

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
        var url = Paths.get("src/test/resources/policies_in_jar.jar!/policies").toUri().toURL();
        val jarPathElements = url.toString().split("!");

        var source = new ResourcesVariablesAndCombinatorSource("");

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(jarPathElements[0].substring("file:".length()));

            var config = source.readConfigFromJar(url);

            assertThat(config, notNullValue());
        }
    }

    @Test
    void test_read_missing_config_from_jar() throws Exception {
        var url = Paths.get("src/test/resources/policies_in_jar.jar!/missing_folder").toUri().toURL();
        val jarPathElements = url.toString().split("!");

        var source = new ResourcesVariablesAndCombinatorSource("");

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(jarPathElements[0].substring("file:".length()));

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

        assertThrows(RuntimeException.class, () -> source.readConfigFromDirectory(urlMock));
    }

    @Test
    void propagate_exception_caught_while_reading_config_from_jar() throws Exception {
        URL url = ClassLoader.getSystemResource("policies_in_jar.jar");
        val url3 = Paths.get(url.getPath() + "!" + File.separator + "policies").toUri().toURL();
        val zipFile = new ZipFile(url.getPath());

        val source = new ResourcesVariablesAndCombinatorSource("");

        try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
            mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(url.getPath());

            try (MockedConstruction<ZipFile> mocked = Mockito.mockConstruction(ZipFile.class,
                    (mockZipFile, context) -> {
                        doThrow(new IOException()).when(mockZipFile).getInputStream(any());
                        doReturn(zipFile.entries()).when(mockZipFile).entries();
                    })) {

                assertThrows(RuntimeException.class, () -> source.readConfigFromJar(url3));
            }

        }
    }


}
