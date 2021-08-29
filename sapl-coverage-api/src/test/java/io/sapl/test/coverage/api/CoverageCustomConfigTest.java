package io.sapl.test.coverage.api;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.sapl.test.coverage.api.model.PolicySetHit;

public class CoverageCustomConfigTest {
	
	private Path basedir;
	private CoverageHitReader reader;
	private CoverageHitRecorder recorder;
	
	@BeforeEach
	void setup() {
		basedir = Paths.get("temp").resolve("sapl-coverage");
		this.reader = new CoverageHitAPIFile(basedir);
		this.recorder = new CoverageHitAPIFile(basedir);
	}
	
	@AfterEach
	void cleanup() {
		TestFileHelper.deleteDirectory(basedir.toFile());
	}
	
	
	@Test
	void testSystemPropertyConfig_Reader() throws IOException {
		Path path = this.basedir.resolve("hits").resolve("_policySetHits.txt");
		if(!Files.exists(path)) {
			if(path.getParent() != null) {
				Files.createDirectories(path.getParent());
			}
			Files.createFile(path);
		}
		Files.write(
				  path, 
			      (new PolicySetHit("set1").toString() + System.lineSeparator()).getBytes(), 
			      StandardOpenOption.APPEND);
		
			
		//act
		List<PolicySetHit> resultPolicySetHits = reader.readPolicySetHits();
		
		//assert
		Assertions.assertThat(resultPolicySetHits.size()).isEqualTo(1);
	    Assertions.assertThat(resultPolicySetHits.get(0).getPolicySetId()).isEqualTo("set1");
	}
	
	@Test
	void testSystemPropertyConfig_Recorder() throws IOException {		
		//act
		recorder.createCoverageHitFiles();
		
		//assert
		Path path = this.basedir.resolve("hits").resolve("_policySetHits.txt");
		Assertions.assertThat(Files.exists(path)).isTrue();
		
	}
}
