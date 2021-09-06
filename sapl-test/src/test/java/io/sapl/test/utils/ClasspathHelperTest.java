package io.sapl.test.utils;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.sapl.test.SaplTestException;

class ClasspathHelperTest {

	@Test
	void test_clazzNull() {
		Assertions.assertThatNullPointerException()
				.isThrownBy(() -> ClasspathHelper.findPathOnClasspath(null, "test.sapl"));
	}

	@Test
	void test_pathNull() {
		Assertions.assertThatNullPointerException().isThrownBy(
				() -> ClasspathHelper.findPathOnClasspath(ClasspathHelperTest.class.getClassLoader(), null));
	}

	@Test
	void test_clazzAndPathNull() {
		Assertions.assertThatNullPointerException().isThrownBy(() -> ClasspathHelper.findPathOnClasspath(null, null));
	}

	@Test
	void test_NothingFound() {
		URLClassLoader classLoader = Mockito.mock(URLClassLoader.class);
		Mockito.when(classLoader.getResource(Mockito.any())).thenReturn(null);
		Mockito.when(classLoader.getURLs()).thenReturn(new URL[0]);

		Assertions.assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> ClasspathHelper.findPathOnClasspath(classLoader, "test.sapl"))
				.withMessage("Error finding test.sapl or policies/test.sapl on the classpath!" + System.lineSeparator()
						+ System.lineSeparator() + "We tried the following paths:" + System.lineSeparator());
	}
	
	@Test
	void test_NothingFound_WithClasspathURLs() throws MalformedURLException {
		URLClassLoader classLoader = Mockito.mock(URLClassLoader.class);
		Mockito.when(classLoader.getResource(Mockito.any())).thenReturn(null);
		Mockito.when(classLoader.getURLs()).thenReturn(new URL[] {new URL("file://test")});

		Assertions.assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> ClasspathHelper.findPathOnClasspath(classLoader, "test.sapl"))
				.withMessage("Error finding test.sapl or policies/test.sapl on the classpath!" + System.lineSeparator()
						+ System.lineSeparator() + "We tried the following paths:" + System.lineSeparator() + "    - file://test");
	}
	
	@Test
	void test_FoundInJar() throws MalformedURLException {
		URLClassLoader classLoader = Mockito.mock(URLClassLoader.class);
		URL url = new URL("jar:file:///C:/test.jar!/test");
		Mockito.when(classLoader.getResource(Mockito.any())).thenReturn(url);

		Assertions.assertThatExceptionOfType(SaplTestException.class)
				.isThrownBy(() -> ClasspathHelper.findPathOnClasspath(classLoader, "test.sapl"))
				.withMessage("Not supporting reading files from jar during test execution!");
	}

}
