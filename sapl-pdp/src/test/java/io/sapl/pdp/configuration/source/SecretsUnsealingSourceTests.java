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
package io.sapl.pdp.configuration.source;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.nimbusds.jose.jwk.OctetKeyPair;

import io.sapl.api.model.ObjectValue;
import io.sapl.api.model.Value;
import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.api.pdp.configuration.PDPConfiguration;
import io.sapl.api.pdp.configuration.PdpData;
import io.sapl.pdp.configuration.source.PDPConfigurationSource.ConfigurationEvent;
import io.sapl.secrets.SecretSealing;
import lombok.val;

@DisplayName("secrets unsealing source")
class SecretsUnsealingSourceTests {

    private final OctetKeyPair recipientKey = SecretSealing.generateRecipientKey();
    private final FakeSource   delegate     = new FakeSource();

    private SecretsUnsealingSource unsealingSource(boolean acceptUnencryptedSecrets) {
        return new SecretsUnsealingSource(delegate, recipientKey, acceptUnencryptedSecrets);
    }

    @Test
    @DisplayName("a load event is forwarded unsealed to the subscriber")
    void whenLoadArrivesThenSubscriberReceivesConfiguration() {
        val captured = new ArrayList<ConfigurationEvent>();
        unsealingSource(true).subscribe(captured::add);

        delegate.emit(new ConfigurationEvent.NewConfiguration(plaintextSecretConfiguration()));

        assertThat(captured).singleElement().isInstanceOfSatisfying(ConfigurationEvent.NewConfiguration.class,
                load -> assertThat(load.configuration().pdpId()).isEqualTo("pdp"));
    }

    @Test
    @DisplayName("an unseal failure is reported as a configuration error without throwing or leaking secrets")
    void whenUnsealFailsThenReportedAsConfigurationError() {
        val captured = new ArrayList<ConfigurationEvent>();
        unsealingSource(false).subscribe(captured::add);

        // Plaintext secrets with acceptUnencryptedSecrets false is refused by the
        // unsealer, so the definitively broken config is reported as an error event
        // rather than escaping onto the monitoring thread.
        delegate.emit(new ConfigurationEvent.NewConfiguration(plaintextSecretConfiguration()));

        assertThat(captured).singleElement().isInstanceOfSatisfying(ConfigurationEvent.ConfigurationError.class,
                error -> assertThat(error.pdpId()).isEqualTo("pdp")
                        .satisfies(id -> assertThat(error.reason()).doesNotContain("PLAINTEXT")));
    }

    @Test
    @DisplayName("a remove event passes through unchanged")
    void whenRemoveArrivesThenPassedThroughUnchanged() {
        val captured = new ArrayList<ConfigurationEvent>();
        unsealingSource(false).subscribe(captured::add);
        val remove = new ConfigurationEvent.ConfigurationRemoved("pdp-x");

        delegate.emit(remove);

        assertThat(captured).singleElement().isEqualTo(remove);
    }

    @Test
    @DisplayName("unsubscribing removes the wrapper from the delegate so no further events arrive")
    void whenUnsubscribedThenDelegateNoLongerForwards() {
        val                          captured = new ArrayList<ConfigurationEvent>();
        Consumer<ConfigurationEvent> listener = captured::add;
        val                          source   = unsealingSource(true);
        source.subscribe(listener);

        source.unsubscribe(listener);
        delegate.emit(new ConfigurationEvent.NewConfiguration(plaintextSecretConfiguration()));

        assertThat(delegate.listenerCount()).isZero();
        assertThat(captured).isEmpty();
    }

    @Test
    @DisplayName("closing the source closes the delegate")
    void whenClosedThenDelegateClosed() throws Exception {
        val source = unsealingSource(true);

        source.close();

        assertThat(source.isClosed()).isTrue();
    }

    private static PDPConfiguration plaintextSecretConfiguration() {
        val secrets = ObjectValue.builder().put("apiKey", Value.of("PLAINTEXT")).build();
        return new PDPConfiguration("pdp", "config", CombiningAlgorithm.DEFAULT, List.of(),
                new PdpData(Value.EMPTY_OBJECT, secrets));
    }

    private static final class FakeSource implements PDPConfigurationSource {

        private final List<Consumer<ConfigurationEvent>> listeners = new ArrayList<>();

        private boolean closed;

        @Override
        public void subscribe(Consumer<ConfigurationEvent> listener) {
            listeners.add(listener);
        }

        @Override
        public void unsubscribe(Consumer<ConfigurationEvent> listener) {
            listeners.remove(listener);
        }

        @Override
        public boolean isClosed() {
            return closed;
        }

        @Override
        public void close() {
            closed = true;
        }

        private void emit(ConfigurationEvent event) {
            listeners.forEach(listener -> listener.accept(event));
        }

        private int listenerCount() {
            return listeners.size();
        }
    }
}
