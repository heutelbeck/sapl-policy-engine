import {css, html, LitElement} from 'lit';
import {
    AutocompleteWidgetStyle,
    CodeMirrorHintStyles,
    CodeMirrorLintStyles,
    CodeMirrorStyles,
    DarkStyle,
    HeightFix,
    ReadOnlyStyle,
    XTextAnnotationsStyles
} from './shared-styles.js';

import './sapl-test-mode';
import {exports as xtext} from './xtext-codemirror';

import CodeMirror from 'codemirror';
import 'codemirror/addon/lint/lint';
import 'codemirror/addon/merge/merge';
import * as DMP from 'diff-match-patch';

const MergeHeightFix = css`
    :host { display:block; height:100%; }
    #sapltest-editor { height:100%; }
    .CodeMirror-merge, .CodeMirror { height:100%; }
`;

const MergeLayout = css`
    .CodeMirror-merge { position:relative; height:100%; white-space:pre; }
    .CodeMirror-merge, .CodeMirror-merge .CodeMirror { height:100%; }
    .CodeMirror-merge-2pane .CodeMirror-merge-pane { width:47%; }
    .CodeMirror-merge-2pane .CodeMirror-merge-gap  { width:6%;  }
    .CodeMirror-merge-3pane .CodeMirror-merge-pane { width:31%; }
    .CodeMirror-merge-3pane .CodeMirror-merge-gap  { width:3.5%; }
    .CodeMirror-merge-pane { display:inline-block; white-space:normal; vertical-align:top; height:100%; box-sizing:border-box; }
    .CodeMirror-merge-pane-rightmost { position:absolute; right:0; z-index:1; top:0; bottom:0; }
    .CodeMirror-merge-gap { z-index:2; display:inline-block; height:100%; box-sizing:border-box; overflow:hidden; position:relative; }
`;

const MergeControls = css`
    .CodeMirror-merge-scrolllock-wrap { position:absolute; bottom:0; left:50%; }
    .CodeMirror-merge-scrolllock { position:relative; left:-50%; cursor:pointer; color:var(--sapl-merge-arrow, #378b8a); line-height:1; }
    .CodeMirror-merge-scrolllock:after { content:"\\21db\\00a0\\00a0\\21da"; }
    .CodeMirror-merge-scrolllock.CodeMirror-merge-scrolllock-enabled:after { content:"\\21db\\21da"; }
    .CodeMirror-merge-copybuttons-left, .CodeMirror-merge-copybuttons-right { position:absolute; left:0; top:0; right:0; bottom:0; line-height:1; }
    .CodeMirror-merge-copy, .CodeMirror-merge-copy-reverse { position:absolute; cursor:pointer; color:var(--sapl-merge-arrow, #378b8a); z-index:3; }
    .CodeMirror-merge-copybuttons-left  .CodeMirror-merge-copy { left:2px; }
    .CodeMirror-merge-copybuttons-right .CodeMirror-merge-copy { right:2px; }
`;

const MergeColorOverrides = css`
    .CodeMirror-merge-l-connect, .CodeMirror-merge-r-connect {
        fill: var(--sapl-merge-connector, #252a2e);
        stroke: var(--sapl-merge-connector, #252a2e);
        stroke-width: 1px;
        opacity: 1;
    }
`;

const MergeArrowStrongOverrides = css`
    .CodeMirror-merge-gap .CodeMirror-merge-copy,
    .CodeMirror-merge-gap .CodeMirror-merge-copy-reverse,
    .CodeMirror-merge-gap .CodeMirror-merge-scrolllock,
    .CodeMirror-merge-gap .CodeMirror-merge-scrolllock::after {
        color: var(--sapl-merge-arrow, #378b8a) !important;
    }
`;

