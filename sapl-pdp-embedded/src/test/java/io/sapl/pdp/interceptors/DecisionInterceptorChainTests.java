package io.sapl.pdp.interceptors;

import java.util.function.Function;

import org.apache.commons.lang3.time.StopWatch;
import org.junit.jupiter.api.Test;

import io.sapl.api.pdp.AuthorizationDecision;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Slf4j
public class DecisionInterceptorChainTests {

	@Test
	void sketch() throws InterruptedException {
		//System.out.println("XXX");
		var interceptorChain = new DecisionInterceptorChain();
		var decisions        = Flux.just(AuthorizationDecision.PERMIT, AuthorizationDecision.DENY)
				.contextWrite(ctx -> ctx.put("key", "value")).flatMap(interceptorChain);

		// decisions.log().blockLast();

		var x = both().log().block();
		System.out.println("->"+x);
//		StopWatch watch = new StopWatch();
//		watch.start();
//		for (int i = 0; i < 10_000; i++)
//			evalA(2_000).block();
//		watch.stop();
//		System.out.println("Elapsed: " + ((double) watch.getTime()) / 1000 + "s");
//
//		watch.reset();
//		watch.start();
//		var x = evalA(2_000);
//		for (int i = 0; i < 10_000; i++)
//			x.block();
//		watch.stop();
//		System.out.println("Elapsed: " + ((double) watch.getTime()) / 1000 + "s");
	}

	Mono<String> both() {
		return operator(pip1(),pip2());
	}

	Mono<String> operator(Mono<String> a, Mono<String> b) {
		return Mono.zip(a, b).map(x -> x.getT1() + "-" + x.getT2()).flatMap(s -> {
			return Mono.deferContextual(ctx -> {
				return Mono.just(s + "[" + ctx.getOrEmpty("pip1") + "|" + ctx.getOrEmpty("pip2") + "]");
			});
		});
	}

	Mono<String> pip1() {
		return Mono.just("pip 1 returned this").contextWrite(ctx -> ctx.put("pip1", "I was here"));
	}

	Mono<String> pip2() {
		return Mono.just("pip 2 returned this").contextWrite(ctx -> ctx.put("pip2", "I was here"));
	}

	Mono<String> evalA(long depth) {
		var result = Mono.just("THE END");
		for (var i = 0; i < depth; i++) {
			result = result.map(Function.identity());
		}
		return result;
	}
}
