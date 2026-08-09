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
import com.vaadin.flow.component.Shortcuts;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.router.Menu;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import io.sapl.attributeapigui.client.AttributeApiClient;
import jakarta.annotation.security.RolesAllowed;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

@Slf4j
@Menu(order = 1, icon = "vaadin:list", title = "Attributes")
@Route(value = "", layout = MainLayout.class)
@PageTitle("Attributes")
@RolesAllowed("ADMIN")
public class AttributesView extends VerticalLayout {
    private static String COLUMN_NAME_ENTITY    = "entity";
    private static String COLUMN_NAME_ARGUMENTS = "arguments";
    private static String COLUMN_NAME_NAME      = "name";
    private static String COLUMN_NAME_VALUE     = "value";

    // Internal http client, fixed SonarQube issue because client wasn't transient
    private transient AttributeApiClient client;

    // Search fields
    private final TextField entityField    = new TextField();
    private final TextField keyField       = new TextField();
    private final TextField argumentsField = new TextField();

    // Publish fields
    private final TextField    publishEntityField    = new TextField();
    private final TextField    publishNameField      = new TextField();
    private final TextField    publishValueField     = new TextField();
    private final IntegerField publishTtlField       = new IntegerField();
    private final TextField    publishArgumentsField = new TextField();

    private final ObjectMapper objectMapper = new ObjectMapper();

    // Grid to display the data
    private final Grid<Map<String, Object>> grid = new Grid<>();

    public AttributesView(AttributeApiClient client) {
        this.client = client;

        // Basic settings
        setSizeFull();
        add(new H2("Repository overview"));

        grid.addColumn(entry -> String.valueOf(entry.get(COLUMN_NAME_ENTITY))).setHeader("Entity").setAutoWidth(true);
        grid.addColumn(entry -> String.valueOf(entry.get(COLUMN_NAME_NAME))).setHeader("Name").setAutoWidth(true);
        grid.addColumn(entry -> String.valueOf(entry.get(COLUMN_NAME_ARGUMENTS))).setHeader("Arguments")
                .setAutoWidth(true);
        grid.addColumn(entry -> String.valueOf(entry.get(COLUMN_NAME_VALUE))).setHeader("Value").setAutoWidth(true);
        grid.setSizeFull();
        grid.setItems(List.of());

        // Key events for the grid - Grid does not implement KeyNotifier, so a
        // Shortcut scoped to the grid is used instead of addKeyDownListener.
        Shortcuts.addShortcutListener(grid, () -> {
            var selected = grid.asSingleSelect().getValue();
            if (selected == null) {
                return;
            }
            var entity       = selected.get(COLUMN_NAME_ENTITY);
            var rawArguments = selected.get(COLUMN_NAME_ARGUMENTS);

            // Generics are erased at runtime, so only the raw List type is checkable here.
            var arguments = rawArguments instanceof List<?> rawList ? rawList.stream().map(String::valueOf).toList()
                    : List.<String>of();
            deleteItem(entity == null ? null : entity.toString(), selected.get(COLUMN_NAME_NAME).toString(), arguments);
        }, Key.DELETE).listenOn(grid);

        // Search fields
        entityField.setPlaceholder("optional");
        entityField.setPrefixComponent(new Span("entity ="));
        entityField.addKeyPressListener(Key.ENTER, event -> search());

        keyField.setPlaceholder("key");
        keyField.setPrefixComponent(new Span("key ="));
        keyField.addKeyPressListener(Key.ENTER, event -> search());

        argumentsField.setPlaceholder("optional, comma-separated");
        argumentsField.setPrefixComponent(new Span("arguments ="));
        argumentsField.addKeyPressListener(Key.ENTER, event -> search());

        var searchButton = new Button("Search", event -> search());
        var searchRow    = new HorizontalLayout(entityField, keyField, argumentsField, searchButton);

        // Publish fields
        publishEntityField.setPlaceholder("optional");
        publishEntityField.setPrefixComponent(new Span("entity ="));

        publishNameField.setPlaceholder("name (required)");
        publishNameField.setPrefixComponent(new Span("name ="));

        publishValueField.setPlaceholder("JSON or text value (required)");
        publishValueField.setPrefixComponent(new Span("value ="));

        publishTtlField.setPlaceholder("optional, seconds");
        publishTtlField.setPrefixComponent(new Span("ttl ="));

        publishArgumentsField.setPlaceholder("optional, comma-separated");
        publishArgumentsField.setPrefixComponent(new Span("arguments ="));

        var publishButton = new Button("Publish", event -> publish());
        var publishRow    = new HorizontalLayout(publishEntityField, publishNameField, publishValueField,
                publishTtlField, publishArgumentsField, publishButton);

        add(new H3("Publish attribute"), publishRow, searchRow, grid);
        setFlexGrow(1, grid);
        // End search fields
    }

