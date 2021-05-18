package io.sapl.test.verification;

public interface MockingVerification {
	
	void verify(MockRunInformation mockRunInformation);
	
	void verify(MockRunInformation mockRunInformation, String verificationFailedMessage);
	
}
