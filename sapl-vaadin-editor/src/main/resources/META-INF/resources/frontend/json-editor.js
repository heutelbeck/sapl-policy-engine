import { LitElement, html, css } from 'lit';

const MergeHeightFix = css`
    :host { display: block; height: 100%; }
    #json-editor { height: 100%; }
    .CodeMirror-merge, .CodeMirror { height: 100%; }
`;

const MergeLayout = css`
    .CodeMirror-merge { position: relative; height: 100%; white-space: pre; }
    .CodeMirror-merge, .CodeMirror-merge .CodeMirror { height: 100%; }
    .CodeMirror-merge-2pane .CodeMirror-merge-pane { width: 47%; }
    .CodeMirror-merge-2pane .CodeMirror-merge-gap  { width: 6%;  }
    .CodeMirror-merge-3pane .CodeMirror-merge-pane { width: 31%; }
    .CodeMirror-merge-3pane .CodeMirror-merge-gap  { width: 3.5%; }
    .CodeMirror-merge-pane { display: inline-block; white-space: normal; vertical-align: top; height: 100%; box-sizing: border-box; }
    .CodeMirror-merge-pane-rightmost { position: absolute; right: 0; z-index: 1; }
    .CodeMirror-merge-gap { z-index: 2; display: inline-block; height: 100%; box-sizing: border-box; overflow: hidden; position: relative; }
`;

