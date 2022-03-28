package io.sapl.vaadin.constraint.providers;

import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;

/**
 * This is the ConfirmationDialog for {@link VaadinConfirmationDialogConstraintHandlerProvider}.
 */
class VaadinConfirmationDialog extends Dialog {

    private static final long serialVersionUID = -4757917880261211602L;
    private final String header;
    private final String text;
    private final String confirmText;

    private final Runnable onConfirmListener;
    private final String cancelText;
    private final Runnable onCancelListener;

    public VaadinConfirmationDialog(String header, String text, String confirmText, Runnable onConfirmListener, String cancelText, Runnable onCancelListener) {
        this.header = header;
        this.text = text;
        this.confirmText = confirmText;
        this.onConfirmListener = onConfirmListener;
        this.cancelText = cancelText;
        this.onCancelListener = onCancelListener;
        setCloseOnOutsideClick(false);
        this.add(this.createUI());
    }

    final VerticalLayout createUI() {
        var layout = new VerticalLayout();

        var headline = new H2(this.header);
        headline.getStyle().set("margin", "var(--lumo-space-m) 0 0 0")
                .set("font-size", "1.5em").set("font-weight", "bold");
        layout.add(headline);

        var textComponent = new Text(this.text);
        layout.add(textComponent);

        var confirmButton = new Button(this.confirmText);
        confirmButton.addClickListener(event -> this.closeAndConfirm());
        confirmButton.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        var cancelButton = new Button(this.cancelText);
        cancelButton.addClickListener(event -> this.closeAndCancel());

        HorizontalLayout buttonLayout = new HorizontalLayout(cancelButton, confirmButton);
        buttonLayout.setJustifyContentMode(FlexComponent.JustifyContentMode.END);
        layout.add(buttonLayout);
        return layout;
    }

    void closeAndConfirm() {
        this.close();
        this.onConfirmListener.run();
    }

    void closeAndCancel() {
        this.close();
        this.onCancelListener.run();
    }

}
