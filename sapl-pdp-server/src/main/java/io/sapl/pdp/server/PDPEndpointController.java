package io.sapl.pdp.server;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/pdp")
public class PDPEndpointController {

    @Autowired
    private PolicyDecisionPoint pdp;

    @PostMapping(value = "/decide", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
    public Flux<Response> decide(@RequestBody Request request) {
        return pdp.decide(request);
    }

}
