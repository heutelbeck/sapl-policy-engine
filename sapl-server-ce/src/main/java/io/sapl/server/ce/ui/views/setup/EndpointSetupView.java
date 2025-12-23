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
package io.sapl.server.ce.ui.views.setup;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Text;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.checkbox.CheckboxGroupVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import io.sapl.api.SaplVersion;
import io.sapl.server.ce.model.setup.*;
import io.sapl.server.ce.ui.utils.ConfirmUtils;
import io.sapl.server.ce.ui.utils.ErrorComponentUtils;
import io.sapl.server.ce.ui.utils.ErrorNotificationUtils;
import io.sapl.server.ce.ui.views.SetupLayout;
import jakarta.annotation.PostConstruct;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serial;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableEntryException;
import java.security.cert.CertificateException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public abstract class EndpointSetupView extends VerticalLayout {

    @Serial
    private static final long serialVersionUID = SaplVersion.VERSION_UID;

    public static final String  ROUTE         = "/setup/rsocket";
    private static final String SUCCESS_COLOR = "var(--lumo-success-color)";
    private static final String WARNING_COLOR = "#e7c200";
    private static final String ERROR_COLOR   = "var(--lumo-error-color)";

    transient ApplicationConfigService applicationConfigService;
    transient EndpointConfig           endpointConfig;
    transient HttpServletRequest       httpServletRequest;

    private final TextField                                adr                    = new TextField("Address");
    private final IntegerField                             port                   = new IntegerField("Port");
    private final TextField                                keyStore               = new TextField("Key store path");
    private final TextField                                keyAlias               = new TextField("Key alias");
    private final PasswordField                            keyStorePassword       = new PasswordField(
            "Key store password");
    private final PasswordField                            keyPassword            = new PasswordField("Key password");
    private final CheckboxGroup<String>                    selectedSslProtocols   = new CheckboxGroup<>(
            "Enabled tls Protocols");
    private final RadioButtonGroup<SupportedKeystoreTypes> keyStoreType           = new RadioButtonGroup<>(
            "Key Store Type");
    private final CheckboxGroup<SupportedCiphers>          ciphers                = new CheckboxGroup<>("TLS ciphers");
    private final Button                                   validateKeyStoreSecret = new Button(
            "Validate keystore settings");
    private final Button                                   endpointSaveConfig     = new Button("Save Configuration");
    private Div                                            tlsDisabledWarning     = ErrorComponentUtils
            .getErrorDiv("Warning: Do not use the option non-TLS setups in production.\n"
                    + "This option may open the server to malicious probing and exfiltration attempts through "
                    + "the authorization endpoints, potentially resulting in unauthorized access to your "
                    + "organization's data, depending on your policies.");
    private Span                                           addressInputValidText;
    private Span                                           portInputValidText;

    protected EndpointSetupView(ApplicationConfigService applicationConfigService,
            EndpointConfig endpointConfig,
            HttpServletRequest httpServletRequest) {
        this.applicationConfigService = applicationConfigService;
        this.endpointConfig           = endpointConfig;
        this.httpServletRequest       = httpServletRequest;
    }

    abstract void persistConfig() throws IOException;

    @PostConstruct
    private void init() {
        if (!httpServletRequest.isSecure()) {
            add(ErrorComponentUtils.getErrorDiv(SetupLayout.INSECURE_CONNECTION_MESSAGE));
        }
        add(getLayout());
    }

    private Component getLayout() {
        selectedSslProtocols.setItems(Arrays.stream(SupportedSslVersions.values())
                .map(SupportedSslVersions::getDisplayName).toArray(String[]::new));

        selectedSslProtocols.setValue(endpointConfig.getEnabledSslProtocols().stream()
                .map(SupportedSslVersions::getDisplayName).collect(Collectors.toSet()));
        selectedSslProtocols.addSelectionListener(e -> updateEndpointConfig());

        setTlsFieldsVisible(endpointConfig.getSslEnabled());
        endpointSaveConfig.addClickListener(e -> {
            try {
                persistConfig();
                ConfirmUtils.inform("saved", "Endpoint setup successfully saved");
            } catch (IOException ioe) {
                ErrorNotificationUtils.show(
                        "Error while writing application.yml-File. Please make sure that the file is not in use and can be written. Otherwise configure the application.yml-file manually. Error: "
                                + ioe.getMessage());
            }
        });
        validateKeyStoreSecret.addClickListener(e -> testKeystore());

        adr.setRequiredIndicatorVisible(true);
        adr.setValueChangeMode(ValueChangeMode.EAGER);
        adr.setValue(endpointConfig.getAddress());
        Div addressInputValid = new Div();
        addressInputValidText = new Span();
        addressInputValid.add(new Text("Input is "), addressInputValidText);
        adr.setHelperComponent(addressInputValid);
        adr.addValueChangeListener(e -> updateEndpointConfig());
        updateAddressHint();

        port.setRequiredIndicatorVisible(true);
        port.setValueChangeMode(ValueChangeMode.EAGER);
        port.setMin(1);
        port.setMax(65535);
        port.setValue(endpointConfig.getPort());

        Div portInputValid = new Div();
        portInputValidText = new Span();
        portInputValidText.setText("Port doesn't match the protocol.");
        portInputValidText.getStyle().setColor(ERROR_COLOR);
        portInputValidText.setVisible(endpointConfig.portAndProtocolsMatch());
        portInputValid.add(new Text("Range from 1 to 65535    "), portInputValidText);
        port.setHelperComponent(portInputValid);

        port.addValueChangeListener(e -> updateEndpointConfig());

        keyStore.setPlaceholder("file:security/keystore.p12");
        keyStore.setRequiredIndicatorVisible(true);
        keyStore.setValueChangeMode(ValueChangeMode.EAGER);
        keyStore.setValue(endpointConfig.getKeyStore());
        keyStore.addValueChangeListener(e -> updateEndpointConfig());

        keyAlias.setPlaceholder("netty");
        keyAlias.setRequiredIndicatorVisible(true);
        keyAlias.setValueChangeMode(ValueChangeMode.EAGER);
        keyAlias.setValue(endpointConfig.getKeyAlias());
        keyAlias.addValueChangeListener(e -> updateEndpointConfig());

        keyStoreType.setItems(EnumSet.allOf(SupportedKeystoreTypes.class));
        keyStoreType.setRequiredIndicatorVisible(true);
        keyStoreType.setValue(endpointConfig.getKeyStoreType());
        keyStoreType.addValueChangeListener(e -> updateEndpointConfig());

        keyStorePassword.setValueChangeMode(ValueChangeMode.EAGER);
        keyStorePassword.setRequiredIndicatorVisible(true);
        keyStorePassword.setValue(endpointConfig.getKeyStorePassword());
        keyStorePassword.addValueChangeListener(e -> updateEndpointConfig());

        keyPassword.setValueChangeMode(ValueChangeMode.EAGER);
        keyPassword.setRequiredIndicatorVisible(true);
        keyPassword.setValue(endpointConfig.getKeyPassword());
        keyPassword.addValueChangeListener(e -> updateEndpointConfig());
        ciphers.setItems(EnumSet.allOf(SupportedCiphers.class));
        ciphers.addSelectionListener(e -> {
            updateEndpointConfig();
            checkIfAtLeastOneCipherOptionSelected();
        });
        ciphers.select(endpointConfig.getCiphers());
        ciphers.addThemeVariants(CheckboxGroupVariant.LUMO_VERTICAL);
        add(ciphers);

        VerticalLayout keyLayout = new VerticalLayout();
        keyLayout.setAlignItems(FlexComponent.Alignment.STRETCH);
        keyLayout.setPadding(false);
        keyLayout.add(keyStore);
        keyLayout.add(keyStorePassword);
        keyLayout.add(keyPassword);
        keyLayout.add(keyAlias);
        keyLayout.add(validateKeyStoreSecret);

        FormLayout tlsLayout = new FormLayout(adr, port, selectedSslProtocols, keyStoreType, ciphers, keyLayout,
                tlsDisabledWarning, endpointSaveConfig);
        tlsLayout.setColspan(tlsDisabledWarning, 2);
        tlsLayout.setColspan(endpointSaveConfig, 2);

        return tlsLayout;
    }

    private void updateAddressHint() {
        if (endpointConfig.isValidURI()) {
            addressInputValidText.setText("valid");
            addressInputValidText.getStyle().setColor(SUCCESS_COLOR);
        } else {
            addressInputValidText
                    .setText("not a valid IP address. Please correct it or make sure to enter a valid hostname.");
            addressInputValidText.getStyle().setColor(WARNING_COLOR);
        }
    }

    private void checkIfAtLeastOneCipherOptionSelected() {
        if (ciphers.getSelectedItems().isEmpty())
            ErrorNotificationUtils.show("At least one cipher option must be selected");
    }

    private void testKeystore() {
        if (keyStore.getValue() == null || !keyStore.getValue().startsWith("file:")) {
            ErrorNotificationUtils.show("Key store path invalid: Key store path must begin with \"file:\"");
            return;
        }
        try {
            if (!endpointConfig.testKeystore()) {
                ErrorNotificationUtils.show("Key alias fault: The given key alias does not exists in this keystore");
                return;
            }
            ConfirmUtils.inform("success", "Keystore settings valid");
            updateEndpointConfig();
        } catch (UnrecoverableEntryException e) {
            ErrorNotificationUtils.show("Key fault: " + e.getMessage());
        } catch (CertificateException e) {
            ErrorNotificationUtils.show("Certificate fault: " + e.getMessage());
        } catch (KeyStoreException e) {
            ErrorNotificationUtils.show("Key store fault: " + e.getMessage());
        } catch (NoSuchAlgorithmException e) {
            ErrorNotificationUtils.show("No such algorithm exception: " + e.getMessage());
        } catch (FileNotFoundException e) {
            ErrorNotificationUtils.show("File not found: " + e.getMessage());
        } catch (IOException e) {
            ErrorNotificationUtils.show("Error: " + e.getMessage());
        }
    }

    private void setTlsFieldsVisible(boolean visible) {
        keyStore.setVisible(visible);
        keyAlias.setVisible(visible);
        keyStoreType.setVisible(visible);
        keyStorePassword.setVisible(visible);
        keyPassword.setVisible(visible);
        ciphers.setVisible(visible);
        validateKeyStoreSecret.setVisible(visible);
        tlsDisabledWarning.setVisible(!visible);
    }

    private void updateEndpointConfig() {
        endpointConfig.setAddress(adr.getValue());
        if (port.getValue() != null) {
            endpointConfig.setPort(port.getValue());
        } else {
            endpointConfig.setPort(-1);
        }
        endpointConfig.setEnabledSslProtocols(selectedSslProtocols.getValue().stream()
                .map(SupportedSslVersions::getByDisplayName).collect(Collectors.toSet()));
        endpointConfig.setCiphers(ciphers.getValue());
        endpointConfig.setKeyStore(keyStore.getValue());
        endpointConfig.setKeyAlias(keyAlias.getValue());
        endpointConfig.setKeyStoreType(keyStoreType.getValue());
        endpointConfig.setKeyStorePassword(keyStorePassword.getValue());
        endpointConfig.setKeyPassword(keyPassword.getValue());

        setTlsFieldsVisible(endpointConfig.getSslEnabled());
        updateAddressHint();
        portInputValidText.setVisible(!endpointConfig.portAndProtocolsMatch());
        endpointSaveConfig.setEnabled(endpointConfig.isValidConfig());

    }

}
