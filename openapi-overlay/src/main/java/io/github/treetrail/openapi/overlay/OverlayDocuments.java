package io.github.treetrail.openapi.overlay;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.core.util.Separators;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.JsonNodeFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.fasterxml.jackson.dataformat.yaml.YAMLGenerator;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLParser;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import org.yaml.snakeyaml.LoaderOptions;

/**
 * Reads and writes OpenAPI descriptions and overlays as JSON or YAML, as Jackson 2 trees.
 *
 * <p>Duplicate member names are rejected, and decimal numbers are read as {@link java.math.BigDecimal}, so that
 * they are written back as they were. In YAML, only {@code true} and {@code false} are booleans, as in YAML 1.2:
 * the YAML 1.1 words {@code yes}, {@code no}, {@code on} and {@code off} stay strings, so that an enum of country
 * codes such as {@code [NO, SE]} is not turned into {@code [false, "SE"]}. YAML documents may be up to 256 MiB,
 * beyond SnakeYAML's default of 3 MiB that large OpenAPI descriptions exceed.
 */
public final class OverlayDocuments {

    /** The text format of a document. */
    public enum Format {
        /** JSON (RFC 8259). */
        JSON,
        /** YAML. */
        YAML
    }

    private static final int MAX_YAML_CODE_POINTS = 256 * 1024 * 1024;

    private static final ObjectMapper JSON_MAPPER = JsonMapper.builder()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
            .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
            .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
            .build();

    private static final YAMLMapper YAML_MAPPER = yamlMapper();

    private OverlayDocuments() {}

    private static YAMLMapper yamlMapper() {
        LoaderOptions loaderOptions = new LoaderOptions();
        loaderOptions.setCodePointLimit(MAX_YAML_CODE_POINTS);
        YAMLFactory factory = YAMLFactory.builder()
                .loaderOptions(loaderOptions)
                .enable(YAMLParser.Feature.PARSE_BOOLEAN_LIKE_WORDS_AS_STRINGS)
                .disable(YAMLGenerator.Feature.WRITE_DOC_START_MARKER)
                .enable(YAMLGenerator.Feature.MINIMIZE_QUOTES)
                .enable(YAMLGenerator.Feature.ALWAYS_QUOTE_NUMBERS_AS_STRINGS)
                .enable(YAMLGenerator.Feature.INDENT_ARRAYS_WITH_INDICATOR)
                .build();
        return YAMLMapper.builder(factory)
                .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
                .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
                .configure(JsonNodeFeature.STRIP_TRAILING_BIGDECIMAL_ZEROES, false)
                .enable(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN)
                .build();
    }

    /**
     * Parses JSON or YAML text: text that starts with <code>{</code> or <code>[</code> is read as JSON, and as
     * YAML if that fails; other text as YAML.
     *
     * @throws UncheckedIOException if the text is neither
     */
    public static JsonNode parse(String text) {
        String trimmed = text.stripLeading();
        if (trimmed.startsWith("﻿")) {
            trimmed = trimmed.substring(1).stripLeading();
        }
        if (trimmed.startsWith("{") || trimmed.startsWith("[")) {
            try {
                return JSON_MAPPER.readTree(trimmed);
            } catch (JsonProcessingException e) {
                // A YAML flow collection, such as {a: 1}, is no JSON.
            }
        }
        return parse(trimmed, Format.YAML);
    }

    /**
     * Parses text in the given format.
     *
     * @throws UncheckedIOException if the text is not valid in that format
     */
    public static JsonNode parse(String text, Format format) {
        try {
            JsonNode node = mapper(format).readTree(text);
            // An empty YAML document is a missing node.
            if (node == null || node.isMissingNode()) {
                throw new UncheckedIOException(new IOException("Empty document"));
            }
            return node;
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Reads a JSON or YAML file; see {@link #parse(String)}. */
    public static JsonNode read(Path file) {
        try {
            return parse(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Returns the format for a file name: JSON for {@code .json}, YAML otherwise. */
    public static Format formatOf(Path file) {
        Path name = file.getFileName();
        return name != null && name.toString().toLowerCase(Locale.ROOT).endsWith(".json") ? Format.JSON : Format.YAML;
    }

    /** Returns the document as text in the given format, indented by two spaces. */
    public static String write(JsonNode document, Format format) {
        try {
            if (format == Format.YAML) {
                return YAML_MAPPER.writeValueAsString(document);
            }
            DefaultPrettyPrinter printer = new DefaultPrettyPrinter(
                            Separators.createDefaultInstance().withObjectFieldValueSpacing(Separators.Spacing.AFTER))
                    .withArrayIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withLinefeed("\n"))
                    .withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE.withLinefeed("\n"));
            return JSON_MAPPER.writer(printer).writeValueAsString(document) + "\n";
        } catch (JsonProcessingException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Writes the document to a file, in the {@linkplain #formatOf(Path) format of its name}. */
    public static void write(JsonNode document, Path file) {
        try {
            Files.writeString(file, write(document, formatOf(file)), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static ObjectMapper mapper(Format format) {
        return format == Format.JSON ? JSON_MAPPER : YAML_MAPPER;
    }
}
