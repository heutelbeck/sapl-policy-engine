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
import com.vaadin.flow.component.details.Details;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.html.H2;
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
    private static final String COLUMN_NAME_ENTITY    = "entity";
    private static final String COLUMN_NAME_ARGUMENTS = "arguments";
    private static final String COLUMN_NAME_NAME      = "name";
    private static final String COLUMN_NAME_VALUE     = "value";

    private static final String MESSAGE_NAME_REQUIRED                      = "Name is required.";
    private static final String MESSAGE_VALUE_REQUIRED                     = "Value is required.";
    private static final String MESSAGE_ATTRIBUTE_PUBLISHED                = "Attribute published.";
    private static final String MESSAGE_PUBLISH_FAILED_PREFIX              = "Publish failed: ";
    private static final String MESSAGE_SEARCH_FAILED_PREFIX               = "Search failed: ";
    private static final String MESSAGE_LOAD_FAILED_PREFIX                 = "Failed to load attributes: ";
    private static final String MESSAGE_NO_ATTRIBUTES_FOUND                = "No attributes found.";
    private static final String MESSAGE_NO_ATTRIBUTE_FOUND_FOR_NAME_PREFIX = "No attribute found for name '";

    // Internal http client, fixed SonarQube issue because client wasn't transient
    private transient AttributeApiClient client;

    // Search fields
    private final TextField entityField    = new TextField();
    private final TextField nameField      = new TextField();
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

        nameField.setPlaceholder("name");
        nameField.setPrefixComponent(new Span("name ="));
        nameField.addKeyPressListener(Key.ENTER, event -> search());

        argumentsField.setPlaceholder("optional, comma-separated");
        argumentsField.setPrefixComponent(new Span("arguments ="));
        argumentsField.addKeyPressListener(Key.ENTER, event -> search());

        // Set id's for the search fields to find them easier in tests
        entityField.setId("search-entity");
        nameField.setId("search-name");
        argumentsField.setId("search-arguments");

        var searchButton = new Button("Search", event -> search());
        searchButton.setId("search-button");

        // collapsable form to show the search fields when needed
        var searchForm = new FormLayout(entityField, nameField, argumentsField);
        searchForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("30em", 2),
                new FormLayout.ResponsiveStep("60em", 3));

        var searchButtonRow = new HorizontalLayout(searchButton);
        searchButtonRow.setWidthFull();
        searchButtonRow.setJustifyContentMode(JustifyContentMode.END);

        var searchDetails = new Details("Search attributes", new VerticalLayout(searchForm, searchButtonRow));
        searchDetails.setWidthFull();
        searchDetails.setOpened(true);

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

        // Set the id's for the publish field to find them easier in tests
        publishEntityField.setId("publish-entity");
        publishNameField.setId("publish-name");
        publishValueField.setId("publish-value");
        publishTtlField.setId("publish-ttl");
        publishArgumentsField.setId("publish-arguments");

        var publishButton = new Button("Publish", event -> publish());
        publishButton.setId("publish-button");
        publishButton.setEnabled(false);
        publishNameField.addValueChangeListener(event -> updatePublishButtonState(publishButton));
        publishValueField.addValueChangeListener(event -> updatePublishButtonState(publishButton));

        // collapsable form to show the publish fields when needed
        var publishForm = new FormLayout(publishEntityField, publishNameField, publishValueField,
                publishArgumentsField);

        // Prevents a break in the layout by dynamically sizing the elements
        publishForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1), new FormLayout.ResponsiveStep("30em", 2),
                new FormLayout.ResponsiveStep("60em", 3));

        var publishButtonRow = new HorizontalLayout(publishButton);
        publishButtonRow.setWidthFull();
        publishButtonRow.setJustifyContentMode(JustifyContentMode.END);

        var publishDetails = new Details("Publish attribute", new VerticalLayout(publishForm, publishButtonRow));
        publishDetails.setWidthFull();

        var deleteHint = new Span("Select a row and press the delete key to remove it.");
        deleteHint.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size",
                "var(--lumo-font-size-s)");

        add(publishDetails, searchDetails, deleteHint, grid);
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
            Notification.show(MESSAGE_NAME_REQUIRED);
            return;
        }
        if (raw == null || raw.isBlank()) {
            Notification.show(MESSAGE_VALUE_REQUIRED);
            return;
        }

        try {
            var value     = toJsonNode(raw);
            var ttl       = publishTtlField.getValue() == null ? null : publishTtlField.getValue().longValue();
            var arguments = splitArguments(publishArgumentsField.getValue()).stream().map(this::toJsonNode).toList();

            client.publishAttribute(entity, name.trim(), value, ttl, arguments);

            publishNameField.clear();
            publishValueField.clear();
            publishTtlField.clear();
            publishArgumentsField.clear();
            Notification.show(MESSAGE_ATTRIBUTE_PUBLISHED);

            if (nameField.getValue() == null || nameField.getValue().isBlank()) {
                grid.getDataProvider().refreshAll();
            }
        } catch (RuntimeException e) {
            log.warn("Failed to publish attribute (name '{}', entity '{}')", name, entity, e);
            var notification = Notification.show(MESSAGE_PUBLISH_FAILED_PREFIX + e.getMessage());
            notification.addThemeVariants(NotificationVariant.LUMO_ERROR);
        }
    }

    // SonarQube : Refactored
    private void search() {
        var name   = nameField.getValue();
        var entity = entityField.getValue();

        try {
            if (name == null || name.isBlank()) {
                searchAllAttributes();
            } else {
                searchByName(entity, name);
            }
        } catch (RuntimeException e) {
            log.warn("Failed to look up attributes (name '{}', entity '{}')", name, entity, e);
            var notification = Notification.show(MESSAGE_SEARCH_FAILED_PREFIX + e.getMessage());
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
                Notification.show(MESSAGE_LOAD_FAILED_PREFIX + e.getMessage());
                return Stream.empty();
            }
        }, query -> {
            try {
                return client.getAttributeCount().intValue();
            } catch (RuntimeException e) {
                Notification.show(MESSAGE_LOAD_FAILED_PREFIX + e.getMessage());
                return 0;
            }
        });

        if (client.getAttributeCount() == 0) {
            Notification.show(MESSAGE_NO_ATTRIBUTES_FOUND);
        }
    }

    private void searchByName(String entity, String name) {
        var arguments = splitArguments(argumentsField.getValue());

        var value = client.getAttribute(entity, name.trim(), arguments);
        if (value.isPresent()) {
            grid.setItems(List.of(Map.of(COLUMN_NAME_ENTITY, entity == null ? "" : entity, COLUMN_NAME_NAME,
                    name.trim(), COLUMN_NAME_ARGUMENTS, arguments, COLUMN_NAME_VALUE, value.get())));
        } else {
            grid.setItems(List.of());
            Notification.show(MESSAGE_NO_ATTRIBUTE_FOUND_FOR_NAME_PREFIX + name.trim() + "'.");
        }
    }

    private void deleteItem(String entity, String name, List<String> arguments) {
        try {
            if (client.deleteAttribute(entity, name, arguments) == AttributeApiClient.DeleteOutput.DELETED) {
                search();
            }
        } catch (RuntimeException e) {
            Notification.show(MESSAGE_SEARCH_FAILED_PREFIX + e.getMessage());
        }
    }

    Grid<Map<String, Object>> getGrid() {
        return grid;
    }

    private List<String> splitArguments(String rawArguments) {
        if (rawArguments == null || rawArguments.isBlank()) {
            return List.of();
        }

        return Arrays.stream(rawArguments.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }

    private void updatePublishButtonState(Button publishButton) {
        var name  = publishNameField.getValue();
        var value = publishValueField.getValue();
        publishButton.setEnabled(name != null && !name.isBlank() && value != null && !value.isBlank());
    }
}
