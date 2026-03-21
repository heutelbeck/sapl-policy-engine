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
package io.sapl.vaadin.theme;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.ComponentEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.DomEvent;
import com.vaadin.flow.component.EventData;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.shared.Registration;
import io.sapl.api.SaplVersion;
import lombok.Getter;

import java.io.Serial;

/**
 * Sun/moon theme toggle button matching the sapl.io pages style.
 * Shows a sun icon in dark mode and a moon icon in light mode.
 * Fires {@link ThemeToggleEvent} when clicked.
 */
@Tag("sapl-theme-toggle")
@JsModule("./theme-toggle-component.ts")
public class ThemeToggleButton extends Component {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    /**
     * Sets the current dark mode state of the toggle.
     *
     * @param darkMode true for dark mode (shows sun icon), false for light mode
     * (shows moon icon)
     */
    public void setDarkMode(boolean darkMode) {
        getElement().setProperty("darkMode", darkMode);
    }

    /**
     * Returns the current dark mode state.
     *
     * @return true if dark mode is active
     */
    public boolean isDarkMode() {
        return getElement().getProperty("darkMode", false);
    }

    /**
     * Adds a listener for theme toggle events.
     *
     * @param listener the listener to add
     * @return a registration for removing the listener
     */
    public Registration addThemeToggleListener(ComponentEventListener<ThemeToggleEvent> listener) {
        return addListener(ThemeToggleEvent.class, listener);
    }

    /**
     * Fired when the toggle button is clicked.
     */
    @DomEvent("theme-changed")
    @Getter
    public static class ThemeToggleEvent extends ComponentEvent<ThemeToggleButton> {

        @Serial
        private static final long serialVersionUID = SaplVersion.VERSION_UID;

        private final boolean darkMode;

        public ThemeToggleEvent(ThemeToggleButton source,
                boolean fromClient,
                @EventData("event.detail.darkMode") boolean darkMode) {
            super(source, fromClient);
            this.darkMode = darkMode;
        }
    }
}
