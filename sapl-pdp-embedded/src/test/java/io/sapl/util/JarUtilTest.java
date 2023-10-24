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

class JarUtilTest {

	@Test
	void inferUrlOfResourcesPathTest() {
		var result = JarUtil.inferUrlOfResourcesPath(getClass(), "/policies");
		assertThat(result.toString().startsWith("file:"), is(true));
		assertThat(result.toString().endsWith("policies"), is(true));
	}

	@Test
	void inferUrlOfResourcesPathTestWithMissingResource() {
		var thisClass = this.getClass();
		assertThrows(RuntimeException.class, () -> JarUtil.inferUrlOfResourcesPath(thisClass, "/iDoNotExist"));
	}

	@Test
	void getJarFilePathTest() {
		var url    = JarUtil.inferUrlOfResourcesPath(getClass(), "/policies");
		var result = JarUtil.getJarFilePath(url);
		assertThat(result.endsWith("policies"), is(true));
	}

	@Test
	void readStringFromZipEntryTest() throws IOException {
		var url       = new URL("jar:" + ClassLoader.getSystemResource("policies_in_jar.jar") + "!/policies");
		var pathOfJar = JarUtil.getJarFilePath(url);
		try (var jarFile = new ZipFile(pathOfJar)) {
			var entry    = jarFile.getEntry("policies/pdp.json");
			var contents = JarUtil.readStringFromZipEntry(jarFile, entry);
			assertThat(contents.length(), is(not(0)));
		}
	}

	@Test
	void readStringFromZipEntryTestWithErrorPropagation() throws IOException {
		var url       = new URL("jar:" + ClassLoader.getSystemResource("policies_in_jar.jar") + "!/policies");
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
