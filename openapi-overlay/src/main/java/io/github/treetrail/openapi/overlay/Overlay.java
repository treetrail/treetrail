package io.github.treetrail.openapi.overlay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.treetrail.jsonpath.JsonPath;
import io.github.treetrail.jsonpath.JsonPathSyntaxException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jspecify.annotations.Nullable;

/**
 * An OpenAPI Overlay document (Overlay Specification 1.0, 1.1 and 1.2): an ordered list of actions, each with an
 * RFC 9535 JSONPath target, to apply to an OpenAPI description or any other JSON document.
 *
 * <pre>{@code
 * Overlay overlay = Overlay.read(Path.of("codegen.overlay.yaml"));
 * OverlayResult result = overlay.applyTo(OverlayDocuments.read(Path.of("openapi.yaml")));
 * OverlayDocuments.write(result.document(), Path.of("build/openapi.yaml"));
 * }</pre>
 *
 * <p>Reading an overlay validates it against the specification of its version, including every rule of the
 * official JSON Schema, and compiles every target and copy query, so that an invalid overlay is rejected with an
 * {@link InvalidOverlayException}, listing all its problems, before it touches a document. Reusable actions
 * (Overlay 1.2) are resolved then too: {@link #actions()} holds only plain actions.
 *
 * <p>Instances are immutable and thread-safe.
 */
public final class Overlay {

    // Like the JSON Schemas' ^1\.1\.\d+$: no leading zeros in the minor version.
    private static final Pattern VERSION = Pattern.compile("1\\.(0|[1-9]\\d*)\\.\\d+");
    private static final int LATEST_MINOR = 2;
    private static final String ACTION_REFERENCE_PREFIX = "#/components/actions/";

    private final String version;
    private final String title;
    private final String infoVersion;
    private final @Nullable String description;
    private final @Nullable String extendsUri;
    private final @Nullable String self;
    private final List<OverlayAction> actions;
    private final Map<String, JsonNode> extensions;

    private Overlay(
            String version,
            String title,
            String infoVersion,
            @Nullable String description,
            @Nullable String extendsUri,
            @Nullable String self,
            List<OverlayAction> actions,
            Map<String, JsonNode> extensions) {
        this.version = version;
        this.title = title;
        this.infoVersion = infoVersion;
        this.description = description;
        this.extendsUri = extendsUri;
        this.self = self;
        this.actions = List.copyOf(actions);
        this.extensions = Map.copyOf(extensions);
    }

    /**
     * Parses an overlay from JSON or YAML text ({@link OverlayDocuments#parse(String)}).
     *
     * @throws InvalidOverlayException if the text is neither JSON nor YAML, or the overlay is invalid
     */
    public static Overlay parse(String text) {
        JsonNode document;
        try {
            document = OverlayDocuments.parse(text);
        } catch (UncheckedIOException e) {
            throw new InvalidOverlayException(List.of(new OverlayProblem("", notJsonOrYaml(e))));
        }
        return of(document);
    }

    /**
     * Reads an overlay from a JSON or YAML file.
     *
     * @throws InvalidOverlayException if the file is neither JSON nor YAML, or the overlay is invalid
     * @throws UncheckedIOException if the file cannot be read
     */
    public static Overlay read(Path file) {
        JsonNode document;
        try {
            document = OverlayDocuments.read(file);
        } catch (UncheckedIOException e) {
            if (!(e.getCause() instanceof JsonProcessingException)) {
                throw e;
            }
            throw new InvalidOverlayException(List.of(new OverlayProblem("", notJsonOrYaml(e))));
        }
        return of(document);
    }

    private static String notJsonOrYaml(UncheckedIOException e) {
        String message = e.getCause() instanceof JsonProcessingException parse
                ? parse.getOriginalMessage()
                : String.valueOf(e.getMessage());
        return "not a JSON or YAML document: " + message;
    }

