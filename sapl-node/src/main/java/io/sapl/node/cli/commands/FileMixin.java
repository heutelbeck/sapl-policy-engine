package io.sapl.node.cli.commands;

import picocli.CommandLine;
import picocli.CommandLine.Spec;
import picocli.CommandLine.Model.CommandSpec;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;

//tbd: wildcards gehen noch nicht
public class FileMixin implements AutoCloseable {
    @Spec
    CommandSpec spec;

    @CommandLine.Option(names = "--output")
    Path file;

    private PrintWriter writer;

    public PrintWriter getFileWriter() throws IOException {
        if (writer == null) {
            writer = file != null ? new PrintWriter(Files.newBufferedWriter(file)) : spec.commandLine().getOut();
        }
        return writer;
    }

    public Integer run(Callable<Integer> action) throws Exception {
        try (this) {
            return action.call();
        }
    }

    @Override
    public void close() {
        if (file != null && writer != null) {
            writer.close();
        }
    }
}
