package io.github.treetrail.jsonpath.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import org.junit.jupiter.api.Test;

/**
 * Unicode categories from a fixed Unicode version (issue #24). The build also runs these tests on Java 17
 * (Unicode 13.0) and 21 (Unicode 15.0).
 */
class UnicodeCategoriesTest {

    @Test
    void usesUnicode16() {
        assertThat(UnicodeCategoryData.VERSION).isEqualTo("16.0.0");
    }

    @Test
    void agreesWithJava25ForEveryCodePoint() {
        // Java 25 implements Unicode 16.0, the version of the tables.
        assumeTrue(Runtime.version().feature() == 25);
        for (int cp = 0; cp <= Character.MAX_CODE_POINT; cp++) {
            if (UnicodeCategories.type(cp) != Character.getType(cp)) {
                assertThat(UnicodeCategories.type(cp)).as("U+%04X", cp).isEqualTo(Character.getType(cp));
            }
        }
    }

    @Test
    void knowsCodePointsAssignedAfterUnicode13OnEveryJdk() {
        // Unicode 14.0, 15.0 and 16.0; unassigned in Java 17's Unicode 13.0.
        assertMatches("\\p{Lo}", 0x0870);
        assertMatches("\\p{L}", 0x1E4D0);
        assertMatches("\\p{Lu}", 0x1C89);
        assertMatches("\\p{Ll}", 0x1C8A);
        assertMatches("\\p{So}", 0x1CC00);
        assertMatches("\\P{Cn}", 0x1C89);
        // Within a range of UnicodeData.txt (CJK Ideograph Extension H, Unicode 15.0).
        assertMatches("\\p{Lo}", 0x31350 + 100);
    }

    @Test
    void classifiesUnassignedSurrogateAndPrivateUseCodePoints() {
        assertThat(UnicodeCategories.type(0x2FFFF)).isEqualTo(Character.UNASSIGNED);
        assertThat(UnicodeCategories.type(0x10FFFD)).isEqualTo(Character.PRIVATE_USE);
        assertThat(UnicodeCategories.type(Character.MAX_CODE_POINT)).isEqualTo(Character.UNASSIGNED);
        assertThat(UnicodeCategories.type(0xD800)).isEqualTo(Character.SURROGATE);
        assertThat(UnicodeCategories.type(0xE000)).isEqualTo(Character.PRIVATE_USE);
        assertThat(UnicodeCategories.type('A')).isEqualTo(Character.UPPERCASE_LETTER);
        assertThat(UnicodeCategories.type(0xFF)).isEqualTo(Character.LOWERCASE_LETTER);
        assertThat(UnicodeCategories.type(0x100)).isEqualTo(Character.UPPERCASE_LETTER);
    }

    private static void assertMatches(String regexp, int cp) {
        assertThat(IRegexp.compile(regexp).orElseThrow().matches(Character.toString(cp)))
                .as("%s on U+%04X", regexp, cp)
                .isTrue();
    }
}