    /**
     * Returns the overlay of a parsed document.
     *
     * @throws InvalidOverlayException if the overlay is invalid
     */
    public static Overlay of(JsonNode document) {
        Reader reader = new Reader();
        Overlay overlay = reader.read(Objects.requireNonNull(document, "document"));
        if (overlay == null) {
            throw new InvalidOverlayException(reader.problems);
        }
        return overlay;
    }

    /**
     * Returns everything that makes a parsed overlay document invalid, field by field (the version and the root's
     * fields first, then {@code info}, {@code components} and the actions); an empty list for a valid overlay.
     */
    public static List<OverlayProblem> validate(JsonNode document) {
        Reader reader = new Reader();
        reader.read(Objects.requireNonNull(document, "document"));
        return List.copyOf(reader.problems);
    }

    /**
     * Applies the actions in order to a copy of the document, with the {@linkplain OverlayOptions#DEFAULT default
     * options}; the document itself is not changed.
     *
     * @throws OverlayException if an action cannot be applied, for example because an update does not fit the
     *     selected nodes
     * @throws io.github.treetrail.jsonpath.JsonPathLimitExceededException if a target or copy query exceeds the
     *     evaluation limits
     */
    public OverlayResult applyTo(JsonNode document) {
        return applyTo(document, OverlayOptions.DEFAULT);
    }

    /**
     * Applies the actions in order to a copy of the document; the document itself is not changed.
     *
     * <p>Each action runs its target query on the document as the previous actions left it. Then:
     *
     * <ul>
     *   <li>{@code remove} removes every selected node from its parent; the root cannot be removed.
     *   <li>{@code update}, or the single node {@code copy} selects, is merged into every selected node: into an
     *       object member by member, recursively for objects, concatenating arrays and replacing primitives; into
     *       an array by concatenating an array value or appending any other value; a primitive replaces a
     *       primitive. The selected nodes must be all objects, all arrays or all primitives, and any other
     *       combination of values is an error, unless {@link OverlayOptions#lenientMerge()} makes the value
     *       replace the node.
     * </ul>
     *
     * <p>Where the specification leaves a case open, this implementation decides:
     *
     * <ul>
     *   <li>An action with {@code remove: true} removes; otherwise {@code update} wins over {@code copy} (the
     *       specification says each has no effect if the other has a value).
     *   <li>A node selected more than once, as by {@code $['a','a']}, is changed once.
     *   <li>JSON {@code null} is a primitive (RFC 8259, section 1): it replaces primitives, and merging it into an
     *       object or array is an error.
     *   <li>A {@code copy} query that selects no node or several nodes is an error.
     *   <li>A target that selects no node is a warning ({@link OverlayResult#warnings()}), as the specification
     *       says, or an error with {@link OverlayOptions#failOnNoMatch()}.
     *   <li>Overlay 1.0 overlays are applied with the rules of 1.1, which state the merge of arrays and
     *       primitives that 1.0 left open.
     * </ul>
     *
     * @throws OverlayException if an action cannot be applied, for example because an update does not fit the
     *     selected nodes, or, with {@link OverlayOptions#failOnNoMatch()}, a target selects no node
     * @throws io.github.treetrail.jsonpath.JsonPathLimitExceededException if a target or copy query exceeds the
     *     {@linkplain OverlayOptions#limits() limits}
     */
    public OverlayResult applyTo(JsonNode document, OverlayOptions options) {
        Objects.requireNonNull(options, "options");
        return new Applier(document.deepCopy(), options).apply(actions);
    }

    /** Returns the Overlay Specification version the overlay uses, for example {@code 1.1.0}. */
    public String version() {
        return version;
    }

    /** Returns {@code info.title}. */
    public String title() {
        return title;
    }

    /** Returns {@code info.version}, the version of the overlay itself. */
    public String infoVersion() {
        return infoVersion;
    }

    /** Returns {@code info.description}, or null. */
    public @Nullable String description() {
        return description;
    }

    /** Returns {@code extends}, the URI reference of the document the overlay was written for, or null. */
    public @Nullable String extendsUri() {
        return extendsUri;
    }

