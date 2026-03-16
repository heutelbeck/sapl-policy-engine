/*
 * Copyright (C) 2017-2026 Dominic Heutelbeck (dominic@heutelbeck.com)
 *
 * SPDX-License-Identifier: Apache-2.0
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.sapl.node.docgen;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.regex.Pattern;

import io.sapl.node.cli.SaplNodeCli;
import lombok.val;
import picocli.CommandLine;
import picocli.CommandLine.Model.ArgGroupSpec;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.Model.PositionalParamSpec;

/**
 * Build-time utility that generates a single Markdown reference page for the
 * SAPL CLI from the picocli {@code @Command} model. Lives in {@code src/test}
 * so it is never packaged in the production JAR or native binary.
 * <p>
 * Invoked via {@code exec-maven-plugin} during {@code process-test-classes}.
 *
 * @see SaplNodeCli
 */
public final class CliReferenceMarkdownGenerator {

    private static final String OUTPUT_FILE = "7_8_CommandLine.md";

    private static final Pattern PICOCLI_MARKUP = Pattern.compile("@\\|[a-z,]+ ([^|]+)\\|@");
    private static final Pattern SEE_ALSO_LINE  = Pattern.compile("^\\s*See Also:.*$", Pattern.MULTILINE);
    private static final Pattern MAN_REF        = Pattern.compile("sapl-([a-z-]+)\\(1\\)");
    private static final Pattern FLAG_TOKENS    = Pattern.compile("(?<=\\s|^)(--?[a-z][-a-z]*)(?=\\s|[,;:]|$)");
    private static final Pattern NAME_TOKENS    = Pattern
            .compile("(?<=\\s|^)(pdp\\.json|application\\.yml|SAPL_[A-Z_]+|\\.sapl(?:bundle)?)(?=[\\s.,;:]|$)");

    private CliReferenceMarkdownGenerator() {
    }

