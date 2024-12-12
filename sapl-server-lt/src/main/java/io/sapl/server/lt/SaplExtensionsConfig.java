/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
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
package io.sapl.server.lt;

import java.util.List;

import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.r2dbc.R2dbcAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Role;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.sapl.api.functions.StaticFunctionLibrarySupplier;
import io.sapl.api.pip.StaticPolicyInformationPointSupplier;
import io.sapl.extensions.mqtt.MqttFunctionLibrary;
import io.sapl.extensions.mqtt.MqttPolicyInformationPoint;
import io.sapl.geo.library.GeoConverter;
import io.sapl.geo.library.GeoFunctions;
import io.sapl.geo.library.GeoParser;
import io.sapl.geo.library.SqlFunctions;
import io.sapl.geo.pip.MySqlPolicyInformationPoint;
import io.sapl.geo.pip.OwnTracksPolicyInformationPoint;
import io.sapl.geo.pip.PostGisPolicyInformationPoint;
import io.sapl.geo.pip.TraccarPolicyInformationPoint;
import io.sapl.pip.http.HttpPolicyInformationPoint;
import io.sapl.pip.http.ReactiveWebClient;

@Configuration
@EnableAutoConfiguration(exclude = { R2dbcAutoConfiguration.class })
public class SaplExtensionsConfig {

    @Bean
    ReactiveWebClient reactiveWebClient(ObjectMapper mapper) {
        return new ReactiveWebClient(mapper);
    }

    @Bean
    MySqlPolicyInformationPoint mySqlPolicyInformationPoint(ObjectMapper mapper) {
        return new MySqlPolicyInformationPoint(mapper);
    }

    @Bean
    OwnTracksPolicyInformationPoint ownTracksPolicyInformationPoint(ObjectMapper mapper) {
        return new OwnTracksPolicyInformationPoint(mapper);
    }

    @Bean
    PostGisPolicyInformationPoint postGisPolicyInformationPoint(ObjectMapper mapper) {
        return new PostGisPolicyInformationPoint(mapper);
    }

    @Bean
    TraccarPolicyInformationPoint traccarPolicyInformationPoint(ObjectMapper mapper) {
        return new TraccarPolicyInformationPoint(mapper);
    }

    @Bean
    GeoParser geoParser(ObjectMapper mapper) {
        return new GeoParser(mapper);
    }

    @Bean
    HttpPolicyInformationPoint httpPolicyInformationPoint(ReactiveWebClient reactiveWebClient) {
        return new HttpPolicyInformationPoint(reactiveWebClient);
    }

    @Bean
    StaticPolicyInformationPointSupplier mqttPolicyInformationPoint() {
        return () -> List.of(MqttPolicyInformationPoint.class);
    }

    @Bean
    @Role(BeanDefinition.ROLE_INFRASTRUCTURE)
    StaticFunctionLibrarySupplier additionalStaticLibraries() {
        return () -> List.of(MqttFunctionLibrary.class, GeoConverter.class, GeoFunctions.class, SqlFunctions.class);
    }

}