    /** Returns {@code $self} (Overlay 1.2), the URI of the overlay itself, or null. */
    public @Nullable String self() {
        return self;
    }

    /** Returns the actions in the order they are applied, with reusable actions resolved. */
    public List<OverlayAction> actions() {
        return actions;
    }

    /** Returns the specification extensions ({@code x-...}) of the overlay object. */
    public Map<String, JsonNode> extensions() {
        return extensions;
    }

    @Override
    public String toString() {
        return "Overlay " + version + " '" + title + "' " + infoVersion + " (" + actions.size() + " actions)";
    }

    /** Validates a parsed overlay document, collecting every problem, and builds the overlay if there is none. */
    private static final class Reader {

        final List<OverlayProblem> problems = new ArrayList<>();
        private int minor = LATEST_MINOR;

        @Nullable
        Overlay read(JsonNode root) {
            if (!root.isObject()) {
                problem("", "must be an object");
                return null;
            }
            String version = requiredString(root, "overlay", "");
            if (version != null) {
                Matcher matcher = VERSION.matcher(version);
                if (!matcher.matches()) {
                    problem("/overlay", "must be a version 1.x.y, not '" + version + "'");
                } else {
                    // Found by fuzzing: a minor version beyond int must not overflow.
                    String digits = matcher.group(1);
                    int parsed = digits.length() > 9 ? Integer.MAX_VALUE : Integer.parseInt(digits);
                    if (parsed > LATEST_MINOR) {
                        problem("/overlay", "Overlay " + version + " is not supported; the latest supported is 1.2");
                    } else {
                        minor = parsed;
                    }
                }
            }
            allowFields(
                    root,
                    "",
                    minor >= 2
                            ? Set.of("overlay", "info", "extends", "actions", "$self", "components")
                            : Set.of("overlay", "info", "extends", "actions"));

            String title = null;
            String infoVersion = null;
            String description = null;
            JsonNode info = required(root, "info", "");
            if (info != null && requireObject(info, "/info")) {
                // info.description is new in 1.1, but harmless, so accepted in 1.0 overlays too.
                allowFields(info, "/info", Set.of("title", "version", "description"));
                title = requiredString(info, "title", "/info");
                infoVersion = requiredString(info, "version", "/info");
                description = optionalString(info, "description", "/info");
            }

            String extendsUri = optionalString(root, "extends", "");
            String self = optionalString(root, "$self", "");
            if (minor >= 2) {
                // Both identify whole documents (Overlay 1.2, "Resolving URI Fragments").
                requireNoFragment(extendsUri, "/extends");
                requireNoFragment(self, "/$self");
            }

            Map<String, JsonNode> reusable = minor >= 2 ? reusableActions(root) : Map.of();

            List<OverlayAction> actions = new ArrayList<>();
            JsonNode actionNodes = required(root, "actions", "");
            if (actionNodes != null) {
                if (!actionNodes.isArray()) {
                    problem("/actions", "must be an array");
                } else if (actionNodes.isEmpty()) {
                    problem("/actions", "must contain at least one action");
                } else {
                    // JsonNode's equals and hashCode compare JSON values, object members in any order.
                    Map<JsonNode, Integer> seen = new HashMap<>();
                    for (int i = 0; i < actionNodes.size(); i++) {
                        JsonNode action = actionNodes.get(i);
                        Integer same = seen.putIfAbsent(action, i);
                        if (same != null) {
                            problem("/actions/" + i, "is the same as action " + same + "; actions must be unique");
                        }
                        OverlayAction parsed = action(action, i, reusable);
                        if (parsed != null) {
                            actions.add(parsed);
                        }
                    }
                }
            }
            if (!problems.isEmpty()) {
                return null;
            }
            return new Overlay(
                    Objects.requireNonNull(version),
                    Objects.requireNonNull(title),
                    Objects.requireNonNull(infoVersion),
                    description,
                    extendsUri,
                    self,
                    actions,
                    extensions(root));
        }

