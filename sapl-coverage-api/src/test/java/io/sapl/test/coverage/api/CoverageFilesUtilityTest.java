package io.sapl.test.coverage.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class CoverageFilesUtilityTest {
	
	Path baseDir;
	
	@BeforeEach
	void setup() throws IOException {
		baseDir = Paths.get("target/tmp");
		Files.createDirectories(baseDir);
	}
	
	@AfterEach
	void cleanup() {
		TestFileHelper.deleteDirectory(baseDir.toFile());
	}
	
	@Test
	void test_DirectoryNotEmptyException() throws IOException {
		var reader = CoverageAPIFactory.constructCoverageHitReader(baseDir);
		Path pathToErrorFile = baseDir.resolve("hits").resolve("_policySetHits.txt").resolve("test.txt");
		Files.createDirectories(pathToErrorFile.getParent());
		Files.createFile(pathToErrorFile);
		
		reader.cleanCoverageHitFiles();
		
		Assertions.assertThatNoException();
	}
	
	@Test
	void test_FileAlreadyExists() throws IOException {
		Path pathToErrorFile = baseDir.resolve("hits").resolve("_policySetHits.txt");
		Files.createDirectories(pathToErrorFile.getParent());
		Files.createFile(pathToErrorFile);
		CoverageHitRecorder recorder = new CoverageHitAPIImpl(baseDir);
		recorder.createCoverageHitFiles();
		Assertions.assertThatNoException();
	}
	
	@Test
	void test_NoParent() throws IOException {
		Path path = Paths.get("target");
		CoverageHitRecorder recorder = new CoverageHitAPIImpl(path);
		recorder.createCoverageHitFiles();
		Assertions.assertThatNoException();
		recorder.cleanCoverageHitFiles();
	}
	
}
