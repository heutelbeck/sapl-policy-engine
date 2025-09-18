import { LitElement, html, css } from 'lit';

const MergeHeightFix = css`
    :host { display: block; height: 100%; }
    #json-editor { height: 100%; }
    .CodeMirror-merge, .CodeMirror { height: 100%; }
`;

const MergeLayout = css`
    .CodeMirror-merge { display: flex; align-items: stretch; gap: 12px; }
    .CodeMirror-merge .CodeMirror-merge-pane { flex: 1 1 0; min-width: 0; }
    .CodeMirror-merge .CodeMirror-merge-gap { flex: 0 0 12px; }
    .CodeMirror-merge .CodeMirror { height: 100%; }
`;

import { CodeMirrorStyles, CodeMirrorLintStyles, CodeMirrorHintStyles, HeightFix, ReadOnlyStyle, DarkStyle } from './shared-styles.js';
import codemirror from 'codemirror';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/addon/lint/lint';
import 'codemirror/addon/lint/json-lint';
import * as jsonlint from 'jsonlint-webpack';
import 'codemirror/addon/merge/merge';
import 'codemirror/addon/merge/merge.css';
import * as DMP from 'diff-match-patch';

const DiffMatchPatch = DMP.default || DMP.diff_match_patch || DMP;
const DIFF_DELETE    = DMP.DIFF_DELETE ?? -1;
const DIFF_INSERT    = DMP.DIFF_INSERT ??  1;
const DIFF_EQUAL     = DMP.DIFF_EQUAL  ??  0;

class JSONEditor extends LitElement {

    constructor() {
        super();
        this.document = "";
        this._editor = undefined;
        this._mergeView = undefined;
        this._mergeOptions = {
            revertButtons: true,
            showDifferences: true,
            connect: null,
            collapseIdentical: false
        };
        this._rightMergeText = "";
        this.mergeEnabled = false;
    }

    static get properties() {
        return {
            document: { type: String },
            isReadOnly: { type: Boolean },
            hasLineNumbers: { type: Boolean },
            autoCloseBrackets: { type: Boolean },
            matchBrackets: { type: Boolean },
            textUpdateDelay: { type: Number },
            editor: { type: Object },
            isLint: { type: Boolean },
            isDarkTheme: { type: Boolean },
            mergeEnabled: { type: Boolean }
        }
    }

    static get styles() {
        return [
            CodeMirrorStyles,
            CodeMirrorLintStyles,
            CodeMirrorHintStyles,
            HeightFix,
            ReadOnlyStyle,
            DarkStyle,
            MergeHeightFix,
            MergeLayout
        ]
    }

    set editor(value) {
        let oldVal = this._editor;
        this._editor = value;
        this.requestUpdate('editor', oldVal);
        this.onEditorChangeCheckOptions(value);
    }

    get editor() {
        return this._editor;
    }

    set isReadOnly(value) {
        let oldVal = this._isReadOnly;
        this._isReadOnly = value;
        this.requestUpdate('isReadOnly', oldVal);
        this.setEditorOption('readOnly', value);
    }

    get isReadOnly() {
        return this._isReadOnly;
    }

    set isLint(value) {
        let oldVal = this._isLint;
        this._isLint = value;
        this.requestUpdate("isLint", oldVal);
        this.setLintEditorOption(value);
    }

    get isLint() {
        return this._isLint;
    }

    set isDarkTheme(value) {
        let oldVal = this._isDarkTheme;
        this._isDarkTheme = value;
        this.requestUpdate("isDarkTheme", oldVal);
        this.setDarkThemeEditorOption(value);
    }

    get isDarkTheme() {
        return this._isDarkTheme;
    }

    firstUpdated(changedProperties) {
        window.jsonlint = jsonlint;
        if (typeof window.diff_match_patch === 'undefined') {
            window.diff_match_patch = DiffMatchPatch;
            window.DIFF_DELETE = DIFF_DELETE;
            window.DIFF_INSERT = DIFF_INSERT;
            window.DIFF_EQUAL  = DIFF_EQUAL;
        }
        if (this.mergeEnabled) {
            this._createMergeView(this.document, this._rightMergeText ?? "");
            this._dispatchMergeModeToggled(true);
        } else {
            this._createSingleEditor(this.document);
        }
    }

