package io.sapl.documentation;

import io.sapl.attributes.documentation.api.LibraryDocumentation;
import io.sapl.attributes.documentation.api.PolicyInformationPointDocumentationProvider;
import io.sapl.interpreter.functions.FunctionContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import lombok.val;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Component responsible for generating markdown documentation for SAPL libraries and policy information points.
 * Executes automatically after application startup, generates documentation files, and shuts down the application.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DocumentationGenerator {
    private static final String MARKDOWN_HEADER_LEVEL_1  = "# ";
    private static final String MARKDOWN_HEADER_LEVEL_2  = "## ";
    private static final String MARKDOWN_HORIZONTAL_RULE = "---\n\n";
    private static final String MARKDOWN_LINE_BREAK      = "\n\n";

    private final ConfigurableApplicationContext applicationContext;

    /**
     * Provider for policy information point documentation.
     * Supplies documentation about available PIPs and their attributes.
     */
    private final PolicyInformationPointDocumentationProvider policyInformationPointDocumentationProvider;

    /**
     * Context providing function library documentation.
     * Supplies documentation about available functions and their usage.
     */
    private final FunctionContext functionContext;

    @Value("${application.version}")
    private String version;

    @Value("${sapl.documentation.target:target/doc}")
    private String targetPath;

    /**
     * Executes documentation generation after the application context has fully started.
     * Generates markdown files for all function libraries and policy information points,
     * then shuts down the application.
     *
     * @param event the application ready event
     * @throws IOException if file operations fail
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) throws IOException {
        log.info("Starting DocumentationGenerator\n");
        log.info("SAPL Version: {}", version);

        createTargetDirectoryIfNotExists();

        val policyInformationPoints = sortPips(policyInformationPointDocumentationProvider.getDocumentation());
        val libraries               = sortLibs(functionContext.getDocumentation());
        var navOrder                = 101;
        for (val lib : libraries) {
            log.info("Library: {}", lib.getName());
            val documentationMD = generateMarkdownForLibrary(lib, navOrder++);
            writeToFile("lib_" + lib.getName() + ".md", documentationMD);
        }
        navOrder = 201;
        for (val pip : policyInformationPoints) {
            log.info("Policy Information Point: {}", pip.namespace());
            val documentationMD = generateMarkdownForPolicyInformationPoint(pip, navOrder++);
            writeToFile("pip_" + pip.namespace() + ".md", documentationMD);
        }

        SpringApplication.exit(applicationContext, () -> 0);
    }

    /**
     * Creates the target directory if it does not already exist.
     * Creates all necessary parent directories as well.
     *
     * @throws IOException if directory creation fails
     */
    private void createTargetDirectoryIfNotExists() throws IOException {
        val targetDirectory = Path.of(targetPath);
        if (!Files.exists(targetDirectory)) {
            Files.createDirectories(targetDirectory);
            log.info("Created target directory: {}", targetPath);
        }
    }

    /**
     * Writes the given content to a file in the target directory.
     * If the file already exists, it will be overwritten.
     *
     * @param filename the name of the file to write
     * @param content the content to write to the file
     * @throws IOException if writing to the file fails
     */
    private void writeToFile(String filename, String content) throws IOException {
        val filePath = Path.of(targetPath, filename);
        Files.writeString(filePath, content, StandardCharsets.UTF_8);
        log.debug("Written documentation to: {}", filePath);
    }

    /**
     * Sorts policy information point documentation by namespace in alphabetical order.
     *
     * @param pips collection of policy information point documentation
     * @return sorted list of policy information point documentation
     */
    List<LibraryDocumentation> sortPips(Collection<LibraryDocumentation> pips) {
        val sortedPips = new ArrayList<LibraryDocumentation>(pips.size());
        sortedPips.addAll(pips);
        sortedPips.sort(Comparator.comparing(LibraryDocumentation::namespace));
        return sortedPips;
    }

    /**
     * Sorts function library documentation by name in alphabetical order.
     *
     * @param libs collection of function library documentation
     * @return sorted list of function library documentation
     */
    List<io.sapl.interpreter.functions.LibraryDocumentation> sortLibs(
            Collection<io.sapl.interpreter.functions.LibraryDocumentation> libs) {
        val sortedLibs = new ArrayList<io.sapl.interpreter.functions.LibraryDocumentation>(libs.size());
        sortedLibs.addAll(libs);
        sortedLibs.sort(Comparator.comparing(io.sapl.interpreter.functions.LibraryDocumentation::getName));
        return sortedLibs;
    }

    /**
     * Generates markdown documentation for a function library.
     * Creates a structured markdown document with library name, description,
     * library-level documentation, and individual function documentation.
     * Each function is separated by horizontal rules.
     *
     * @param documentation the function library documentation object
     * @param navOrder the navigation order for the documentation page
     * @return markdown-formatted documentation string
     */
    public String generateMarkdownForLibrary(io.sapl.interpreter.functions.LibraryDocumentation documentation,
                                             int navOrder) {
        val stringBuilder = new StringBuilder();
        appendFrontmatter(stringBuilder, navOrder, documentation.getName(), "Function Libraries");
        appendHeader(stringBuilder, documentation.getName());
        stringBuilder.append(documentation.getDescription()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(documentation.getLibraryDocumentation()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);

        for (var entry : documentation.getDocumentation().entrySet()) {
            val functionName          = entry.getKey();
            val functionDocumentation = entry.getValue();
            appendSubHeader(stringBuilder, functionName);
            stringBuilder.append(functionDocumentation).append(MARKDOWN_LINE_BREAK);
            stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);
        }

        return stringBuilder.toString();
    }

    /**
     * Generates markdown documentation for a policy information point.
     * Creates a structured markdown document with PIP namespace, description,
     * general documentation, and individual attribute documentation.
     * Each attribute is separated by horizontal rules.
     *
     * @param documentation the policy information point documentation object
     * @param navOrder the navigation order for the documentation page
     * @return markdown-formatted documentation string
     */
    public String generateMarkdownForPolicyInformationPoint(LibraryDocumentation documentation, int navOrder) {
        val stringBuilder = new StringBuilder();
        appendFrontmatter(stringBuilder, navOrder, documentation.namespace(), "Policy Information Points");
        appendHeader(stringBuilder, documentation.namespace());
        stringBuilder.append(documentation.descriptionMarkdown()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(documentation.documentationMarkdown()).append(MARKDOWN_LINE_BREAK);
        stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);

        for (var entry : documentation.attributesMap().entrySet()) {
            val attributeName          = entry.getKey();
            val attributeDocumentation = entry.getValue();
            appendSubHeader(stringBuilder, attributeName);
            stringBuilder.append(attributeDocumentation).append(MARKDOWN_LINE_BREAK);
            stringBuilder.append(MARKDOWN_HORIZONTAL_RULE);
        }

        return stringBuilder.toString();
    }

    /**
     * Appends Jekyll front matter to the markdown document.
     * Includes title, parent page, and navigation order.
     *
     * @param stringBuilder the string builder to append to
     * @param navOrder the navigation order for the page
     * @param title the page title
     * @param parent the parent page name
     */
    private void appendFrontmatter(StringBuilder stringBuilder, int navOrder, String title, String parent) {
        stringBuilder.append(String.format("""
                ---
                title: %s
                parent: %s
                nav_order: %d
                ---
                """, title, parent, navOrder));
    }

    /**
     * Appends a level 1 markdown header with the specified title.
     * Adds two line breaks after the header.
     *
     * @param stringBuilder the string builder to append to
     * @param title the header title
     */
    private void appendHeader(StringBuilder stringBuilder, String title) {
        stringBuilder.append(MARKDOWN_HEADER_LEVEL_1).append(title).append(MARKDOWN_LINE_BREAK);
    }

    /**
     * Appends a level 2 markdown header with the specified title.
     * Adds two line breaks after the header.
     *
     * @param stringBuilder the string builder to append to
     * @param title the header title
     */
    private void appendSubHeader(StringBuilder stringBuilder, String title) {
        stringBuilder.append(MARKDOWN_HEADER_LEVEL_2).append(title).append(MARKDOWN_LINE_BREAK);
    }
}