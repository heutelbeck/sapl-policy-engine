package io.sapl.interpreter.pip;

public interface LibraryEntryMetadata {

	String getLibraryName();

	String getFunctionName();

	String getCodeTemplate();
	
	String getDocumentationCodeTemplate();
	
	default String fullyQualifiedName() {
		return getLibraryName() + '.' + getFunctionName();
	}
}