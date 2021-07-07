define("sapl-mode", ["codemirror", "codemirror/addon/mode/simple"], function(CodeMirror, SimpleMode) {
	var keywords = "where|var|undefined|true|transform|set|policy|permit-unless-deny|permit-overrides|permit|only-one-applicable|obligation|null|in|import|for|first-applicable|false|each|deny-unless-permit|deny-overrides|deny|as|advice";
	CodeMirror.defineSimpleMode("xtext/sapl", {
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
