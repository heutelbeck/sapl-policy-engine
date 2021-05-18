package io.sapl.test.steps;


/**
 * Composite Step to allow repeating of {@link WhenStep} or go over to a {@link GivenStep}
 */
public interface GivenOrWhenStep extends GivenStep, WhenStep {
}