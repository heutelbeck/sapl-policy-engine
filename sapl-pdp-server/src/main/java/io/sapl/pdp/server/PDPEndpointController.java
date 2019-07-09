package io.sapl.pdp.server;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.sapl.api.pdp.PolicyDecisionPoint;
import io.sapl.api.pdp.Request;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/pdp")
@RequiredArgsConstructor
public class PDPEndpointController {

	private final PolicyDecisionPoint pdp;

	@PostMapping(value = "/decide", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<Response> decide(@RequestBody Request request) {
		return pdp.decide(request).onErrorResume(error -> Flux.just(Response.INDETERMINATE));
	}

	@PostMapping(value = "/multi-decide",
			produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<IdentifiableResponse> decide(@RequestBody MultiRequest request) {
		return pdp.decide(request).onErrorResume(error -> Flux.just(IdentifiableResponse.INDETERMINATE));
	}

	@PostMapping(value = "/multi-decide-all",
			produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<MultiResponse> decideAll(@RequestBody MultiRequest request) {
		return pdp.decideAll(request).onErrorResume(error -> Flux.just(MultiResponse.indeterminate()));
	}

}
