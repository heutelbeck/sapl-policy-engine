package io.sapl.vaadin.constraint.providers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class VaadinConfirmationDialogTests {

    @Test
    void closeAndConfirm() {
        // GIVEN
        AtomicReference<Boolean> result = new AtomicReference<>();
        var vaadinConfirmationDialog = spy(new VaadinConfirmationDialog(
                "header", "text body",
                "confirm", () -> result.set(true), "cancel", () -> result.set(false)));

        // WHEN
        vaadinConfirmationDialog.closeAndConfirm();

        // THEN
        verify(vaadinConfirmationDialog, times(1)).close();
        assertEquals(true, result.get());
    }

    @Test
    void closeAndCancel() {
        // GIVEN
        AtomicReference<Boolean> result = new AtomicReference<>();
        var vaadinConfirmationDialog = spy(new VaadinConfirmationDialog(
                "header", "text body",
                "confirm", () -> result.set(true), "cancel", () -> result.set(false)));

        // WHEN
        vaadinConfirmationDialog.closeAndCancel();

        // THEN
        verify(vaadinConfirmationDialog, times(1)).close();
        assertEquals(false, result.get());
    }
}