        /** Returns {@code components.actions} by key, after validating them. */
        private Map<String, JsonNode> reusableActions(JsonNode root) {
            JsonNode components = root.get("components");
            if (components == null || !requireObject(components, "/components")) {
                return Map.of();
            }
            allowFields(components, "/components", Set.of("actions"));
            JsonNode actions = components.get("actions");
            if (actions == null || !requireObject(actions, "/components/actions")) {
                return Map.of();
            }
            Map<String, JsonNode> result = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : actions.properties()) {
                String location = "/components/actions/" + pointerToken(entry.getKey());
                JsonNode action = entry.getValue();
                if (!requireObject(action, location)) {
                    continue;
                }
                allowFields(action, location, Set.of("description", "fields"));
                optionalString(action, "description", location);
                JsonNode fields = action.get("fields");
                if (fields != null && requireObject(fields, location + "/fields")) {
                    // Like an action object, but without target, which the reference supplies.
                    allowFields(fields, location + "/fields", Set.of("description", "update", "copy", "remove"));
                    optionalString(fields, "description", location + "/fields");
                    optionalBoolean(fields, "remove", location + "/fields");
                    compileOptional(fields, "copy", location + "/fields");
                }
                result.put(entry.getKey(), action);
            }
            return result;
        }

        private @Nullable OverlayAction action(JsonNode action, int index, Map<String, JsonNode> reusable) {
            String location = "/actions/" + index;
            if (!requireObject(action, location)) {
                return null;
            }
            if (minor >= 2 && action.has("$ref")) {
                return reference(action, index, reusable);
            }
            allowFields(
                    action,
                    location,
                    minor >= 1
                            ? Set.of("target", "description", "update", "copy", "remove")
                            : Set.of("target", "description", "update", "remove"));
            JsonPath target = compileRequired(action, "target", location);
            String description = optionalString(action, "description", location);
            JsonPath copy = compileOptional(action, "copy", location);
            boolean remove = optionalBoolean(action, "remove", location);
            if (target == null) {
                return null;
            }
            return new OverlayAction(
                    index, target, description, action.get("update"), copy, remove, null, extensions(action));
        }

        /**
         * The unescaped key of a reference {@code #/components/actions/<key>}, or null if the reference has another
         * form. Checked without a regular expression: java.util.regex matches an alternation in a loop recursively, so
         * a long reference overflowed the stack (found by fuzzing).
         */
        private static @Nullable String actionKey(String ref) {
            if (!ref.startsWith(ACTION_REFERENCE_PREFIX) || ref.length() == ACTION_REFERENCE_PREFIX.length()) {
                return null;
            }
            StringBuilder key = new StringBuilder(ref.length() - ACTION_REFERENCE_PREFIX.length());
            for (int i = ACTION_REFERENCE_PREFIX.length(); i < ref.length(); i++) {
                char c = ref.charAt(i);
                if (c == '/') {
                    return null;
                }
                if (c == '~') {
                    char next = i + 1 < ref.length() ? ref.charAt(i + 1) : 0;
                    if (next != '0' && next != '1') {
                        return null;
                    }
                    key.append(next == '0' ? '~' : '/');
                    i++;
                } else {
                    key.append(c);
                }
            }
            return key.toString();
        }

