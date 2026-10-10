package io.github.treetrail.jsonpath.testkit;

import io.github.treetrail.jsonpath.JavaObjectModel;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * One case of the JSONPath Compliance Test Suite.
 *
 * @param name the name of the case
 * @param selector the JSONPath expression
 * @param invalidSelector whether the expression is not valid RFC 9535; such cases have no document
 * @param documentJson the document as JSON text, or {@code null} for an invalid selector
 * @param results the values the expression selects: one list, or several allowed alternatives where the
 *     order of object members is not defined; values are plain Java objects as {@link JavaObjectModel} parses
 *     them
 * @param resultPaths the normalized paths of the selected nodes, one list per alternative of {@code results},
 *     or empty if the case does not give them
 */
public record ComplianceCase(
        String name,
        String selector,
        boolean invalidSelector,
        @Nullable String documentJson,
        List<List<@Nullable Object>> results,
        List<List<String>> resultPaths) {

    /** Returns the document as plain Java objects ({@link JavaObjectModel}); {@code null} for JSON null. */
    public @Nullable Object document() {
        if (documentJson == null) {
            throw new IllegalStateException("The case '" + name + "' has an invalid selector and no document");
        }
        return JavaObjectModel.parse(documentJson);
    }
}
