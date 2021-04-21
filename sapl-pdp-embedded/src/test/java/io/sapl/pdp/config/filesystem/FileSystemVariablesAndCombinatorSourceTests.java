package io.sapl.pdp.config.filesystem;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.sapl.grammar.sapl.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.util.filemonitoring.FileCreatedEvent;
import io.sapl.util.filemonitoring.FileDeletedEvent;
import io.sapl.util.filemonitoring.FileMonitorUtil;
import reactor.core.publisher.Flux;

class FileSystemVariablesAndCombinatorSourceTest {

	@Test
	void loadExistingConfigTest() {
		var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources/policies");
		var algo = configProvider.getCombiningAlgorithm().blockFirst();
		var variables = configProvider.getVariables().blockFirst();
		configProvider.dispose();

		assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
		assertThat(variables.get().size(), is(3));
	}

	@Test
	void return_default_config_for_missing_configuration_file() {
		var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources");
		var algo = configProvider.getCombiningAlgorithm().blockFirst();
		var variables = configProvider.getVariables().blockFirst();
		configProvider.dispose();

		assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
		assertThat(variables.get().size(), is(0));
	}

	@Test
	void return_empty_optional_for_exception_during_config_load() throws Exception {
		var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources/broken_config");
		var algo = configProvider.getCombiningAlgorithm().blockFirst();
		var variables = configProvider.getVariables().blockFirst();
		configProvider.dispose();
		assertThat(algo.isEmpty(), is(true));
		assertThat(variables.isEmpty(), is(true));
	}

	@Test
	void test_process_watcher_event() {
		try (MockedStatic<FileMonitorUtil> mock = mockStatic(FileMonitorUtil.class)) {
			mock.when(() -> FileMonitorUtil.monitorDirectory(any(), any()))
					.thenReturn(Flux.just(new FileCreatedEvent(null), new FileDeletedEvent(null)));

			var configProvider = new FileSystemVariablesAndCombinatorSource("src/test/resources/policies");
			var algo = configProvider.getCombiningAlgorithm().blockLast();
			configProvider.getVariables().blockFirst();
			configProvider.dispose();

			mock.verify(() -> FileMonitorUtil.monitorDirectory(any(), any()), times(1));
			assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
		}
	}
}
