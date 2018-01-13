package io.sapl.api.interpreter;

import lombok.Value;

@Value
public class DocumentAnalysisResult {
	boolean valid;
	String name;
	DocumentType type;
	String parserError;
}
