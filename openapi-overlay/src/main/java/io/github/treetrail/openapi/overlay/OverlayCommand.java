package io.github.treetrail.openapi.overlay;

import com.fasterxml.jackson.databind.JsonNode;
import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Command line: applies overlays to an OpenAPI description, for example as a build step before a code
 * generator.
 *
 * <pre>
 * java -cp ... io.github.treetrail.openapi.overlay.OverlayCommand \
 *     --overlay first.overlay.yaml --overlay second.overlay.yaml \
 *     --output build/openapi.yaml [--strict] [--lenient] openapi.yaml
 * </pre>
 *
 * <p>The overlays are applied in the order given. With {@code --strict}, an action whose target selects no node
 * is an error ({@link OverlayOptions#withFailOnNoMatch}), so that an overlay that no longer fits a changed
 * description fails the build. {@code --lenient} replaces incompatible values instead of failing
 * ({@link OverlayOptions#withLenientMerge}). Exit status: 0 on success, 1 if an overlay is invalid or cannot be
 * applied, 2 on wrong arguments.
 */
public final class OverlayCommand {

    private static final String USAGE =
            "Usage: OverlayCommand --overlay <file> [--overlay <file> ...] --output <file> [--strict] [--lenient]"
                    + " <openapi file>";

    private OverlayCommand() {}

    /** Runs the command and exits with its status. */
    public static void main(String[] args) {
        int status = run(args, System.out, System.err);
        if (status != 0) {
            System.exit(status);
        }
    }

    /** Runs the command; returns the exit status. */
    static int run(String[] args, PrintStream out, PrintStream err) {
        List<Path> overlays = new ArrayList<>();
        Path output = null;
        Path input = null;
        OverlayOptions options = OverlayOptions.DEFAULT;
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            switch (arg) {
                case "--overlay", "--output" -> {
                    if (i + 1 == args.length) {
                        return usage(err, arg + " needs a file");
                    }
                    Path file = Path.of(args[++i]);
                    if (arg.equals("--overlay")) {
                        overlays.add(file);
                    } else {
                        output = file;
                    }
                }
                case "--strict" -> options = options.withFailOnNoMatch(true);
                case "--lenient" -> options = options.withLenientMerge(true);
                default -> {
                    if (arg.startsWith("--") || input != null) {
                        return usage(err, "unexpected argument " + arg);
                    }
                    input = Path.of(arg);
                }
            }
        }
        if (input == null || output == null || overlays.isEmpty()) {
            return usage(err, null);
        }
        try {
            JsonNode document = OverlayDocuments.read(input);
            for (Path file : overlays) {
                OverlayResult result;
                try {
                    result = Overlay.read(file).applyTo(document, options);
                } catch (OverlayException e) {
                    err.println("Failed: " + file + ": " + e.getMessage());
                    return 1;
                }
                for (OverlayWarning warning : result.warnings()) {
                    err.println(file + ": " + warning);
                }
                document = result.document();
            }
            Path directory = output.toAbsolutePath().getParent();
            if (directory != null) {
                Files.createDirectories(directory);
            }
            OverlayDocuments.write(document, output);
            out.println("Applied " + overlays.size() + " overlay(s) to " + input + ", wrote " + output);
            return 0;
        } catch (OverlayException | UncheckedIOException | IOException e) {
            err.println("Failed: " + e.getMessage());
            return 1;
        }
    }

    private static int usage(PrintStream err, @Nullable String problem) {
        if (problem != null) {
            err.println(problem);
        }
        err.println(USAGE);
        return 2;
    }
}
