/**
 * CodeMirror bundle entry point for sapl.io (sapl-pages).
 * Includes language support for Java, Python, JavaScript alongside SAPL core.
 * Build: npx esbuild sapl-pages-bundle-entry.js --bundle --format=esm --minify --outfile=sapl-pages-codemirror-bundle.min.js
 */
export { EditorView } from '@codemirror/view';
export { Compartment } from '@codemirror/state';
export { StreamLanguage, syntaxHighlighting, HighlightStyle } from '@codemirror/language';
export { minimalSetup } from 'codemirror';
export { lineNumbers } from '@codemirror/view';
export { oneDark } from '@codemirror/theme-one-dark';
export { tags } from '@lezer/highlight';

import { java } from '@codemirror/lang-java';
import { python } from '@codemirror/lang-python';
import { javascript } from '@codemirror/lang-javascript';
export const languages = { java, python, javascript };
