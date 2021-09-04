package io.sapl.test.integration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.grammar.sapl.impl.DenyUnlessPermitCombiningAlgorithmImplCustom;
import io.sapl.pdp.config.PolicyDecisionPointConfiguration;
import reactor.core.publisher.SignalType;

class ClasspathVariablesAndCombinatorSourceTest {

	@Test
	void doTest() throws InterruptedException {
		var configProvider = new ClasspathVariablesAndCombinatorSource("policiesIT", new ObjectMapper(), null, null);
		Assertions.assertThat(configProvider.getCombiningAlgorithm().blockFirst().get()).isInstanceOf(DenyUnlessPermitCombiningAlgorithmImplCustom.class);
		Assertions.assertThat(configProvider.getVariables().log(null, Level.INFO, SignalType.ON_NEXT).blockFirst().get().keySet().size()).isZero();
		configProvider.dispose();
	}
	
	@Test
	void test_nullPath() {
		Assertions.assertThatNullPointerException().isThrownBy(() -> new ClasspathVariablesAndCombinatorSource(null, new ObjectMapper(), null, null));
	}
	
	@Test
	void test_nullObjectMapper() {
		Assertions.assertThatNullPointerException().isThrownBy(() -> new ClasspathVariablesAndCombinatorSource("", null, null, null));
	}
	
	@Test
	void test_IOException() throws JsonParseException, JsonMappingException, IOException {
		ObjectMapper mapper = Mockito.mock(ObjectMapper.class);
		Mockito.when(mapper.readValue((File)Mockito.any(),  Mockito.<Class<PolicyDecisionPointConfiguration>>any())).thenThrow(new IOException());
		Assertions.assertThatExceptionOfType(RuntimeException.class).isThrownBy(() -> new ClasspathVariablesAndCombinatorSource("policiesIT", mapper, null, null))
			.withCauseInstanceOf(IOException.class);
	}
}