const MergeControls = css`
    .CodeMirror-merge-scrolllock-wrap { position: absolute; bottom: 0; left: 50%; }
    .CodeMirror-merge-scrolllock { position: relative; left: -50%; cursor: pointer; color: var(--sapl-merge-arrow, #378b8a); line-height: 1; }
    .CodeMirror-merge-scrolllock:after { content: "\\21db\\00a0\\00a0\\21da"; }
    .CodeMirror-merge-scrolllock.CodeMirror-merge-scrolllock-enabled:after { content: "\\21db\\21da"; }
    .CodeMirror-merge-copybuttons-left, .CodeMirror-merge-copybuttons-right { position: absolute; left: 0; top: 0; right: 0; bottom: 0; line-height: 1; }
    .CodeMirror-merge-copy, .CodeMirror-merge-copy-reverse { position: absolute; cursor: pointer; color: var(--sapl-merge-arrow, #378b8a); z-index: 3; }
    .CodeMirror-merge-copybuttons-left  .CodeMirror-merge-copy { left: 2px; }
    .CodeMirror-merge-copybuttons-right .CodeMirror-merge-copy { right: 2px; }
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

const ChangeMarkerStyles = css`
    .cm-merge-chunk-line { background: rgba(255, 200, 0, 0.15); }
    .cm-merge-gutter-marker { width: 8px; height: 8px; border-radius: 50%; display: inline-block; }
    .cm-merge-gutter-marker.changed { background: #f39c12; }
`;

import { CodeMirrorStyles, CodeMirrorLintStyles, CodeMirrorHintStyles, HeightFix, ReadOnlyStyle, DarkStyle } from './shared-styles.js';
import codemirror from 'codemirror';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/addon/lint/lint';
import 'codemirror/addon/lint/json-lint';
import * as jsonlint from 'jsonlint-webpack';
import 'codemirror/addon/merge/merge';
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
            collapseIdentical: false,
            allowEditingOriginals: false,
            ignoreWhitespace: false
        };

        this._rightMergeText = "";
        this.mergeEnabled = false;

        this._changeMarkersEnabled = true;

        this._chunkList = [];
        this._gutterId = 'merge-changes';
        this._appliedLineClassesLeft = [];
        this._appliedLineClassesRight = [];
        this._appliedGutterMarkersLeft = [];
        this._appliedGutterMarkersRight = [];
        this._recalcChunksDebounced = null;
        this._resizeObserver = null;

        this._isReadOnly = false;
        this._isLint = true;
        this._isDarkTheme = false;
        this.hasLineNumbers = true;
        this.textUpdateDelay = 0;

        this._mainScrollHandler = null;
        this._rightScrollHandler = null;
        this._keydownHandler = null;

        this._largeDocThresholdLines = 50000;
        this._largeDocThresholdBytes = 2 * 1024 * 1024;
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
        };
    }

    static get styles() {
        return [
            CodeMirrorStyles, CodeMirrorLintStyles, CodeMirrorHintStyles, HeightFix,
            ReadOnlyStyle, DarkStyle, MergeHeightFix, MergeLayout, MergeControls,
            MergeColorOverrides, ChangeMarkerStyles, MergeArrowStrongOverrides
        ];
    }

    set editor(value) { const o=this._editor; this._editor=value; this.requestUpdate('editor',o); this.onEditorChangeCheckOptions(value); }
    get editor() { return this._editor; }

    set isReadOnly(v){const o=this._isReadOnly; this._isReadOnly=v; this.requestUpdate('isReadOnly',o); this.setEditorOption('readOnly', v);}
    get isReadOnly(){return this._isReadOnly;}

    set isLint(v){const o=this._isLint; this._isLint=v; this.requestUpdate("isLint",o); this.setLintEditorOption(v);}
    get isLint(){return this._isLint;}

    set isDarkTheme(v){const o=this._isDarkTheme; this._isDarkTheme=v; this.requestUpdate("isDarkTheme",o); this.setDarkThemeEditorOption(v);}
    get isDarkTheme(){return this._isDarkTheme;}

    firstUpdated() {
        if (typeof window.jsonlint === 'undefined') window.jsonlint = jsonlint;
        if (typeof window.diff_match_patch === 'undefined') {
            window.diff_match_patch = DiffMatchPatch;
            window.DIFF_DELETE = DIFF_DELETE;
            window.DIFF_INSERT = DIFF_INSERT;
            window.DIFF_EQUAL  = DIFF_EQUAL;
        }

        const start = () => {
            if (this.mergeEnabled) {
                this._createMergeView(this.document, this._rightMergeText ?? "");
                this._dispatchMergeModeToggled(true);
            } else {
                this._createSingleEditor(this.document);
            }
            const ro = new ResizeObserver(() => this._scheduleRecalcChunks());
            this._resizeObserver = ro;
            const container = this._editorContainer();
            if (container) ro.observe(container);
        };
        const container = this._editorContainer();
        if (container && container.clientHeight === 0) requestAnimationFrame(start); else start();

        this._applyThemeVars();

        this._keydownHandler = (e) => {
            if (e.altKey && e.key === 'ArrowDown') { this.nextChange(); e.preventDefault(); }
            if (e.altKey && e.key === 'ArrowUp')   { this.prevChange(); e.preventDefault(); }
        };
        this.addEventListener('keydown', this._keydownHandler, true);
    }

    disconnectedCallback() {
        super.disconnectedCallback?.();
        try { this._resizeObserver?.disconnect(); } catch(_) {}
        this._detachAllHandlers();
        this._destroyEditors();
    }

    _editorContainer() { return this.shadowRoot.querySelector('#json-editor'); }

    _destroyEditors() {
        this._mergeView = undefined;
        this._editor = undefined;
        this._clearChangeMarkers();
        this._chunkList = [];
    }

    _detachAllHandlers() {
        const main = this._getMainEditor();
        const right = this._getRightEditor();
        if (main && this._mainScrollHandler) try { main.getScrollerElement().removeEventListener('scroll', this._mainScrollHandler); } catch(_) {}
        if (right && this._rightScrollHandler) try { right.getScrollerElement().removeEventListener('scroll', this._rightScrollHandler); } catch(_) {}
        if (this._keydownHandler) try { this.removeEventListener('keydown', this._keydownHandler, true); } catch(_) {}
        this._mainScrollHandler = null;
        this._rightScrollHandler = null;
        this._keydownHandler = null;
    }

    _tearDownEditors() {
        const container = this._editorContainer();
        if (container) container.innerHTML = '';
        this._detachAllHandlers();
        this._destroyEditors();
    }

    _createSingleEditor(value) {
        const container = this._editorContainer();
        this._tearDownEditors();
        const opts = {
            value: value ?? "",
            mode: "application/json",
            gutters: ["CodeMirror-lint-markers"],
            readOnly: this.isReadOnly,
            lineNumbers: this.hasLineNumbers,
            showCursorWhenSelecting: true,
            textUpdateDelay: this.textUpdateDelay,
            lint: this._shouldEnableLintFor(value) ? { selfContain: true } : false,
            theme: "default"
        };
        this.editor = codemirror(container, opts);

        let isInternalUpdate = false;

        this.editor._isInternalUpdate = () => isInternalUpdate;
        this.editor._setInternalUpdate = (value) => { isInternalUpdate = value; };

        this._preventUnwantedChanges(this.editor, () => isInternalUpdate);

        this.editor.on("change", () => {
            if (isInternalUpdate) return;

            const value = this.editor.getValue();
            this.onDocumentChanged(value);
            this._scheduleRecalcChunks();
        });

        this.editor.on("click", (instance, event) => {
            const line = instance.lineAtHeight(event.clientY, "client");
            const content = instance.getLine(line);
            this.onEditorClicked(line + 1, content);
        });

        this._mainScrollHandler = () => this._scheduleRecalcChunks();
        this.editor.getScrollerElement().addEventListener('scroll', this._mainScrollHandler);

        this.onEditorChangeCheckOptions(this.editor);
        this._scheduleRecalcChunks();
    }

    _createMergeView(leftValue, rightValue) {
        const container = this._editorContainer();
        this._tearDownEditors();

        if (!container) {
            console.error('[_createMergeView] Container not ready');
            return;
        }

        if (!codemirror.MergeView || typeof window.diff_match_patch !== 'function') {
            this._createSingleEditor(leftValue ?? "");
            return;
        }

        const lintLeft  = this._shouldEnableLintFor(leftValue);
        const lintRight = this._shouldEnableLintFor(rightValue);

        this._mergeView = codemirror.MergeView(container, {
            value: leftValue ?? "",
            origLeft: null,
            origRight: rightValue ?? "",
            lineNumbers: this.hasLineNumbers,
            mode: "application/json",
            readOnly: this.isReadOnly === true,
            allowEditingOriginals: this._mergeOptions.allowEditingOriginals,
            showDifferences: this._mergeOptions.showDifferences,
            revertButtons: this._mergeOptions.revertButtons,
            connect: this._mergeOptions.connect,
            collapseIdentical: this._mergeOptions.collapseIdentical,
            gutters: ["CodeMirror-lint-markers"],
            lint: lintLeft ? { selfContain: true } : false,
            theme: this._themeForCurrentState(),
            ignoreWhitespace: this._mergeOptions.ignoreWhitespace
        });

        const main = this._mergeView.edit;

        let isInternalUpdate = false;

        main._isInternalUpdate = () => isInternalUpdate;
        main._setInternalUpdate = (value) => { isInternalUpdate = value; };

        this._preventUnwantedChanges(main, () => isInternalUpdate);

        main.on("change", () => {
            if (isInternalUpdate) return;

            const value = main.getValue();
            this.onDocumentChanged(value);
            this._scheduleRecalcChunks();
        });

        main.on("click", (instance, event) => {
            const line = instance.lineAtHeight(event.clientY, "client");
            const content = instance.getLine(line);
            this.onEditorClicked(line + 1, content);
        });

        this._mainScrollHandler = () => this._scheduleRecalcChunks();
        main.getScrollerElement().addEventListener('scroll', this._mainScrollHandler);

        const right = this._mergeView.right && this._mergeView.right.orig;
        if (right) {
            right.setOption('readOnly', !this._mergeOptions.allowEditingOriginals);
            right.setOption('lineNumbers', this.hasLineNumbers);
            right.setOption('mode', 'application/json');
            right.setOption('theme', this._themeForCurrentState());
            right.setOption('lint', lintRight ? { selfContain: true } : false);
            right.on("change", () => this._scheduleRecalcChunks());
            this._rightScrollHandler = () => this._scheduleRecalcChunks();
            right.getScrollerElement().addEventListener('scroll', this._rightScrollHandler);
        }

        this._scheduleRecalcChunks();
    }

    /// Prevents unwanted changes during text selection by validating change origins and content.
    ///
    /// Protects against spurious change events (e.g., from linting or validation processes)
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

    _getMainEditor()  { return this._mergeView ? this._mergeView.edit : this.editor; }
    _getRightEditor() { return this._mergeView && this._mergeView.right ? this._mergeView.right.orig : undefined; }

    _themeForCurrentState() {
        if (this._isReadOnly === true) return this._isDarkTheme === true ? 'dracularo' : 'readOnly';
        return this._isDarkTheme === true ? 'dracula' : 'default';
    }
    _lintOptionValue() { return this._isLint === true ? { selfContain: true } : false; }

    _shouldEnableLintFor(text) {
        const t = typeof text === 'string' ? text : (text || '');
        if (t.length > this._largeDocThresholdBytes) return false;
        const nl = (t.match(/\n/g) || []).length + 1;
        return this._isLint && nl <= this._largeDocThresholdLines;
    }

    onDocumentChanged(value) {
        this.document = value;
        if (this.$server && this.$server.onDocumentChanged) { try { this.$server.onDocumentChanged(value); } catch (e) { console.error(e); } }
    }

    onEditorClicked(value, content) {
        if (this.$server && this.$server.onEditorClicked) { try { this.$server.onEditorClicked(value, content); } catch (e) { console.error(e); } }
    }

    setEditorDocument(_element, document) {
        this.document = document;

        const main = this._getMainEditor();
        if (!main) return;

        const currentValue = main.getValue();
        if (currentValue === document) return;

        if (main._setInternalUpdate) {
            main._setInternalUpdate(true);
        }

        try {
            main.doc.setValue(document);
            this._scheduleRecalcChunks();
        } finally {
            if (main._setInternalUpdate) {
                main._setInternalUpdate(false);
            }
        }
    }

    setEditorOption(option, value) {
        const main = this._getMainEditor();
        if (main) {
            if (option === 'readOnly') {
                main.setOption("theme", value ? (this._isDarkTheme ? 'dracularo' : 'readOnly') : (this._isDarkTheme ? 'dracula'  : 'default'));
                this._scheduleRecalcChunks();
            }
            main.setOption(option, value);
        }
        const right = this._getRightEditor();
        if (right && (option === 'lineNumbers' || option === 'mode')) right.setOption(option, value);
        if (right && option === 'readOnly') right.setOption('readOnly', !this._mergeOptions.allowEditingOriginals);
        if (option === 'lineNumbers') this._scheduleRecalcChunks();
    }

    onEditorChangeCheckOptions(editor) {
        if (editor) {
            this.setEditorOption('readOnly', this.isReadOnly);
            this.setDarkThemeEditorOption(this.isDarkTheme);
            this.setLintEditorOption(this.isLint);
        }
    }

    onRefreshEditor() {
        const main = this._getMainEditor();
        if (main) { main.refresh(); this._scheduleRecalcChunks(); }
    }

    render() { return html`<div id="json-editor"></div>`; }

    scrollToBottom() { const m = this._getMainEditor(); if (m) { const s = m.getScrollInfo(); m.scrollTo(null, s.height); } }
    appendText(text) { const m = this._getMainEditor(); if (m) { const lines = m.lineCount(); m.replaceRange(text + "\n", { line: lines }); } }

    setDarkThemeEditorOption(value) {
        const main = this._getMainEditor();
        if (main) { if (value === true) main.setOption("theme", 'dracula'); else this.setEditorOption('readOnly', this._isReadOnly); }
        const right = this._getRightEditor();
        if (right) right.setOption('theme', this._themeForCurrentState());
        this._applyThemeVars();
        this._scheduleRecalcChunks();
    }

    setLintEditorOption(value) { const m = this._getMainEditor(); if (m) m.setOption("lint", value === true ? { selfContain: true } : false); }

    setMergeModeEnabled(enabled) {
        this.mergeEnabled = !!enabled;

        const container = this._editorContainer();
        if (!container) {
            return;
        }

        const leftText = this._getMainEditor() ? this._getMainEditor().getValue() : (this.document ?? "");
        if (this.mergeEnabled) { this._createMergeView(leftText, this._rightMergeText ?? ""); this._dispatchMergeModeToggled(true); }
        else { this._createSingleEditor(leftText); this._dispatchMergeModeToggled(false); }
    }
    enableMergeView() { this.setMergeModeEnabled(true); }
    disableMergeView() { this.setMergeModeEnabled(false); }

    setMergeRightContent(content) {
        this._rightMergeText = content ?? "";
        const right = this._getRightEditor();
        if (right) right.setValue(this._rightMergeText);
        this._scheduleRecalcChunks();
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
            const leftText = this._getMainEditor().getValue();
            const rightText = this._getRightEditor() ? this._getRightEditor().getValue() : this._rightMergeText;
            this._createMergeView(leftText, rightText);
        }
    }

    enableChangeMarkers(enabled) {
        this._changeMarkersEnabled = !!enabled;
        if (this._changeMarkersEnabled) this._scheduleRecalcChunks();
        this._applyChangeMarkers();
    }

    nextChange()  { const m = this._getMainEditor(); if (m) m.execCommand('goNextDiff'); }
    prevChange()  { const m = this._getMainEditor(); if (m) m.execCommand('goPrevDiff'); }
    firstChange() { if (this._chunkList.length) this.scrollToChange(0); }
    lastChange()  { if (this._chunkList.length) this.scrollToChange(this._chunkList.length - 1); }
    scrollToChange(index) { const m = this._getMainEditor(); if (!m) return; const c = this._chunkList[index]; if (!c) return; m.scrollIntoView({ line: c.left.fromLine, ch: 0 }, 100); }

    _scheduleRecalcChunks() { if (this._recalcChunksDebounced) clearTimeout(this._recalcChunksDebounced); this._recalcChunksDebounced = setTimeout(() => this._recalcChunks(), 30); }

    _recalcChunks() {
        if (!this._mergeView) { this._chunkList = []; this._emitChunks(); this._applyChangeMarkers(); return; }
        let rawChunks = [];
        try { rawChunks = this._mergeView.rightChunks ? (this._mergeView.rightChunks() || []) : (this._mergeView.leftChunks ? (this._mergeView.leftChunks() || []) : []); } catch { rawChunks = []; }
        const chunks = rawChunks.map(ch => ({ left:{fromLine: ch.editFrom, toLine: ch.editTo - 1}, right:{fromLine: ch.origFrom, toLine: ch.origTo - 1} }));
        chunks.sort((a,b)=>a.left.fromLine-b.left.fromLine || a.left.toLine-b.left.toLine);
        this._chunkList = chunks;
        this._emitChunks();
        this._applyChangeMarkers();
    }

    _emitChunks() {
        const compact = this._chunkList.map(c => ({ fromLine: c.left.fromLine, toLine: c.left.toLine }));
        this.dispatchEvent(new CustomEvent('sapl-merge-chunks', { bubbles: true, composed: true, detail: { chunks: compact } }));
    }

    _applyChangeMarkers() {
        this._clearChangeMarkers();
        if (!this._changeMarkersEnabled || this._chunkList.length === 0) return;

        const left = this._getMainEditor(); if (!left) return;
        const right = this._getRightEditor();

        const ensureGutter = (ed) => {
            const current = ed.getOption('gutters') || [];
            const set = new Set(current);
            if (!set.has(this._gutterId)) { set.add(this._gutterId); ed.setOption('gutters', Array.from(set)); }
        };
        ensureGutter(left); if (right) ensureGutter(right);

        this._chunkList.forEach(chunk => {
            for (let line = chunk.left.fromLine; line <= chunk.left.toLine; line++) {
                left.addLineClass(line, 'wrap', 'cm-merge-chunk-line');
                this._appliedLineClassesLeft.push({ line });
            }
            const lm = document.createElement('span');
            lm.className = 'cm-merge-gutter-marker changed';
            left.setGutterMarker(chunk.left.fromLine, this._gutterId, lm);
            this._appliedGutterMarkersLeft.push({ line: chunk.left.fromLine });

            if (right && chunk.right) {
                for (let line = chunk.right.fromLine; line <= chunk.right.toLine; line++) {
                    right.addLineClass(line, 'wrap', 'cm-merge-chunk-line');
                    this._appliedLineClassesRight.push({ line });
                }
                const rm = document.createElement('span');
                rm.className = 'cm-merge-gutter-marker changed';
                right.setGutterMarker(chunk.right.fromLine, this._gutterId, rm);
                this._appliedGutterMarkersRight.push({ line: chunk.right.fromLine });
            }
        });
    }

    _clearChangeMarkers() {
        const left = this._getMainEditor();
        const right = this._getRightEditor();
        if (left) {
            this._appliedLineClassesLeft.forEach(e => { try { left.removeLineClass(e.line, 'wrap', 'cm-merge-chunk-line'); } catch(_) {} });
            this._appliedGutterMarkersLeft.forEach(e => { try { left.setGutterMarker(e.line, this._gutterId, null); } catch(_) {} });
        }
        if (right) {
            this._appliedLineClassesRight.forEach(e => { try { right.removeLineClass(e.line, 'wrap', 'cm-merge-chunk-line'); } catch(_) {} });
            this._appliedGutterMarkersRight.forEach(e => { try { right.setGutterMarker(e.line, this._gutterId, null); } catch(_) {} });
        }
        this._appliedLineClassesLeft = [];
        this._appliedLineClassesRight = [];
        this._appliedGutterMarkersLeft = [];
        this._appliedGutterMarkersRight = [];
    }

    updateMergeRightContent(content) { this.setMergeRightContent(content); }
    enableMergeViewWithContent(content) { this.setMergeRightContent(content); this.setMergeModeEnabled(true); }

    _dispatchMergeModeToggled(active) { this.dispatchEvent(new CustomEvent('sapl-merge-mode-toggled', { bubbles: true, composed: true, detail: { active } })); }

    _applyThemeVars() {
        const isDark = this._isDarkTheme === true;
        const connector = isDark ? '#252a2e' : '#c7d1d6';
        const arrow     = isDark ? '#5ac8c7' : '#378b8a';
        this.style.setProperty('--sapl-merge-connector', connector);
        this.style.setProperty('--sapl-merge-arrow', arrow);
    }
}

customElements.define('json-editor', JSONEditor);