    _editorContainer() {
        return this.shadowRoot.querySelector('#json-editor');
    }

    _tearDownEditors() {
        const container = this._editorContainer();
        if (container) {
            container.innerHTML = '';
        }
        this._mergeView = undefined;
        this._editor = undefined;
    }

    _createSingleEditor(value) {
        const container = this._editorContainer();
        this._tearDownEditors();
        this.editor = codemirror(container, {
            value: value ?? "",
            mode: "application/json",
            gutters: ["CodeMirror-lint-markers"],
            readOnly: this.isReadOnly,
            lineNumbers: this.hasLineNumbers,
            showCursorWhenSelecting: true,
            textUpdateDelay: this.textUpdateDelay,
            lint: { selfContain: true },
            theme: "default"
        });
        this._attachMainEditorListeners(this.editor);
        this.onEditorChangeCheckOptions(this.editor);
    }

    _createMergeView(leftValue, rightValue) {
        const container = this._editorContainer();
        this._tearDownEditors();
        if (!codemirror.MergeView || typeof window.diff_match_patch !== 'function') {
            this._createSingleEditor(leftValue ?? "");
            return;
        }
        this._mergeView = codemirror.MergeView(container, {
            value: leftValue ?? "",
            origLeft: null,
            origRight: rightValue ?? "",
            lineNumbers: this.hasLineNumbers,
            mode: "application/json",
            readOnly: this.isReadOnly === true ? true : false,
            allowEditingOriginals: false,
            showDifferences: this._mergeOptions.showDifferences,
            revertButtons: this._mergeOptions.revertButtons,
            connect: this._mergeOptions.connect,
            collapseIdentical: this._mergeOptions.collapseIdentical,
            gutters: ["CodeMirror-lint-markers"],
            lint: this._lintOptionValue(),
            theme: this._themeForCurrentState()
        });
        const main = this._mergeView.edit;
        this._attachMainEditorListeners(main);
        const right = this._mergeView.right && this._mergeView.right.orig;
        if (right) {
            right.setOption('readOnly', true);
            right.setOption('lineNumbers', this.hasLineNumbers);
            right.setOption('mode', 'application/json');
            right.setOption('theme', this._themeForCurrentState());
        }
    }

    _getMainEditor() {
        return this._mergeView ? this._mergeView.edit : this.editor;
    }

    _getRightEditor() {
        if (!this._mergeView) return undefined;
        return this._mergeView.right && this._mergeView.right.orig ? this._mergeView.right.orig : undefined;
    }

    _attachMainEditorListeners(cm) {
        cm.on("change", (instance, changeObj) => {
            const value = instance.getValue();
            this.onDocumentChanged(value);
        });
        cm.on("mousedown", (instance, event) => {
            const line = instance.lineAtHeight(event.clientY, "client");
            const content = instance.getLine(line);
            this.onEditorClicked(line + 1, content);
        });
    }

    _themeForCurrentState() {
        if (this._isReadOnly === true) {
            if (this._isDarkTheme === true) {
                return 'dracularo';
            }
            return 'readOnly';
        }
        if (this._isDarkTheme === true) {
            return 'dracula';
        }
        return 'default';
    }

    _lintOptionValue() {
        return this._isLint === true ? { selfContain: true } : false;
    }

    onDocumentChanged(value) {
        this.document = value;
        if (this.$server) {
            this.$server.onDocumentChanged(value);
        }
    }

    onEditorClicked(value, content) {
        if (this.$server) {
            this.$server.onEditorClicked(value, content);
        }
    }

    setEditorDocument(element, document) {
        this.document = document;
        const main = this._getMainEditor();
        if (main !== undefined) {
            main.doc.setValue(document);
        }
    }

