package io.sapl.playground;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import io.sapl.interpreter.functions.LibraryDocumentation;
import lombok.NonNull;
import lombok.experimental.UtilityClass;

@UtilityClass
public class MarkdownGenerator {
    private static final Parser       PARSER   = Parser.builder().build();
    private static final HtmlRenderer RENDERER = HtmlRenderer.builder().build();

    /*
     * Generates markdown documentation for a function library.
     *
     * @param documentation the library documentation
     *
     * @return markdown-formatted documentation string
     */
    public String generateMarkdownForLibrary(LibraryDocumentation documentation) {
        final var sb = new StringBuilder();
        sb.append("# ").append(documentation.getName()).append("\n\n");
        sb.append(documentation.getDescription()).append("\n\n");
        sb.append(documentation.getLibraryDocumentation()).append("\n\n");
        sb.append("---\n\n");

        for (var entry : documentation.getDocumentation().entrySet()) {
            final var name = entry.getKey();
            final var docs = entry.getValue();
            sb.append("## ").append(name).append("\n\n");
            sb.append(docs).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    /*
     * Generates markdown documentation for a policy information point.
     *
     * @param documentation the PIP documentation
     *
     * @return markdown-formatted documentation string
     */
    public String generateMarkdownForPolicyInformationPoint(
            io.sapl.attributes.documentation.api.LibraryDocumentation documentation) {
        final var sb = new StringBuilder();
        sb.append("# ").append(documentation.namespace()).append("\n\n");
        sb.append(documentation.descriptionMarkdown()).append("\n\n");
        sb.append(documentation.documentationMarkdown()).append("\n\n");
        sb.append("---\n\n");

        for (var entry : documentation.attributesMap().entrySet()) {
            final var name = entry.getKey();
            final var docs = entry.getValue();
            sb.append("## ").append(name).append("\n\n");
            sb.append(docs).append("\n\n---\n\n");
        }
        return sb.toString();
    }

    /*
     * Converts markdown to HTML.
     *
     * @param markdown the markdown string to convert
     *
     * @return HTML string
     */
    public String markdownToHtml(@NonNull String markdown) {
        final var document = PARSER.parse(markdown);
        return RENDERER.render(document);
    }

    /*
     * Wraps HTML content in a div element.
     *
     * @param innerHtml the inner HTML content
     *
     * @return the wrapped HTML string
     */
    public String wrapInDiv(String innerHtml) {
        return String.format("<div>%n%s%n</div>", innerHtml);
    }
}
