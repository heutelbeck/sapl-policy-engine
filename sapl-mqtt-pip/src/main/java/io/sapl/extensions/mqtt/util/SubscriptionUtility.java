/*
 * Copyright © 2019-2022 Dominic Heutelbeck (dominic@heutelbeck.com)
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

package io.sapl.extensions.mqtt.util;

import static io.sapl.extensions.mqtt.util.ConfigUtility.getQos;

import java.util.LinkedList;
import java.util.List;

import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscribe;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.Mqtt5Subscription;
import com.hivemq.client.mqtt.mqtt5.message.subscribe.suback.Mqtt5SubAck;

import io.sapl.api.interpreter.Val;
import lombok.experimental.UtilityClass;

/**
 * This utility class provides functions to build mqtt subscriptions.
 */
@UtilityClass
public class SubscriptionUtility {

	/**
	 * Builds a mqtt topic subscription containing one or multiple topics.
	 * 
	 * @param topic a {@link Val} of one or multiple topics
	 * @param qos   the qos level for the mqtt topic subscription
	 * @return returns the build mqtt topic subscription
	 */
	public static Mqtt5Subscribe buildTopicSubscription(Val topic, Val qos) {
		if (topic.isArray()) {
			return buildTopicSubscriptionOfArray(topic, qos);
		} else {
			return buildTopicSubscriptionOfString(topic, qos);
		}
	}

	private static Mqtt5Subscribe buildTopicSubscriptionOfArray(Val topics, Val qos) {
		List<Mqtt5Subscription> topicSubscriptionList = new LinkedList<>();
		for (var topic : topics.getArrayNode()) {
			topicSubscriptionList.add(Mqtt5Subscription.builder()
					.topicFilter(topic.asText())
					.qos(getQos(qos))
					.build());
		}
		return Mqtt5Subscribe.builder().addSubscriptions(topicSubscriptionList).build();
	}

	private static Mqtt5Subscribe buildTopicSubscriptionOfString(Val topic, Val qos) {
		return Mqtt5Subscribe.builder()
				.topicFilter(topic.getText())
				.qos(getQos(qos))
				.build();
	}

	/**
	 * Adds the count of the topics contained in the subscription to the count of
	 * the topic subscription list.
	 * 
	 * @param clientValues      the object containing the topic subscription list
	 * @param mqtt5SubAck       the acknowledgement message specifying whether a
	 *                          subscription of a topic was successfully established
	 *                          or not
	 * @param topicSubscription the topic subscription containing the topics which
	 *                          count is to add
	 */
	public static void addSubscriptionsCountToSubscriptionList(MqttClientValues clientValues, Mqtt5SubAck mqtt5SubAck,
			Mqtt5Subscribe topicSubscription) {
		var subscriptions = topicSubscription.getSubscriptions();
		var reasonCodes   = mqtt5SubAck.getReasonCodes();
		for (var i = 0; i < subscriptions.size(); i++) {
			if (!reasonCodes.get(i).isError()) {
				String subscription = subscriptions.get(i).getTopicFilter().toString();
				clientValues.countTopicSubscriptionsCountMapUp(subscription);
			}
		}
	}
}
