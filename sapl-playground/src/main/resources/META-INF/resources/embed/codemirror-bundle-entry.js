/**
 * CodeMirror bundle entry point for the SAPL embed script.
 * Build: npx esbuild codemirror-bundle-entry.js --bundle --format=esm --minify --outfile=codemirror-bundle.min.js
 */
export { EditorView } from '@codemirror/view';
export { Compartment } from '@codemirror/state';
export { StreamLanguage, syntaxHighlighting, HighlightStyle } from '@codemirror/language';
export { minimalSetup } from 'codemirror';
export { lineNumbers } from '@codemirror/view';
export { oneDark } from '@codemirror/theme-one-dark';
export { tags } from '@lezer/highlight';
