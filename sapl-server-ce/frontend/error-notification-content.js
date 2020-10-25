import {html, PolymerElement} from '@polymer/polymer/polymer-element.js';
import '@vaadin/vaadin-ordered-layout/src/vaadin-horizontal-layout.js';
import '@polymer/iron-icon/iron-icon.js';
import '@vaadin/vaadin-text-field/src/vaadin-text-area.js';

class ErrorNotificationContent extends PolymerElement {

    static get template() {
        return html`
<style include="shared-styles">
                :host {
                    display: block;
                    height: 100%;
                }
            </style>
<vaadin-horizontal-layout style="width: 100%; height: 100%;">
 <iron-icon style="align-self: center; margin: var(--lumo-space-s); padding: var(--lumo-space-s); color: red; flex-shrink: 0; height: 50px;
  width: 50px;" icon="vaadin:close-circle"></iron-icon>
 <div id="errorMessageDiv" style="padding: var(--lumo-space-s); margin: var(--lumo-space-s); align-self: center; flex-grow: 1; color: rgb(255, 100, 100)">
  default error message
 </div>
</vaadin-horizontal-layout>
`;
    }

    static get is() {
        return 'error-notification-content';
    }

    static get properties() {
        return {
            // Declare your properties here.
        };
    }
}

customElements.define(ErrorNotificationContent.is, ErrorNotificationContent);
