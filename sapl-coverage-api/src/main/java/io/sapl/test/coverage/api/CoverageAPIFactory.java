package io.sapl.test.coverage.api;

import java.nio.file.Path;

import lombok.experimental.UtilityClass;

@UtilityClass
public class CoverageAPIFactory {

	/**
	 * Constructs a CoverageHitRecorder implementation.
	 * @param basedir where to find the hit files
	 * @return {@link CoverageHitReader}
	 */
	public static CoverageHitReader constructCoverageHitReader(Path basedir) {
		return new CoverageHitAPIFile(basedir);
	}
	
	/**
	 * Constructs a CoverageHitRecorder implementation and create empty Coverage-Hit-Files if they don't exist
	 * @param basedir where to write the hit files
	 * @return {@link CoverageHitRecorder}
	 */
	public static CoverageHitRecorder constructCoverageHitRecorder(Path basedir) {
		var recorder = new CoverageHitAPIFile(basedir);
		recorder.createCoverageHitFiles();
		return recorder;
	}
}
