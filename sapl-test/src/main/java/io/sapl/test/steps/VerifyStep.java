package io.sapl.test.steps;


/**
 * This is the final step in charge of executing the test case and verifying the results.
 */
public interface VerifyStep {
	/**
	 * Executes the test case and verifies expectations.
	 */
    void verify();
}