    private JsonNode toJsonNode(String raw) {
        try {
            return objectMapper.readTree(raw);
        } catch (JacksonException e) {
            return objectMapper.getNodeFactory().stringNode(raw);
        }
    }

    private void publish() {
        var entity = publishEntityField.getValue();
        var name   = publishNameField.getValue();
        var raw    = publishValueField.getValue();

        if (name == null || name.isBlank()) {
            Notification.show("Name is required.");
            return;
        }
        if (raw == null || raw.isBlank()) {
            Notification.show("Value is required.");
            return;
        }

        try {
            var value = toJsonNode(raw);
            var ttl   = publishTtlField.getValue() == null ? null : publishTtlField.getValue().longValue();

            var rawArguments = publishArgumentsField.getValue();
            var arguments    = (rawArguments == null || rawArguments.isBlank()) ? List.<JsonNode>of()
                    : Arrays.stream(rawArguments.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                            .map(this::toJsonNode).toList();

            client.publishAttribute(entity, name.trim(), value, ttl, arguments);

            publishNameField.clear();
            publishValueField.clear();
            publishTtlField.clear();
            publishArgumentsField.clear();
            Notification.show("Attribute published.");

            if (keyField.getValue() == null || keyField.getValue().isBlank()) {
                grid.getDataProvider().refreshAll();
            }
        } catch (RuntimeException e) {
            log.warn("Failed to publish attribute (name '{}', entity '{}')", name, entity, e);
            var notification = Notification.show("Publish failed: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    // SonarQube : Refactored
    private void search() {
        var key    = keyField.getValue();
        var entity = entityField.getValue();

        try {
            if (key == null || key.isBlank()) {
                searchAllAttributes();
            } else {
                searchByKey(entity, key);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to look up attributes (key '{}', entity '{}')", key, entity, e);
            var notification = Notification.show("Search failed: " + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    private void searchAllAttributes() {
        grid.setItems(query -> {
            try {
                return client.getAllAttributes(query.getLimit(), query.getOffset()).stream();
            } catch (RuntimeException e) {
                log.warn("Failed to fetch attributes page (limit {}, offset {})", query.getLimit(), query.getOffset(),
                        e);
                Notification.show("Fehler beim Laden: " + e.getMessage());
                return Stream.empty();
            }
        }, query -> client.getAttributeCount().intValue());

        if (client.getAttributeCount() == 0) {
            Notification.show("No attributes found.");
        }
    }

    private void searchByKey(String entity, String key) {
        var rawArguments = argumentsField.getValue();
        var arguments    = (rawArguments == null || rawArguments.isBlank()) ? List.<String>of()
                : Arrays.stream(rawArguments.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();

        var value = client.getAttribute(entity, key.trim(), arguments);
        if (value.isPresent()) {
            grid.setItems(List.of(Map.of(COLUMN_NAME_ENTITY, entity == null ? "" : entity, COLUMN_NAME_NAME, key.trim(),
                    COLUMN_NAME_ARGUMENTS, arguments, COLUMN_NAME_VALUE, value.get())));
        } else {
            grid.setItems(List.of());
            Notification.show("No attribute found for key '" + key.trim() + "'.");
        }
    }

    private void deleteItem(String entity, String name, List<String> arguments) {
        if (client.deleteAttribute(entity, name, arguments)) {
            search();
        }
    }
}
