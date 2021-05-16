package io.sapl.prp.resources;

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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.zip.ZipFile;

import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.util.JarPathUtil;
import lombok.val;

class ResourcesPrpUpdateEventSourceTest {

	static final DefaultSAPLInterpreter DEFAULT_SAPL_INTERPRETER = new DefaultSAPLInterpreter();

	@Test
	void test_guard_clauses() {
		assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null));
		assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource("", null));
		assertThrows(NullPointerException.class,
				() -> new ResourcesPrpUpdateEventSource(null, mock(SAPLInterpreter.class)));

		assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null, null));
		assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, "", null));
		assertThrows(NullPointerException.class,
				() -> new ResourcesPrpUpdateEventSource(null, null, mock(SAPLInterpreter.class)));
		assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(this.getClass(), null, null));
		assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(this.getClass(), "", null));

	}

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
		assertThat(update.getUpdates().length, is(3));

		assertThrows(RuntimeException.class,
				() -> new ResourcesPrpUpdateEventSource("/NON-EXISTING-PATH", DEFAULT_SAPL_INTERPRETER));

		assertThrows(PolicyEvaluationException.class,
				() -> new ResourcesPrpUpdateEventSource("/it/invalid", DEFAULT_SAPL_INTERPRETER));

	}

	@Test
	void test_read_config_from_jar() throws Exception {
		URI uri = ClassLoader.getSystemResource("policies_in_jar.jar").toURI();
		String pathToJar = Paths.get(uri).toString();
		String pathToJarWithDirectory = pathToJar + "!" + "policies";
		val url = Paths.get(pathToJarWithDirectory).toUri().toURL();

		System.out.println(url);

		var source = new ResourcesPrpUpdateEventSource("", DEFAULT_SAPL_INTERPRETER);

		try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
			mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(pathToJar);

			var config = source.readPoliciesFromJar(url);

			assertThat(config, notNullValue());
		}
	}

	@Test
	void test_read_missing_config_from_jar() throws Exception {
		URI uri = ClassLoader.getSystemResource("policies_in_jar.jar").toURI();
		String pathToJar = Paths.get(uri).toString();
		String pathToJarWithDirectory = pathToJar + "!" + "missing_folder";
		val url = Paths.get(pathToJarWithDirectory).toUri().toURL();

		var source = new ResourcesPrpUpdateEventSource("", DEFAULT_SAPL_INTERPRETER);

		try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
			mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(pathToJar);

			var config = source.readPoliciesFromJar(url);

			assertThat(config, notNullValue());
		}
	}

	@Test
	void throw_exception_when_initialized_with_resource_that_cannot_be_found() {
		assertThrows(RuntimeException.class,
				() -> new ResourcesPrpUpdateEventSource("kljsdfklösöfdjs/dklsjdsaöfs", DEFAULT_SAPL_INTERPRETER));
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
		URI uri = ClassLoader.getSystemResource("policies_in_jar.jar").toURI();
		String pathToJar = Paths.get(uri).toString();
		String pathToJarWithDirectory = pathToJar + "!" + File.separator + "policies";
		val url = Paths.get(pathToJarWithDirectory).toUri().toURL();

		val zipFile = new ZipFile(pathToJar);
		var source = new ResourcesPrpUpdateEventSource("", DEFAULT_SAPL_INTERPRETER);

		try (MockedStatic<JarPathUtil> mock = mockStatic(JarPathUtil.class)) {
			mock.when(() -> JarPathUtil.getJarFilePath(any())).thenReturn(pathToJar);

			try (MockedConstruction<ZipFile> mocked = Mockito.mockConstruction(ZipFile.class,
					(mockZipFile, context) -> {
						doThrow(new IOException()).when(mockZipFile).getInputStream(any());
						doReturn(zipFile.entries()).when(mockZipFile).entries();
					})) {

				assertThrows(RuntimeException.class, () -> source.readPoliciesFromJar(url));
			}

		}

		zipFile.close();
	}

}
