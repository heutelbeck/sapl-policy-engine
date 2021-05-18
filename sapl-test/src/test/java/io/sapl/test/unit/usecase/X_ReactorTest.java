package io.sapl.test.unit.usecase;

import java.io.InputStream;
import java.time.Duration;
import java.util.HashMap;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;

import io.sapl.api.interpreter.Val;
import io.sapl.api.pdp.AuthorizationDecision;
import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.grammar.sapl.SAPL;
import io.sapl.interpreter.DefaultSAPLInterpreter;
import io.sapl.interpreter.EvaluationContext;
import io.sapl.interpreter.InitializationException;
import io.sapl.interpreter.functions.AnnotationFunctionContext;
import io.sapl.interpreter.pip.AnnotationAttributeContext;
import io.sapl.interpreter.pip.AttributeContext;
import io.sapl.test.SaplTestException;
import io.sapl.test.unit.TestPIP2;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.test.publisher.TestPublisher;
import reactor.test.scheduler.VirtualTimeScheduler;

@Disabled
public class X_ReactorTest {
	
	
	
	@Test
    void testWithStepVerifier() {
		System.out.println("A");
        VirtualTimeScheduler.getOrSet();
        System.out.println("B");
        
        var mono = Mono.just(10);
        
        var flux = Flux
                .interval(Duration.ofSeconds(10), Duration.ofSeconds(5))
                .take(3);
        
        var combined = Flux.combineLatest(mono, flux, (a, b) -> a+b);
 
        StepVerifier.withVirtualTime(() -> combined)
                .expectSubscription()
                .thenAwait(Duration.ofSeconds(10))
                .expectNext(10l)
                .thenAwait(Duration.ofSeconds(5))
                .expectNext(11l)
                .thenAwait(Duration.ofSeconds(5))
                .expectNext(12l)
                .expectComplete()
                .log()
                .verify();
    }
	
	
	@Test
	void test() {
		TestPublisher<Val> testpublisher = TestPublisher.<Val>create();
		Flux<Val> testpubFlux = testpublisher.flux();
		Mono<Val> mono = Mono.just(Val.of("A"));
		var combined = Flux.combineLatest(mono, testpubFlux, (Val a, Val b) -> Val.of(a.getText() + b.getText()));
		
		StepVerifier.create(combined)
			.then(() -> testpublisher.emit(Val.of("B")))
			.expectNext(Val.of("AB"))
			.verifyComplete();
	}
	
	

	@Test
	void test_ab() {

		Flux<Integer> pip = Flux.just(10, 20, 30, 40, 50)
				.doOnNext(val -> System.out.println("Left: " + val));
		
		Flux<Integer> sub = Flux.just(1)
				.doOnNext(val -> System.out.println("Right: " + val));
		
		var combined = Flux.combineLatest(pip, sub, (a, b) -> a+b);
		
		combined.subscribe((a) -> System.out.println("Subscribed " + a));	
		
		
		
		//Problem: https://stackoverflow.com/questions/48326625/reactors-flux-combinelatest-with-flux-and-mono
	}
	
	@Test
	void test_ba() {

		Flux<Integer> pip = Flux.just(10, 20, 30, 40, 50)
				.doOnNext(val -> System.out.println("Left: " + val));
		
		Flux<Integer> sub = Flux.just(1)
				.doOnNext(val -> System.out.println("Right: " + val));
		
		var combined = Flux.combineLatest(sub, pip, (a, b) -> a+b);
		
		combined.subscribe((a) -> System.out.println("Subscribed " + a));	
	}
	

	@Test
	void test_ab_join() {
		
		Flux<Integer> pip = Flux.just(10, 20, 30, 40, 50).doOnNext(val -> System.out.println("Left: " + val));
		
		Flux<Integer> sub = Flux.just(1)
				.doOnNext(val -> System.out.println("Right: " + val));
		
		var combined = sub.join(pip, (v1) -> Flux.never(), (v2) -> Flux.never(), (a, b) -> a+b);
		
		combined.subscribe((a) -> System.out.println("Subscribed " + a));	
	}
	
	@Test
	void test_ba_join() {
		
		Flux<Integer> pip = Flux.just(10, 20, 30, 40, 50).doOnNext(val -> System.out.println("Left: " + val));
		
		Flux<Integer> sub = Flux.just(1)
				.doOnNext(val -> System.out.println("Right: " + val));
		
		var combined = pip.join(sub, (v1) -> Flux.never(), (v2) -> Flux.never(), (a, b) -> a+b);
		
		combined.subscribe((a) -> System.out.println("Subscribed " + a));	
	}
	
	@Test
	void test_ab_zip() {
		
		Flux<Integer> pip = Flux.just(10, 20, 30, 40, 50).doOnNext(val -> System.out.println("Left: " + val));
		
		Flux<Integer> sub = Flux.just(1)
				.doOnNext(val -> System.out.println("Right: " + val));
		
		var combined = Flux.zip(pip, sub, (a, b) -> a+b);
		
		combined.subscribe((a) -> System.out.println("Subscribed " + a));	
	}
	
	
	
	
	
	
	@Test
	//@Ignore
	void test_test2() throws InitializationException {
		DefaultSAPLInterpreter interpreter = new DefaultSAPLInterpreter();
		SAPL policy = interpreter.parse(findFileOnClasspath("policyWithSimplePIP.sapl"));
		
		AttributeContext attCtx = new AnnotationAttributeContext();
		attCtx.loadPolicyInformationPoint(new TestPIP2());

		EvaluationContext ctx = new EvaluationContext(attCtx, new AnnotationFunctionContext(), new HashMap<String, JsonNode>(1));
		
		Flux<AuthorizationDecision> flux = policy.evaluate(ctx.forAuthorizationSubscription(AuthorizationSubscription.of("willi", "read", "something")));

		flux.subscribe((a) -> System.out.println("   " + a));
	}
	
	private InputStream findFileOnClasspath(String filename) {
		if(getClass().getClassLoader().getResourceAsStream(filename) != null) {
			return getClass().getClassLoader().getResourceAsStream(filename);
		} else if(getClass().getClassLoader().getResourceAsStream("policies/" + filename) != null) {
			return getClass().getClassLoader().getResourceAsStream("policies/" + filename);
		} else {
			throw new SaplTestException();
		}
	}

}
