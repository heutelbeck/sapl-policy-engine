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
package io.sapl.spring.pdp.embedded;

import io.sapl.reactive.api.pdp.ReactivePolicyDecisionPoint;
import io.sapl.reactive.pdp.DelegatingReactivePolicyDecisionPoint;
import io.sapl.secrets.SecretSealing;
import lombok.val;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class PDPAutoConfigurationTests {

    @TempDir
    Path temporaryDirectory;

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(JsonMapper.class, JsonMapper::new)
            .withConfiguration(AutoConfigurations.of(PDPAutoConfiguration.class));

    @Test
    void whenContextLoads_thenPDPIsCreated() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.embedded.policiesPath=/policies", "io.sapl.pdp.embedded.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(DelegatingReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenAnotherPDPIsAlreadyPresent_thenDoNotLoadANewOne() {
        contextRunner.withBean(ReactivePolicyDecisionPoint.class, () -> mock(ReactivePolicyDecisionPoint.class))
                .withPropertyValues("io.sapl.pdp.embedded.policiesPath=/policies", "io.sapl.pdp.embedded.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(ReactivePolicyDecisionPoint.class)
                            .doesNotHaveBean(DelegatingReactivePolicyDecisionPoint.class);
                });
    }

    @Test
    void whenCatalogueKeyHasMalformedBase64_thenStartupFailureNamesTheOffendingKeyId() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.embedded.policiesPath=/policies", "io.sapl.pdp.embedded.enabled=true",
                        "io.sapl.pdp.embedded.pdpConfigType=BUNDLES",
                        "io.sapl.pdp.embedded.bundle-security.keys.signing-key=not-valid-base64!!!")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("Failed to parse public key 'signing-key' in key catalogue"));
    }

    @Test
    @DisplayName("plural recipient key paths produce one validated shared keyring")
    void whenPluralRecipientKeyPathsConfiguredThenOneValidatedKeyringIsShared() throws IOException {
        val previous     = SecretSealing.generateRecipientKey("previous");
        val current      = SecretSealing.generateRecipientKey("current");
        val previousPath = temporaryDirectory.resolve("previous.jwk");
        val currentPath  = temporaryDirectory.resolve("current.jwk");
        Files.writeString(previousPath, previous.toJSONString());
        Files.writeString(currentPath, current.toJSONString());

        contextRunner.withPropertyValues("io.sapl.pdp.embedded.policiesPath=/policies",
                "io.sapl.pdp.embedded.enabled=true", "io.sapl.pdp.embedded.secrets.accept-unencrypted=true",
                "io.sapl.pdp.embedded.secrets.private-key-paths[0]=" + currentPath,
                "io.sapl.pdp.embedded.secrets.private-key-paths[1]=" + previousPath).run(context -> {
                    assertThat(context).hasNotFailed().hasSingleBean(SecretsKeyringConfiguration.class);
                    val configured = context.getBean(SecretsKeyringConfiguration.class);
                    assertThat(configured.keyIds()).containsExactlyInAnyOrder("current", "previous");
                    assertThat(configured.toString()).doesNotContain(current.toJSONString(), previous.toJSONString());
                });
    }

    @Test
    @DisplayName("a blank entry in a plural recipient key list fails startup closed")
    void whenPluralRecipientKeyListContainsBlankEntryThenStartupFailsClosed() {
        contextRunner
                .withPropertyValues("io.sapl.pdp.embedded.policiesPath=/policies",
                        "io.sapl.pdp.embedded.secrets.private-key-paths[0]= ")
                .run(context -> assertThat(context).hasFailed().getFailure()
                        .hasStackTraceContaining("must not contain null or blank entries"));
    }

    @Test
    @DisplayName("rendering secrets properties excludes inline private keys")
    void whenSecretsPropertiesRenderedThenInlinePrivateKeysAreExcluded() {
        val secret     = SecretSealing.generateRecipientKey("recipient").toJSONString();
        val properties = new EmbeddedPDPProperties.SecretsProperties();
        properties.setPrivateKeys(java.util.List.of(secret));

        assertThat(properties.toString()).doesNotContain(secret);
    }

}
