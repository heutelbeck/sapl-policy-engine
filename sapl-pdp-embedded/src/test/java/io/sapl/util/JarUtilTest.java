package io.sapl.util;

import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.zip.ZipFile;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

public class JarUtilTest {
	@Test
	public void inferUrlOfRecourcesPathTest() {
		var result = JarUtil.inferUrlOfRecourcesPath(getClass(), "/policies");
		assertThat(result.toString().startsWith("file:"), is(true));
		assertThat(result.toString().endsWith("policies"), is(true));
	}

	@Test
	public void inferUrlOfRecourcesPathTestWithMissingResource() {
		assertThrows(RuntimeException.class, () -> JarUtil.inferUrlOfRecourcesPath(getClass(), "/iDoNotExist"));
	}

	@Test
	public void getJarFilePathTest() {
		var url = JarUtil.inferUrlOfRecourcesPath(getClass(), "/policies");
		var result = JarUtil.getJarFilePath(url);
		assertThat(result.toString().endsWith("policies"), is(true));
	}

	@Test
	public void readStringFromZipEntryTest() throws IOException {
		var url = new URL("jar:" + ClassLoader.getSystemResource("policies_in_jar.jar") + "!/policies");
		var pathOfJar = JarUtil.getJarFilePath(url);
		try (var jarFile = new ZipFile(pathOfJar)) {
			var entry = jarFile.getEntry("policies/pdp.json");
			var contents = JarUtil.readStringFromZipEntry(jarFile, entry);
			assertThat(contents.length(), is(not(0)));
		}
	}

	@Test
	public void readStringFromZipEntryTestWithErrorPropagation() throws IOException {
		var url = new URL("jar:" + ClassLoader.getSystemResource("policies_in_jar.jar") + "!/policies");
		var pathOfJar = JarUtil.getJarFilePath(url);
		try (MockedStatic<IOUtils> mock = mockStatic(IOUtils.class)) {
			mock.when(() -> IOUtils.toString(any(InputStream.class), any(Charset.class))).thenThrow(new IOException());
			try (var jarFile = new ZipFile(pathOfJar)) {
				var entry = jarFile.getEntry("policies/pdp.json");
				assertThrows(IOException.class, () -> JarUtil.readStringFromZipEntry(jarFile, entry));
			}
		}
	}

}
