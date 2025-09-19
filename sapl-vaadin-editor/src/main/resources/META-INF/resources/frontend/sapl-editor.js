import { LitElement, html, css } from 'lit';
import {
    CodeMirrorStyles,
    CodeMirrorLintStyles,
    CodeMirrorHintStyles,
    XTextAnnotationsStyles,
    AutocompleteWidgetStyle,
    ReadOnlyStyle,
    HeightFix,
    DarkStyle
} from './shared-styles.js';

import { exports as XtextExports } from './xtext-codemirror-patched.js';
import './sapl-mode';

import codemirror from 'codemirror';
import 'codemirror/addon/merge/merge';
import 'codemirror/addon/hint/show-hint';
import 'codemirror/mode/javascript/javascript';

// diff-match-patch for MergeView
import * as DMP from 'diff-match-patch';
const DiffMatchPatch = DMP.default || DMP.diff_match_patch || DMP;
if (typeof window !== 'undefined' && !window.diff_match_patch) {
    window.diff_match_patch = DiffMatchPatch;
    window.DIFF_DELETE = DMP.DIFF_DELETE ?? -1;
    window.DIFF_INSERT = DMP.DIFF_INSERT ?? 1;
    window.DIFF_EQUAL  = DMP.DIFF_EQUAL  ?? 0;
}

// ---- exported configuration id (used by the patched Xtext client) ----
let saplPdpConfigurationId = null;
export { saplPdpConfigurationId };

// ---- component styles (lightweight, uses your shared styles) ----
const HostFix = css`
  :host { display:block; height:100%; }
  #xtext-editor, .CodeMirror, .CodeMirror-merge { height:100%; }
`;

export class SAPLEditor extends LitElement {
    constructor() {
        super();
        this.document = '';
        this.xtextLang = 'sapl';

        // runtime state
        this._editor = undefined;         // current active left editor (CM instance)
        this._mergeView = undefined;      // MergeView instance (when enabled)
        this._rightText = '';             // right content
        this._mergeOptions = {
            revertButtons: true,
            showDifferences: true,
            connect: null,                  // or "align"
            collapseIdentical: false,
            allowEditingOriginals: false,
            ignoreWhitespace: false
        };

        // public defaults
        this._isReadOnly = false;
        this._isLint = true;              // Xtext validate
        this._isDarkTheme = false;

        // behavior
        this.hasLineNumbers = true;
        this.textUpdateDelay = 500;
        this.mergeEnabled = false;        // OFF by default in the component
    }

    static get properties() {
        return {
            document: { type: String },
            isReadOnly: { type: Boolean, attribute: 'is-read-only' },
            hasLineNumbers: { type: Boolean, attribute: 'has-line-numbers' },
            textUpdateDelay: { type: Number, attribute: 'text-update-delay' },
            isLint: { type: Boolean, attribute: 'is-lint' },
            xtextLang: { type: String, attribute: 'xtext-lang' },
            isDarkTheme: { type: Boolean, attribute: 'is-dark-theme' },
            mergeEnabled: { type: Boolean, attribute: 'merge-enabled' }
        };
    }

    static get styles() {
        return [
            CodeMirrorStyles,
            CodeMirrorLintStyles,
            CodeMirrorHintStyles,
            XTextAnnotationsStyles,
            AutocompleteWidgetStyle,
            ReadOnlyStyle,
            HeightFix,
            DarkStyle,
            HostFix
        ];
    }

    // ----- setters/getters forwarders to keep options in sync -----
    set editor(v) {
        const old = this._editor;
        this._editor = v;
        this.requestUpdate('editor', old);
    }
    get editor() { return this._editor; }

    set isReadOnly(v) {
        const old = this._isReadOnly;
        this._isReadOnly = !!v;
        this.requestUpdate('isReadOnly', old);
        this._applyReadOnlyTheme();
        const ed = this._leftEditor();
        if (ed) ed.setOption('readOnly', this._isReadOnly);
    }
    get isReadOnly() { return this._isReadOnly; }

    set isLint(v) {
        const old = this._isLint;
        this._isLint = !!v;
        this.requestUpdate('isLint', old);
        // handled by Xtext services; will be applied on (re)create
    }
    get isLint() { return this._isLint; }

