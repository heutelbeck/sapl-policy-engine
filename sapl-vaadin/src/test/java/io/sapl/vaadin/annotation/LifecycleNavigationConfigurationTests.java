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
package io.sapl.vaadin.annotation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.UIInitEvent;
import com.vaadin.flow.server.UIInitListener;
import com.vaadin.flow.server.VaadinService;

@DisplayName("Lifecycle navigation configuration")
class LifecycleNavigationConfigurationTests {

    LifecycleNavigationConfiguration sut;

    @BeforeEach
    void setup() {
        var vaadinNavigationPepServiceMock = mock(VaadinNavigationPepService.class);
        sut = new LifecycleNavigationConfiguration(vaadinNavigationPepServiceMock);
    }

    @Test
    void when_ServiceInitIsCalled_Then_AddBeforeEnterAndBeforeLeaveListenerIsCalled() {
        // GIVEN
        var serviceInitEventMock = mock(ServiceInitEvent.class);
        var vaadinServiceMock    = mock(VaadinService.class);
        var eventMock            = mock(UIInitEvent.class);
        var uiMock               = mock(UI.class);
        when(eventMock.getUI()).thenReturn(uiMock);
        doAnswer(invocation -> {
            invocation.getArgument(0, UIInitListener.class).uiInit(eventMock);
            return null;
        }).when(vaadinServiceMock).addUIInitListener(any());
        when(serviceInitEventMock.getSource()).thenReturn(vaadinServiceMock);

        // WHEN
        sut.serviceInit(serviceInitEventMock);

        // THEN
        verify(uiMock).addBeforeEnterListener(any());
        verify(uiMock).addBeforeLeaveListener(any());
    }
}
