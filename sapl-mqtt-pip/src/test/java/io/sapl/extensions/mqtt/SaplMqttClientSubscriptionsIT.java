/*
 * Copyright © 2023 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.extensions.mqtt;

import static io.sapl.extensions.mqtt.MqttTestUtility.buildAndStartBroker;
import static io.sapl.extensions.mqtt.MqttTestUtility.buildMqttPublishMessage;
import static io.sapl.extensions.mqtt.MqttTestUtility.buildVariables;
import static io.sapl.extensions.mqtt.MqttTestUtility.startClient;
import static io.sapl.extensions.mqtt.MqttTestUtility.stopBroker;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.hivemq.embedded.EmbeddedHiveMQ;

import io.sapl.api.interpreter.Val;
import io.sapl.interpreter.InitializationException;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

@Timeout(360)
class SaplMqttClientSubscriptionsIT {

	private final static long DELAY_MS = 800L;

	private final static JsonNodeFactory JSON = JsonNodeFactory.instance;

	@TempDir
	Path configDir;

	@TempDir
	Path dataDir;

	@TempDir
	Path extensionsDir;

	EmbeddedHiveMQ      mqttBroker;
	Mqtt5BlockingClient mqttClient;
	SaplMqttClient      saplMqttClient;

	@BeforeEach
	void beforeEach() {
		System.out.println("Before 1 .. build and start broker");
		this.mqttBroker = buildAndStartBroker(configDir, dataDir, extensionsDir);
		System.out.println("Before 2 .. start client");
		this.mqttClient = startClient();
		System.out.println("Before 3 .. create sapl client");
		this.saplMqttClient = new SaplMqttClient();
		System.out.println("Before 4 ... done");
	}

	@AfterEach
	void afterEach() {
		System.out.println("After 1 disconnect");
		mqttClient.disconnect();
		System.out.println("After 2 stop");
		stopBroker(mqttBroker);
		System.out.println("After 3 done");
	}

	@Test
	void when_subscribeToMultipleTopicsOnSingleFlux_then_getMessagesOfMultipleTopics() throws InitializationException {
		System.out.println("when_subscribeToMultipleTopicsOnSingleFlux_then_getMessagesOfMultipleTopics ...");

		// GIVEN
		var topics = JSON.arrayNode().add("topic1").add("topic2");

		// WHEN
		var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topics), buildVariables())
				.filter(val -> !val.isUndefined());

		// THEN
		StepVerifier.create(saplMqttMessageFlux)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic1",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic2",
						"message2", false)))
				.expectNext(Val.of("message2"))
				.thenCancel()
				.verify();
		System.out.println("when_subscribeToMultipleTopicsOnSingleFlux_then_getMessagesOfMultipleTopics ... done");
	}

	@Test
	void when_subscribeToMultipleTopicsOnDifferentFlux_then_getMessagesOfMultipleTopics()
			throws InitializationException {
		System.out.println("when_subscribeToMultipleTopicsOnDifferentFlux_then_getMessagesOfMultipleTopics ...");

		// GIVEN
		var topicsFirstFlux  = JSON.arrayNode().add("topic1").add("topic2");
		var topicsSecondFlux = JSON.arrayNode().add("topic2").add("topic3");

		var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsFirstFlux),
				buildVariables());
		var saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsSecondFlux),
				buildVariables());

		// WHEN
		var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
				.filter(val -> !val.isUndefined());

		// THEN
		StepVerifier.create(saplMqttMessageFluxMerge)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic1",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic2",
						"message2", false)))
				.expectNext(Val.of("message2"))
				.expectNext(Val.of("message2"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic3",
						"message3", false)))
				.expectNext(Val.of("message3"))
				.thenCancel()
				.verify();
		System.out.println("when_subscribeToMultipleTopicsOnDifferentFlux_then_getMessagesOfMultipleTopics ... done");
	}

	@Test
	void when_oneFluxIsCancelledWhileSubscribingToSingleTopics_then_getMessagesOfLeftTopics()
			throws InitializationException {
		System.out.println("when_oneFluxIsCancelledWhileSubscribingToSingleTopics_then_getMessagesOfLeftTopics ...");

		// GIVEN
		var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic"),
				buildVariables());
		var saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(Val.of("topic"), buildVariables())
				.takeUntil(value -> "message".equals(value.getText()));

		// WHEN
		var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
				.filter(val -> !val.isUndefined());

		// THEN
		StepVerifier.create(saplMqttMessageFluxMerge)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic", "message", false)))
				.expectNext(Val.of("message"))
				.expectNext(Val.of("message"))
				.thenAwait(Duration.ofMillis(DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic", "message", false)))
				.expectNext(Val.of("message"))
				.expectNoEvent(Duration.ofMillis(2 * DELAY_MS))
				.thenCancel()
				.verify();

		System.out
				.println("when_oneFluxIsCancelledWhileSubscribingToSingleTopics_then_getMessagesOfLeftTopics ... done");

	}

//	@Test
//	@Timeout(150)
	void stressIt() throws InitializationException {
		for (int i = 0; i < 100; i++) {
			System.out.println("A");
			afterEach();
			System.out.println("B");
			beforeEach();
			System.out.println("i=" + i);
			when_oneFluxIsCancelledWhileSubscribingToMultipleTopics_then_getMessagesOfLeftTopics();
			System.out.println("C");

		}
	}

	@Test
	void when_oneFluxIsCancelledWhileSubscribingToMultipleTopics_then_getMessagesOfLeftTopics()
			throws InitializationException {
		System.out.println("Starting problematic test...");
		// GIVEN
		var topicsFirstFlux  = JSON.arrayNode().add("topic1").add("topic2");
		var topicsSecondFlux = JSON.arrayNode().add("topic2").add("topic3");

		var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of(topicsFirstFlux),
				buildVariables());
		var saplMqttMessageFluxSecond = saplMqttClient
				.buildSaplMqttMessageFlux(Val.of(topicsSecondFlux), buildVariables())
				.takeUntil(value -> "message2".equals(value.getText()));

		// WHEN
		var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
				.filter(val -> !val.isUndefined());
		System.out.println("Starting verification...");

		// THEN
		StepVerifier.create(saplMqttMessageFluxMerge)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
				.expectNext(Val.of("message1"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
				.expectNext(Val.of("message3"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
				.expectNext(Val.of("message2"))
				.expectNext(Val.of("message2"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic3", "message3", false)))
				.expectNoEvent(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic1", "message1", false)))
				.expectNext(Val.of("message1"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("topic2", "message2", false)))
				.expectNext(Val.of("message2"))
				.thenCancel()
				.verify();
		System.out.println("Done verification...");
	}

	@Test
	void when_subscribingWithSingleLevelWildcard_then_getMessagesMatchingTopicsOfSingleLevelWildcard()
			throws InitializationException {
		System.out.println(
				"when_subscribingWithSingleLevelWildcard_then_getMessagesMatchingTopicsOfSingleLevelWildcard ...");

		// GIVEN

		// WHEN
		var saplMqttMessageFlux = saplMqttClient
				.buildSaplMqttMessageFlux(Val.of("level1/+/level3"), buildVariables())
				.filter(val -> !val.isUndefined());

		// THEN
		StepVerifier.create(saplMqttMessageFlux)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/singleLevelWildcard/level3",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.thenCancel()
				.verify();
		System.out.println(
				"when_subscribingWithSingleLevelWildcard_then_getMessagesMatchingTopicsOfSingleLevelWildcard ... done");
	}

	@Test
	void when_subscribingWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard()
			throws InitializationException {
		System.out.println(
				"when_subscribingWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard ...");

		// GIVEN

		// WHEN
		var saplMqttMessageFlux = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/#"), buildVariables())
				.filter(val -> !val.isUndefined());

		// THEN
		StepVerifier.create(saplMqttMessageFlux)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/multiLevelWildcard",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/multiLevelWildcard/level3",
						"message2", false)))
				.expectNext(Val.of("message2"))
				.thenCancel()
				.verify();
		System.out.println(
				"when_subscribingWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard ... done");

	}

	@Test
	void when_unsubscribingTopicOnSharedConnectionWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard()
			throws InitializationException {
		System.out.println(
				"when_unsubscribingTopicOnSharedConnectionWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard ...");

		// GIVEN
		var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/#"),
				buildVariables());
		var saplMqttMessageFluxSecond = saplMqttClient
				.buildSaplMqttMessageFlux(Val.of("level1/level2"), buildVariables())
				.takeUntil(value -> "message1".equals(value.getText()));

		// WHEN
		var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
				.filter(val -> !val.isUndefined());

		// THEN
		StepVerifier.create(saplMqttMessageFluxMerge)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.expectNext(Val.of("message1"))
				.thenAwait(Duration.ofMillis(DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.expectNoEvent(Duration.ofMillis(2 * DELAY_MS))
				.thenCancel()
				.verify();
		System.out.println(
				"when_unsubscribingTopicOnSharedConnectionWithMultiLevelWildcard_then_getMessagesMatchingTopicsOfMultiLevelWildcard ... done");

	}

	@Test
	void when_unsubscribingMultiLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic()
			throws InitializationException {
		System.out.println(
				"when_unsubscribingMultiLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic ...");

		// GIVEN
		var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(Val.of("level1/level2"),
				buildVariables());
		var saplMqttMessageFluxSecond = saplMqttClient
				.buildSaplMqttMessageFlux(Val.of("level1/#"), buildVariables())
				.takeUntil(value -> "message1".equals(value.getText()));

		// WHEN
		var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
				.filter(val -> !val.isUndefined());

		// THEN
		StepVerifier.create(saplMqttMessageFluxMerge)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.expectNext(Val.of("message1"))
				.thenAwait(Duration.ofMillis(DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/xxx",
						"message1", false)))
				.expectNoEvent(Duration.ofMillis(2 * DELAY_MS))
				.thenCancel()
				.verify();
		System.out.println(
				"when_unsubscribingMultiLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic ... done");

	}

	@Test
	void when_unsubscribingSingleLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic()
			throws InitializationException {
		System.out.println(
				"when_unsubscribingSingleLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic ...");

		// GIVEN
		var saplMqttMessageFluxFirst  = saplMqttClient.buildSaplMqttMessageFlux(
				Val.of("level1/level2/level3"), buildVariables());
		var saplMqttMessageFluxSecond = saplMqttClient.buildSaplMqttMessageFlux(
				Val.of("level1/+/level3"), buildVariables())
				.takeUntil(value -> "message1".equals(value.getText()));

		// WHEN
		var saplMqttMessageFluxMerge = Flux.merge(saplMqttMessageFluxFirst, saplMqttMessageFluxSecond)
				.filter(val -> !val.isUndefined());

		// THEN
		StepVerifier.create(saplMqttMessageFluxMerge)
				.thenAwait(Duration.ofMillis(2 * DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2/level3",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.expectNext(Val.of("message1"))
				.thenAwait(Duration.ofMillis(DELAY_MS))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/level2/level3",
						"message1", false)))
				.expectNext(Val.of("message1"))
				.then(() -> mqttClient.publish(buildMqttPublishMessage("level1/xxx/level3",
						"message1", false)))
				.expectNoEvent(Duration.ofMillis(2 * DELAY_MS))
				.thenCancel()
				.verify();
		System.out.println(
				"when_unsubscribingSingleLevelWildcardTopicOnSharedConnectionWithSimpleTopic_then_getMessagesMatchingSimpleTopic ... done");

	}
}
