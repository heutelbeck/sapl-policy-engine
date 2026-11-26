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
package io.sapl.playground.ui;

import static com.vaadin.browserless.BrowserlessApplicationContext.forComponent;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.tabs.Tab;

import io.sapl.api.pdp.configuration.CombiningAlgorithm;
import io.sapl.pdp.configuration.PdpStatus;
import io.sapl.playground.config.PermalinkConfiguration;
import io.sapl.playground.domain.PermalinkService;
import io.sapl.playground.domain.PlaygroundPolicyDecisionPoint;
import io.sapl.playground.domain.PlaygroundValidator;
import io.sapl.playground.ui.components.DocumentationDrawer;
import lombok.val;
import tools.jackson.databind.json.JsonMapper;

class PlaygroundPermalinkTests {

    static Stream<Arguments> sharedTabs() {
        return Stream.of(Arguments.of(null, "Variables"), Arguments.of(0, "first"), Arguments.of(1, "second"));
    }

    @ParameterizedTest(name = "Opening a shared playground restores the {1} tab")
    @MethodSource("sharedTabs")
    void whenOpeningASharedPlaygroundThenItsSelectedTabIsRestored(Integer selectedPolicy, String expectedTab)
            throws PermalinkService.PermalinkException {
        val mapper    = JsonMapper.builder().build();
        val validator = new PlaygroundValidator(mapper);
        val links     = new PermalinkService(mapper, validator);
        val drawer    = mock(DocumentationDrawer.class);
        val pdp       = mock(PlaygroundPolicyDecisionPoint.class);
        when(drawer.getToggleButton()).thenReturn(new Button());
        when(pdp.getPdpStatus()).thenReturn(PdpStatus.initial());

        val state = new PermalinkService.PlaygroundState(List.of("policy \"first\" permit", "policy \"second\" deny"),
                "{\"subject\":null,\"action\":null,\"resource\":null}", "{}", CombiningAlgorithm.DEFAULT,
                selectedPolicy);
        try (val app = forComponent(
                () -> new PlaygroundView(mapper, pdp, validator, drawer, links, new PermalinkConfiguration()))) {
            val window = app.newUser().newWindow();
            window.find(PlaygroundView.class).single().handleHashChange("permalink/" + links.encode(state));
            assertThat(window.find(Tab.class).all()).filteredOn(Tab::isSelected)
                    .extracting(tab -> tab.getElement().getTextRecursively()).contains(expectedTab);
        }
    }
}
