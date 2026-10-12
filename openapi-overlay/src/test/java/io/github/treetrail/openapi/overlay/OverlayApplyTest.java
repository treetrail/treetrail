package io.github.treetrail.openapi.overlay;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/** The rules of the Action Object, and the edge cases the specification leaves to implementations. */
class OverlayApplyTest {

    static JsonNode yaml(String text) {
        return OverlayDocuments.parse(text, OverlayDocuments.Format.YAML);
    }

    /** Applies an overlay with the given actions (YAML, one per list item) to a document. */
    static OverlayResult apply(String version, String actions, String document) {
        String overlay = "overlay: " + version + "\ninfo: {title: test, version: '1'}\nactions:\n" + actions;
        return Overlay.parse(overlay).applyTo(yaml(document));
    }

    static JsonNode apply(String actions, String document) {
        return apply("1.1.0", actions, document).document();
    }

    @Nested
    class Targets {

        @Test
        void rootIsMergedLikeAnyObject() {
            assertThat(apply("- target: $\n  update: {info: {x-a: 1}, x-b: 2}", "{info: {title: t}}"))
                    .isEqualTo(yaml("{info: {title: t, x-a: 1}, x-b: 2}"));
        }

        @Test
        void primitiveRootIsReplaced() {
            assertThat(apply("- target: $\n  update: 6", "5")).isEqualTo(yaml("6"));
        }