    set isDarkTheme(v) {
        const old = this._isDarkTheme;
        this._isDarkTheme = !!v;
        this.requestUpdate('isDarkTheme', old);
        this._applyTheme();
    }
    get isDarkTheme() { return this._isDarkTheme; }

    // ---------- lifecycle ----------
    firstUpdated() {
        // start in single-editor mode by default
        this._createSingleEditor(this.document ?? '');
    }

    disconnectedCallback() {
        super.disconnectedCallback?.();
        this._destroyEditors();
    }

    render() {
        return html`<div id="xtext-editor"></div>`;
    }

    // ---------- public API called from Java wrapper ----------
    setConfigurationId(id) {
        saplPdpConfigurationId = id ?? null;
    }

    setDocument(text) {
        this.document = text ?? '';
        const ed = this._leftEditor();
        if (ed) ed.doc.setValue(this.document);
    }

    setMergeModeEnabled(enabled) {
        const want = !!enabled;
        if (want === !!this._mergeView) return;
        const leftText = this._leftEditor()?.getValue?.() ?? this.document ?? '';
        if (want) {
            this._createMergeView(leftText, this._rightText ?? '');
        } else {
            this._createSingleEditor(leftText);
        }
    }

    setMergeRightContent(text) {
        this._rightText = text ?? '';
        const right = this._rightEditor();
        if (right) right.setValue(this._rightText);
    }

    setMergeOption(option, value) {
        if (Object.prototype.hasOwnProperty.call(this._mergeOptions, option)) {
            this._mergeOptions[option] = value;
            if (this._mergeView) {
                // rebuild safest
                const leftText = this._leftEditor()?.getValue?.() ?? this.document ?? '';
                const rightText = this._rightEditor()?.getValue?.() ?? this._rightText ?? '';
                this._createMergeView(leftText, rightText);
            }
        }
    }

    nextChange()  { this._leftEditor()?.execCommand?.('goNextDiff'); }
    prevChange()  { this._leftEditor()?.execCommand?.('goPrevDiff'); }

    enableChangeMarkers(_enabled) {
        // no-op here; Xtext markers are already shown; merge chunks stay default
    }

    setDarkTheme(value) { this.isDarkTheme = !!value; }
    setReadOnly(value)  { this.isReadOnly = !!value; }

    // ---------- private helpers ----------
    _container() { return this.shadowRoot.getElementById('xtext-editor'); }

    _leftEditor() {
        if (this._mergeView) return this._mergeView.edit;
        return this.editor;
    }
    _rightEditor() {
        return this._mergeView && this._mergeView.right ? this._mergeView.right.orig : undefined;
    }

    _themeForState() {
        if (this._isReadOnly) return this._isDarkTheme ? 'dracularo' : 'readOnly';
        return this._isDarkTheme ? 'dracula' : 'default';
    }
    _applyReadOnlyTheme() {
        const ed = this._leftEditor();
        if (!ed) return;
        ed.setOption('theme', this._themeForState());
        const right = this._rightEditor();
        if (right) right.setOption('theme', this._themeForState());
    }
    _applyTheme() {
        const ed = this._leftEditor();
        if (ed) ed.setOption('theme', this._themeForState());
        const right = this._rightEditor();
        if (right) right.setOption('theme', this._themeForState());
    }

    _emitChange(value) {
        this.document = value;
        if (this.$server && this.$server.onDocumentChanged) {
            try { this.$server.onDocumentChanged(value); } catch (e) { /* ignore */ }
        }
    }
    _wireValidationCallback(cm) {
        // hook into Xtext validation to forward to server
        const xs = cm?.xtextServices;
        if (!xs || !xs.validationService) return;
        const originalValidate = xs.validate.bind(xs);
        xs.validate = (addParam) => {
            return originalValidate(addParam).done((result) => {
                if (this.$server && this.$server.onValidation) {
                    try { this.$server.onValidation(result.issues); } catch (_) {}
                }
            });
        };
    }

    _destroyEditors() {
        // remove Xtext services if present
        try {
            const ed = this._leftEditor();
            if (ed?.xtextServices) XtextExports.removeServices(ed);
        } catch (_) {}
        // clear DOM
        const c = this._container();
        if (c) c.innerHTML = '';
        this._mergeView = undefined;
        this.editor = undefined;
    }

