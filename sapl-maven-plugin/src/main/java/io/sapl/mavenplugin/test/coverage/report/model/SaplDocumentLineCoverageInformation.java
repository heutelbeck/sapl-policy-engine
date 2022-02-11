/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.mavenplugin.test.coverage.report.model;

import lombok.Getter;

@Getter
public class SaplDocumentLineCoverageInformation {

	private final int lineNumber;

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
