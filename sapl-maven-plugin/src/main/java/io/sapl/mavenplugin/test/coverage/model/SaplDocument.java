package io.sapl.mavenplugin.test.coverage.model;

import java.nio.file.Path;

import io.sapl.grammar.sapl.SAPL;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class SaplDocument {

	private Path pathToDocument;
	int lineCount;
	private SAPL saplDocument;
	
}
