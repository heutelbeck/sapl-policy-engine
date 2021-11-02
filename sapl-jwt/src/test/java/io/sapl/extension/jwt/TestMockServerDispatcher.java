package io.sapl.extension.jwt;

import java.util.Map;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.RecordedRequest;

public class TestMockServerDispatcher extends Dispatcher {

	private final String kidPath;
	private final Map<String, String> kidToPubKeyMap;
	
	public TestMockServerDispatcher(String kidPath, Map<String, String> kidToPubKeyMap) {
		this.kidPath = kidPath;
		this.kidToPubKeyMap = kidToPubKeyMap;
	}
	
	@Override
	public MockResponse dispatch(RecordedRequest request) throws InterruptedException {
		
		if (!request.getPath().startsWith(kidPath))
			return new MockResponse().setResponseCode(404);
		
		String requestedId = request.getPath().substring(kidPath.length());
		
		if (!kidToPubKeyMap.containsKey(requestedId))
			return new MockResponse().setResponseCode(404);
		
		return new MockResponse().setBody(kidToPubKeyMap.get(requestedId));
		
	}
	
}
