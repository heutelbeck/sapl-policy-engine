package io.sapl.server.lt;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;

@Configuration
public class SaplExtensionsConfig {

	@Bean
	MqttPolicyInformationPoint mqttPolicyInformationPoint() {
		return new MqttPolicyInformationPoint();
	}

	@Bean
	MqttFunctionLibrary mqttFunctionLibrary() {
		return new MqttFunctionLibrary();
	}
}
