package io.sapl.test.verification;

import java.util.LinkedList;
import java.util.List;

import io.sapl.test.mocking.FunctionCall;
import lombok.AllArgsConstructor;
import lombok.Data;

public class MockRunInformation {
	private final String fullname;
	private List<CallWithMetadata> timesCalled;
	
	public MockRunInformation(String fullname) {
		this.fullname = fullname;
		this.timesCalled = new LinkedList<>();
	}
	
	public String getFullname() {
		return this.fullname;
	}
	
	public int getTimesCalled() {
		return timesCalled.size();
	}
	
	public List<CallWithMetadata> getCalls() {
		return this.timesCalled;
	}
	
	public void saveCall(FunctionCall call) {
		this.timesCalled.add(new CallWithMetadata(false, call));
	}
	
	
	@Data
	@AllArgsConstructor
	static class CallWithMetadata {
		private boolean isUsed;
		private FunctionCall call; 
	}
}