    _createSingleEditor(text) {
        this._destroyEditors();
        const mount = this._container();
        if (!mount) return;

        // Create a plain CodeMirror, then add Xtext services on it
        const cm = codemirror((el) => mount.appendChild(el), {
            value: text ?? '',
            mode: 'xtext/' + (this.xtextLang || 'sapl'),
            readOnly: !!this._isReadOnly,
            lineNumbers: !!this.hasLineNumbers,
            theme: this._themeForState(),
            gutters: ['annotations-gutter'],
            showCursorWhenSelecting: true
        });

        // Attach Xtext services
        XtextExports.createServices(cm, {
            document: this.shadowRoot,
            xtextLang: this.xtextLang || 'sapl',
            sendFullText: true,
            syntaxDefinition: 'xtext/sapl',
            readOnly: !!this._isReadOnly,
            lineNumbers: !!this.hasLineNumbers,
            showCursorWhenSelecting: true,
            enableValidationService: !!this._isLint,
            textUpdateDelay: this.textUpdateDelay ?? 500,
            gutters: ['annotations-gutter'],
            extraKeys: { 'Ctrl-Space': 'autocomplete' },
            // IMPORTANT: render hint popup outside merge/gap clipping
            hintOptions: { container: document.body, updateOnCursorActivity: false },
            theme: this._themeForState()
        });

        // ensure hint options on CM side too (CM reads from its own option)
        cm.setOption('hintOptions', { container: document.body, updateOnCursorActivity: false });

        cm.on('changes', () => this._emitChange(cm.getValue()));
        this.editor = cm;

        // wire server validation callback
        this._wireValidationCallback(cm);
    }

    _createMergeView(leftText, rightText) {
        this._destroyEditors();
        const mount = this._container();
        if (!mount) return;

        // Build MergeView (lets it create BOTH editors)
        this._mergeView = codemirror.MergeView(mount, {
            value: leftText ?? '',
            origLeft: null,
            origRight: rightText ?? '',
            lineNumbers: !!this.hasLineNumbers,
            mode: 'xtext/' + (this.xtextLang || 'sapl'), // CM mode (Xtext does semantics)
            readOnly: !!this._isReadOnly,
            allowEditingOriginals: !!this._mergeOptions.allowEditingOriginals,
            showDifferences: !!this._mergeOptions.showDifferences,
            revertButtons: !!this._mergeOptions.revertButtons,
            connect: this._mergeOptions.connect,                 // null | "align"
            collapseIdentical: !!this._mergeOptions.collapseIdentical,
            theme: this._themeForState(),
            ignoreWhitespace: !!this._mergeOptions.ignoreWhitespace
        });

        const left = this._mergeView.edit;
        // Attach Xtext services to LEFT editor (no DOM swap)
        XtextExports.createServices(left, {
            document: this.shadowRoot,
            xtextLang: this.xtextLang || 'sapl',
            sendFullText: true,
            syntaxDefinition: 'xtext/sapl',
            readOnly: !!this._isReadOnly,
            lineNumbers: !!this.hasLineNumbers,
            showCursorWhenSelecting: true,
            enableValidationService: !!this._isLint,
            textUpdateDelay: this.textUpdateDelay ?? 500,
            gutters: ['annotations-gutter'],
            extraKeys: { 'Ctrl-Space': 'autocomplete' },
            hintOptions: { container: document.body, updateOnCursorActivity: false },
            theme: this._themeForState()
        });
        left.setOption('hintOptions', { container: document.body, updateOnCursorActivity: false });
        left.on('changes', () => this._emitChange(left.getValue()));
        this.editor = left;

        // Right editor just mirrors options
        const right = this._rightEditor();
        if (right) {
            right.setOption('readOnly', !this._mergeOptions.allowEditingOriginals);
            right.setOption('lineNumbers', !!this.hasLineNumbers);
            right.setOption('theme', this._themeForState());
            right.setOption('lineWrapping', false);
            right.setValue(rightText ?? '');
        }

        // validation forwarding
        this._wireValidationCallback(left);

        // Give MergeView a tick to measure and lay out connectors
        setTimeout(() => {
            try { left.refresh(); } catch (_) {}
            try { right?.refresh?.(); } catch (_) {}
        }, 0);
    }
}

customElements.define('sapl-editor', SAPLEditor);
