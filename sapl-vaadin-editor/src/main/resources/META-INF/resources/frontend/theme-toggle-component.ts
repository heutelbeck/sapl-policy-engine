/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Sun/moon theme toggle matching sapl.io pages style.
 * Shows sun icon in dark mode, moon icon in light mode.
 */

import {css, html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators.js';

@customElement('sapl-theme-toggle')
export class ThemeToggleComponent extends LitElement {

    @property({ type: Boolean, attribute: 'dark-mode', reflect: true })
    darkMode: boolean = false;

    static styles = css`
        :host {
            display: inline-flex;
        }

        button {
            background: none;
            border: 1px solid var(--lumo-contrast-20pct, rgba(0, 0, 0, 0.1));
            border-radius: 6px;
            padding: 0.35rem;
            cursor: pointer;
            color: var(--lumo-secondary-text-color, #4a5568);
            display: flex;
            align-items: center;
            justify-content: center;
            transition: color 150ms ease, border-color 150ms ease;
        }

        button:hover {
            color: var(--lumo-primary-text-color, #028392);
            border-color: var(--lumo-primary-text-color, #028392);
        }

        .icon-sun,
        .icon-moon {
            width: 18px;
            height: 18px;
        }

        .icon-sun { display: none; }
        .icon-moon { display: block; }

        :host([dark-mode]) .icon-sun { display: block; }
        :host([dark-mode]) .icon-moon { display: none; }
    `;

    render() {
        return html`
            <button
                @click=${this.toggle}
                aria-label="Toggle dark mode"
                title="Toggle dark mode">
                <svg class="icon-sun" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2">
                    <circle cx="12" cy="12" r="5"/>
                    <path d="M12 1v2M12 21v2M4.22 4.22l1.42 1.42M18.36 18.36l1.42 1.42M1 12h2M21 12h2M4.22 19.78l1.42-1.42M18.36 5.64l1.42-1.42"/>
                </svg>
                <svg class="icon-moon" viewBox="0 0 24 24" fill="none"
                     stroke="currentColor" stroke-width="2">
                    <path d="M21 12.79A9 9 0 1 1 11.21 3 7 7 0 0 0 21 12.79z"/>
                </svg>
            </button>
        `;
    }

    private toggle() {
        this.darkMode = !this.darkMode;
        this.dispatchEvent(new CustomEvent('theme-changed', {
            detail: { darkMode: this.darkMode },
            bubbles: true,
            composed: true
        }));
    }
}
