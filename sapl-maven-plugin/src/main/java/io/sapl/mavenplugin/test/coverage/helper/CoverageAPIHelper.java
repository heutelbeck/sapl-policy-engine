package io.sapl.mavenplugin.test.coverage.helper;

import java.nio.file.Path;

import javax.inject.Named;
import javax.inject.Singleton;

import io.sapl.mavenplugin.test.coverage.model.CoverageTargets;
import io.sapl.test.coverage.api.CoverageAPIFactory;

@Named
@Singleton
public class CoverageAPIHelper {
	
	public CoverageTargets readHits(Path baseDir) {
		var reader = CoverageAPIFactory.constructCoverageHitReader(baseDir);
		return new CoverageTargets(reader.readPolicySetHits(), reader.readPolicyHits(), reader.readPolicyConditionHits());
	}
}
