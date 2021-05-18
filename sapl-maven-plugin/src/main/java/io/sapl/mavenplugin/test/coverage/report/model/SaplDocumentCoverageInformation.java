package io.sapl.mavenplugin.test.coverage.report.model;

import java.nio.file.Path;

/**
 * Generic model with coverage information of type {@link LineCoveredValue} for each line of a SAPL document *
 */
public class SaplDocumentCoverageInformation {

	/**
	 * Holding detailed information for every line
	 */
	private SaplDocumentLineCoverageInformation[] lines;
	/**
	 * Path to the SAPL policy on the filesystem
	 */
	private Path pathToDocument;

	/**
	 * initialize a {@link SaplDocumentCoverageInformation} with 
	 * @param pathToDocument path to the document on the filesystem
	 * @param lineCount line count
	 */
	public SaplDocumentCoverageInformation(Path pathToDocument, int lineCount) {
		this.pathToDocument = pathToDocument;
		this.lines = new SaplDocumentLineCoverageInformation[lineCount];
		for(int i = 0; i < lineCount; i++) {
			lines[i] = new SaplDocumentLineCoverageInformation(i+1);
		}
	}
	
	/**
	 * mark the line as one of {@link LineCoveredValue}
	 * @param lineNumber (starting with 1)
	 * @param value {@link LineCoveredValue}
	 * @param coveredBranches covered branches of this line
	 * @param branchesToCover branches to cover of this line
	 */
	public void markLine(int lineNumber, LineCoveredValue value, int coveredBranches, int branchesToCover) {
		lines[lineNumber-1].setCoveredValue(value, coveredBranches, branchesToCover);
	}
	
	
	/**
	 * Get line coverage information for a line in this document
	 * @param lineNumber (starting with 1)
	 * @return the {@link SaplDocumentLineCoverageInformation} for this line
	 */
	public SaplDocumentLineCoverageInformation getLine(int lineNumber) {
		return this.lines[lineNumber-1];
	}
	
	public Path getPathToDocument() {
		return this.pathToDocument;
	}
	
	public int getLineCount() {
		return this.lines.length;
	}
}
