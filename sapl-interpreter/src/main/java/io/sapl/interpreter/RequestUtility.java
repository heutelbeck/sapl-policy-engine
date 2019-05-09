package io.sapl.interpreter;

import java.util.Set;
import java.util.concurrent.CountDownLatch;

import io.sapl.api.pdp.Decision;
import io.sapl.api.pdp.Response;
import io.sapl.api.pdp.multirequest.IdentifiableDecision;
import io.sapl.api.pdp.multirequest.IdentifiableResponse;
import io.sapl.api.pdp.multirequest.MultiDecision;
import io.sapl.api.pdp.multirequest.MultiRequest;
import io.sapl.api.pdp.multirequest.MultiResponse;
import lombok.experimental.UtilityClass;
import reactor.core.Disposable;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;

/**
 * Multi requests return a {@link Flux flux} of {@link IdentifiableDecision identifiable
 * decisions} or {@link IdentifiableResponse identifiable responses}. The blocking
 * implementation should collect the emitted decisions / responses and return a
 * {@link MultiDecision} / {@link MultiResponse} as soon as at least one decision /
 * response for each request contained in the {@link MultiRequest} has been collected.
 * This class provides according helper methods.
 */
@UtilityClass
public class RequestUtility {

	public static MultiDecision collectDecisions(MultiRequest multiRequest,
			Flux<IdentifiableDecision> decisionFlux) {
		final MultiDecision multiDecision = new MultiDecision();

		final Set<String> keys = multiRequest.getRequests().keySet();
		final CountDownLatch cdl = new CountDownLatch(keys.size());

		final Disposable subscription = decisionFlux.subscribe(identifiableDecision -> {
			// collect the decisions and wait until at least one decision has arrived for
			// each request
			final String requestId = identifiableDecision.getRequestId();
			final Decision decision = identifiableDecision.getDecision();
			multiDecision.setDecisionForRequestWithId(requestId, decision);
			if (keys.remove(requestId)) {
				cdl.countDown();
			}
		}, error -> {
			throw Exceptions.propagate(error);
		}, () -> {
			long cdlCount = cdl.getCount();
			while (cdlCount > 0) {
				cdlCount--;
				cdl.countDown();
			}
		});

		awaitTaskCompletion(cdl, subscription);

		return multiDecision;
	}

	public static MultiResponse collectResponses(MultiRequest multiRequest,
			Flux<IdentifiableResponse> responseFlux) {
		final MultiResponse multiResponse = new MultiResponse();

		final Set<String> keys = multiRequest.getRequests().keySet();
		final CountDownLatch cdl = new CountDownLatch(keys.size());

		final Disposable subscription = responseFlux.subscribe(identifiableResponse -> {
			// collect the responses and wait until at least one response has arrived for
			// each request
			final String requestId = identifiableResponse.getRequestId();
			final Response response = identifiableResponse.getResponse();
			multiResponse.setResponseForRequestWithId(requestId, response);
			if (keys.remove(requestId)) {
				cdl.countDown();
			}
		}, error -> {
			throw Exceptions.propagate(error);
		}, () -> {
			long cdlCount = cdl.getCount();
			while (cdlCount > 0) {
				cdlCount--;
				cdl.countDown();
			}
		});

		awaitTaskCompletion(cdl, subscription);

		return multiResponse;
	}

	private static void awaitTaskCompletion(CountDownLatch cdl, Disposable subscription) {
		try {
			cdl.await();
		}
		catch (InterruptedException e) {
			subscription.dispose();
			throw new RuntimeException(e);
		}

		if (!subscription.isDisposed()) {
			subscription.dispose();
		}
	}

}
