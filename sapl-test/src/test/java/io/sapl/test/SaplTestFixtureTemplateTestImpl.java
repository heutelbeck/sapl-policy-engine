package io.sapl.test;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.test.steps.GivenStep;
import io.sapl.test.steps.WhenStep;

public class SaplTestFixtureTemplateTestImpl extends SaplTestFixtureTemplate {

	@Override
	public GivenStep constructTestCaseWithMocks() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public WhenStep constructTestCase() {
		// TODO Auto-generated method stub
		return null;
	}
	
	public Map<String, JsonNode> getVariablesMap() {
		return this.variables;
	}
}