    setEditorOption(option, value) {
        const main = this._getMainEditor();
        if (main !== undefined) {
            if (option === 'readOnly') {
                if (value === true) {
                    if (this._isDarkTheme === true) {
                        main.setOption("theme", 'dracularo');
                    } else {
                        main.setOption("theme", 'readOnly');
                    }
                } else if (this._isDarkTheme === true) {
                    main.setOption("theme", 'dracula');
                } else {
                    main.setOption("theme", 'default');
                }
            }
            main.setOption(option, value);
        }
        const right = this._getRightEditor();
        if (right && (option === 'lineNumbers' || option === 'mode')) {
            right.setOption(option, value);
        }
        if (right && option === 'readOnly') {
            right.setOption('readOnly', true);
        }
    }

    onEditorChangeCheckOptions(editor) {
        if (editor !== undefined) {
            this.setEditorOption('readOnly', this.isReadOnly);
            this.setDarkThemeEditorOption(this.isDarkTheme);
            this.setLintEditorOption(this.isLint);
        }
    }

    onRefreshEditor() {
        const main = this._getMainEditor();
        if (main) {
            main.refresh();
        }
    }

    render() {
        return html`<div id="json-editor"></div>`;
    }

    scrollToBottom() {
        const main = this._getMainEditor();
        if (main) {
            const scrollInfo = main.getScrollInfo();
            main.scrollTo(null, scrollInfo.height);
        }
    }

    appendText(text) {
        const main = this._getMainEditor();
        if (main) {
            const lines = main.lineCount();
            main.replaceRange(text + "\n", { line: lines });
        }
    }

    setDarkThemeEditorOption(value) {
        const main = this._getMainEditor();
        if (main) {
            if (value === true) {
                main.setOption("theme", 'dracula');
            } else {
                this.setEditorOption('readOnly', this._isReadOnly);
            }
        }
        const right = this._getRightEditor();
        if (right) {
            right.setOption('theme', this._themeForCurrentState());
        }
    }

    setLintEditorOption(value) {
        const main = this._getMainEditor();
        if (main) {
            if (value === true) {
                main.setOption("lint", { selfContain: true });
            } else if (value === false) {
                main.setOption("lint", false);
            }
        }
    }

    setMergeModeEnabled(enabled) {
        this.mergeEnabled = !!enabled;
        const leftText = this._getMainEditor() ? this._getMainEditor().getValue() : (this.document ?? "");
        if (this.mergeEnabled) {
            this._createMergeView(leftText, this._rightMergeText ?? "");
            this._dispatchMergeModeToggled(true);
        } else {
            this._createSingleEditor(leftText);
            this._dispatchMergeModeToggled(false);
        }
    }

    enableMergeView() {
        this.setMergeModeEnabled(true);
    }

    disableMergeView() {
        this.setMergeModeEnabled(false);
    }

    setMergeRightContent(content) {
        this._rightMergeText = content ?? "";
        const right = this._getRightEditor();
        if (right) {
            right.setValue(this._rightMergeText);
        }
    }

    setMergeOption(option, value) {
        if (option === 'revertButtons') {
            this._mergeOptions.revertButtons = !!value;
        } else if (option === 'showDifferences') {
            this._mergeOptions.showDifferences = !!value;
        } else if (option === 'connect') {
            this._mergeOptions.connect = value;
        } else if (option === 'collapseIdentical') {
            this._mergeOptions.collapseIdentical = value;
        } else {
            return;
        }
        if (this._mergeView) {
            const leftText = this._getMainEditor().getValue();
            const rightText = this._getRightEditor() ? this._getRightEditor().getValue() : this._rightMergeText;
            this._createMergeView(leftText, rightText);
        }
    }

    _dispatchMergeModeToggled(active) {
        this.dispatchEvent(new CustomEvent('sapl-merge-mode-toggled', {
            bubbles: true, composed: true, detail: { active }
        }));
    }

    updateMergeRightContent(content) { this.setMergeRightContent(content); }
    enableMergeViewWithContent(content) { this.setMergeRightContent(content); this.setMergeModeEnabled(true); }
}

customElements.define('json-editor', JSONEditor);
