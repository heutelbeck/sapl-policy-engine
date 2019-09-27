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

/**
 * REST controller providing endpoints for a policy decision point. The endpoints can be
 * connected using the client {@link io.sapl.pdp.remote.RemotePolicyDecisionPoint} in the
 * module sapl-pdp-client.
 */
@RestController
@RequestMapping("/api/pdp")
@RequiredArgsConstructor
public class PDPEndpointController {

	private final PolicyDecisionPoint pdp;

	/**
	 * Delegates to {@link PolicyDecisionPoint#decide(Request)}.
	 * @param request the authorization request to be processed by the PDP.
	 * @return a flux emitting the current authorization responses.
	 * @see PolicyDecisionPoint#decide(Request)
	 */
	@PostMapping(value = "/decide", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<Response> decide(@RequestBody Request request) {
		return pdp.decide(request).onErrorResume(error -> Flux.just(Response.INDETERMINATE));
	}

	/**
	 * Delegates to {@link PolicyDecisionPoint#decide(MultiRequest)}.
	 * @param multiRequest the authorization multi-request to be processed by the PDP.
	 * @return a flux emitting authorization responses related to the individual requests
	 * contained in the given {@code multiRequest} as soon as they are available.
	 * @see PolicyDecisionPoint#decide(MultiRequest)
	 */
	@PostMapping(value = "/multi-decide", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<IdentifiableResponse> decide(@RequestBody MultiRequest multiRequest) {
		return pdp.decide(multiRequest).onErrorResume(error -> Flux.just(IdentifiableResponse.INDETERMINATE));
	}

	/**
	 * Delegates to {@link PolicyDecisionPoint#decideAll(MultiRequest)}.
	 * @param multiRequest the authorization multi-request to be processed by the PDP.
	 * @return a flux emitting multi-responses containing responses for all the individual
	 * requests contained in the given {@code multiRequest}.
	 * @see PolicyDecisionPoint#decideAll(MultiRequest)
	 */
	@PostMapping(value = "/multi-decide-all", produces = MediaType.APPLICATION_STREAM_JSON_VALUE)
	public Flux<MultiResponse> decideAll(@RequestBody MultiRequest multiRequest) {
		return pdp.decideAll(multiRequest).onErrorResume(error -> Flux.just(MultiResponse.indeterminate()));
	}

}
