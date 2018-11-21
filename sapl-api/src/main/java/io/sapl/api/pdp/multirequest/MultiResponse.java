package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;
import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_EMPTY;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.sapl.api.pdp.Response;

public class MultiResponse implements Iterable<IdentifiableResponse> {

    @JsonInclude(NON_EMPTY)
    private Map<String, Response> responses = new HashMap<>();

    public static MultiResponse indeterminate() {
        final MultiResponse multiResponse = new MultiResponse();
        multiResponse.setResponseForRequestWithId("", Response.indeterminate());
        return multiResponse;
    }

    public int size() {
        return responses.size();
    }

    public void setResponseForRequestWithId(String requestId, Response response) {
        requireNonNull(requestId, "requestId must not be null");
        requireNonNull(response, "response must not be null");
        responses.put(requestId, response);
    }

    public Response getResponseForRequestWithId(String requestId) {
        requireNonNull(requestId, "requestId must not be null");
        return responses.get(requestId);
    }

    @Override
    public Iterator<IdentifiableResponse> iterator() {
        final Iterator<Map.Entry<String, Response>> responseIterator = responses.entrySet().iterator();
        return new Iterator<IdentifiableResponse>() {
            @Override
            public boolean hasNext() {
                return responseIterator.hasNext();
            }

            @Override
            public IdentifiableResponse next() {
                final Map.Entry<String, Response> responseEntry = responseIterator.next();
                return new IdentifiableResponse(responseEntry.getKey(), responseEntry.getValue());
            }
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }
        final MultiResponse other = (MultiResponse) obj;

        final Map<String, Response> otherResponses = other.responses;
        if (responses.size() != otherResponses.size()) {
            return false;
        }

        final Set<String> thisKeys = responses.keySet();
        final Set<String> otherKeys = otherResponses.keySet();
        for (String key : thisKeys) {
            if (! otherKeys.contains(key)) {
                return false;
            }
            if (! Objects.equals(responses.get(key), otherResponses.get(key))) {
                return false;
            }
        }

        return true;
    }

    @Override
    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        for (Response response : responses.values()) {
            result = result * PRIME + (response == null ? 43 : response.hashCode());
        }
        return result;
    }
}