        @Test
        void rootCannotBeRemoved() {
            assertThatThrownBy(() -> apply("- target: $\n  remove: true", "{a: 1}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage("/actions/0: cannot remove the root of the document");
        }

        @Test
        void noMatchChangesNothingAndWarns() {
            OverlayResult result = apply("1.1.0", "- target: $.nothing\n  update: {a: 1}", "{b: 2}");
            assertThat(result.document()).isEqualTo(yaml("{b: 2}"));
            assertThat(result.warnings())
                    .containsExactly(new OverlayWarning(0, "$.nothing", "target $.nothing selects no node"));
            assertThat(result.warnings().get(0)).hasToString("/actions/0: target $.nothing selects no node");
            assertThat(result.changes()).isEmpty();
        }

        @Test
        void actionWithoutModifierChangesNothing() {
            OverlayResult result = apply("1.1.0", "- target: $.a\n  description: only a note", "{a: 1}");
            assertThat(result.document()).isEqualTo(yaml("{a: 1}"));
            assertThat(result.warnings()).isEmpty();
        }

        @Test
        void nodeSelectedTwiceIsChangedOnce() {
            assertThat(apply("- target: $['a','a']\n  update: [2]", "{a: [1]}")).isEqualTo(yaml("{a: [1, 2]}"));
            assertThat(apply("- target: $.a[0,0]\n  remove: true", "{a: [1, 2]}"))
                    .isEqualTo(yaml("{a: [2]}"));
        }

        @Test
        void namesThatNeedEscapingInPointersOrPaths() {
            String document = "{paths: {'/a~b/{id}': {get: {}}, \"it's\": {get: {}}}}";
            assertThat(apply(
                            "- target: \"$.paths['/a~b/{id}'].get\"\n  update: {x: 1}\n"
                                    + "- target: \"$.paths[\\\"it's\\\"].get\"\n  update: {y: 2}",
                            document))
                    .isEqualTo(yaml("{paths: {'/a~b/{id}': {get: {x: 1}}, \"it's\": {get: {y: 2}}}}"));
        }

        @Test
        void actionsSeeTheResultOfThePreviousOnes() {
            assertThat(apply("""
                            - target: $.servers
                              remove: true
                            - target: $
                              update: {servers: [{url: b}]}
                            - target: $.servers[?@.url == 'b']
                              update: {description: new}""", "{servers: [{url: a}]}")).isEqualTo(yaml("{servers: [{url: b, description: new}]}"));
        }
    }

    @Nested
    class Update {

        @Test
        void primitivesAreReplacedWhereTheyAre() {
            JsonNode result = apply(
                    "- target: $..description\n  update: changed",
                    "{info: {description: a, title: t}, paths: {/x: {get: {description: b}}}}");
            assertThat(result)
                    .isEqualTo(yaml("{info: {description: changed, title: t}, "
                            + "paths: {/x: {get: {description: changed}}}}"));
            List<String> names = new ArrayList<>();
            result.get("info").fieldNames().forEachRemaining(names::add);
            assertThat(names).as("the member keeps its place").containsExactly("description", "title");
        }

        @Test
        void arrayElementsThatArePrimitivesAreReplaced() {
            assertThat(apply("- target: $.tags[?@ == 'old']\n  update: new", "{tags: [a, old, b, old]}"))
                    .isEqualTo(yaml("{tags: [a, new, b, new]}"));
        }

        @Test
        void arrayTargetsGetArraysConcatenatedAndOtherValuesAppended() {
            assertThat(apply("- target: $.a\n  update: [3, 4]", "{a: [1, 2]}")).isEqualTo(yaml("{a: [1, 2, 3, 4]}"));
            assertThat(apply("- target: $.a\n  update: {b: 1}", "{a: [1]}")).isEqualTo(yaml("{a: [1, {b: 1}]}"));
            assertThat(apply("- target: $.a\n  update: x", "{a: [1]}")).isEqualTo(yaml("{a: [1, x]}"));
            assertThat(apply("- target: $.a\n  update: null", "{a: [1]}")).isEqualTo(yaml("{a: [1, null]}"));
        }

        @Test
        void nestedArraysAreConcatenatedAndNestedObjectsMerged() {
            assertThat(apply(
                            "- target: $.op\n  update: {tags: [b], responses: {'404': {description: nf}}}",
                            "{op: {tags: [a], responses: {'200': {description: ok}}}}"))
                    .isEqualTo(yaml("{op: {tags: [a, b], "
                            + "responses: {'200': {description: ok}, '404': {description: nf}}}}"));
        }

        @Test
        void nullIsAPrimitive() {
            assertThat(apply("- target: $.a\n  update: null", "{a: 1}")).isEqualTo(yaml("{a: null}"));
            assertThat(apply("- target: $\n  update: {a: 1}", "{a: null}")).isEqualTo(yaml("{a: 1}"));
        }

        @Test
        void nullCannotBeMergedIntoAnObject() {
            // The structured example of the specification, on a description that has components.
            assertThatThrownBy(() -> apply("- target: $\n  update: {components: null}", "{components: {schemas: {}}}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage("/actions/0: cannot merge a primitive value into the object at $['components']"
                            + " (/components)");
        }

        @Test
        void objectTargetsNeedAnObject() {
            assertThatThrownBy(() -> apply("- target: $.info\n  update: text", "{info: {}}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage("/actions/0: cannot merge a primitive value into the object at $['info'] (/info)");
            assertThatThrownBy(() -> apply("- target: $.info\n  update: [1]", "{info: {}}"))
                    .hasMessageContaining("cannot merge an array into the object");
        }

        @Test
        void primitiveTargetsNeedAPrimitive() {
            assertThatThrownBy(() -> apply("- target: $.a\n  update: {b: 1}", "{a: 1}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage("/actions/0: cannot merge an object into the primitive value at $['a'] (/a)");
        }

        @Test
        void incompatibleMembersAreAnError() {
            assertThatThrownBy(() -> apply("- target: $\n  update: {a: {b: [1]}}", "{a: {b: {c: 1}}}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage("/actions/0: cannot merge an array into the object at $['a']['b'] (/a/b)");
        }

        @Test
        void targetsOfMixedKindsAreAnError() {
            assertThatThrownBy(() -> apply("- target: $.*\n  update: {x: 1}", "{a: {}, b: []}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessageContaining("target selects objects and arrays ($['a'], $['b'])");
        }

        @Test
        void aFailedActionLeavesTheInputUnchanged() {
            JsonNode document = yaml("{a: {x: 1}, b: {x: [1]}}");
            Overlay overlay = Overlay.parse(
                    "overlay: 1.1.0\ninfo: {title: t, version: '1'}\n" + "actions: [{target: '$.*', update: {x: 2}}]");
            assertThatThrownBy(() -> overlay.applyTo(document)).isInstanceOf(OverlayException.class);
            assertThat(document).isEqualTo(yaml("{a: {x: 1}, b: {x: [1]}}"));
        }

        @Test
        void removeWinsOverUpdate() {
            assertThat(apply("- target: $.a\n  update: {b: 1}\n  remove: true", "{a: {}, c: 1}"))
                    .isEqualTo(yaml("{c: 1}"));
        }
    }

    @Nested
    class Remove {

        @Test
        void severalElementsOfOneArray() {
            assertThat(apply("- target: $.a[0,2,4]\n  remove: true", "{a: [0, 1, 2, 3, 4, 5]}"))
                    .isEqualTo(yaml("{a: [1, 3, 5]}"));
            assertThat(apply("- target: $.a[4,0,2]\n  remove: true", "{a: [0, 1, 2, 3, 4, 5]}"))
                    .isEqualTo(yaml("{a: [1, 3, 5]}"));
            assertThat(apply("- target: $.a[1:]\n  remove: true", "{a: [0, 1, 2]}"))
                    .isEqualTo(yaml("{a: [0]}"));
            assertThat(apply("- target: $.a[-1]\n  remove: true", "{a: [0, 1, 2]}"))
                    .isEqualTo(yaml("{a: [0, 1]}"));
            assertThat(apply("- target: $.a[::2]\n  remove: true", "{a: [0, 1, 2, 3]}"))
                    .isEqualTo(yaml("{a: [1, 3]}"));
        }

        @Test
        void nodesInsideOtherSelectedNodes() {
            assertThat(apply(
                            "- target: $..[?@.drop == true]\n  remove: true",
                            "{a: [{drop: true, items: [{drop: true}, {keep: 1}]}, {keep: 2}, {drop: true}]}"))
                    .isEqualTo(yaml("{a: [{keep: 2}]}"));
        }

        @Test
        void membersAndElementsInOneAction() {
            assertThat(apply(
                            "- target: $..[?@.internal]\n  remove: true",
                            "{paths: {/a: {internal: true}, /b: {get: {parameters: [{internal: 1}, {name: n}]}}}}"))
                    .isEqualTo(yaml("{paths: {/b: {get: {parameters: [{name: n}]}}}}"));
        }
    }

    @Nested
    class Copy {

        @Test
        void sourceMustExist() {
            assertThatThrownBy(() -> apply("- target: $.a\n  copy: $.missing", "{a: {}}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage("/actions/0: copy $.missing must select exactly one node, but selects 0");
        }

        @Test
        void sourceMustBeASingleNode() {
            assertThatThrownBy(() -> apply("- target: $.a\n  copy: $.b[*]", "{a: {}, b: [{}, {}]}"))
                    .hasMessage("/actions/0: copy $.b[*] must select exactly one node, but selects 2");
        }

        @Test
        void noTargetMeansTheSourceIsNotNeeded() {
            OverlayResult result = apply("1.1.0", "- target: $.missing\n  copy: $.alsoMissing", "{a: 1}");
            assertThat(result.document()).isEqualTo(yaml("{a: 1}"));
            assertThat(result.warnings()).hasSize(1);
        }

        @Test
        void intoSeveralTargets() {
            assertThat(apply("- target: $.paths.*.get.tags\n  copy: $.defaultTags", """
                            defaultTags: [x]
                            paths: {/a: {get: {tags: [a]}}, /b: {get: {tags: []}}}"""))
                    .isEqualTo(yaml("""
                            defaultTags: [x]
                            paths: {/a: {get: {tags: [a, x]}}, /b: {get: {tags: [x]}}}"""));
        }

        @Test
        void aNodeIntoAChildOfItself() {
            assertThat(apply("- target: $.a.b\n  copy: $.a", "{a: {x: 1, b: {y: 2}}}"))
                    .isEqualTo(yaml("{a: {x: 1, b: {y: 2, x: 1, b: {y: 2}}}}"));
        }

        @Test
        void aPrimitive() {
            assertThat(apply("- target: $..[?@.version].version\n  copy: $.info.version", """
                            info: {version: 2.0.0}
                            x-clients: [{version: 1.0.0}, {version: 1.5.0}]"""))
                    .isEqualTo(yaml("""
                            info: {version: 2.0.0}
                            x-clients: [{version: 2.0.0}, {version: 2.0.0}]"""));
        }

        @Test
        void updateWinsOverCopy() {
            // The specification says each has no impact if the other has a value; like Speakeasy, update wins.
            assertThat(apply("- target: $.a\n  update: {u: 1}\n  copy: $.b", "{a: {}, b: {c: 1}}"))
                    .isEqualTo(yaml("{a: {u: 1}, b: {c: 1}}"));
        }
    }

    @Nested
    class Versions {

        @Test
        void copyIsNewIn11() {
            assertThatThrownBy(() -> apply("1.0.0", "- target: $.a\n  copy: $.b", "{}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage("/actions/0: unknown field 'copy'");
        }

        @Test
        void reusableActionsAreNewIn12() {
            String overlay = "overlay: 1.1.0\ninfo: {title: t, version: '1'}\n"
                    + "components: {actions: {}}\nactions: [{target: $}]";
            assertThatThrownBy(() -> Overlay.parse(overlay)).hasMessage("Overlay: unknown field 'components'");
        }

        @Test
        void laterVersionsAreRejected() {
            assertThatThrownBy(() -> apply("1.3.0", "- target: $", "{}"))
                    .hasMessage("/overlay: Overlay 1.3.0 is not supported; the latest supported is 1.2");
            assertThatThrownBy(() -> apply("2.0.0", "- target: $", "{}"))
                    .hasMessage("/overlay: must be a version 1.x.y, not '2.0.0'");
        }

        @Test
        void patchVersionsAreIgnored() {
            assertThat(apply("1.1.7", "- target: $.a\n  update: 2", "{a: 1}").document())
                    .isEqualTo(yaml("{a: 2}"));
        }
    }

    @Nested
    class ReusableActions {

        static final String OVERLAY = """
                overlay: 1.2.0
                info: {title: t, version: '1'}
                components:
                  actions:
                    'error/v1~beta':
                      description: the reusable action
                      fields:
                        description: from the fields
                        update: {'404': {description: Not Found}}
                    removal:
                      fields: {remove: true}
                    nothing: {}
                actions:
                  - $ref: '#/components/actions/error~1v1~0beta'
                    target: $.paths.*.get.responses
                  - $ref: '#/components/actions/error~1v1~0beta'
                    target: $.paths['/b'].get.responses
                    description: from the reference
                    x-note: 1
                  - $ref: '#/components/actions/removal'
                    target: $.paths['/c']
                  - $ref: '#/components/actions/nothing'
                    target: $.paths
                """;

        @Test
        void areResolvedWhenParsed() {
            Overlay overlay = Overlay.parse(OVERLAY);
            assertThat(overlay.actions())
                    .extracting(OverlayAction::reusableAction)
                    .containsExactly("error/v1~beta", "error/v1~beta", "removal", "nothing");
            assertThat(overlay.actions())
                    .extracting(OverlayAction::description)
                    .containsExactly("from the fields", "from the reference", null, null);
            assertThat(overlay.actions())
                    .extracting(OverlayAction::kind)
                    .containsExactly(
                            OverlayAction.Kind.UPDATE,
                            OverlayAction.Kind.UPDATE,
                            OverlayAction.Kind.REMOVE,
                            OverlayAction.Kind.NONE);
            assertThat(overlay.actions().get(1).extensions()).containsOnlyKeys("x-note");
        }

        @Test
        void applied() {
            JsonNode result = Overlay.parse(OVERLAY).applyTo(yaml("""
                            paths:
                              /a: {get: {responses: {'200': {description: OK}}}}
                              /b: {get: {responses: {}}}
                              /c: {get: {responses: {}}}""")).document();
            assertThat(result).isEqualTo(yaml("""
                    paths:
                      /a: {get: {responses: {'200': {description: OK}, '404': {description: Not Found}}}}
                      /b: {get: {responses: {'404': {description: Not Found}}}}"""));
        }

        @Test
        void unknownKey() {
            assertThatThrownBy(() -> Overlay.parse(
                            OVERLAY.replace("#/components/actions/removal", "#/components/actions/missing")))
                    .isInstanceOf(OverlayException.class)
                    .hasMessage("/actions/2/$ref: there is no reusable action 'missing'");
        }
    }

    @Nested
    class Metadata {

        @Test
        void fieldsOfTheOverlay() {
            Overlay overlay = Overlay.parse("""
                    overlay: 1.2.0
                    $self: https://example.com/overlays/a.yaml
                    info: {title: T, version: '2', description: D, x-i: 1}
                    extends: ../openapi.yaml
                    x-root: true
                    actions:
                      - target: $.info
                        description: first
                        update: {x-a: 1}
                        x-action: 1
                    """);
            assertThat(overlay.version()).isEqualTo("1.2.0");
            assertThat(overlay.title()).isEqualTo("T");
            assertThat(overlay.infoVersion()).isEqualTo("2");
            assertThat(overlay.description()).isEqualTo("D");
            assertThat(overlay.extendsUri()).isEqualTo("../openapi.yaml");
            assertThat(overlay.self()).isEqualTo("https://example.com/overlays/a.yaml");
            assertThat(overlay.extensions()).containsOnlyKeys("x-root");
            OverlayAction action = overlay.actions().get(0);
            assertThat(action.index()).isZero();
            assertThat(action.target().expression()).isEqualTo("$.info");
            assertThat(action.description()).isEqualTo("first");
            assertThat(action.update()).isEqualTo(yaml("{x-a: 1}"));
            assertThat(action.copy()).isNull();
            assertThat(action.remove()).isFalse();
            assertThat(action.reusableAction()).isNull();
            assertThat(action.extensions()).containsOnlyKeys("x-action");
        }

        @Test
        void invalidCopyQuery() {
            assertThatThrownBy(() -> apply("- target: $.a\n  copy: $.a[?@.b = 1]", "{}"))
                    .isInstanceOf(OverlayException.class)
                    .hasMessageStartingWith("/actions/0/copy: not an RFC 9535 JSONPath query")
                    .extracting(e -> ((OverlayException) e).location())
                    .isEqualTo("/actions/0/copy");
        }
    }
}
