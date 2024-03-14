define("sapl-test-mode", ["codemirror", "codemirror/addon/mode/simple"], function(CodeMirror, SimpleMode) {
	var keywords = "with|whitespace|where|when|wait|virtual-time|variables|value|undefined|true|to|timing|times|then|text|subject|stream|static-pip|static-function-library|starting|set|scenario|resource|requirement|regex|policy|policies|pip|permit-unless-deny|permit-overrides|permit|pdp|order|only-one-applicable|once|on|of|obligations|obligation|object|number|null-or-empty|null-or-blank|null|notApplicable|no-event|matching|maps|length|key|is|indeterminate|in|given|function-library|function|for|false|expect|exception|error|equals|equal|environment|ending|empty|emits|deny-unless-permit|deny-overrides|deny|decision|containing|configuration|compressed|combining-algorithm|case-insensitive|called|boolean|blank|attribute|attempts|array|any|and|advice|action";
	CodeMirror.defineSimpleMode("xtext/sapltest", {
		start: [
			{token: "comment", regex: "\\/\\/.*$"},
			{token: "comment", regex: "\\/\\*", next : "comment"},
			{token: "string", regex: '["](?:(?:\\\\.)|(?:[^"\\\\]))*?["]'},
			{token: "string", regex: "['](?:(?:\\\\.)|(?:[^'\\\\]))*?[']"},
			{token: "constant.numeric", regex: "[+-]?\\d+(?:(?:\\.\\d*)?(?:[eE][+-]?\\d+)?)?\\b"},
			{token: "keyword", regex: "\\b(?:" + keywords + ")\\b"}
		],
		comment: [
			{token: "comment", regex: ".*?\\*\\/", next : "start"},
			{token: "comment", regex: ".+"}
		],
		meta: {
			dontIndentStates: ["comment"],
			lineComment: "//"
		}
	});
});
