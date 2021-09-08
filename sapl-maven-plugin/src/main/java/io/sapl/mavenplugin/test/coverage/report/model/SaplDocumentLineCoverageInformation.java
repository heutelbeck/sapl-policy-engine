package io.sapl.mavenplugin.test.coverage.report.model;

import lombok.Getter;

@Getter
public class SaplDocumentLineCoverageInformation {

	private int lineNumber;
	private LineCoveredValue coveredValue;
	private int branchesToCover;
	private int coveredBranches;

	public SaplDocumentLineCoverageInformation(int lineNumber) {
		this.lineNumber = lineNumber;
		this.coveredValue = LineCoveredValue.IRRELEVANT;
		this.branchesToCover = 0;
		this.coveredBranches = 0;
	}
	
	public void setCoveredValue(LineCoveredValue value, int coveredBranches, int branchesToCover) {
		this.coveredValue = value;
		this.coveredBranches = coveredBranches;
		this.branchesToCover = branchesToCover;
	}
}