        /** A Reusable Action Reference Object (Overlay 1.2), resolved against {@code components.actions}. */
        private @Nullable OverlayAction reference(JsonNode reference, int index, Map<String, JsonNode> reusable) {
            String location = "/actions/" + index;
            allowFields(reference, location, Set.of("$ref", "target", "description"));
            JsonPath target = compileRequired(reference, "target", location);
            String description = optionalString(reference, "description", location);
            String ref = requiredString(reference, "$ref", location);
            if (ref == null) {
                return null;
            }
            String key = actionKey(ref);
            if (key == null) {
                problem(
                        location + "/$ref",
                        "must be '#/components/actions/' followed by an escaped key (~0 for ~, ~1 for /), not '" + ref
                                + "'");
                return null;
            }
            JsonNode action = reusable.get(key);
            if (action == null) {
                problem(location + "/$ref", "there is no reusable action '" + key + "'");
                return null;
            }
            if (target == null) {
                return null;
            }
            JsonNode fields = action.get("fields");
            JsonNode update = null;
            JsonPath copy = null;
            boolean remove = false;
            if (fields != null && fields.isObject()) {
                if (description == null) {
                    JsonNode fieldsDescription = fields.get("description");
                    description = fieldsDescription != null ? fieldsDescription.textValue() : null;
                }
                update = fields.get("update");
                JsonNode copyQuery = fields.get("copy");
                // Validated with the reusable action; compiling again cannot fail.
                copy = copyQuery != null && copyQuery.isTextual() ? JsonPath.compile(copyQuery.textValue()) : null;
                remove = fields.path("remove").booleanValue();
            }
            return new OverlayAction(index, target, description, update, copy, remove, key, extensions(reference));
        }

        private @Nullable JsonPath compileRequired(JsonNode object, String name, String location) {
            String query = requiredString(object, name, location);
            return query == null ? null : compile(query, location + "/" + name);
        }

        private @Nullable JsonPath compileOptional(JsonNode object, String name, String location) {
            String query = optionalString(object, name, location);
            return query == null ? null : compile(query, location + "/" + name);
        }

        private @Nullable JsonPath compile(String query, String location) {
            try {
                return JsonPath.compile(query);
            } catch (JsonPathSyntaxException e) {
                problem(location, "not an RFC 9535 JSONPath query: " + e.getMessage());
                return null;
            }
        }

        private boolean requireObject(JsonNode node, String location) {
            if (node.isObject()) {
                return true;
            }
            problem(location, "must be an object");
            return false;
        }

        private @Nullable JsonNode required(JsonNode object, String name, String location) {
            JsonNode value = object.get(name);
            if (value == null) {
                problem(location, "'" + name + "' is required");
            }
            return value;
        }

        private @Nullable String requiredString(JsonNode object, String name, String location) {
            JsonNode value = required(object, name, location);
            return value == null ? null : string(value, location + "/" + pointerToken(name));
        }

        private @Nullable String optionalString(JsonNode object, String name, String location) {
            JsonNode value = object.get(name);
            return value == null ? null : string(value, location + "/" + pointerToken(name));
        }

        private @Nullable String string(JsonNode value, String location) {
            if (!value.isTextual()) {
                problem(location, "must be a string");
                return null;
            }
            return value.textValue();
        }

        private boolean optionalBoolean(JsonNode object, String name, String location) {
            JsonNode value = object.get(name);
            if (value == null) {
                return false;
            }
            if (!value.isBoolean()) {
                problem(location + "/" + name, "must be a boolean");
                return false;
            }
            return value.booleanValue();
        }

        private void requireNoFragment(@Nullable String uri, String location) {
            if (uri != null && uri.indexOf('#') >= 0) {
                problem(location, "must not contain a fragment");
            }
        }

        /** Rejects fields other than the given ones and specification extensions. */
        private void allowFields(JsonNode object, String location, Set<String> allowed) {
            for (Map.Entry<String, JsonNode> entry : object.properties()) {
                String name = entry.getKey();
                if (!allowed.contains(name) && !name.startsWith("x-")) {
                    problem(location, "unknown field '" + name + "'");
                }
            }
        }

        private static Map<String, JsonNode> extensions(JsonNode object) {
            Map<String, JsonNode> extensions = new LinkedHashMap<>();
            for (Map.Entry<String, JsonNode> entry : object.properties()) {
                if (entry.getKey().startsWith("x-")) {
                    extensions.put(entry.getKey(), entry.getValue());
                }
            }
            return extensions;
        }

        private static String pointerToken(String name) {
            return name.replace("~", "~0").replace("/", "~1");
        }

        private void problem(String location, String message) {
            problems.add(new OverlayProblem(location, message));
        }
    }
}
