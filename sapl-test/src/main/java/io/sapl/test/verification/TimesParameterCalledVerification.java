package io.sapl.test.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import org.hamcrest.Matcher;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.verification.MockRunInformation.CallWithMetadata;

/**
 * Verify that this mock was called n times.
 *
 */
public class TimesParameterCalledVerification implements MockingVerification {
	List<Matcher<Val>> wantedArgs;
	TimesCalledVerification verification;
	 
	public TimesParameterCalledVerification(TimesCalledVerification verification, List<Matcher<Val>> wantedArgs) {
		 this.verification = verification;
		 this.wantedArgs = wantedArgs;
	}

	@Override
	public void verify(MockRunInformation mockRunInformation) {
		this.verify(mockRunInformation, null);	
	}
	

	@Override
	public void verify(MockRunInformation mockRunInformation, String verificationFailedMessage) {
		//collect calls with specified information in new MockRunInformation
		MockRunInformation callsForParameter = new MockRunInformation(mockRunInformation.getFullname());
		
		//iterate over all calls to the function during test execution
		for(int i = 0; i < mockRunInformation.getCalls().size(); i++) {
			CallWithMetadata call = mockRunInformation.getCalls().get(i);
			
			//is this call already used on another TimesParameterCalledVerification -> don't use it
			if(call.isUsed()) {
				continue;
			}
			
			//count call if every call-argument matches Val-argument matcher
			boolean callMatchesArgs = listCombiner(this.wantedArgs, call.getCall().getListOfArguments(), 
					(Matcher<Val> wanted, Val actual) -> wanted.matches(actual))
					.stream().allMatch(b -> b == true);
			
			if(callMatchesArgs) {
				callsForParameter.saveCall(call.getCall());
				mockRunInformation.getCalls().get(i).setUsed(true);
			}
		}
		
		//construct error string
		String assertErrorMessage = "";
		if(verificationFailedMessage != null && !verificationFailedMessage.isEmpty()) {
			assertErrorMessage = verificationFailedMessage;
		} else {
			assertErrorMessage = constructErrorMessage(mockRunInformation.getFullname(), this.wantedArgs);
		}
		
		this.verification.verify(callsForParameter, assertErrorMessage); 
		
	}
	
	
	private String constructErrorMessage(String fullname, Iterable<Matcher<Val>> wantedArgs) {
		StringBuilder builder = new StringBuilder("Error verifiying the expected number of calls to the mock \"" + fullname + "\" for parameters [");
		
		for(Matcher<Val> matcher : wantedArgs) {
			builder.append(matcher.toString() + ", ");
		}
		
		builder.deleteCharAt(builder.length() - 1);
		builder.append(']');
		
		return builder.toString();
	}
	
	private <T, U, R> List<R> listCombiner(List<T> list1, List<U> list2, BiFunction<T, U, R> combiner) {
		if(list1.size() != list2.size()) {
			throw new SaplTestException("Number of parameters in the function call is not equals the number of provided parameter matcher!");
		}
		
	    List<R> result = new ArrayList<>(list1.size());
	    for (int i = 0; i < list1.size(); i++) {
	        result.add(combiner.apply(list1.get(i), list2.get(i)));
	    }
	    return result;
	}
}
