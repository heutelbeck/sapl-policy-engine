package io.sapl.test.steps;

import static io.sapl.hamcrest.Matchers.anyVal;
import static io.sapl.test.Imports.*;

import java.util.HashMap;
import java.util.Map;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.api.pdp.Decision;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.functions.FunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.test.SaplTestException;
import io.sapl.test.mocking.MockingAttributeContext;
import io.sapl.test.mocking.MockingFunctionContext;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class StepsDefaultImplTest {
	
		private AttributeContext attrCtx;
		private FunctionContext funcCtx;
		private Map<String, JsonNode> variables;
		
		private String Policy_SimpleFunction = "policy \"policyWithSimpleFunction\"\r\n"
				+ "permit\r\n"
				+ "    action == \"read\"\r\n"
				+ "where\r\n"
				+ "    time.dayOfWeek(\"2021-02-08T16:16:33.616Z\") =~ \"MONDAY|TUESDAY|WEDNESDAY|THURSDAY|FRIDAY|SATURDAY|SUNDAY\";";
		
		private String Policy_Streaming_Permit = "import io.sapl.pip.ClockPolicyInformationPoint as clock\r\n"
				+ "import io.sapl.functions.TemporalFunctionLibrary as time\r\n"
				+ "\r\n"
				+ "\r\n"
				+ "policy \"policyStreaming\"\r\n"
				+ "permit\r\n"
				+ "  resource == \"heartBeatData\"\r\n"
				+ "where\r\n"
				+ "  subject == \"ROLE_DOCTOR\";\r\n"
				+ "  var interval = 2;\r\n"
				+ "  time.localSecond(<clock.ticker(interval)>) > 4;";
		
		
		private String Policy_Streaming_Deny = "import io.sapl.pip.ClockPolicyInformationPoint as clock\r\n"
				+ "import io.sapl.functions.TemporalFunctionLibrary as time\r\n"
				+ "\r\n"
				+ "\r\n"
				+ "policy \"policyStreaming\"\r\n"
				+ "deny\r\n"
				+ "  resource == \"heartBeatData\"\r\n"
				+ "where\r\n"
				+ "  subject == \"ROLE_DOCTOR\";\r\n"
				+ "  var interval = 2;\r\n"
				+ "  time.localSecond(<clock.ticker(interval)>) > 4;";
		
		private String Policy_Indeterminate = "policy \"policy division by zero\"\r\n"
				+ "permit\r\n"
				+ "where\r\n"
				+ "    17 / 0;";
		
		
		private String Policy_Streaming_Indeterminate = "import io.sapl.pip.ClockPolicyInformationPoint as clock\r\n"
				+ "import io.sapl.functions.TemporalFunctionLibrary as time\r\n"
				+ "\r\n"
				+ "\r\n"
				+ "policy \"policyStreaming\"\r\n"
				+ "permit\r\n"
				+ "  resource == \"heartBeatData\"\r\n"
				+ "where\r\n"
				+ "  subject == \"ROLE_DOCTOR\";\r\n"
				+ "  var interval = 2;\r\n"
				+ "  time.localSecond(<clock.ticker(interval)>) > 4;"
				+ "  17 / 0;";
	
		@BeforeEach
	    void setUp() {
			this.attrCtx = new MockingAttributeContext(Mockito.mock(AnnotationAttributeContext.class));
			this.funcCtx = new MockingFunctionContext(Mockito.mock(AnnotationFunctionContext.class));
			this.variables = new HashMap<>();
	    }


	    @Test
	    void test_mockFunction_withParameters_withTimesVerification() throws InitializationException {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY"), whenParameters(anyVal()), times(1))
                .when(AuthorizationSubscription.of("willi", "read", "something"))
                .expectPermit()
                .verify();
	    }
	    
	    @Test
	    void test_mockFunction_Function_withTimesVerification() throws InitializationException {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        steps.givenFunction("time.dayOfWeek", (call) -> Val.of("SATURDAY"), times(1))
                .when(AuthorizationSubscription.of("willi", "read", "something"))
                .expectPermit()
                .verify();
	    }
	    
	    
	    @Test
	    void test_when_fromJsonString() throws InitializationException, JsonProcessingException {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
	        	.when("{\"subject\":\"willi\", \"action\":\"read\", \"resource\":\"something\", \"environment\":{}}")
                .expectPermit()
                .verify();
	    }

	    
	    @Test
	    void test_when_fromJsonNode() throws InitializationException, JsonProcessingException {
	    	ObjectMapper mapper = new ObjectMapper();
	    	JsonNode authzSub = mapper.createObjectNode().put("subject", "willi").put("action",  "read").put("resource", "something").put("environment", "test");
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
        		.when(authzSub)
                .expectPermit()
                .verify();
	    }
	    
	    @Test
	    void test_when_fromJsonNode_null() throws InitializationException, JsonProcessingException {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        Assertions.assertThatExceptionOfType(SaplTestException.class)
	    		.isThrownBy(() -> steps.when((JsonNode)null));
	    }
	    
	    @Test
	    void test_expectIndeterminate() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_Indeterminate, attrCtx, funcCtx, variables);
	        steps.when(AuthorizationSubscription.of("willi", "read", "something"))
	            .expectIndeterminate()
	            .verify();
	    }
	    
	    
	    @Test
	    void test_expectNextPermit_XTimes_0() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        Assertions.assertThatExceptionOfType(SaplTestException.class)
	    		.isThrownBy(() -> steps.when(AuthorizationSubscription.of("willi", "read", "something"))
    					.expectNextPermit(0));
	    }
	    
	    @Test
	    void test_expectNextPermit_XTimes_Greater1() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_Streaming_Permit, attrCtx, funcCtx, variables);
	        steps.givenAttribute("clock.ticker", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
            	.givenFunctionOnce("time.localSecond", Val.of(5), Val.of(6), Val.of(7))
            	.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
				.expectNextPermit(3)
				.verify();
	    }
	    
	    @Test
	    void test_expectNextDeny_XTimes_0() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        Assertions.assertThatExceptionOfType(SaplTestException.class)
	    		.isThrownBy(() -> steps.when(AuthorizationSubscription.of("willi", "read", "something"))
    					.expectNextDeny(0));
	    }
	    
	    @Test
	    void test_expectNextDeny_XTimes_Greater1() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_Streaming_Deny, attrCtx, funcCtx, variables);
	        steps.givenAttribute("clock.ticker", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
	        	.givenFunctionOnce("time.localSecond", Val.of(5), Val.of(6), Val.of(7))
	        	.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
				.expectNextDeny(3)
				.verify();
	    }
	    
	    @Test
	    void test_expectNextIndeterminate_XTimes_0() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        Assertions.assertThatExceptionOfType(SaplTestException.class)
	    		.isThrownBy(() -> steps.when(AuthorizationSubscription.of("willi", "read", "something"))
    					.expectNextIndeterminate(0));
	    }
	    
	    @Test
	    void test_expectNextIndeterminate_XTimes_Greater1() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_Streaming_Indeterminate, attrCtx, funcCtx, variables);
	        steps.givenAttribute("clock.ticker", Val.of("value"), Val.of("doesn't"), Val.of("matter"))
        		.givenFunctionOnce("time.localSecond", Val.of(5), Val.of(6), Val.of(7))
	        	.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "heartBeatData"))
				.expectNextIndeterminate(3)
				.verify();
	    }
	    
	    
	    @Test
	    void test_expectNextNotApplicable_XTimes_0() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        Assertions.assertThatExceptionOfType(SaplTestException.class)
	    		.isThrownBy(() -> steps.when(AuthorizationSubscription.of("willi", "read", "something"))
    					.expectNextNotApplicable(0));
	    }
	    
	    
	    @Test
	    void test_expectNextNotApplicable_XTimes_Greater1() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_Streaming_Permit, attrCtx, funcCtx, variables);
	        steps.when(AuthorizationSubscription.of("ROLE_DOCTOR", "read", "somethingDifferent"))
				.expectNextNotApplicable(1)
				.verify();
	    }
	    
	    
	    @Test
	    void test_expectNextIndeterminate() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_Indeterminate, attrCtx, funcCtx, variables);
	        steps.when(AuthorizationSubscription.of("willi", "read", "something"))
	            .expectNextIndeterminate()
	            .verify();
	    }
	    
	    @Test
	    void test_expectNext_Equals() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
				.when(AuthorizationSubscription.of("will", "read", "something"))
				.expectNext(AuthorizationDecision.PERMIT)
				.verify();
	    }
	    
	    @Test
	    void test_expectNext_Predicate() {
	    	StepsDefaultImpl steps = new StepsDefaultImplTestImpl(Policy_SimpleFunction, attrCtx, funcCtx, variables);
	        steps.givenFunction("time.dayOfWeek", Val.of("SATURDAY"))
				.when(AuthorizationSubscription.of("will", "read", "something"))
				.expectNext((authzDec) -> authzDec.getDecision().equals(Decision.PERMIT))
				.verify();
	    }

}
