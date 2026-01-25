/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.benchmark;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.sapl.api.model.jackson.SaplJacksonModule;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import io.sapl.api.pdp.AuthorizationSubscription;
import io.sapl.benchmark.util.BenchmarkException;
import lombok.Getter;
import lombok.Setter;

/**
 * Class holds the configuration from a benchmark yaml file
 */
public class BenchmarkConfiguration {
    private final ObjectMapper  mapper          = new ObjectMapper();
    private static final String ENABLED         = "enabled";
    private static final String CLIENT_SECRET   = "client_secret";
    private static final String DOCKER          = "docker";
    private static final String REMOTE          = "remote";
    private String              benchmarkTarget = DOCKER;

    public BenchmarkConfiguration() {
        mapper.registerModule(new SaplJacksonModule());
    }

    private static void failOnFurtherMapEntries(Set<String> keySet, String parentEntryPath) {
        for (var key : keySet) {
            if (null != key) {
                throw new BenchmarkException("Unknown configuration entry " + parentEntryPath + "." + key);
            }
        }
    }

    // ---------------------------
    // - Connectivity setup
    // ---------------------------
    @JsonProperty("target")
    public void setBenchmarkTarget(String target) {
        if (DOCKER.equals(target) || REMOTE.equals(target)) {
            this.benchmarkTarget = target;
        } else {
            throw new BenchmarkException("invalid target=" + target);
        }
    }

    @Getter
    private String dockerPdpImage;
    @Getter
    private String oauth2MockImage;

    public static final int DOCKER_DEFAULT_RSOCKET_PORT          = 7000;
    public static final int DOCKER_DEFAULT_PROTOBUF_RSOCKET_PORT = 7001;
    public static final int DOCKER_DEFAULT_HTTP_PORT             = 8080;
    @Getter
    private boolean         dockerUseSsl                         = true;

    @JsonProperty(DOCKER)
    public void setDocker(Map<String, String> map) {
        this.dockerPdpImage = map.remove("pdp_image");
        this.dockerUseSsl   = Boolean.parseBoolean(map.remove("use_ssl"));
        failOnFurtherMapEntries(map.keySet(), DOCKER);
    }

    @Getter
    private String  remoteBaseUrl;
    @Getter
    private String  remoteRsocketHost;
    @Getter
    private int     remoteRsocketPort;
    @Getter
    private int     remoteProtobufRsocketPort = 7001;
    @Getter
    private boolean remoteUseSsl;

    @JsonProperty(REMOTE)
    public void setRemote(Map<String, String> map) {
        this.remoteBaseUrl     = map.remove("base_url");
        this.remoteRsocketHost = map.remove("rsocket_host");
        this.remoteRsocketPort = Integer.parseInt(map.remove("rsocket_port"));
        var protobufPort = map.remove("protobuf_rsocket_port");
        if (protobufPort != null) {
            this.remoteProtobufRsocketPort = Integer.parseInt(protobufPort);
        }
        this.remoteUseSsl = Boolean.parseBoolean(map.remove("use_ssl"));
        failOnFurtherMapEntries(map.keySet(), REMOTE);
    }

    // ---------------------------
    // - Subscription
    // ---------------------------
    @Getter
    private AuthorizationSubscription authorizationSubscription = AuthorizationSubscription.of("Willi", "eat", "apple");

    @JsonSetter("subscription")
    public void setSubscription(String subscription) throws JsonProcessingException {
        this.authorizationSubscription = mapper.readValue(subscription, AuthorizationSubscription.class);
    }

    // ---------------------------
    // - Benchmark scope
    // ---------------------------
    @Setter
    private boolean runEmbeddedBenchmarks        = true;
    @Setter
    private boolean runHttpBenchmarks            = true;
    @Setter
    private boolean runRsocketBenchmarks         = true;
    @Setter
    private boolean runProtobufRsocketBenchmarks = false;
    private boolean runDecideOnceBenchmarks      = true;
    private boolean runDecideSubscribeBenchmarks = true;

    @JsonProperty("benchmark_pdp")
    public void setBenchmarkPdp(Map<String, String> map) {
        this.runEmbeddedBenchmarks = Boolean.parseBoolean(map.remove("embedded"));
        this.runHttpBenchmarks     = Boolean.parseBoolean(map.remove("http"));
        this.runRsocketBenchmarks  = Boolean.parseBoolean(map.remove("rsocket"));
        var protobufRsocket = map.remove("protobuf_rsocket");
        if (protobufRsocket != null) {
            this.runProtobufRsocketBenchmarks = Boolean.parseBoolean(protobufRsocket);
        }
        failOnFurtherMapEntries(map.keySet(), "benchmark_pdp");
    }

    @JsonProperty("decision_method")
    public void setDecisionMethod(Map<String, String> map) {
        this.runDecideOnceBenchmarks      = Boolean.parseBoolean(map.remove("decide_once"));
        this.runDecideSubscribeBenchmarks = Boolean.parseBoolean(map.remove("decide_subscribe"));
        failOnFurtherMapEntries(map.keySet(), "decision_method");
    }

    // ---------------------------
    // - Authentication
    // ---------------------------
    @Getter
    @Setter
    private boolean useNoAuth        = true;
    @Getter
    @Setter
    private boolean useBasicAuth     = true;
    @Getter
    @Setter
    private boolean useAuthApiKey    = true;
    @Getter
    @Setter
    private boolean useOauth2        = false;
    @Getter
    @Setter
    private String  basicClientKey;
    @Getter
    @Setter
    private String  basicClientSecret;
    @Getter
    @Setter
    private String  apiKeySecret;
    @Getter
    private boolean oauth2MockServer = true;
    @Getter
    private String  oauth2ClientId;
    @Getter
    private String  oauth2ClientSecret;
    @Getter
    private String  oauth2Scope;
    @Getter
    private String  oauth2TokenUri;
    @Getter
    private String  oauth2IssuerUrl;