class SAPLTestEditor extends LitElement {
    constructor() {
        super();
        this.document = '';
        this.xtextLang = 'sapltest';
        this.mergeEnabled = false;
        this._isReadOnly = false;
        this._isLint = true;
        this._isDarkTheme = false;
        this.hasLineNumbers = true;
        this.textUpdateDelay = 500;
        this._mergeOptions = {
            revertButtons: true,
            showDifferences: true,
            connect: null,
            collapseIdentical: false,
            allowEditingOriginals: false,
            ignoreWhitespace: false
        };
        this._rightMergeText = '';
        this._editor = undefined;
        this._mergeView = undefined;
        this._shadowHintContainer = null;
        this._resizeObserver = null;
        this._keydownHandler = null;
        this._showHintPatched = false;
    }

    static get properties() {
        return {
            document: { type: String },
            isReadOnly: { type: Boolean },
            hasLineNumbers: { type: Boolean },
            textUpdateDelay: { type: Number },
            editor: { type: Object },
            xtextLang: { type: String },
            isLint: { type: Boolean },
            isDarkTheme: { type: Boolean },
            mergeEnabled: { type: Boolean }
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
            MergeHeightFix,
            MergeLayout,
            MergeControls,
            MergeColorOverrides,
            MergeArrowStrongOverrides
        ];
    }

    set editor(v) {
        const old = this._editor;
        this._editor = v;
        this.requestUpdate('editor', old);
        this._applyBasicOptionsToCurrentEditor();
    }
    get editor() { return this._editor; }

    set isReadOnly(v) {
        const old = this._isReadOnly;
        this._isReadOnly = v;
        this.requestUpdate('isReadOnly', old);
        this._setEditorOption('readOnly', v);
    }
    get isReadOnly(){ return this._isReadOnly; }

    set isLint(v) {
        const old = this._isLint;
        this._isLint = v;
        this.requestUpdate('isLint', old);
        this._setLintOption(v);
    }
    get isLint(){ return this._isLint; }

    set isDarkTheme(v) {
        const old = this._isDarkTheme;
        this._isDarkTheme = v;
        this.requestUpdate('isDarkTheme', old);
        this._setDarkTheme(v);
    }
    get isDarkTheme(){ return this._isDarkTheme; }

    firstUpdated() {
        const DiffMatchPatch = DMP.default || DMP.diff_match_patch || DMP;
        if (typeof window.diff_match_patch === 'undefined') {
            window.diff_match_patch = DiffMatchPatch;
            window.DIFF_DELETE = DMP.DIFF_DELETE ?? -1;
            window.DIFF_INSERT = DMP.DIFF_INSERT ??  1;
            window.DIFF_EQUAL  = DMP.DIFF_EQUAL  ??  0;
        }

        const widget = document.createElement('div');
        widget.id = 'widgetContainer';
        this._shadowHintContainer = widget;
        this.shadowRoot.appendChild(widget);

        this._patchShowHintOnce();
        this._ensureGlobalHintCSS();

        const start = () => {
            if (this.mergeEnabled) this._createMergeView(this.document, this._rightMergeText ?? '');
            else this._createSingleEditor(this.document);
        };
        const host = this._container();
        if (host && host.clientHeight === 0) requestAnimationFrame(start); else start();

        this._keydownHandler = (e) => {
            if (e.altKey && e.key === 'ArrowDown') { try { this.editor?.execCommand('goNextDiff'); } catch{} e.preventDefault(); }
            if (e.altKey && e.key === 'ArrowUp')   { try { this.editor?.execCommand('goPrevDiff'); } catch{} e.preventDefault(); }
        };
        this.addEventListener('keydown', this._keydownHandler, true);

        this._resizeObserver = new ResizeObserver(() => this._refresh());
        this._resizeObserver.observe(this._container());

        this._applyThemeVars();
    }

    disconnectedCallback() {
        super.disconnectedCallback?.();
        try { this._resizeObserver?.disconnect(); } catch {}
        if (this._keydownHandler) this.removeEventListener('keydown', this._keydownHandler, true);
    }

    render() { return html`<div id="sapltest-editor"></div>`; }
    _container(){ return this.shadowRoot.getElementById('sapltest-editor'); }

    _teardown() {
        const c = this._container();
        if (c) c.innerHTML = '';
        this._mergeView = undefined;
        this._editor = undefined;
    }

