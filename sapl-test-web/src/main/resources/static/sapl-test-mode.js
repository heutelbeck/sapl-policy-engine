define("sapl-test-mode", ["codemirror", "codemirror/addon/mode/simple"], function(CodeMirror, SimpleMode) {
	var keywords = "with|whitespaces|where|when|wait|virtual-time|value|undefined|true|then|text|tests|test|subject|starts|single|set|scenario|returns|returning|return|resource|register|regex|policies|permit-unless-deny|permit-overrides|permit|pdp|parent|parameters|order|only-one-applicable|once|on|of|obligations|obligation|object|number|null-or-empty|null-or-blank|null|notApplicable|no-event|missing|matching|matches|library|let|length|key|is|invoked|indeterminate|in|ignoring|identifier|has|given|function|for|false|expect|exception|error|equals|environment|ends|empty|deny-unless-permit|deny-overrides|deny|decision|custom|contains|containing|config|compressed|case|boolean|blank|attribute|attempts|array|any|and|advice|action|TemporalFunctionLibrary|StandardFunctionLibrary|PIP|LoggingFunctionLibrary|FilterFunctionLibrary";
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
