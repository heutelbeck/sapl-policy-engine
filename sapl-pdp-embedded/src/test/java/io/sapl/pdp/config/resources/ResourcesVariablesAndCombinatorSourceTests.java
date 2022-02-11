/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.pdp.config.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.grammar.sapl.DenyUnlessPermitCombiningAlgorithm;
import io.sapl.grammar.sapl.PermitUnlessDenyCombiningAlgorithm;
import io.sapl.interpreter.InitializationException;
import io.sapl.util.JarUtil;

class ResourcesVariablesAndCombinatorSourceTests {

	@Test
	void test_guard_clauses() {
		assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, null));
		assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource("", null));
		assertThrows(NullPointerException.class,
				() -> new ResourcesVariablesAndCombinatorSource(null, mock(ObjectMapper.class)));

		assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, null, null));
		assertThrows(NullPointerException.class, () -> new ResourcesVariablesAndCombinatorSource(null, "", null));
		assertThrows(NullPointerException.class,
				() -> new ResourcesVariablesAndCombinatorSource(null, null, mock(ObjectMapper.class)));
		assertThrows(NullPointerException.class,
				() -> new ResourcesVariablesAndCombinatorSource(this.getClass(), null, null));
		assertThrows(NullPointerException.class,
				() -> new ResourcesVariablesAndCombinatorSource(this.getClass(), "", null));

	}

	@Test
	void ifExecutedDuringUnitTests_thenLoadConfigurationFileFromFileSystem() throws InitializationException {
		var configProvider = new ResourcesVariablesAndCombinatorSource("/valid_config");
		var algo = configProvider.getCombiningAlgorithm().blockFirst();
		var variables = configProvider.getVariables().blockFirst();
		configProvider.dispose();

		assertThat(algo.get() instanceof PermitUnlessDenyCombiningAlgorithm, is(true));
		assertThat(variables.get().size(), is(2));
	}

	@Test
	void ifExecutedDuringUnitTestsAndNoConfigFilePresent_thenLoadDefaultConfiguration() throws InitializationException {
		var configProvider = new ResourcesVariablesAndCombinatorSource("");
		var algo = configProvider.getCombiningAlgorithm().blockFirst();
		var variables = configProvider.getVariables().blockFirst();
		configProvider.dispose();

		assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
		assertThat(variables.get().size(), is(0));
	}

	@Test
	void ifExecutedDuringUnitTestsAndConfigFileBroken_thenPropagateException() {
		assertThrows(InitializationException.class, () -> new ResourcesVariablesAndCombinatorSource("/broken_config"));
	}

	@Test
	void ifExecutedInJar_thenLoadConfigurationFileFromJar() throws InitializationException, MalformedURLException {
		var url = new URL("jar:" + ClassLoader.getSystemResource("policies_in_jar.jar") + "!/policies");
		try (MockedStatic<JarUtil> mock = mockStatic(JarUtil.class, CALLS_REAL_METHODS)) {
			mock.when(() -> JarUtil.inferUrlOfResourcesPath(any(), any())).thenReturn(url);

			var configProvider = new ResourcesVariablesAndCombinatorSource();
			var algo = configProvider.getCombiningAlgorithm().blockFirst();
			var variables = configProvider.getVariables().blockFirst();
			configProvider.dispose();

			assertThat(algo.get() instanceof PermitUnlessDenyCombiningAlgorithm, is(true));
			assertThat(variables.get().size(), is(2));
		}
	}

	@Test
	void ifExecutedInJarAndConfigFileBroken_thenPropagateException() throws URISyntaxException, MalformedURLException {
		var url = new URL("jar:" + ClassLoader.getSystemResource("broken_config_in_jar.jar") + "!/policies");
		try (MockedStatic<JarUtil> mock = mockStatic(JarUtil.class, CALLS_REAL_METHODS)) {
			mock.when(() -> JarUtil.inferUrlOfResourcesPath(any(), any())).thenReturn(url);
			assertThrows(InitializationException.class, () -> new ResourcesVariablesAndCombinatorSource("/policies"));
		}
	}

	@Test
	void ifExecutedInJarAndNoConfigFilePresent_thenLoadDefaultConfiguration()
			throws InitializationException, MalformedURLException {
		var url = new URL("jar:" + ClassLoader.getSystemResource("policies_in_jar.jar") + "!/not_existing");
		try (MockedStatic<JarUtil> mock = mockStatic(JarUtil.class, CALLS_REAL_METHODS)) {
			mock.when(() -> JarUtil.inferUrlOfResourcesPath(any(), any())).thenReturn(url);

			var configProvider = new ResourcesVariablesAndCombinatorSource("/not_existing");
			var algo = configProvider.getCombiningAlgorithm().blockFirst();
			var variables = configProvider.getVariables().blockFirst();
			configProvider.dispose();

			assertThat(algo.get() instanceof DenyUnlessPermitCombiningAlgorithm, is(true));
			assertThat(variables.get().size(), is(0));
		}
	}

}