    _createSingleEditor(value) {
        this._teardown();

        const cm = CodeMirror(this._container(), {
            value: value ?? '',
            mode: 'xtext/sapltest',
            readOnly: this.isReadOnly === true,
            lineNumbers: this.hasLineNumbers,
            showCursorWhenSelecting: true,
            gutters: ['annotations-gutter'],
            theme: this._themeName(),
            extraKeys: { 'Ctrl-Space': 'autocomplete' },
            hintOptions: {
                container: this._shadowHintContainer,
                updateOnCursorActivity: false,
                completeSingle: false
            }
        });

        let isInternalUpdate = false;

        cm._isInternalUpdate = () => isInternalUpdate;
        cm._setInternalUpdate = (value) => { isInternalUpdate = value; };

        this._preventUnwantedChanges(cm, () => isInternalUpdate);

        cm.getDoc().on('change', () => {
            if (isInternalUpdate) return;
            const valueNow = cm.getValue();
            this._onDocumentChanged(valueNow);
        });

        xtext.createServices(cm, {
            document: this.shadowRoot,
            xtextLang: this.xtextLang,
            sendFullText: true,
            syntaxDefinition: 'xtext/sapltest',
            baseUrl: '/sapl-test/',
            enableValidationService: this._isLint === true,
            textUpdateDelay: this.textUpdateDelay,
            showErrorDialogs: false
        });

        this._hookValidation(cm);

        this.editor = cm;
        this._applyBasicOptionsToCurrentEditor();
    }

    _createMergeView(left, right) {
        this._teardown();

        const mount = this._container();
        if (!mount) {
            console.error('[_createMergeView] Container not ready');
            return;
        }

        if ((mount.clientHeight || 0) < 50) mount.style.height = '400px';

        this._mergeView = CodeMirror.MergeView(mount, {
            value: left ?? '',
            origLeft: null,
            origRight: right ?? '',
            mode: 'xtext/sapltest',
            readOnly: this.isReadOnly === true,
            allowEditingOriginals: this._mergeOptions.allowEditingOriginals,
            showDifferences: this._mergeOptions.showDifferences,
            revertButtons: this._mergeOptions.revertButtons,
            connect: this._mergeOptions.connect,
            collapseIdentical: this._mergeOptions.collapseIdentical,
            theme: this._themeName()
        });

        const main = this._mergeView.edit;
        const origRight = this._mergeView.right && this._mergeView.right.orig;

        let isInternalUpdate = false;

        main._isInternalUpdate = () => isInternalUpdate;
        main._setInternalUpdate = (value) => { isInternalUpdate = value; };

        this._preventUnwantedChanges(main, () => isInternalUpdate);

        main.getDoc().on('change', () => {
            if (isInternalUpdate) return;
            const v = main.getValue();
            this._onDocumentChanged(v);
            requestAnimationFrame(() => this._refresh());
        });

        xtext.createServices(main, {
            document: this.shadowRoot,
            xtextLang: this.xtextLang,
            sendFullText: true,
            syntaxDefinition: 'xtext/sapltest',
            baseUrl: '/sapl-test/',
            enableValidationService: this._isLint === true,
            textUpdateDelay: this.textUpdateDelay,
            showErrorDialogs: false
        });

        main.setOption('hintOptions', {
            container: document.body,
            updateOnCursorActivity: false,
            completeSingle: false,
            closeOnUnfocus: false
        });

        if (origRight) {
            origRight.setOption('readOnly', !this._mergeOptions.allowEditingOriginals);
            origRight.setOption('mode', 'xtext/sapltest');
            origRight.setOption('lineNumbers', this.hasLineNumbers);
            origRight.setOption('theme', this._themeName());
        }

        this._hookValidation(main);

        this.editor = main;

        requestAnimationFrame(() => this._refresh());
    }

