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
package io.sapl.node.cli.benchmark;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.CombiningAlgorithm;
import io.sapl.api.pdp.CombiningAlgorithm.DefaultDecision;
import io.sapl.api.pdp.CombiningAlgorithm.ErrorHandling;
import io.sapl.api.pdp.CombiningAlgorithm.VotingMode;
import io.sapl.api.pdp.PDPConfiguration;
import io.sapl.api.pdp.PdpData;
import io.sapl.pdp.PolicyDecisionPointBuilder;
import io.sapl.pdp.PolicyDecisionPointBuilder.PDPComponents;
import io.sapl.pdp.configuration.bundle.BundleSecurityPolicy;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

/**
 * Serializable context for passing embedded benchmark configuration to the
 * benchmark runner.
 *
 * @param subscriptionJson the authorization subscription as a JSON string
 * @param policiesPath absolute path to the policy directory or bundle (null
 * for built-in RBAC preset)
 * @param configType DIRECTORY, BUNDLES, or RBAC
 */
public record BenchmarkContext(String subscriptionJson, @Nullable String policiesPath, String configType) {

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    static final String RBAC_CONFIG_TYPE = "RBAC";

    private static final String RBAC_ACTION = "action";
    private static final String RBAC_RESOURCE = "foo123";

    static final String RBAC_POLICY = """
            policy "RBAC"
            permit
                { "type" : resource.type, "action": action } in permissions[(subject.role)];
            """;

    static final String RBAC_SUBSCRIPTION = "{\"subject\":{\"username\":\"bob\",\"role\":\"test\"},\"action\":\"write\",\"resource\":{\"type\":\""
            + RBAC_RESOURCE + "\"}}";

    /**
     * Creates a context for the built-in RBAC benchmark preset. User "bob"
     * with role "test" requests "write" access to "foo123". The "test" role
     * only permits "read", so the expected result is deny.
     *
     * @return a self-contained benchmark context requiring no external files
     */
    public static BenchmarkContext rbacDefault() {
        return new BenchmarkContext(RBAC_SUBSCRIPTION, null, RBAC_CONFIG_TYPE);
    }

    /**
     * Builds an embedded PDP from the policy source configured in this context.
     *
     * @return the built PDP components (caller must dispose)
     */
    public PDPComponents buildEmbeddedPdp() {
        if (RBAC_CONFIG_TYPE.equals(configType)) {
            return buildRbacPdp();
        }
        val builder       = PolicyDecisionPointBuilder.withDefaults();
        val directoryPath = Path.of(policiesPath);
        if ("BUNDLES".equals(configType)) {
            val securityPolicy = BundleSecurityPolicy.builder().disableSignatureVerification().build();
            builder.withBundleDirectorySource(directoryPath, securityPolicy);
        } else {
            builder.withDirectorySource(directoryPath);
        }
        return builder.build();
    }

    private static PDPComponents buildRbacPdp() {
        val algorithm   = new CombiningAlgorithm(VotingMode.PRIORITY_DENY, DefaultDecision.DENY,
                ErrorHandling.PROPAGATE);
        val permissions = ObjectValue.builder().put("dev",
                Value.ofArray(Value.ofObject(Map.of("type", Value.of(RBAC_RESOURCE), RBAC_ACTION, Value.of("write"))),
                        Value.ofObject(Map.of("type", Value.of(RBAC_RESOURCE), RBAC_ACTION, Value.of("read")))))
                .put("test",
                        Value.ofArray(
                                Value.ofObject(Map.of("type", Value.of(RBAC_RESOURCE), RBAC_ACTION, Value.of("read")))))
                .build();
        val variables   = ObjectValue.builder().put("permissions", permissions).build();
        val data        = new PdpData(variables, Value.EMPTY_OBJECT);
        val config      = new PDPConfiguration("benchmark", "rbac-builtin", algorithm, List.of(RBAC_POLICY), data);
        return PolicyDecisionPointBuilder.withDefaults().withConfiguration(config).build();
    }

    public String toJson() {
        return MAPPER.writeValueAsString(this);
    }

    public static BenchmarkContext fromJson(String json) {
        return MAPPER.readValue(json, BenchmarkContext.class);
    }
}
