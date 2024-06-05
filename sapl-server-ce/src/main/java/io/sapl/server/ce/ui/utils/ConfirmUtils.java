/*
 * Copyright (C) 2017-2024 Dominic Heutelbeck (dominic@heutelbeck.com)
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
package io.sapl.server.ce.ui.utils;

import com.vaadin.flow.component.confirmdialog.ConfirmDialog;

import lombok.NonNull;
import lombok.experimental.UtilityClass;

/**
 * Utilities for letting the user confirm a specific message.
 */
@UtilityClass
public class ConfirmUtils {
    /**
     * Lets the user confirm a specific message.
     *
     * @param header           the dialog title
     * @param message          the message to confirm
     * @param confirmedHandler the handler for confirmation
     * @param cancelledHandler the handler for cancellation
     */
    public static void letConfirm(@NonNull String header, @NonNull String message, Runnable confirmedHandler,
            Runnable cancelledHandler) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(header);
        dialog.setText(message);

        dialog.setCancelable(true);
        dialog.addCancelListener(event -> cancelledHandler.run());

        dialog.setConfirmText("Ok");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(event -> confirmedHandler.run());

        dialog.open();
    }

    public static void inform(@NonNull String header, @NonNull String message, Runnable confirmedHandler) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(header);
        dialog.setText(message);
        dialog.setCancelable(false);
        dialog.setConfirmText("Ok");
        dialog.addConfirmListener(event -> confirmedHandler.run());
        dialog.open();
    }

    public static void inform(@NonNull String header, @NonNull String message) {
        var dialog = new ConfirmDialog();
        dialog.setHeader(header);
        dialog.setText(message);
        dialog.setCancelable(false);
        dialog.setConfirmText("Ok");
        dialog.open();
    }
}
