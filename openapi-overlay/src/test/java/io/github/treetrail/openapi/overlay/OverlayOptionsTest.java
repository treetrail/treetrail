package io.github.treetrail.openapi.overlay;

import static io.github.treetrail.openapi.overlay.OverlayApplyTest.yaml;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.EvaluationLimits;
import io.github.treetrail.jsonpath.JsonPathLimitExceededException;
import io.github.treetrail.jsonpath.NormalizedPath;
import org.junit.jupiter.api.Test;

/** {@link OverlayOptions} and the changes an application reports. */
class OverlayOptionsTest {

    static Overlay overlay(String actions) {
        return Overlay.parse("overlay: 1.1.0\ninfo: {title: t, version: '1'}\nactions:\n" + actions);
    }

    @Test
    void failOnNoMatch() {
        Overlay overlay = overlay("- {target: $.a, update: 2}\n- {target: $.missing, remove: true}");
        OverlayOptions options = OverlayOptions.DEFAULT.withFailOnNoMatch(true);

        assertThatThrownBy(() -> overlay.applyTo(yaml("{a: 1}"), options))
                .isInstanceOf(OverlayException.class)
                .isNotInstanceOf(InvalidOverlayException.class)
                .hasMessage("/actions/1: target $.missing selects no node")
                .extracting(e -> ((OverlayException) e).location())
                .isEqualTo("/actions/1");
        assertThat(overlay.applyTo(yaml("{a: 1, missing: 0}"), options).document())
                .isEqualTo(yaml("{a: 2}"));
    }

    @Test
    void lenientMergeReplacesIncompatibleValues() {
        OverlayOptions lenient = OverlayOptions.DEFAULT.withLenientMerge(true);

        // Speakeasy's lax mode: an object replaces a primitive target, and members of other kinds are replaced.
        assertThat(overlay("- {target: $.a, update: {b: 1}}")
                        .applyTo(yaml("{a: true}"), lenient)
                        .document())
                .isEqualTo(yaml("{a: {b: 1}}"));
        assertThat(overlay("- {target: $, update: {a: [1], c: x}}")
                        .applyTo(yaml("{a: {b: 1}, c: {d: 2}}"), lenient)
                        .document())
                .isEqualTo(yaml("{a: [1], c: x}"));
        assertThat(overlay("- {target: $.a, update: 1}")
                        .applyTo(yaml("{a: {}}"), lenient)
                        .document())
                .isEqualTo(yaml("{a: 1}"));
        // Targets of different kinds.
        assertThat(overlay("- {target: '$.*', update: {x: 1}}")
                        .applyTo(yaml("{a: {}, b: [], c: 3}"), lenient)
                        .document())
                .isEqualTo(yaml("{a: {x: 1}, b: [{x: 1}], c: {x: 1}}"));
        // Compatible values merge as in strict mode.
        assertThat(overlay("- {target: $, update: {a: [2], b: {c: 1}}}")
                        .applyTo(yaml("{a: [1], b: {d: 2}}"), lenient)
                        .document())
                .isEqualTo(yaml("{a: [1, 2], b: {d: 2, c: 1}}"));
    }

    @Test
    void lenientMergeSkipsTargetsInsideANodeItReplaced() {
        // Found by OverlayProperties: $..a selects $.a[0].a and, inside it, $.a[0].a.a; replacing the first by a
        // primitive leaves no node at the second path.
        OverlayResult result = overlay("- {target: '$..a', update: 7}")
                .applyTo(yaml("{a: [{a: {a: 0}}]}"), OverlayOptions.DEFAULT.withLenientMerge(true));

        assertThat(result.document()).isEqualTo(yaml("{a: [{a: 7}, 7]}"));
        assertThat(result.changes())
                .extracting(OverlayChange::kind)
                .containsExactly(OverlayChange.Kind.APPENDED, OverlayChange.Kind.REPLACED);
        assertThat(result.warnings())
                .extracting(OverlayWarning::message)
                .containsExactly("target $['a'][0]['a']['a'] is gone: an earlier target of this action that contained"
                        + " it was replaced");
        // Strict merging rejects the mixed targets before changing anything.
        assertThatThrownBy(() -> overlay("- {target: '$..a', update: 7}").applyTo(yaml("{a: [{a: {a: 0}}]}")))
                .hasMessageContaining("target selects arrays and objects");
    }

    @Test
    void limitsBoundTheQueries() {
        OverlayOptions tight = OverlayOptions.DEFAULT.withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(10));
        Overlay overlay = overlay("- {target: '$..x', remove: true}");

