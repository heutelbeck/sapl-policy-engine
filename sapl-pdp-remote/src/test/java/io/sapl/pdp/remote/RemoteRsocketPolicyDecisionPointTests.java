/*
 * Copyright © 2017-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.pdp.remote;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.bridge.SLF4JBridgeHandler;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.rsocket.RSocketRequester;
import org.springframework.messaging.rsocket.RSocketStrategies;
import org.springframework.messaging.rsocket.annotation.ConnectMapping;
import org.springframework.messaging.rsocket.annotation.support.RSocketMessageHandler;
import org.springframework.stereotype.Controller;

import io.rsocket.SocketAcceptor;
import io.rsocket.core.RSocketServer;
import io.rsocket.frame.decoder.PayloadDecoder;
import io.rsocket.transport.netty.server.CloseableChannel;
import io.rsocket.transport.netty.server.TcpServerTransport;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.core.publisher.MonoProcessor;
import reactor.core.scheduler.Schedulers;
import reactor.test.StepVerifier;

class RemoteRsocketPolicyDecisionPointTests {

	private static String clientId;

	private static AnnotationConfigApplicationContext context;

	private static CloseableChannel server;
	
	private RemoteRsocketPolicyDecisionPoint pdp;

	@BeforeAll
	static void setupLog() {
		// Route MockWebServer logs to shared logs
		SLF4JBridgeHandler.removeHandlersForRootLogger();
		SLF4JBridgeHandler.install();
		Hooks.onOperatorDebug();
	}
	@BeforeAll
	public static void setupOnce() {
		// create a client identity spring for this test suite
		clientId = UUID.randomUUID().toString();

		// create a Spring context for this test suite and obtain some beans
		context = new AnnotationConfigApplicationContext(ServerConfig.class);

		// Create an RSocket server for use in testing
		RSocketMessageHandler messageHandler = context.getBean(RSocketMessageHandler.class);
		server = RSocketServer.create(messageHandler.responder())
				.payloadDecoder(PayloadDecoder.ZERO_COPY)
				.bind(TcpServerTransport.create("localhost", 0))
				.block();
	}

	@AfterAll
	public static void tearDownOnce() {
		server.dispose();
	}

	/**
	 * Test that our client-side 'ClientHandler' class responds to server sent messages correctly.
	 */
	@Test
	@Disabled
	public void testServerCallsClientAfterConnection() {
		connectAndRunTest("shell-client");
	}

	/**
	 * This private method is used to establish a connection to our fake RSocket server.
	 * It also controls the state of our test controller. This method is reusable by many tests.
	 *
	 * @param connectionRoute
	 */
	private void connectAndRunTest(String connectionRoute) {

		ServerController controller = context.getBean(ServerController.class);
		RSocketStrategies strategies = context.getBean(RSocketStrategies.class);
		RSocketRequester requester = null;

		try {
			controller.reset();

			// Add our ClientHandler as a responder
			SocketAcceptor responder = RSocketMessageHandler.responder(strategies, new ClientHandler());

			// Create an RSocket requester that includes our responder
			requester = RSocketRequester.builder()
					.setupRoute(connectionRoute)
					.setupData(clientId)
					.rsocketStrategies(strategies)
					.rsocketConnector(connector -> connector.acceptor(responder))
					.tcp("localhost", server.address().getPort());

			pdp = RemotePolicyDecisionPoint.builder()
					.rsocket()
					.host(server.address().getHostString())
					.port(server.address().getPort())
					//.basicAuth("secret", "key")
					.build();
			pdp.setBackoffFactor(2);
			pdp.setFirstBackoffMillis(100);
			pdp.setMaxBackOffMillis(200);

			// Give the test time to run, wait for the server's call.
			controller.await(Duration.ofSeconds(10));
		} finally {
			if (requester != null) {
				requester.rsocket().dispose();
			}
		}
	}

	/**
	 * Fake Spring @Controller class which is a stand-in 'test rig' for our real server.
	 * It contains a custom @ConnectMapping that tests if our ClientHandler is responding to
	 * server-side calls for telemetry data.
	 */
	@Controller
	static class ServerController {

		// volatile guarantees visibility across threads.
		// MonoProcessor implements stateful semantics for a mono
		volatile MonoProcessor<Object> result;

		// Reset the stateful Mono
		public void reset() {
			this.result = MonoProcessor.create();
		}

		// Allow some time for the test to execute
		public void await(Duration duration) {
			this.result.block(duration);
		}

		/**
		 * Test method. When a client connects to this server, ask the client for its telemetry data
		 * and test that the telemetry received is within a good range.
		 *
		 * @param requester
		 * @param client
		 */
		@ConnectMapping("shell-client")
		void verifyConnectShellClientAndAskForTelemetry(RSocketRequester requester, @Payload String client) {

			// test the client's message payload contains the expected client ID
			assertThat(client).isNotNull();
			assertThat(client).isNotEmpty();
			assertThat(client).isEqualTo(clientId);

			runTest(() -> {
				Flux<String> flux = requester
						.route("client-status") // Test the 'client-status' message handler mapping
						.data("OPEN") // confirm to the client th connection is open
						.retrieveFlux(String.class); // ask the client for its telemetry

				StepVerifier.create(flux)
						.consumeNextWith(s -> {
							// assert the memory reading is in the 'good' range
							assertThat(s).isNotNull();
							assertThat(s).isNotEmpty();
							assertThat(Integer.valueOf(s)).isPositive();
							assertThat(Integer.valueOf(s)).isGreaterThan(0);
						})
						.thenCancel()
						.verify(Duration.ofSeconds(10));
			});
		}

		/**
		 * Run the provided test, collecting the results into a stateful Mono.
		 *
		 * @param test
		 */
		private void runTest(Runnable test) {
			// Run the test provided
			Mono.fromRunnable(test)
					.doOnError(ex -> result.onError(ex)) // test result was an error
					.doOnSuccess(o -> result.onComplete()) // test result was success
					.subscribeOn(Schedulers.elastic()) // StepVerifier will block
					.subscribe();
		}
	}

	/**
	 * This test-specific configuration allows Spring to help configure our test environment.
	 * These beans will be placed into the Spring context and can be accessed when required.
	 */
	@TestConfiguration
	static class ServerConfig {

		@Bean
		ServerController serverController() {
			return new ServerController();
		}

		@Bean
		RSocketMessageHandler serverMessageHandler(@Qualifier("testStrategies") RSocketStrategies strategies) {
			RSocketMessageHandler handler = new RSocketMessageHandler();
			handler.setRSocketStrategies(strategies);
			return handler;
		}

		@Bean("testStrategies")
		RSocketStrategies rsocketStrategies() {
			return RSocketStrategies.create();
		}
	}
/*
	@BeforeEach
	void startServer() throws IOException {
		// create a Spring context for this test suite and obtain some beans
		var context = new AnnotationConfigApplicationContext(ServerConfig.class);
		RSocketMessageHandler messageHandler = context.getBean(RSocketMessageHandler.class);
		server = RSocketServer.create(messageHandler.responder())
				.payloadDecoder(PayloadDecoder.ZERO_COPY)
				.bind(TcpServerTransport.create("localhost", 0))
				.block();

		pdp = RemotePolicyDecisionPoint.builder()
				.rsocket()
				.host(server.address().getHostString())
				.port(server.address().getPort())
				//.basicAuth("secret", "key")
				.build();
		pdp.setBackoffFactor(2);
		pdp.setFirstBackoffMillis(100);
		pdp.setMaxBackOffMillis(200);
	}

	@AfterEach
	void shutdownServer() throws IOException {
		server.dispose();
	}

 */

	static class ClientHandler {

		@MessageMapping("client-status")
		public Flux<String> statusUpdate(String status) {
			return Flux.interval(Duration.ofSeconds(5)).map(index -> String.valueOf(Runtime.getRuntime().freeMemory()));
		}
	}

}
