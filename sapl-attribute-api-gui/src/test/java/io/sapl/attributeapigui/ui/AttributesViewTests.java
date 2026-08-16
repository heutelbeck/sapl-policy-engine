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
package io.sapl.attributeapigui.ui;

import com.vaadin.flow.component.Key;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.browserless.internal.ShortcutsKt;
import io.sapl.attributeapigui.client.AttributeApiClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ViewPackages(packages = "io.sapl.attributeapigui.none")
@ExtendWith(MockitoExtension.class)
class AttributesViewTests extends BrowserlessTest {
    @Mock
    private AttributeApiClient client;

    private AttributesView view;

    @BeforeEach
    void setUp() {
        view = new AttributesView(client);
        UI.getCurrent().add(view);
    }

    @Test
    @DisplayName("Send an attribute via form to trigger publish() and clear the form afterwards")
    void whenPublishFormFilledThenClientCalledAndFormCleared() {
        var nameField     = find(TextField.class).id("publish-name");
        var valueField    = find(TextField.class).id("publish-value");
        var publishButton = find(Button.class).id("publish-button");

        test(nameField).setValue("sapl.test.attribute");
        test(valueField).setValue("42");
        test(publishButton).click();

        verify(client).publishAttribute(eq(""), eq("sapl.test.attribute"), any(JsonNode.class), isNull(),
                eq(List.of()));
        assertThat(nameField.getValue()).isEmpty();
        assertThat(valueField.getValue()).isEmpty();
    }

    @Test
    @DisplayName("Selecting a row and pressing delete calls the client and refreshes the UI afterwards")
    void whenRowSelectedAndDeleteKeyPressedThenClientDeletesAndSearchRefreshes() {
        var value = JsonNodeFactory.instance.stringNode("value");

        when(client.getAttribute("sapl.test.entity", "sapl.test.attribute", List.of())).thenReturn(Optional.of(value));
        when(client.deleteAttribute("sapl.test.entity", "sapl.test.attribute", List.of()))
                .thenReturn(AttributeApiClient.DeleteOutput.DELETED);

        var entityField  = find(TextField.class).id("search-entity");
        var nameField    = find(TextField.class).id("search-name");
        var searchButton = find(Button.class).id("search-button");

        test(entityField).setValue("sapl.test.entity");
        test(nameField).setValue("sapl.test.attribute");
        test(searchButton).click();

        var grid = view.getGrid();
        test(grid).select(0);

        // Key events in tests must be aligned with the element otherwise the event is never received
        ShortcutsKt._fireShortcut(grid, Key.DELETE);

        verify(client).deleteAttribute("sapl.test.entity", "sapl.test.attribute", List.of());
        verify(client, times(2)).getAttribute("sapl.test.entity", "sapl.test.attribute", List.of());
    }

    @Test
    @DisplayName("Search all attributes in the UI shows all attributes for the given user")
    void whenSearchWithBlankKeyThenAllAttributesLoaded() {
        var rows = List.<Map<String, Object>>of(
                Map.of("entity", "user:alice", "name", "sapl.test.attribute", "arguments", List.of(), "value", "test1"),
                Map.of("entity", "user:bob", "name", "sapl.test.attribute", "arguments", List.of("2026"), "value",
                        "test2"));
        when(client.getAllAttributes(anyInt(), anyInt())).thenAnswer(invocation -> {
            int limit  = invocation.getArgument(0);
            int offset = invocation.getArgument(1);
            return rows.stream().skip(offset).limit(limit).toList();
        });
        when(client.getAttributeCount()).thenReturn(2L);

        var searchButton = find(Button.class).id("search-button");
        test(searchButton).click();

        var grid = view.getGrid();
        assertThat(test(grid).size()).isEqualTo(2);

        assertThat(test(grid).getCellText(0, 0)).isEqualTo("user:alice");
        assertThat(test(grid).getCellText(0, 1)).isEqualTo("sapl.test.attribute");
        assertThat(test(grid).getCellText(0, 2)).isEqualTo("[]");
        assertThat(test(grid).getCellText(0, 3)).isEqualTo("test1");

        assertThat(test(grid).getCellText(1, 0)).isEqualTo("user:bob");
        assertThat(test(grid).getCellText(1, 1)).isEqualTo("sapl.test.attribute");
        assertThat(test(grid).getCellText(1, 2)).isEqualTo("[2026]");
        assertThat(test(grid).getCellText(1, 3)).isEqualTo("test2");

        verify(client, atLeastOnce()).getAllAttributes(anyInt(), anyInt());
    }
}
