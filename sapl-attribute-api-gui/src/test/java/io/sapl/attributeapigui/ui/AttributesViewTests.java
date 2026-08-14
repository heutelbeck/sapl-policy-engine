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

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.textfield.TextField;
import io.sapl.attributeapigui.client.AttributeApiClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AttributesViewTests {

    @Mock
    private AttributeApiClient client;

    private AttributesView view;

    @BeforeEach
    void setUp() {
        UI.setCurrent(new UI());
        view = new AttributesView(client);
    }

    @AfterEach
    void tearDown() {
        UI.setCurrent(null);
    }

    @Test
    @DisplayName("The publish() method sends the entered attribute and clears the form afterwards")
    void whenPublishFormFilledThenClientCalledAndFormCleared() {
        var nameField  = (TextField) ReflectionTestUtils.getField(view, "publishNameField");
        var valueField = (TextField) ReflectionTestUtils.getField(view, "publishValueField");
        nameField.setValue("sapl.test.attribute");
        valueField.setValue("42");

        ReflectionTestUtils.invokeMethod(view, "publish");

        verify(client).publishAttribute(eq(""), eq("sapl.test.attribute"), any(JsonNode.class), isNull(),
                eq(List.of()));
        assertThat(nameField.getValue()).isEmpty();
        assertThat(valueField.getValue()).isEmpty();
    }
}
