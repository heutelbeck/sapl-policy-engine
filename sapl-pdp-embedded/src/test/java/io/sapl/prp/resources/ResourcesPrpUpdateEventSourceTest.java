package io.sapl.prp.resources;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.sapl.api.interpreter.PolicyEvaluationException;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.SAPLInterpreter;
import io.sapl.util.JarUtility;

class ResourcesPrpUpdateEventSourceTest {

	static final DefaultSAPLInterpreter DEFAULT_SAPL_INTERPRETER = new DefaultSAPLInterpreter();

	@Test
	void test_guard_clauses() {
		assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource(null, null));
		assertThrows(NullPointerException.class, () -> new ResourcesPrpUpdateEventSource("", null));
		assertThrows(NullPointerException.class,
				() -> new ResourcesPrpUpdateEventSource(null, DEFAULT_SAPL_INTERPRETER));
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
	void readPoliciesFromDirectory() {
		var source = new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER);
		var update = source.getUpdates().blockFirst();
		assertThat(update, notNullValue());
		assertThat(update.getUpdates().length, is(3));
		assertThrows(RuntimeException.class,
				() -> new ResourcesPrpUpdateEventSource("/NON-EXISTING-PATH", DEFAULT_SAPL_INTERPRETER));
		assertThrows(PolicyEvaluationException.class,
				() -> new ResourcesPrpUpdateEventSource("/it/invalid", DEFAULT_SAPL_INTERPRETER));
		source.dispose();
	}

	@Test
	void ifFileIOError_exceptionPropagates() throws IOException {
		var failingInputStream = mock(InputStream.class);
		when(failingInputStream.read(any(), anyInt(), anyInt())).thenThrow(new IOException("to be expected"));

		try (MockedStatic<Channels> mock = mockStatic(Channels.class, CALLS_REAL_METHODS)) {
			mock.when(() -> Channels.newInputStream(any(ReadableByteChannel.class))).thenReturn(failingInputStream);
			assertThrows(IOException.class,
					() -> new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER));
		}
	}

	@Test
	void ifExecutedInJar_thenLoadDocumentsFromJar() throws URISyntaxException, MalformedURLException {
		var url = new URL("jar:" + ClassLoader.getSystemResource("policies_in_jar.jar") + "!/policies");
		try (MockedStatic<JarUtility> mock = mockStatic(JarUtility.class, CALLS_REAL_METHODS)) {
			mock.when(() -> JarUtility.inferUrlOfRecourcesPath(any(), any())).thenReturn(url);

			var source = new ResourcesPrpUpdateEventSource("/policies", DEFAULT_SAPL_INTERPRETER);
			var update = source.getUpdates().blockFirst();

			assertThat(update, notNullValue());
			assertThat(update.getUpdates().length, is(2));
		}
	}

}
