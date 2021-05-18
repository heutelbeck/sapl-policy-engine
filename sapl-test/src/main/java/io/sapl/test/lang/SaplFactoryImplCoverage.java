package io.sapl.test.lang;

import io.sapl.grammar.sapl.Policy;
import io.sapl.grammar.sapl.PolicyBody;
import io.sapl.grammar.sapl.PolicySet;
import io.sapl.grammar.sapl.impl.SaplFactoryImpl;
import io.sapl.test.coverage.api.CoverageHitRecorder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SaplFactoryImplCoverage extends SaplFactoryImpl {

	private CoverageHitRecorder recorder;
	
	public SaplFactoryImplCoverage(CoverageHitRecorder recorder) {
		this.recorder = recorder;
	}
	
	@Override
	public PolicySet createPolicySet()
	{
		log.trace("Creating PolicySet Subclass for test mode");
		PolicySetImplCustomCoverage policySet = new PolicySetImplCustomCoverage(this.recorder);
		return policySet;
	}

	@Override
	public Policy createPolicy()
	{
		log.trace("Creating Policy Subclass for test mode");
		PolicyImplCustomCoverage policy = new PolicyImplCustomCoverage(this.recorder);
		return policy;
	}

	@Override
	public PolicyBody createPolicyBody()
	{
		log.trace("Creating PolicyBody Subclass for test mode");
		PolicyBodyImplCustomCoverage body = new PolicyBodyImplCustomCoverage(this.recorder);
		return body;
	}

}