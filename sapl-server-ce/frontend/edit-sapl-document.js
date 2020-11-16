import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-vertical-layout.js';
import '@vaadin/vaadin-button/src/vaadin-button.js';
import '@vaadin/flow-frontend/sapl-editor.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@vaadin/vaadin-form-layout/src/vaadin-form-layout.js';
import '@vaadin/vaadin-combo-box/src/vaadin-combo-box.js';
import '@vaadin/vaadin-combo-box/src/vaadin-combo-box-item.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-field.js';

class EditSaplDocument extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-vertical-layout style="width: 100%; height: 100%;">
 <vaadin-horizontal-layout style="flex-wrap: wrap;" theme="spacing-s">
  <vaadin-text-field label="Policy Identifier" id="policyIdTextField" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s);" readonly></vaadin-text-field>
  <vaadin-text-field label="Current Version" id="currentVersionTextField" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s);" readonly></vaadin-text-field>
  <vaadin-text-field label="Last Modified" placeholder="Placeholder" id="lastModifiedTextField" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s);" readonly></vaadin-text-field>
 </vaadin-horizontal-layout>
 <vaadin-horizontal-layout style="flex-wrap: wrap;" theme="spacing-s">
  <vaadin-text-field label="Published Version" id="publishedVersionTextField" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s);" readonly></vaadin-text-field>
  <vaadin-text-field label="Published Name" placeholder="Placeholder" id="publishedNameTextField" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s);" readonly></vaadin-text-field>
 </vaadin-horizontal-layout>
 <vaadin-horizontal-layout>
  <label style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: center;">Version History:</label>
  <vaadin-combo-box id="versionSelectionComboBox" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s);"></vaadin-combo-box>
  <vaadin-button id="publishCurrentVersionButton" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: center;">
    Publish Selected Version 
  </vaadin-button>
  <vaadin-button id="unpublishButton" style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: center;">
    Unpublish 
  </vaadin-button>
 </vaadin-horizontal-layout>
 <sapl-editor id="saplEditor" document="policy \&quot;set by Vaadin View after instantiation ->\\u2588<-\&quot; permit" style="width: 100%; height: 100%;">
   policy \"set by Vaadin View after instantiation -&gt;\\u2588&lt;-\" permit 
 </sapl-editor>
 <vaadin-horizontal-layout style="margin: var(--lumo-space-s); padding: var(--lumo-space-s); align-self: flex-end;">
  <vaadin-button theme="primary" id="saveVersionButton" style="margin: var(--lumo-space-s);">
    Save New Version 
  </vaadin-button>
  <vaadin-button theme="cancel" style="margin: var(--lumo-space-s);" id="cancelButton">
    Cancel 
  </vaadin-button>
 </vaadin-horizontal-layout>
</vaadin-vertical-layout>
`;
    }

    static get is() {
        return 'edit-sapl-document';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(EditSaplDocument.is, EditSaplDocument);
