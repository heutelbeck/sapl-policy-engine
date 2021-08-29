package io.sapl.test.integration;

import java.util.logging.Level;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import reactor.core.publisher.SignalType;

class ClasspathVariablesAndCombinatorSourceTest {

	@Test
	void doTest() throws InterruptedException {
		var configProvider = new ClasspathVariablesAndCombinatorSource("policiesIT", new ObjectMapper(), null, null);
		configProvider.getCombiningAlgorithm().log(null, Level.INFO, SignalType.ON_NEXT).blockFirst();
		configProvider.getVariables().log(null, Level.INFO, SignalType.ON_NEXT).blockFirst();
		configProvider.dispose();
	}
}
