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
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.browserless.BrowserlessTest;
import com.vaadin.browserless.ViewPackages;
import com.vaadin.browserless.internal.ShortcutsKt;
import io.sapl.attributeapigui.client.AttributeApiClient;
import io.sapl.attributeapigui.connection.ConnectionRegistry;
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
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ViewPackages(packages = "io.sapl.attributeapigui.none")
@ExtendWith(MockitoExtension.class)
class AttributesViewTests extends BrowserlessTest {
    @Mock
    private AttributeApiClient client;

    @Mock
    private ConnectionRegistry registry;

    private AttributesView view;

    @BeforeEach
    void setUp() {
        // Not every test exercises a search/publish/delete action, so this shared
        // stub is lenient to avoid Mockito's strict-stubbing "unused stub" failure.
        lenient().when(registry.activeClient()).thenReturn(client);
        view = new AttributesView(registry);
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

    @Test
    @DisplayName("Pressing the delete key without a selected row does nothing")
    void whenDeleteKeyIsPressedAndNoRowSelectedThenNothingHappens() {
        var grid = view.getGrid();
        ShortcutsKt._fireShortcut(grid, Key.DELETE);

        verify(client, never()).deleteAttribute(any(), any(), any());
    }

    @Test
    @DisplayName("When the publish fails then show an error notification and the form is not cleared")
    void whenPublishFailsThenShowErrorNotificationAndKeepTheEnteredData() {
        doThrow(new RuntimeException("Attribute store not reachable")).when(client).publishAttribute(any(), any(),
                any(), any(), any());

        test(find(TextField.class).id("publish-name")).setValue("sapl.test.attribute");
        test(find(TextField.class).id("publish-value")).setValue("test");
        test(find(Button.class).id("publish-button")).click();

        var notification = find(Notification.class).all().getFirst();
        assertThat(test(notification).getText()).isEqualTo("Publish failed: Attribute store not reachable");
        assertThat(find(TextField.class).id("publish-name").getValue()).isEqualTo("sapl.test.attribute");
    }

    @Test
    @DisplayName("A published attribute with comma-separated arguments is properly split and trimmed")
    void whenPublishAttributeWithArgumentThenArgumentsAreProperlyParsed() {
        test(find(TextField.class).id("publish-name")).setValue("sapl.test.attribute");
        test(find(TextField.class).id("publish-value")).setValue("test");
        test(find(TextField.class).id("publish-arguments")).setValue("arg1, arg2 ,arg3");
        test(find(Button.class).id("publish-button")).click();

        verify(client).publishAttribute(eq(""), eq("sapl.test.attribute"), any(JsonNode.class), isNull(),
                argThat(args -> args.size() == 3));
    }

    @Test
    @DisplayName("When the search query for one attribute fails then show a notification within the UI")
    void whenSearchByNameFailsThenAnErrorNotificationIsShown() {
        when(client.getAttribute(any(), any(), any())).thenThrow(new RuntimeException("timeout"));

        test(find(TextField.class).id("search-name")).setValue("sapl.test.attribute");
        test(find(Button.class).id("search-button")).click();

        var notification = find(Notification.class).all().getFirst();
        assertThat(test(notification).getText()).isEqualTo("Search failed: timeout");
    }

    @Test
    @DisplayName("When the search query for all attribute fails then show a notification within the UI")
    void whenSearchAllFailsThenAnErrorNotificationIsShown() {
        when(client.getAllAttributes(anyInt(), anyInt())).thenThrow(new RuntimeException("timeout. no connection."));
        test(find(Button.class).id("search-button")).click();

        assertThat(test(view.getGrid()).size()).isZero();

        var notification = find(Notification.class).all().getFirst();
        assertThat(test(notification).getText()).isEqualTo("Failed to load attributes: timeout. no connection.");
    }

    @Test
    @DisplayName("Search all attributes with an empty repository shows a notification")
    void whenSearchAllAttributesAndRepositoryEmptyThenShowNotification() {
        when(client.getAllAttributes(anyInt(), anyInt())).thenReturn(List.of());
        test(find(Button.class).id("search-button")).click();

        var notification = find(Notification.class).all().getFirst();
        assertThat(test(notification).getText()).isEqualTo("No attributes found.");
    }

    @Test
    @DisplayName("Search all attributes with a set attribute name shows a notification")
    void whenSearchAllAttributesWithAttributeNameAndRepositoryEmptyThenShowNotification() {
        when(client.getAttribute(any(), any(), any())).thenReturn(Optional.empty());
        test(find(TextField.class).id("search-name")).setValue("sapl.test.missing");
        test(find(Button.class).id("search-button")).click();

        var notification = find(Notification.class).all().getFirst();
        assertThat(test(notification).getText()).isEqualTo("No attribute found for name 'sapl.test.missing'.");
    }

    @Test
    @DisplayName("The publish button is only activated when an attribute name and value are entered")
    void whenAttributeNameAndValueAreEnteredThenPublishButtonIsActivated() {
        var publishButton = find(Button.class).id("publish-button");
        assertThat(publishButton.isEnabled()).isFalse();

        test(find(TextField.class).id("publish-name")).setValue("sapl.test.attribute");
        assertThat(publishButton.isEnabled()).isFalse();

        test(find(TextField.class).id("publish-value")).setValue("test");
        assertThat(publishButton.isEnabled()).isTrue();
    }
}
