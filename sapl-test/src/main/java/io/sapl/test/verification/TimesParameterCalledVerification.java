package io.sapl.test.verification;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiFunction;

import io.sapl.api.interpreter.Val;
import io.sapl.test.SaplTestException;
import io.sapl.test.verification.MockRunInformation.CallWithMetadata;

import org.hamcrest.Matcher;

/**
 * Verify that this mock was called n times with the specified list of Matcher<Val>.
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
		MockRunInformation callsMatchingWantedArgs = new MockRunInformation(mockRunInformation.getFullname());
		
		for(int i = 0; i < mockRunInformation.getCalls().size(); i++) {
			CallWithMetadata call = mockRunInformation.getCalls().get(i);
			
			//is this call already used on another TimesParameterCalledVerification -> don't use it
			if(call.isUsed()) {
				continue;
			}
			
			boolean callMatchesArgs = areAllCallArgumentsMatchingTheArgumentMatcher(call);
			
			if(callMatchesArgs) {
				callsMatchingWantedArgs.saveCall(call.getCall());
				mockRunInformation.getCalls().get(i).setUsed(true);
			}
		}
				
		this.verification.verify(callsMatchingWantedArgs, constructErrorMessage(verificationFailedMessage, callsMatchingWantedArgs, this.wantedArgs, this.verification)); 
		
	}

	private String constructErrorMessage(String verificationFailedMessage, MockRunInformation callsMatchingWantedArgs, Iterable<Matcher<Val>> wantedArgs, TimesCalledVerification verification) {

		if(verificationFailedMessage != null && !verificationFailedMessage.isEmpty()) {
			return verificationFailedMessage;
		}
		
		StringBuilder builder = new StringBuilder("Error verifiying the expected number of calls to the mock \"" + callsMatchingWantedArgs.getFullname() + "\" for parameters [");
		
		for(Matcher<Val> matcher : wantedArgs) {
			builder.append(matcher.toString() + ", ");
		}
		
		builder.deleteCharAt(builder.length() - 1);
		builder.append(']');
		
		builder.append(" - Expected: " + verification.toString() + " - got: " + callsMatchingWantedArgs.getTimesCalled());
		
		return builder.toString();
	}
	
	private boolean areAllCallArgumentsMatchingTheArgumentMatcher(CallWithMetadata call) {
		return listCombiner(this.wantedArgs, call.getCall().getListOfArguments(), 
				(Matcher<Val> wanted, Val actual) -> wanted.matches(actual))
				.stream().allMatch(b -> b == true);
	}
	
	private List<Boolean> listCombiner(List<Matcher<Val>> list1, List<Val> list2, BiFunction<Matcher<Val>, Val, Boolean> combiner) {
		if(list1.size() != list2.size()) {
			throw new SaplTestException("Number of parameters in the mock call is not equals the number of provided parameter matcher!");
		}
		
	    List<Boolean> result = new ArrayList<>(list1.size());
	    for (int i = 0; i < list1.size(); i++) {
	        result.add(combiner.apply(list1.get(i), list2.get(i)));
	    }
	    return result;
	}
}
