package io.sapl.test.steps;

/**
 * Composite Step to allow repeating of {@link ExpectStep} or go over to a {@link VerifyStep}
 */
public interface ExpectOrVerifyStep extends ExpectStep, VerifyStep {
}