package io.github.treetrail.jsonpath.testkit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.treetrail.jsonpath.JavaObjectModel;
import io.github.treetrail.jsonpath.JsonKind;
import io.github.treetrail.jsonpath.JsonModel;
import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.function.Executable;

class JsonModelTestKitTest {

    @Test
    void readsTheWholeComplianceSuiteWithoutJackson() {
        List<ComplianceCase> cases = JsonModelTestKit.complianceCases();

        assertThat(cases).hasSize(706);
        assertThat(cases).filteredOn(ComplianceCase::invalidSelector).allMatch(c -> c.documentJson() == null);
        assertThat(cases)
                .filteredOn(c -> !c.invalidSelector())
                .allMatch(c -> !c.results().isEmpty());
        ComplianceCase first = cases.get(0);
        assertThat(first.selector()).isEqualTo("$");
        assertThat(JavaObjectModel.jsonEquals(first.document(), JavaObjectModel.parse(first.documentJson())))
                .isTrue();
    }

    @TestFactory
    Stream<DynamicTest> theReferenceModelPassesEverything() {
        return Stream.concat(
                JsonModelTestKit.complianceTests(JavaObjectModel.INSTANCE, JavaObjectModel::parse),
                JsonModelTestKit.contractTests(JavaObjectModel.INSTANCE, JavaObjectModel::parse));
    }

    @Test
    void reportsAModelThatConfusesMissingMembersWithJsonNull() {
        JsonModel<@Nullable Object> broken = new Delegating() {
            @Override
            public boolean hasMember(@Nullable Object object, String name) {
                return JavaObjectModel.INSTANCE.member(object, name) != null;
            }
        };

        assertThat(failures(JsonModelTestKit.contractTests(broken, JavaObjectModel::parse)))
                .contains("missing members and JSON null");
        assertThat(failures(JsonModelTestKit.complianceTests(broken, JavaObjectModel::parse)))
                .isNotEmpty();
    }

    @Test
    void reportsAModelWithWrongNumbers() {
        JsonModel<@Nullable Object> broken = new Delegating() {
            @Override
            public @Nullable BigDecimal numberValue(@Nullable Object value) {
                BigDecimal number = JavaObjectModel.INSTANCE.numberValue(value);
                return number == null ? null : number.setScale(0, java.math.RoundingMode.DOWN);
            }
        };

        assertThat(failures(JsonModelTestKit.contractTests(broken, JavaObjectModel::parse)))
                .contains("numbers");
    }

    @Test
    void showsTheSelectedValuesWhenACaseFails() {
        JsonModel<@Nullable Object> reversed = new Delegating() {
            @Override
            public @Nullable Object element(@Nullable Object array, int index) {
                return JavaObjectModel.INSTANCE.element(array, size(array) - 1 - index);
            }
        };
        DynamicTest slice = JsonModelTestKit.complianceTests(reversed, JavaObjectModel::parse)
                .filter(t -> t.getDisplayName().startsWith("slice selector, slice selector |"))
                .findFirst()
                .orElseThrow();

        assertThatThrownBy(slice.getExecutable()::execute)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("Expected [")
                .hasMessageContaining("but the query selected [");
    }

    private static List<String> failures(Stream<DynamicTest> tests) {
        return tests.filter(test -> fails(test.getExecutable()))
                .map(DynamicTest::getDisplayName)
                .toList();
    }

    private static boolean fails(Executable executable) {
        try {
            executable.execute();
            return false;
        } catch (Throwable e) {
            return true;
        }
    }

    /** JavaObjectModel behind the abstract methods, so that a test can break one of them. */
    private abstract static class Delegating implements JsonModel<@Nullable Object> {
        private static final JavaObjectModel MODEL = JavaObjectModel.INSTANCE;

        @Override
        public JsonKind kind(@Nullable Object value) {
            return MODEL.kind(value);
        }

        @Override
        public Iterable<String> memberNames(@Nullable Object object) {
            return MODEL.memberNames(object);
        }

        @Override
        public boolean hasMember(@Nullable Object object, String name) {
            return MODEL.hasMember(object, name);
        }

        @Override
        public @Nullable Object member(@Nullable Object object, String name) {
            return MODEL.member(object, name);
        }

        @Override
        public int memberCount(@Nullable Object object) {
            return MODEL.memberCount(object);
        }

        @Override
        public int size(@Nullable Object array) {
            return MODEL.size(array);
        }

        @Override
        public @Nullable Object element(@Nullable Object array, int index) {
            return MODEL.element(array, index);
        }

        @Override
        public String stringValue(@Nullable Object value) {
            return MODEL.stringValue(value);
        }

        @Override
        public @Nullable BigDecimal numberValue(@Nullable Object value) {
            return MODEL.numberValue(value);
        }

        @Override
        public boolean booleanValue(@Nullable Object value) {
            return MODEL.booleanValue(value);
        }
    }
}
