package io.sapl.test.verification;

import org.assertj.core.api.Assertions;
import org.hamcrest.Matcher;

/**
 * Verify that this mock was called n times.
 *
 */
public class TimesCalledVerification implements MockingVerification {
	private static final String ERROR_TIMES_VERIFICATION = "Error verifiying the expected number of calls to the mock \"%s\": ";
		
	Matcher<Integer> matcher;
	 
	public TimesCalledVerification(Matcher<Integer> matcher) {
		 this.matcher = matcher;
	}

	@Override
	public void verify(MockRunInformation mockRunInformation) {
		this.verify(mockRunInformation, null);
	}

	@Override
	public void verify(MockRunInformation mockRunInformation, String verificationFailedMessage) {
		
		String message = "";
		if(verificationFailedMessage != null && !verificationFailedMessage.isEmpty()) {
			message = verificationFailedMessage;
		} else {
			message = String.format(ERROR_TIMES_VERIFICATION, mockRunInformation.getFullname());
		}
		
		Assertions.assertThat(this.matcher.matches(mockRunInformation.getTimesCalled())).isTrue().as(message);
	}
	
	public Matcher<Integer> getWantedInvocations() {
		return this.matcher;
	}
}
