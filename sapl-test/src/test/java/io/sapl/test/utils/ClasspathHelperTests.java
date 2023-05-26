/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLClassLoader;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.test.SaplTestException;

class ClasspathHelperTests {

	@Test
	void test_clazzNull() {
		assertThatNullPointerException().isThrownBy(() -> ClasspathHelper.findPathOnClasspath(null, "test.sapl"));
	}

	@Test
	void test_pathNull() {
		assertThatNullPointerException().isThrownBy(
				() -> ClasspathHelper.findPathOnClasspath(ClasspathHelperTests.class.getClassLoader(), null));
	}

	@Test
	void test_clazzAndPathNull() {
		assertThatNullPointerException().isThrownBy(() -> ClasspathHelper.findPathOnClasspath(null, null));
	}

	@Test
	void test_NothingFound() {
		var classLoader = Mockito.mock(URLClassLoader.class);
		Mockito.when(classLoader.getResource(Mockito.any())).thenReturn(null);
		Mockito.when(classLoader.getURLs()).thenReturn(new URL[0]);

		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> ClasspathHelper.findPathOnClasspath(classLoader, "test.sapl"))
				.withMessage("Error finding test.sapl or policies/test.sapl on the classpath!" + System.lineSeparator()
						+ System.lineSeparator() + "We tried the following paths:" + System.lineSeparator());
	}

	@Test
	void test_NothingFound_WithClasspathURLs() throws MalformedURLException {
		var classLoader = Mockito.mock(URLClassLoader.class);
		Mockito.when(classLoader.getResource(Mockito.any())).thenReturn(null);
		Mockito.when(classLoader.getURLs()).thenReturn(new URL[] { new URL("file://test") });

		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> ClasspathHelper.findPathOnClasspath(classLoader, "test.sapl"))
				.withMessage("Error finding test.sapl or policies/test.sapl on the classpath!" + System.lineSeparator()
						+ System.lineSeparator() + "We tried the following paths:" + System.lineSeparator()
						+ "    - file://test");
	}

	@Test
	void test_FoundInJar() throws MalformedURLException {
		var classLoader = Mockito.mock(URLClassLoader.class);
		var url         = new URL("jar:file:///C:/test.jar!/test");
		Mockito.when(classLoader.getResource(Mockito.any())).thenReturn(url);

		assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> ClasspathHelper.findPathOnClasspath(classLoader, "test.sapl"))
				.withMessage("Not supporting reading files from jar during test execution!");
	}

	@Test
	void test_MalformedURI() throws URISyntaxException {
		var classLoader = Mockito.mock(URLClassLoader.class);
		var url         = mock(URL.class);
		when(url.toURI()).thenThrow(new URISyntaxException("XXX", "YYY"));
		Mockito.when(classLoader.getResource(Mockito.any())).thenReturn(url);

		assertThatExceptionOfType(RuntimeException.class)
				.isThrownBy(() -> ClasspathHelper.findPathOnClasspath(classLoader, "test.sapl"))
				.withMessage("java.net.URISyntaxException: YYY: XXX");
	}

}
