package io.sapl.mavenplugin.test.coverage.helper;

import java.nio.file.Path;

import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.test.coverage.api.CoverageAPIFactory;
import io.sapl.test.coverage.api.CoverageHitReader;

public class CoverageAPIHelper {
	
	private final CoverageHitReader reader;
	
	public CoverageAPIHelper(Path baseDir) {
		this.reader = CoverageAPIFactory.constructCoverageHitReader(baseDir);
	}
	
	public CoverageTargets readHits() {
		return new CoverageTargets(reader.readPolicySetHits(), reader.readPolicyHits(), reader.readPolicyConditionHits());
	}
	
	public void cleanCoverageHitFiles() {
    	this.reader.cleanCoverageHitFiles();
	}
	

}
