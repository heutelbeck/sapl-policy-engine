package io.sapl.api.pdp.multirequest;

import static java.util.Objects.requireNonNull;

import io.sapl.api.pdp.Request;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class IdentifiableRequest implements Identifiable {

    private String id;
    private Request request;

    public IdentifiableRequest(String id, Request request) {
        requireNonNull(id, "id must not be null");
        requireNonNull(request, "request must not be null");

        this.id = id;
        this.request = request;
    }

}