    /**
     * Entry point. Accepts a single argument: the output directory path.
     *
     * @param args {@code args[0]} is the target directory for the generated
     * Markdown file
     */
    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: CliReferenceMarkdownGenerator <output-directory>");
            System.exit(1);
        }
        val outputDir = Path.of(args[0]);
        Files.createDirectories(outputDir);

        val commandLine = new CommandLine(new SaplNodeCli());
        val markdown    = generate(commandLine);

        Files.writeString(outputDir.resolve(OUTPUT_FILE), markdown);
    }

    static String generate(CommandLine commandLine) {
        val sb = new StringBuilder();
        appendFrontMatter(sb);
        appendOverview(sb, commandLine);
        appendCommandReference(sb, commandLine, 2);
        return sb.toString();
    }

    private static void appendFrontMatter(StringBuilder sb) {
        sb.append("""
                ---
                layout: default
                title: Command Line
                parent: SAPL Node
                nav_order: 708
                ---

                """);
    }

    private static void appendOverview(StringBuilder sb, CommandLine commandLine) {
        sb.append("# Command Line\n\n");
        val header = commandLine.getCommandSpec().usageMessage().header();
        if (header.length > 0) {
            sb.append(markdownify(header[0])).append("\n\n");
        }
        sb.append("## Commands\n\n");
        appendCommandTree(sb, commandLine, "");
        sb.append('\n');
    }

    private static void appendCommandTree(StringBuilder sb, CommandLine commandLine, String indent) {
        val spec   = commandLine.getCommandSpec();
        val name   = spec.qualifiedName(" ");
        val anchor = toAnchor(name);
        sb.append(indent).append("- [`").append(name).append("`](#").append(anchor).append(')');
        val header = spec.usageMessage().header();
        if (header.length > 0) {
            sb.append(" -- ").append(markdownify(header[0]));
        }
        sb.append('\n');
        for (val sub : commandLine.getSubcommands().values()) {
            appendCommandTree(sb, sub, indent + "  ");
        }
    }

    private static void appendCommandReference(StringBuilder sb, CommandLine commandLine, int headingLevel) {
        for (val entry : commandLine.getSubcommands().entrySet()) {
            appendCommandSection(sb, entry, headingLevel);
        }
    }

    private static void appendCommandSection(StringBuilder sb, Map.Entry<String, CommandLine> entry, int headingLevel) {
        val sub  = entry.getValue();
        val spec = sub.getCommandSpec();

        sb.append("#".repeat(headingLevel)).append(' ').append(spec.qualifiedName(" ")).append("\n\n");

        val header = spec.usageMessage().header();
        if (header.length > 0) {
            sb.append(markdownify(header[0])).append("\n\n");
        }

        appendDescription(sb, spec);
        appendSynopsis(sb, spec);
        appendOptions(sb, spec);
        appendExitCodes(sb, spec);
        appendFooter(sb, spec);
        appendSubcommands(sb, sub, headingLevel);
    }

    private static void appendDescription(StringBuilder sb, CommandSpec spec) {
        val descriptions = spec.usageMessage().description();
        if (descriptions.length > 0) {
            for (val line : descriptions) {
                sb.append(markdownify(line)).append('\n');
            }
            sb.append('\n');
        }
    }

    private static void appendSynopsis(StringBuilder sb, CommandSpec spec) {
        val writer = new StringWriter();
        val out    = new PrintWriter(writer);
        spec.commandLine().usage(out, CommandLine.Help.Ansi.OFF);
        val usageText = writer.toString();

        val synopsisStart = usageText.indexOf("Usage:");
        if (synopsisStart >= 0) {
            var synopsisEnd = usageText.indexOf('\n', synopsisStart);
            while (synopsisEnd >= 0 && synopsisEnd + 1 < usageText.length()
                    && usageText.charAt(synopsisEnd + 1) == ' ') {
                synopsisEnd = usageText.indexOf('\n', synopsisEnd + 1);
            }
            if (synopsisEnd < 0) {
                synopsisEnd = usageText.length();
            }
            val synopsis = usageText.substring(synopsisStart + "Usage: ".length(), synopsisEnd).trim();
            sb.append("**Synopsis**\n\n```\n").append(synopsis).append("\n```\n\n");
        }
    }

    private static void appendOptions(StringBuilder sb, CommandSpec spec) {
        val options     = spec.options();
        val positionals = spec.positionalParameters();

        if (options.isEmpty() && positionals.isEmpty()) {
            return;
        }

        sb.append("**Options**\n\n");

        for (val param : positionals) {
            ensureTableHeader(sb);
            appendParamRow(sb, param);
        }

        val argGroups = spec.argGroups();
        if (!argGroups.isEmpty()) {
            for (val group : argGroups) {
                appendArgGroup(sb, group);
            }
        }

        val ungrouped = options.stream().filter(o -> o.group() == null).toList();
        if (!ungrouped.isEmpty()) {
            ensureTableHeader(sb);
            for (val option : ungrouped) {
                appendOptionRow(sb, option);
            }
        }

        sb.append('\n');
    }

    private static void appendArgGroup(StringBuilder sb, ArgGroupSpec group) {
        val heading = group.heading();
        if (heading != null && !heading.isBlank()) {
            val clean = heading.replace("%n", "").strip();
            if (!clean.isEmpty()) {
                sb.append("\n*").append(clean).append("*\n\n");
            }
        }

        ensureTableHeader(sb);
        for (val arg : group.args()) {
            if (arg instanceof OptionSpec option) {
                appendOptionRow(sb, option);
            }
        }

        for (val subGroup : group.subgroups()) {
            appendArgGroup(sb, subGroup);
        }
    }

    private static void ensureTableHeader(StringBuilder sb) {
        val content = sb.toString();
        if (content.endsWith("|\n") || content.endsWith("|\n\n")) {
            return;
        }
        sb.append("| Option | Description | Default |\n");
        sb.append("|--------|-------------|---------|\n");
    }

    private static void appendParamRow(StringBuilder sb, PositionalParamSpec param) {
        val label       = param.paramLabel();
        val description = descriptionOf(param);
        val defaultVal  = defaultValueOf(param);
        sb.append("| `").append(label).append("` | ").append(escapeTable(markdownify(description))).append(" | ")
                .append(defaultVal).append(" |\n");
    }

    private static void appendOptionRow(StringBuilder sb, OptionSpec option) {
        val names      = String.join(", ", option.names());
        val paramLabel = option.typeInfo().isBoolean() ? "" : " " + option.paramLabel();
        val desc       = descriptionOf(option);
        val defaultVal = defaultValueOf(option);
        sb.append("| `").append(names).append(paramLabel).append("` | ").append(escapeTable(markdownify(desc)))
                .append(" | ").append(defaultVal).append(" |\n");
    }

    private static void appendExitCodes(StringBuilder sb, CommandSpec spec) {
        val exitCodes = spec.usageMessage().exitCodeList();
        if (exitCodes.isEmpty()) {
            return;
        }
        sb.append("**Exit Codes**\n\n");
        sb.append("| Code | Description |\n");
        sb.append("|------|-------------|\n");
        for (val entry : exitCodes.entrySet()) {
            sb.append("| ").append(entry.getKey().trim()).append(" | ").append(escapeTable(entry.getValue()))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    private static void appendFooter(StringBuilder sb, CommandSpec spec) {
        val footer = spec.usageMessage().footer();
        if (footer.length == 0) {
            return;
        }

        val raw = new StringBuilder();
        for (val line : footer) {
            raw.append(line).append('\n');
        }
        val fullText = raw.toString();

        val seeAlsoMatcher = SEE_ALSO_LINE.matcher(fullText);
        var seeAlsoText    = "";
        val exampleText    = seeAlsoMatcher.find() ? fullText.substring(0, seeAlsoMatcher.start()).stripTrailing()
                : fullText.stripTrailing();
        if (seeAlsoMatcher.find(0)) {
            seeAlsoText = seeAlsoMatcher.group().strip();
        }

        if (!exampleText.isBlank()) {
            sb.append("**Examples**\n\n```shell\n");
            for (val line : exampleText.split("\n")) {
                val stripped = line.stripLeading();
                if (!stripped.isEmpty()) {
                    sb.append(stripped).append('\n');
                } else {
                    sb.append('\n');
                }
            }
            sb.append("```\n\n");
        }

        if (!seeAlsoText.isEmpty()) {
            val converted = MAN_REF.matcher(seeAlsoText).replaceAll(mr -> {
                val cmd    = mr.group(1).replace("-", " ");
                val anchor = toAnchor("sapl " + cmd);
                return "[sapl " + cmd + "](#" + anchor + ")";
            });
            sb.append(converted).append("\n\n");
        }
    }

    private static void appendSubcommands(StringBuilder sb, CommandLine commandLine, int headingLevel) {
        val subcommands = commandLine.getSubcommands();
        if (!subcommands.isEmpty()) {
            for (val entry : subcommands.entrySet()) {
                appendCommandSection(sb, entry, headingLevel + 1);
            }
        }
    }

    private static String descriptionOf(ArgSpec argSpec) {
        val descriptions = argSpec.description();
        if (descriptions.length > 0) {
            return stripPicocliMarkup(descriptions[0]);
        }
        return "";
    }

    private static String defaultValueOf(ArgSpec argSpec) {
        val defaultValue = argSpec.defaultValue();
        if (defaultValue != null && !defaultValue.isEmpty()) {
            return "`" + defaultValue + "`";
        }
        return "";
    }

    private static String markdownify(String text) {
        val stripped  = stripPicocliMarkup(text);
        val withFlags = FLAG_TOKENS.matcher(stripped).replaceAll("`$1`");
        return NAME_TOKENS.matcher(withFlags).replaceAll("`$1`");
    }

    private static String stripPicocliMarkup(String text) {
        return PICOCLI_MARKUP.matcher(text).replaceAll("$1");
    }

    private static String escapeTable(String text) {
        return text.replace("|", "\\|");
    }

    private static String toAnchor(String name) {
        return name.toLowerCase().replace(" ", "-");
    }

}