    /// Prevents unwanted changes during text selection by validating change origins and content.
    ///
    /// Protects against spurious change events (e.g., from Xtext autocomplete or validation)
    /// that can delete selected text. Allows only legitimate user actions:
    /// - +input: typing (must contain actual content)
    /// - +delete: backspace/delete keys
    /// - paste, cut, *compose: clipboard and IME operations
    ///
    /// @param cm CodeMirror instance to protect
    /// @param isInternalUpdateFn function returning whether updates are internal (programmatic)
    _preventUnwantedChanges(cm, isInternalUpdateFn) {
        cm.on('beforeChange', (instance, changeObj) => {
            if (isInternalUpdateFn()) {
                return;
            }

            const hasSelection = cm.somethingSelected();
            if (!hasSelection) {
                return;
            }

            const sel = cm.listSelections()[0];
            const selStart = sel.anchor.line < sel.head.line ||
            (sel.anchor.line === sel.head.line && sel.anchor.ch < sel.head.ch)
                ? sel.anchor : sel.head;
            const selEnd = sel.anchor.line > sel.head.line ||
            (sel.anchor.line === sel.head.line && sel.anchor.ch > sel.head.ch)
                ? sel.anchor : sel.head;

            const changeAffectsSelection = (
                changeObj.from.line === selStart.line &&
                changeObj.from.ch === selStart.ch &&
                changeObj.to.line === selEnd.line &&
                changeObj.to.ch === selEnd.ch
            );

            if (!changeAffectsSelection) {
                changeObj.cancel();
                return;
            }

            if (changeObj.origin === '+input') {
                const isSingleLineWithContent = changeObj.text.length === 1 && changeObj.text[0].length > 0;
                if (!isSingleLineWithContent) {
                    changeObj.cancel();
                    return;
                }
            }

            const userActions = ['+delete', 'paste', 'cut', '*compose'];
            if (!userActions.includes(changeObj.origin) && changeObj.origin !== '+input') {
                changeObj.cancel();
                return;
            }
        });
    }

    _hookValidation(cm) {
        const xs = cm.xtextServices;
        if (!xs || xs._wrappedValidate) return;
        xs._wrappedValidate = true;

        const original = xs.validate;
        xs.validate = (addParam) => {
            return original.call(xs, addParam).done((result) => {
                if (this.$server && this.$server.onValidation) {
                    try { this.$server.onValidation(result.issues); } catch(e) { console.error(e); }
                }
            });
        };
    }

    _themeName() {
        if (this._isReadOnly) return this._isDarkTheme ? 'dracularo' : 'readOnly';
        return this._isDarkTheme ? 'dracula' : 'default';
    }

    _applyBasicOptionsToCurrentEditor() {
        const cm = this.editor;
        if (!cm) return;
        cm.setOption('theme', this._themeName());
        cm.setOption('lineNumbers', this.hasLineNumbers);
        cm.setOption('readOnly', this._isReadOnly === true);
    }

    _setEditorOption(option, value) {
        const cm = this.editor;
        if (cm) {
            if (option === 'readOnly') cm.setOption('theme', this._themeName());
            cm.setOption(option, value);
        }
        const right = this._mergeView?.right?.orig;
        if (right && option === 'readOnly')
            right.setOption('readOnly', !this._mergeOptions.allowEditingOriginals);
        if (right && option === 'lineNumbers')
            right.setOption('lineNumbers', value);
        this._refresh();
    }

    _setDarkTheme(v) {
        const cm = this.editor;
        if (cm) cm.setOption('theme', this._themeName());
        const right = this._mergeView?.right?.orig;
        if (right) right.setOption('theme', this._themeName());
        this._applyThemeVars();
        this._refresh();
    }

    _setLintOption(v) {
        const cm = this.editor;
        if (!cm) return;
        xtext.createServices(cm, {
            document: this.shadowRoot,
            xtextLang: this.xtextLang,
            sendFullText: true,
            syntaxDefinition: 'xtext/sapltest',
            baseUrl: '/sapl-test/',
            enableValidationService: v === true,
            textUpdateDelay: this.textUpdateDelay,
            showErrorDialogs: false
        });
        this._hookValidation(cm);
    }

    setEditorDocument(_el, doc) {
        this.document = doc;

        const cm = this.editor;
        if (!cm) return;

        const currentValue = cm.getValue();
        if (currentValue === doc) return;

        if (cm._setInternalUpdate) {
            cm._setInternalUpdate(true);
        }

        try {
            cm.getDoc().setValue(doc ?? '');
        } finally {
            if (cm._setInternalUpdate) {
                cm._setInternalUpdate(false);
            }
        }
    }