    @JsonProperty("noauth")
    public void setNoAuth(Map<String, String> map) {
        this.useNoAuth = Boolean.parseBoolean(map.remove(ENABLED));
        failOnFurtherMapEntries(map.keySet(), "noauth");
    }

    @JsonProperty("basic")
    public void setBasic(Map<String, String> map) {
        this.useBasicAuth = Boolean.parseBoolean(map.remove(ENABLED));
        if (this.useBasicAuth) {
            this.basicClientKey    = map.get("client_key");
            this.basicClientSecret = map.get(CLIENT_SECRET);
        }
        map.remove("client_key");
        map.remove(CLIENT_SECRET);
        failOnFurtherMapEntries(map.keySet(), "basic");
    }

    @JsonProperty("apikey")
    public void setApiKey(Map<String, String> map) {
        this.useAuthApiKey = Boolean.parseBoolean(map.remove(ENABLED));
        final var secret = map.remove("api_key");
        if (this.useAuthApiKey) {
            this.apiKeySecret = secret;
        }
        failOnFurtherMapEntries(map.keySet(), "apikey");
    }

    @JsonProperty("oauth2")
    public void setOauth2(Map<String, String> map) {
        this.useOauth2 = Boolean.parseBoolean(map.remove(ENABLED));
        if (this.useOauth2) {
            this.oauth2MockServer   = Boolean.parseBoolean(map.get("mock_server"));
            this.oauth2MockImage    = map.get("mock_image");
            this.oauth2ClientId     = map.get("client_id");
            this.oauth2ClientSecret = map.get(CLIENT_SECRET);
            this.oauth2Scope        = map.get("scope");
            if (!oauth2MockServer) {
                this.oauth2TokenUri  = map.get("token_uri");
                this.oauth2IssuerUrl = map.get("issuer_url");
            }
        }
        map.remove("mock_server");
        map.remove("mock_image");
        map.remove("client_id");
        map.remove(CLIENT_SECRET);
        map.remove("scope");
        map.remove("token_uri");
        map.remove("issuer_url");
        failOnFurtherMapEntries(map.keySet(), "oauth2");
    }

    // ---------------------------
    // - Benchmark execution settings
    // ---------------------------
    @Getter
    private Integer       forks                 = 2;
    @Getter
    private List<String>  jvmArgs               = new ArrayList<>();
    @Getter
    private boolean       failOnError           = false;
    @Getter
    private List<Integer> threadList            = List.of(1);
    @Getter
    private Integer       warmupSeconds         = 10;
    @Getter
    private Integer       warmupIterations      = 2;
    @Getter
    private Integer       measurementSeconds    = 10;
    @Getter
    private Integer       measurementIterations = 10;

    @JsonProperty("execution")
    public void setExecution(Map<String, JsonNode> map) throws JsonProcessingException {
        this.forks                 = map.remove("forks").intValue();
        this.jvmArgs               = mapper.readValue(map.remove("jvm_args").toString(), new TypeReference<>() {});
        this.threadList            = mapper.readValue(map.remove("threads").toString(), new TypeReference<>() {});
        this.failOnError           = map.remove("fail_on_error").booleanValue();
        this.warmupSeconds         = map.remove("warmup_seconds").intValue();
        this.warmupIterations      = map.remove("warmup_iterations").intValue();
        this.measurementSeconds    = map.remove("measure_seconds").intValue();
        this.measurementIterations = map.remove("measure_iterations").intValue();
        failOnFurtherMapEntries(map.keySet(), "execution");
    }

    @JsonIgnore
    public String getBenchmarkPattern() {
        List<String> classes         = new ArrayList<>();
        List<String> authMethods     = new ArrayList<>();
        List<String> decisionMethods = new ArrayList<>();

        if (runEmbeddedBenchmarks) {
            classes.add("EmbeddedBenchmark");
        }
        if (runHttpBenchmarks) {
            classes.add("HttpBenchmark");
        }
        if (runRsocketBenchmarks) {
            classes.add("RsocketBenchmark");
        }
        if (runProtobufRsocketBenchmarks) {
            classes.add("ProtobufRsocketBenchmark");
        }

        if (useNoAuth) {
            authMethods.add("noAuth");
        }
        if (useBasicAuth) {
            authMethods.add("basicAuth");
        }
        if (useAuthApiKey) {
            authMethods.add("apiKey");
        }
        if (useOauth2) {
            authMethods.add("oAuth2");
        }

        if (runDecideOnceBenchmarks) {
            decisionMethods.add("DecideOnce");
        }
        if (runDecideSubscribeBenchmarks) {
            decisionMethods.add("DecideSubscribe");
        }
        return "^io.sapl.benchmark.jmh.(" + StringUtils.join(classes, "|") + ").(" + StringUtils.join(authMethods, "|")
                + ")(" + StringUtils.join(decisionMethods, "|") + ")$";
    }

    /**
     * Load Benchmark configuration from file to BenchmarkConfiguration object.
     */
    public static BenchmarkConfiguration fromFile(String filePath) throws IOException {
        final var file   = new File(filePath);
        final var mapper = new ObjectMapper(new YAMLFactory());
        mapper.configure(DeserializationFeature.FAIL_ON_NULL_CREATOR_PROPERTIES, true);
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, true);
        return mapper.readValue(file, BenchmarkConfiguration.class);
    }

    /**
     * True if this configuration requires a docker environment for benchmark
     * execution
     */
    public boolean requiredDockerEnvironment() {
        return DOCKER.equals(benchmarkTarget) && (runHttpBenchmarks || runRsocketBenchmarks);
    }
}
