package io.sapl.mavenplugin.test.coverage.helper;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.assertj.core.api.Assertions;
import org.junit.Before;
import org.junit.Test;

import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;

public class SaplDocumentReaderTest {
	
	private MavenProjectStub project;
	private String policyPath = "policies";
	
	@Before
	public void setup() {
		project = new MavenProjectStub();
		//project.setTestClasspathElements(List.of("C:/Users/Nikolai/eclipse-sapl-workspace/sapl-test/sapl-maven-plugin/target/test-classes"));
		project.setRuntimeClasspathElements(List.of("target/classes"));
	}
	
	@Test
	public void test() {
		SaplDocumentReader reader = new SaplDocumentReader(new SilentLog(), project);
		Collection<SaplDocument> documents = reader.retrievePolicyDocuments(policyPath);
		Assertions.assertThat(documents.size()).isEqualTo(2);
	}

	@Test
	public void test_nonExistentPath() {
		SaplDocumentReader reader = new SaplDocumentReader(new SilentLog(), project);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
		.isThrownBy(() -> reader.retrievePolicyDocuments("src" + File.separator + "test" + File.separator + "resources" + File.separator + "policies"));
	}
	
	@Test
	public void test_pathPointsToFile() {
		SaplDocumentReader reader = new SaplDocumentReader(new SilentLog(), project);
		Assertions.assertThatExceptionOfType(SaplTestException.class)
		.isThrownBy(() -> reader.retrievePolicyDocuments("policies" + File.separator + "policy_1.sapl"));
	}
	
	@Test
	public void test_FilePathWithDot() {
		SaplDocumentReader reader = new SaplDocumentReader(new SilentLog(), project);
		Collection<SaplDocument> documents = reader.retrievePolicyDocuments("." + File.separator + "policies");
		Assertions.assertThat(documents.size()).isEqualTo(2);
	}

}
