package io.sapl.pdp.config.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.io.File;
import java.io.IOException;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.grammar.sapl.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.grammar.sapl.impl.PermitUnlessDenyCombiningAlgorithmImplCustom;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import reactor.core.publisher.Flux;


@Disabled
class ResourcesVariablesAndCombinatorSourceTests {

    @Test
    void loadExistingConfigTest() {
        var configProvider = new ResourcesVariablesAndCombinatorSource("src/test/resources/policies");
        var algo = configProvider.getCombiningAlgorithm().blockFirst();
        var variables = configProvider.getVariables().blockFirst();
        configProvider.dispose();

        assertThat(algo.get() instanceof PermitUnlessDenyCombiningAlgorithmImplCustom, is(true));
        assertThat(variables.get().size(), is(3));
    }


    @Test
    void return_default_config_for_missing_configuration_file() {
        var configProvider = new ResourcesVariablesAndCombinatorSource("src/test/resources");
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
                    doThrow(new IOException()).when(mock).readValue(ArgumentMatchers.any(File.class), ArgumentMatchers.any(Class.class));
                })) {

            var configProvider = new ResourcesVariablesAndCombinatorSource("src/test/resources/policies");
            var algo = configProvider.getCombiningAlgorithm().blockFirst();
            var variables = configProvider.getVariables().blockFirst();
            configProvider.dispose();

            verify(mocked.constructed().get(0), times(1)).
                    readValue(ArgumentMatchers.any(File.class), ArgumentMatchers.any(Class.class));

            assertThat(algo.isEmpty(), is(true));
            assertThat(variables.isEmpty(), is(true));
        }
    }

    @Test
    void test_process_watcher_event() {
        try (MockedStatic<FileMonitorUtil> mock = mockStatic(FileMonitorUtil.class)) {
            mock.when(() -> FileMonitorUtil.monitorDirectory(any(), any()))
                    .thenReturn(Flux.just(new FileCreatedEvent(null), new FileDeletedEvent(null)));


            var configProvider = new ResourcesVariablesAndCombinatorSource("src/test/resources/policies");
            var algo = configProvider.getCombiningAlgorithm().blockLast();
            configProvider.getVariables().blockFirst();
            configProvider.dispose();


            mock.verify(() -> FileMonitorUtil.monitorDirectory(any(), any()), times(1));
            assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
        }
    }
}
