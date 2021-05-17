package io.sapl.test.coverage.api;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class CoverageAPIFactoryTest {

	@AfterEach
	void cleanup() {
		TestFileHelper.deleteDirectory(Paths.get("target/tmp").toFile());
	}
	
	@Test
	void test() throws IOException {

		Path hitDir = Paths.get("target/tmp/hits");
		
		Assertions.assertThat(countFilesInDir(hitDir)).isEqualTo(0);
		
		CoverageAPIFactory.constructCoverageHitRecorder(Paths.get("target/tmp"));

		Assertions.assertThat(countFilesInDir(hitDir)).isEqualTo(3);
	}
	
	private int countFilesInDir(Path path) throws IOException {
		int count = 0;
		if(!path.toFile().exists()) {
			return count;
		}
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(path, "*.txt")) {
			Iterator<Path> it = stream.iterator();
			while(it.hasNext()) {
				it.next();
				count++;
			}
		}
		return count;
	}

	
	@Test
	void test_reader() {
		var object = CoverageAPIFactory.constructCoverageHitReader(Paths.get(""));
		Assertions.assertThat(object).isNotNull();
	}
}
