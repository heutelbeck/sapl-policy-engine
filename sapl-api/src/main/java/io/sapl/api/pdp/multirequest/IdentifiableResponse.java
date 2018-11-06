package io.sapl.api.pdp.multirequest;

import io.sapl.api.pdp.Response;
import lombok.Value;

@Value
public class IdentifiableResponse {

    private String requestId;
    private Response response;

    public static IdentifiableResponse indeterminate() {
        return new IdentifiableResponse(null, Response.indeterminate());
    }

}
