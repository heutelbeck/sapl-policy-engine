package io.sapl.test.coverage.api;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.sapl.test.coverage.api.model.PolicySetHit;

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
		assertDoesNotThrow(() -> reader.cleanCoverageHitFiles());
	}
	
	@Test
	void test_FileAlreadyExists() throws IOException {
		Path pathToErrorFile = baseDir.resolve("hits").resolve("_policySetHits.txt");
		Files.createDirectories(pathToErrorFile.getParent());
		Files.createFile(pathToErrorFile);
		CoverageHitRecorder recorder = new CoverageHitAPIFile(baseDir);
		assertDoesNotThrow(() -> recorder.createCoverageHitFiles());
	}
	
	@Test
	void test_NoParent() throws IOException {
		try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
			mockedFiles.when(() -> Files.exists(Mockito.any())).thenReturn(false);
			Path path = Mockito.mock(Path.class);
			when(path.getParent()).thenReturn(null);
			when(path.resolve(Mockito.anyString())).thenReturn(path);
			CoverageHitRecorder recorder = new CoverageHitAPIFile(path);
			assertDoesNotThrow(() -> recorder.createCoverageHitFiles());
			recorder.cleanCoverageHitFiles();
	    }
	}

	
	@Test
	void test_ThrowsIOException_OnCreateCoverageFiles() {
		Path path = Paths.get("target");
		CoverageHitRecorder recorder = new CoverageHitAPIFile(path);
		try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
			mockedFiles.when(() -> Files.createDirectories(Mockito.any())).thenThrow(IOException.class);
			assertDoesNotThrow(() -> recorder.createCoverageHitFiles());
	    }
	}
	
	@Test
	void test_ThrowsIOException_OnRecordHit() {
		CoverageHitRecorder recorder = new CoverageHitAPIFile(baseDir);
		recorder.createCoverageHitFiles();
		try (MockedStatic<Files> mockedFiles = Mockito.mockStatic(Files.class)) {
			mockedFiles.when(() -> Files.lines(Mockito.any())).thenThrow(IOException.class);
			assertDoesNotThrow(() -> recorder.recordPolicySetHit(new PolicySetHit("")));
	    }
		recorder.cleanCoverageHitFiles();
	}
	
	@Test
	void test_NullFilePath() {
		Path path = Mockito.mock(Path.class);
		when(path.getParent()).thenReturn(null);
		when(path.resolve(Mockito.anyString()))
			.thenReturn(path).thenReturn(null)
			.thenReturn(path).thenReturn(null)
			.thenReturn(path).thenReturn(null);
		CoverageHitRecorder recorder = new CoverageHitAPIFile(path);
		assertThrows(NullPointerException.class, () -> recorder.recordPolicySetHit(new PolicySetHit("")));
	}
	
}
