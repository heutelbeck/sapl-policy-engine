package io.sapl.mavenplugin.test.coverage.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.File;
import java.util.Collection;
import java.util.List;

import org.apache.maven.plugin.testing.SilentLog;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.mavenplugin.test.coverage.SaplTestException;
import io.sapl.mavenplugin.test.coverage.model.SaplDocument;

public class SaplDocumentReaderTest {
	
	private MavenProjectStub project;
	private String policyPath = "policies";
	private SaplDocumentReader reader;
	
	@BeforeEach
	public void setup() {
		project = new MavenProjectStub();
		//project.setTestClasspathElements(List.of("C:/Users/Nikolai/eclipse-sapl-workspace/sapl-test/sapl-maven-plugin/target/test-classes"));
		project.setRuntimeClasspathElements(List.of("target/classes"));
		reader = new SaplDocumentReader();
	}
	
	@Test
	public void test() {
		Collection<SaplDocument> documents = reader.retrievePolicyDocuments(new SilentLog(), project, policyPath);
		assertEquals(2, documents.size());
	}

	@Test
	public void test_nonExistentPath() {
		assertThrows(SaplTestException.class, () -> reader.retrievePolicyDocuments(new SilentLog(), project, "src" + File.separator + "test" + File.separator + "resources" + File.separator + "policies"));
	}
	
	@Test
	public void test_pathPointsToFile() {
		assertThrows(SaplTestException.class, () -> reader.retrievePolicyDocuments(new SilentLog(), project, "policies" + File.separator + "policy_1.sapl"));
	}
	
	@Test
	public void test_FilePathWithDot() {
		Collection<SaplDocument> documents = reader.retrievePolicyDocuments(new SilentLog(), project, "." + File.separator + "policies");
		assertEquals(2, documents.size());
	}

}