    setMergeModeEnabled(enabled) {
        this.mergeEnabled = !!enabled;

        const container = this._container();
        if (!container) {
            return;
        }

        const leftText = this.editor ? this.editor.getValue() : (this.document ?? '');
        if (this.mergeEnabled) this._createMergeView(leftText, this._rightMergeText ?? '');
        else this._createSingleEditor(leftText);
    }

    enableMergeView(){ this.setMergeModeEnabled(true); }
    disableMergeView(){ this.setMergeModeEnabled(false); }

    setMergeRightContent(content) {
        this._rightMergeText = content ?? '';
        const right = this._mergeView?.right?.orig;
        if (right) right.setValue(this._rightMergeText);
        this._refresh();
    }

    setMergeOption(option, value) {
        if (option === 'revertButtons') this._mergeOptions.revertButtons = !!value;
        else if (option === 'showDifferences') this._mergeOptions.showDifferences = !!value;
        else if (option === 'connect') this._mergeOptions.connect = value;
        else if (option === 'collapseIdentical') this._mergeOptions.collapseIdentical = !!value;
        else if (option === 'allowEditingOriginals') this._mergeOptions.allowEditingOriginals = !!value;
        else if (option === 'ignoreWhitespace') this._mergeOptions.ignoreWhitespace = !!value;
        else return;

        if (this._mergeView) {
            const left = this.editor?.getValue() ?? this.document ?? '';
            const right = this._mergeView?.right?.orig?.getValue() ?? this._rightMergeText ?? '';
            this._createMergeView(left, right);
        }
    }

    goToNextChange() { try { this.editor?.execCommand('goNextDiff'); } catch {} }
    goToPreviousChange() { try { this.editor?.execCommand('goPrevDiff'); } catch {} }

    _onDocumentChanged(v) {
        this.document = v;
        if (this.$server && this.$server.onDocumentChanged) {
            try { this.$server.onDocumentChanged(v); } catch (e) { console.error(e); }
        }
    }

    _applyThemeVars() {
        const isDark = this._isDarkTheme === true;
        const connector = isDark ? '#252a2e' : '#c7d1d6';
        const arrow     = isDark ? '#5ac8c7' : '#378b8a';
        this.style.setProperty('--sapl-merge-connector', connector);
        this.style.setProperty('--sapl-merge-arrow', arrow);
    }

    _refresh() {
        try { this.editor?.refresh(); } catch {}
        try {
            const l = this._mergeView?.left;  const r = this._mergeView?.right;
            l?.forceUpdate?.('full'); r?.forceUpdate?.('full');
        } catch {}
    }

    _patchShowHintOnce() {
        if (this._showHintPatched || !CodeMirror || !CodeMirror.showHint) return;
        this._showHintPatched = true;
        const orig = CodeMirror.showHint;
        CodeMirror.showHint = function(cm, getHints, options) {
            const patched = Object.assign(
                {
                    container: document.body,
                    completeSingle: false,
                    closeOnUnfocus: false
                },
                options || {}
            );
            return orig.call(this, cm, getHints, patched);
        };
    }

    _ensureGlobalHintCSS() {
        if (document.getElementById('cm-hint-global-style')) return;
        const s = document.createElement('style');
        s.id = 'cm-hint-global-style';
        s.textContent = `
      .CodeMirror-hints {
        position: absolute; z-index: 2147483647; list-style: none; margin: 0; padding: 2px;
        font-family: monospace; font-size: 12px; max-height: 20em; overflow-y: auto;
        background: #fff; color: #000;
        border: 1px solid rgba(0,0,0,.2); box-shadow: 0 2px 6px rgba(0,0,0,.15);
      }
      .CodeMirror-hint { margin: 0; padding: 2px 6px; white-space: pre; cursor: pointer; }
      .CodeMirror-hint-active { background: #08f; color: #fff; }
    `;
        document.head.appendChild(s);
    }
}

customElements.define('sapl-test-editor', SAPLTestEditor);