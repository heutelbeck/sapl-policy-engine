package io.sapl.test.steps;

public class NumberOfExpectSteps {
	private int number;
	
	public NumberOfExpectSteps() {
		this.number = 0;
	}
	
	public int getNumberOfExpectSteps() {
		return this.number;
	}
	
	public void addExpectStep() {
		this.number++;
	}
	
}
