package io.sapl.server.ce.views.client;

import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.polymertemplate.Id;
import com.vaadin.flow.component.polymertemplate.PolymerTemplate;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.templatemodel.TemplateModel;

import lombok.NonNull;
import lombok.Setter;

@Tag("show-client-secret")
@JsModule("./show-client-secret.js")
public class ShowClientSecret extends PolymerTemplate<ShowClientSecret.ShowClientSecretModel> {
    @Id(value = "keyTextField")
    private TextField keyTextField;

    @Id(value = "secretTextField")
    private TextField secretTextField;

    @Id(value = "okButton")
    private Button okButton;

    @Setter
    private OnClosingListener onClosingListener;

    public ShowClientSecret(@NonNull String key, @NonNull String secret) {
        initUi(key, secret);
    }

    private void initUi(@NonNull String key, @NonNull String secret) {
        keyTextField.setValue(key);
        secretTextField.setValue(secret);

        okButton.addClickListener((clickEvent) -> {
            if (onClosingListener != null) {
                onClosingListener.onClosing();
            }
        });
    }

    public interface ShowClientSecretModel extends TemplateModel {
    }

    public interface OnClosingListener {
        void onClosing();
    }
}