        assertThatThrownBy(() -> overlay.applyTo(yaml("{a: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]}"), tight))
                .isInstanceOf(JsonPathLimitExceededException.class);
        assertThat(overlay.applyTo(yaml("{a: [1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12]}"))
                        .warnings())
                .hasSize(1);
    }

    @Test
    void valueSemantics() {
        OverlayOptions options = OverlayOptions.DEFAULT.withFailOnNoMatch(true).withLenientMerge(true);

        assertThat(options.failOnNoMatch()).isTrue();
        assertThat(options.lenientMerge()).isTrue();
        assertThat(options.limits()).isEqualTo(EvaluationLimits.DEFAULT);
        assertThat(options)
                .isEqualTo(OverlayOptions.DEFAULT.withLenientMerge(true).withFailOnNoMatch(true))
                .hasSameHashCodeAs(OverlayOptions.DEFAULT.withLenientMerge(true).withFailOnNoMatch(true))
                .isNotEqualTo(OverlayOptions.DEFAULT)
                .isNotEqualTo(OverlayOptions.DEFAULT.withFailOnNoMatch(true))
                .isNotEqualTo(OverlayOptions.DEFAULT.withLenientMerge(true))
                .isNotEqualTo(options.withLimits(EvaluationLimits.DEFAULT.withMaxVisitedNodes(1)))
                .isNotEqualTo("options");
        assertThat(OverlayOptions.DEFAULT.failOnNoMatch()).isFalse();
        assertThat(OverlayOptions.DEFAULT.lenientMerge()).isFalse();
        assertThat(options.toString()).startsWith("OverlayOptions[failOnNoMatch=true, lenientMerge=true, limits=");
        assertThatThrownBy(() -> OverlayOptions.DEFAULT.withLimits(null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> overlay("- {target: $}").applyTo(yaml("{}"), null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void changesSayWhatEachActionDidWhere() {
        OverlayResult result = overlay("""
                        - {target: $.info, update: {x-a: 1}}
                        - {target: $.tags, update: [c, d]}
                        - {target: $.tags, update: e}
                        - {target: "$.servers[?@.url == 'dev']", remove: true}
                        - {target: $.info.title, copy: $.info.version}
                        - {target: $.missing, remove: true}
                        """)
                .applyTo(yaml("{info: {title: t, version: '2'}, tags: [a], servers: [{url: dev}, {url: prod}]}"));

        assertThat(result.changes())
                .containsExactly(
                        new OverlayChange(0, NormalizedPath.parse("$['info']"), OverlayChange.Kind.MERGED),
                        new OverlayChange(1, NormalizedPath.parse("$['tags']"), OverlayChange.Kind.CONCATENATED),
                        new OverlayChange(2, NormalizedPath.parse("$['tags']"), OverlayChange.Kind.APPENDED),
                        new OverlayChange(3, NormalizedPath.parse("$['servers'][0]"), OverlayChange.Kind.REMOVED),
                        new OverlayChange(4, NormalizedPath.parse("$['info']['title']"), OverlayChange.Kind.REPLACED));
        assertThat(result.changes().get(3)).hasToString("/actions/3: REMOVED $['servers'][0]");
        assertThat(result.warnings()).extracting(OverlayWarning::actionIndex).containsExactly(5);
        assertThat(result.document())
                .isEqualTo(
                        yaml("{info: {title: '2', version: '2', x-a: 1}, tags: [a, c, d, e], servers: [{url: prod}]}"));
    }

    @Test
    void removalsAreReportedInSelectionOrder() {
        OverlayResult result = overlay("- {target: '$.a[0, 2]', remove: true}").applyTo(yaml("{a: [0, 1, 2, 3]}"));

        assertThat(result.changes())
                .extracting(change -> change.path().toString())
                .containsExactly("$['a'][0]", "$['a'][2]");
        assertThat(result.document()).isEqualTo(yaml("{a: [1, 3]}"));
    }

    @Test
    void recordsCheckTheirArguments() {
        assertThatThrownBy(() -> new OverlayChange(0, null, OverlayChange.Kind.MERGED))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OverlayChange(0, NormalizedPath.root(), null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OverlayWarning(0, null, "m")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OverlayWarning(0, "$", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OverlayProblem(null, "m")).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OverlayProblem("", null)).isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new OverlayResult(null, java.util.List.of(), java.util.List.of()))
                .isInstanceOf(NullPointerException.class);
    }
}
