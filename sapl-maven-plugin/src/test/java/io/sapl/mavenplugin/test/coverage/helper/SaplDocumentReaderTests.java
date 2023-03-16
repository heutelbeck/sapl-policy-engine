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
package io.sapl.mavenplugin.test.coverage.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.List;

import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.project.MavenProject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.mavenplugin.test.coverage.model.SaplDocument;

class SaplDocumentReaderTests {

	private MavenProjectStub project;

	private String policyPath = "policies";

	private SaplDocumentReader reader;

	@BeforeEach
	void setup() {
		project = new MavenProjectStub();
		project.setRuntimeClasspathElements(List.of("target/classes"));
		reader = new SaplDocumentReader();
	}

	@Test
	void test() throws MojoExecutionException {
		Collection<SaplDocument> documents = reader.retrievePolicyDocuments(new SilentLog(), project, policyPath);
		assertEquals(2, documents.size());
	}

	@Test
	void test_nonExistentPath() {
		assertThrows(MojoExecutionException.class, () -> reader.retrievePolicyDocuments(new SilentLog(), project,
				"src" + File.separator + "test" + File.separator + "resources" + File.separator + "policies"));
	}

	@Test
	void test_pathPointsToFile() {
		assertThrows(MojoExecutionException.class, () -> reader.retrievePolicyDocuments(new SilentLog(), project,
				"policies" + File.separator + "policy_1.sapl"));
	}

	@Test
	void test_FilePathWithDot() throws MojoExecutionException {
		Collection<SaplDocument> documents = reader.retrievePolicyDocuments(new SilentLog(), project,
				"." + File.separator + "policies");
		assertEquals(2, documents.size());
	}

	@Test
	void test_File_IOException() {
		try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
			mockedFiles.when(() -> Files.readString(Mockito.any())).thenThrow(IOException.class);
			assertThrows(MojoExecutionException.class,
					() -> reader.retrievePolicyDocuments(new SilentLog(), project, "." + File.separator + "policies"));
		}
	}

	@Test
	void test_DependencyResolutionRequiredException() throws DependencyResolutionRequiredException {
		var project = mock(MavenProject.class);
		when(project.getRuntimeClasspathElements()).thenThrow(DependencyResolutionRequiredException.class);
		assertThrows(MojoExecutionException.class,
				() -> reader.retrievePolicyDocuments(new SilentLog(), project, "." + File.separator + "policies"));
	}

}
