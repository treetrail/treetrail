# Jayway JsonPath 3.0.0 vs. RFC 9535

JSONPath Compliance Test Suite, Jayway default configuration; Jayway parses each document
itself (json-smart). Valid queries are judged
against the suite's expected results.

RFC 9535 allows filters without parentheses (`[?@.a]`); Jayway requires `[?(@.a)]`.
Part 2 therefore repeats the run with parentheses added around every filter, so that it
measures behaviour rather than this one difference in notation.

# Part 1: queries as written

## Valid RFC 9535 queries: 459

| Outcome | Cases |
| --- | --- |
| SAME | 86 |
| DIFFERENT_VALUES | 21 |
| ONLY_RFC_ACCEPTS | 352 |

## Invalid RFC 9535 queries: 247

Jayway accepts 132 of them.

## DIFFERENT_VALUES

| Test | Selector | Expected (RFC 9535) | Jayway |
| --- | --- | --- | --- |
| basic, descendant segment, multiple selectors | `$..['a','d']` | [b, e, c, f] | [{a=b, d=e}, {a=c, d=f}] |
| basic, descendant segment, object traversal, multiple selectors | `$..['a','d']` | [b, e, c, f] | [{a=b, d=e}, {a=c, d=f}] |
| slice selector, slice selector with step | `$[1:6:2]` | [1, 3, 5] | [1, 2, 3, 4, 5] |
| slice selector, negative step with default start | `$[:0:-1]` | [3, 2, 1] | [] |
| slice selector, negative step with default end | `$[2::-1]` | [2, 1, 0] | [2, 3] |
| slice selector, negative range with negative step | `$[-1:-3:-1]` | [9, 8] | [] |
| slice selector, negative range with larger negative step | `$[-1:-6:-2]` | [9, 7, 5] | [] |
| slice selector, larger negative range with larger negative step | `$[-1:-7:-2]` | [9, 7, 5] | [] |
| slice selector, negative from, positive to | `$[-5:7]` | [5, 6] | [5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6] |
| slice selector, positive from, negative to | `$[1:-1]` | [1, 2, 3, 4, 5, 6, 7, 8] | [] |
| slice selector, negative from, positive to, negative step | `$[-1:1:-1]` | [9, 8, 7, 6, 5, 4, 3, 2] | [9, 0] |
| slice selector, positive from, negative to, negative step | `$[7:-5:-1]` | [7, 6] | [] |
| slice selector, zero step | `$[1:2:0]` | [] | [1] |
| slice selector, maximal range with negative step | `$[9:0:-1]` | [9, 8, 7, 6, 5, 4, 3, 2, 1] | [] |
| slice selector, excessively large step | `$[1:10:113667776004]` | [1] | [1, 2, 3, 4, 5, 6, 7, 8, 9] |
| slice selector, excessively small step | `$[-1:-10:-113667776004]` | [9] | [] |
| whitespace, selectors, space between selector and comma | `$['a' ,'b']` | [ab, bc] | [{a=ab, b=bc}] |
| whitespace, selectors, space between comma and selector | `$['a', 'b']` | [ab, bc] | [{a=ab, b=bc}] |
| whitespace, selectors, newline between comma and selector | `$['a',\n'b']` | [ab, bc] | [{a=ab, b=bc}] |
| whitespace, selectors, tab between comma and selector | `$['a',\t'b']` | [ab, bc] | [{a=ab, b=bc}] |
| whitespace, selectors, return between comma and selector | `$['a',\r'b']` | [ab, bc] | [{a=ab, b=bc}] |

## ONLY_RFC_ACCEPTS

| Test | Selector | Expected (RFC 9535) | Jayway |
| --- | --- | --- | --- |
| basic, multiple selectors, name and index, array data | `$['a',1]` | [1] | Jayway: Found empty property at index 7 |
| basic, multiple selectors, name and index, object data | `$['a',1]` | [1] | Jayway: Found empty property at index 7 |
| basic, multiple selectors, index and slice | `$[1,5:7]` | [1, 5, 6] | Jayway: Failed to parse SliceOperation: 1,5:7 |
| basic, multiple selectors, index and slice, overlapping | `$[1,0:3]` | [1, 0, 1, 2] | Jayway: Failed to parse SliceOperation: 1,0:3 |
| basic, multiple selectors, wildcard and index | `$[*,1]` | [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1] | Jayway: Expected wildcard token to end with ']' on position 3 |
| basic, multiple selectors, wildcard and name | `$[*,'a']` | [A, B, A] | Jayway: Expected wildcard token to end with ']' on position 3 |
| basic, multiple selectors, wildcard and slice | `$[*,0:2]` | [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1] | Jayway: Expected wildcard token to end with ']' on position 3 |
| basic, multiple selectors, multiple wildcards | `$[*,*]` | [0, 1, 2, 0, 1, 2] | Jayway: Expected wildcard token to end with ']' on position 3 |
| filter, existence, without segments | `$[?@]` | [1, null] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, existence | `$[?@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, existence, present with null | `$[?@.a]` | [{a=null, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, absolute existence, without segments | `$[?$]` | [1, null] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, absolute existence, with segments | `$[?$.*.a]` | [{a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals string, single quotes | `$[?@.a=='b']` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals numeric string, single quotes | `$[?@.a=='1']` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals string, double quotes | `$[?@.a=="b"]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals numeric string, double quotes | `$[?@.a=="1"]` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number | `$[?@.a==1]` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals null | `$[?@.a==null]` | [{a=null, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals null, absent from data | `$[?@.a==null]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals true | `$[?@.a==true]` | [{a=true, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals false | `$[?@.a==false]` | [{a=false, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals self | `$[?@==@]` | [1, null, true, {a=b}, [false]] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, absolute, equals self | `$[?$==$]` | [1, null, true, {a=b}, [false]] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals, absent from index selector equals absent from name selector | `$[?@.absent==@.list[9]]` | [{list=[1]}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, deep equality, arrays | `$[?@.a==@.b]` | [{a=[[1, [2]]], b=[[1, [2]]]}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, deep equality, objects | `$[?@.a==@.b]` | [{a={x=1, y={z=1}}, b={x=1, y={z=1}}}, {a={x=1, y={z=1}}, b={y={z=1}, x=1}}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals string, single quotes | `$[?@.a!='b']` | [{a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals numeric string, single quotes | `$[?@.a!='1']` | [{a=1, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals string, single quotes, different type | `$[?@.a!='b']` | [{a=1, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals string, double quotes | `$[?@.a!="b"]` | [{a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals numeric string, double quotes | `$[?@.a!="1"]` | [{a=1, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals string, double quotes, different types | `$[?@.a!="b"]` | [{a=1, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals number | `$[?@.a!=1]` | [{a=2, d=f}, {a=1, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals number, different types | `$[?@.a!=1]` | [{a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals null | `$[?@.a!=null]` | [{a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals null, absent from data | `$[?@.a!=null]` | [{d=e}, {a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals true | `$[?@.a!=true]` | [{a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not-equals false | `$[?@.a!=false]` | [{a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than string, single quotes | `$[?@.a<'c']` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than string, double quotes | `$[?@.a<"c"]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than number | `$[?@.a<10]` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than null | `$[?@.a<null]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than true | `$[?@.a<true]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than false | `$[?@.a<false]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than or equal to string, single quotes | `$[?@.a<='c']` | [{a=b, d=e}, {a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than or equal to string, double quotes | `$[?@.a<="c"]` | [{a=b, d=e}, {a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than or equal to number | `$[?@.a<=10]` | [{a=1, d=e}, {a=10, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than or equal to null | `$[?@.a<=null]` | [{a=null, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than or equal to true | `$[?@.a<=true]` | [{a=true, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, less than or equal to false | `$[?@.a<=false]` | [{a=false, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than string, single quotes | `$[?@.a>'c']` | [{a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than string, double quotes | `$[?@.a>"c"]` | [{a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than number | `$[?@.a>10]` | [{a=20, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than null | `$[?@.a>null]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than true | `$[?@.a>true]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than false | `$[?@.a>false]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than or equal to string, single quotes | `$[?@.a>='c']` | [{a=c, d=f}, {a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than or equal to string, double quotes | `$[?@.a>="c"]` | [{a=c, d=f}, {a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than or equal to number | `$[?@.a>=10]` | [{a=10, d=e}, {a=20, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than or equal to null | `$[?@.a>=null]` | [{a=null, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than or equal to true | `$[?@.a>=true]` | [{a=true, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, greater than or equal to false | `$[?@.a>=false]` | [{a=false, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, exists and not-equals null, absent from data | `$[?@.a&&@.a!=null]` | [{a=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, exists and exists, data false | `$[?@.a&&@.b]` | [{a=false, b=false}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, exists or exists, data false | `$[?@.a\|\|@.b]` | [{a=false, b=false}, {b=false}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, and | `$[?@.a>0&&@.a<10]` | [{a=5, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, or | `$[?@.a=='b'\|\|@.a=='d']` | [{a=b, d=f}, {a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not expression | `$[?!(@.a=='b')]` | [{a=a, d=e}, {a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not exists | `$[?!@.a]` | [{d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, not exists, data null | `$[?!@.a]` | [{d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, non-singular existence, wildcard | `$[?@.*]` | [[2], {a=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, non-singular existence, multiple | `$[?@[0, 0, 'a']]` | [[2], [2, 3], {a=3}, {a=3, b=4}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, non-singular existence, slice | `$[?@[0:2]]` | [[2], [2, 3, 4]] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, non-singular existence, negated | `$[?!@.*]` | [1, [], {}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, nested | `$[?@[?@>1]]` | [[0, 1, 2], [42]] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, name segment on primitive, selects nothing | `$[?@.a == 1]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, name segment on array, selects nothing | `$[?@['0'] == 5]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, index segment on object, selects nothing | `$[?@[0] == 5]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, followed by name selector | `$[?@.a==1].b.x` | [2] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, followed by child segment that selects multiple elements | `$[?@.z=='_']['x','y']` | [1, null] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors | `$[?@.a,?@.b]` | [{a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, comparison | `$[?@.a=='b',?@.b=='x']` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, overlapping | `$[?@.a,?@.d]` | [{a=b, d=e}, {a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, filter and index | `$[?@.a,1]` | [{a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, filter and wildcard | `$[?@.a,*]` | [{a=b, d=e}, {a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, filter and slice | `$[?@.a,1:]` | [{a=b, d=e}, {b=c, d=f}, {g=h}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, comparison filter, index and slice | `$[1, ?@.a=='b', 1:]` | [{b=c, d=f}, {a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, zero and negative zero | `$[?@.a==0]` | [{a=0, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, negative zero and zero | `$[?@.a==-0]` | [{a=0, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, with and without decimal fraction | `$[?@.a==1.0]` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, exponent | `$[?@.a==1e2]` | [{a=100, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, exponent upper e | `$[?@.a==1E2]` | [{a=100, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, positive exponent | `$[?@.a==1e+2]` | [{a=100, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, negative exponent | `$[?@.a==1e-2]` | [{a=0.01, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, exponent 0 | `$[?@.a==1e0]` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, exponent -0 | `$[?@.a==1e-0]` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, exponent +0 | `$[?@.a==1e+0]` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, exponent leading -0 | `$[?@.a==1e-02]` | [{a=0.01, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, exponent +00 | `$[?@.a==1e+00]` | [{a=1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, decimal fraction | `$[?@.a==1.1]` | [{a=1.1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, decimal fraction, trailing 0 | `$[?@.a==1.10]` | [{a=1.1, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, decimal fraction, exponent | `$[?@.a==1.1e2]` | [{a=110, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, decimal fraction, positive exponent | `$[?@.a==1.1e+2]` | [{a=110, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, decimal fraction, negative exponent | `$[?@.a==1.1e-2]` | [{a=0.011, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals, special nothing | `$.values[?length(@.a) == value($..c)]` | [{c=d}, {a=null}] | Jayway: Could not parse token starting at position 8. Expected ?, ', 0-9, *  |
| filter, equals, empty node list and empty node list | `$[?@.a == @.b]` | [{c=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals, empty node list and special nothing | `$[?@.a == length(@.b)]` | [{b=2}, {c=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, object data | `$[?@<3]` | [1, 2] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, two consecutive ands | `$[?@.a && @.b && @.c]` | [{a=1, b=2, c=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, two consecutive ors | `$[?@.a \|\| @.b \|\| @.c]` | [{a=1, b=2}, {a=1, c=3}, {b=2, c=3}, {a=1, b=2, c=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple consecutive ands | `$[?@.a && @.b && @.c && @.d && @.e]` | [{a=1, b=2, c=3, d=4, e=5}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple consecutive ors | `$[?@.a \|\| @.b \|\| @.c \|\| @.d \|\| @.e]` | [{a=1, b=2, c=3, d=4}, {b=2, c=3, d=4, e=5}, {a=1, c=3, e=5}, {a=1, b=2, c=3, d=4, e=5}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple consecutive ors and ands | `$[?@.a && @.b && @.c \|\| @.d \|\| @.e]` | [{e=5}, {d=4, e=5}, {a=1, b=2, c=3}, {c=3, d=4, e=5}, {a=1, c=3, e=5}, {a=1, b=2, c=3, d=4, e=5}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, and binds more tightly than or | `$[?@.a \|\| @.b && @.c]` | [{a=1}, {b=2, c=3}, {a=1, b=2, c=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, left to right evaluation | `$[?@.a && @.b \|\| @.c]` | [{a=1, b=2}, {a=1, c=3}, {b=1, c=3}, {c=3}, {a=1, b=2, c=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, group terms, left | `$[?(@.a \|\| @.b) && @.c]` | [{a=1, c=3}, {b=2, c=3}, {a=1, b=2, c=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, group terms, right | `$[?@.a && (@.b \|\| @.c)]` | [{a=1, b=2}, {a=1, c=2}, {a=1, b=2, c=3}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, string literal, single quote in double quotes | `$[?@ == "quoted' literal"]` | [quoted' literal] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, string literal, double quote in single quotes | `$[?@ == 'quoted" literal']` | [quoted" literal] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, string literal, escaped single quote in single quotes | `$[?@ == 'quoted\' literal']` | [quoted' literal] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, string literal, escaped double quote in double quotes | `$[?@ == "quoted\" literal"]` | [quoted" literal] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, quoted True, double quotes | `$[?@.a=="True"]` | [{a=True}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, quoted True, single quotes | `$[?@.a=='True']` | [{a=True}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, quoted False, double quotes | `$[?@.a=="False"]` | [{a=False}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, quoted False, single quotes | `$[?@.a=='False']` | [{a=False}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, quoted Null, double quotes | `$[?@.a=="Null"]` | [{a=Null}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, quoted Null, single quotes | `$[?@.a=='Null']` | [{a=Null}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| index selector, min exact index | `$[-9007199254740991]` | [] | Jayway: Failed to parse token in ArrayIndexOperation: -9007199254740991 |
| index selector, max exact index | `$[9007199254740991]` | [] | Jayway: Failed to parse token in ArrayIndexOperation: 9007199254740991 |
| slice selector, slice selector with everything omitted, short form | `$[:]` | [0, 1, 2, 3] | Jayway: Failed to parse SliceOperation: : |
| slice selector, slice selector with everything omitted, long form | `$[::]` | [0, 1, 2, 3] | Jayway: Failed to parse SliceOperation: :: |
| slice selector, slice selector with start and end omitted | `$[::2]` | [0, 2, 4, 6, 8] | Jayway: Failed to parse SliceOperation: ::2 |
| slice selector, negative step with default start and end | `$[::-1]` | [3, 2, 1, 0] | Jayway: Failed to parse SliceOperation: ::-1 |
| slice selector, larger negative step | `$[::-2]` | [3, 1] | Jayway: Failed to parse SliceOperation: ::-2 |
| slice selector, in serial, on flat array | `$[1:3][::]` | [] | Jayway: Failed to parse SliceOperation: :: |
| slice selector, slice selector with everything omitted with empty array | `$[:]` | [] | Jayway: Failed to parse SliceOperation: : |
| slice selector, negative step with empty array | `$[::-1]` | [] | Jayway: Failed to parse SliceOperation: ::-1 |
| slice selector, excessively large to value | `$[2:113667776004]` | [2, 3, 4, 5, 6, 7, 8, 9] | Jayway: java.lang.NumberFormatException: For input string: "113667776004" |
| slice selector, excessively small from value | `$[-113667776004:1]` | [0] | Jayway: java.lang.NumberFormatException: For input string: "-113667776004" |
| slice selector, excessively large from value with negative step | `$[113667776004:0:-1]` | [9, 8, 7, 6, 5, 4, 3, 2, 1] | Jayway: java.lang.NumberFormatException: For input string: "113667776004" |
| slice selector, excessively small to value with negative step | `$[3:-113667776004:-1]` | [3, 2, 1, 0] | Jayway: java.lang.NumberFormatException: For input string: "-113667776004" |
| slice selector, start, min exact | `$[-9007199254740991::]` | [] | Jayway: java.lang.NumberFormatException: For input string: "-9007199254740991" |
| slice selector, start, max exact | `$[9007199254740991::]` | [] | Jayway: java.lang.NumberFormatException: For input string: "9007199254740991" |
| slice selector, end, min exact | `$[:-9007199254740991:]` | [] | Jayway: java.lang.NumberFormatException: For input string: "-9007199254740991" |
| slice selector, end, max exact | `$[:9007199254740991:]` | [] | Jayway: java.lang.NumberFormatException: For input string: "9007199254740991" |
| slice selector, step, min exact | `$[::-9007199254740991]` | [] | Jayway: Failed to parse SliceOperation: ::-9007199254740991 |
| slice selector, step, max exact | `$[::9007199254740991]` | [] | Jayway: Failed to parse SliceOperation: ::9007199254740991 |
| functions, count, count function | `$[?count(@..*)>2]` | [{a=[1, 2, 3]}, {a=[1], d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, count, single-node arg | `$[?count(@.a)>1]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, count, multiple-selector arg | `$[?count(@['a','d'])>1]` | [{a=[1], d=f}, {a=1, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, string data | `$[?length(@.a)>=2]` | [{a=ab}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, string data, unicode | `$[?length(@)==2]` | [☺☺, жж, 阿美] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, array data | `$[?length(@.a)>=2]` | [{a=[1, 2, 3]}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, missing data | `$[?length(@.a)>=2]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, number arg | `$[?length(1)>=2]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, true arg | `$[?length(true)>=2]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, false arg | `$[?length(false)>=2]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, null arg | `$[?length(null)>=2]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, length, arg is a function expression | `$.values[?length(@.a)==length(value($..c))]` | [{a=ab}] | Jayway: Could not parse token starting at position 8. Expected ?, ', 0-9, *  |
| functions, length, arg is special nothing | `$[?length(value(@.a))>0]` | [{a=ab}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, found match | `$[?match(@.a, 'a.*')]` | [{a=ab}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, double quotes | `$[?match(@.a, "a.*")]` | [{a=ab}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, regex from the document | `$.values[?match(@, $.regex)]` | [bab] | Jayway: Could not parse token starting at position 8. Expected ?, ', 0-9, *  |
| functions, match, don't select match | `$[?!match(@.a, 'a.*')]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, not a match | `$[?match(@.a, 'a.*')]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, select non-match | `$[?!match(@.a, 'a.*')]` | [{a=bc}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, non-string first arg | `$[?match(1, 'a.*')]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, non-string second arg | `$[?match(@.a, 1)]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, filter, match function, unicode char class, uppercase | `$[?match(@, '\\p{Lu}')]` | [Ж] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, filter, match function, unicode char class negated, uppercase | `$[?match(@, '\\P{Lu}')]` | [ж, 1] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, filter, match function, unicode, surrogate pair | `$[?match(@, 'a.b')]` | [a𐄁b] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, dot matcher on \u2028 | `$[?match(@, '.')]` | [ ] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, dot matcher on \u2029 | `$[?match(@, '.')]` | [ ] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, arg is a function expression | `$.values[?match(@.a, value($..['regex']))]` | [{a=ab}] | Jayway: Could not parse token starting at position 8. Expected ?, ', 0-9, *  |
| functions, match, dot in character class | `$[?match(@, 'a[.b]c')]` | [abc, a.c] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, escaped dot | `$[?match(@, 'a\\.c')]` | [a.c] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, escaped backslash before dot | `$[?match(@, 'a\\\\.c')]` | [a\ c] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, escaped left square bracket | `$[?match(@, 'a\\[.c')]` | [a[ c] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, escaped right square bracket | `$[?match(@, 'a[\\].]c')]` | [a.c, a]c] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, explicit caret | `$[?match(@, '^ab.*')]` | [abc, ab] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, match, explicit dollar | `$[?match(@, '.*bc$')]` | [abc] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, at the end | `$[?search(@.a, 'a.*')]` | [{a=the end is ab}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, double quotes | `$[?search(@.a, "a.*")]` | [{a=the end is ab}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, at the start | `$[?search(@.a, 'a.*')]` | [{a=ab is at the start}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, in the middle | `$[?search(@.a, 'a.*')]` | [{a=contains two matches}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, regex from the document | `$.values[?search(@, $.regex)]` | [bab, bba, bbab] | Jayway: Could not parse token starting at position 8. Expected ?, ', 0-9, *  |
| functions, search, don't select match | `$[?!search(@.a, 'a.*')]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, not a match | `$[?search(@.a, 'a.*')]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, select non-match | `$[?!search(@.a, 'a.*')]` | [{a=bc}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, non-string first arg | `$[?search(1, 'a.*')]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, non-string second arg | `$[?search(@.a, 1)]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, filter, search function, unicode char class, uppercase | `$[?search(@, '\\p{Lu}')]` | [Ж, жЖ] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, filter, search function, unicode char class negated, uppercase | `$[?search(@, '\\P{Lu}')]` | [ж, 1] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, filter, search function, unicode, surrogate pair | `$[?search(@, 'a.b')]` | [a𐄁bc] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, dot matcher on \u2028 | `$[?search(@, '.')]` | [ , \r \n] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, dot matcher on \u2029 | `$[?search(@, '.')]` | [ , \r \n] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, arg is a function expression | `$.values[?search(@, value($..['regex']))]` | [bab, bba, bbab] | Jayway: Could not parse token starting at position 8. Expected ?, ', 0-9, *  |
| functions, search, dot in character class | `$[?search(@, 'a[.b]c')]` | [x abc y, x a.c y] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, escaped dot | `$[?search(@, 'a\\.c')]` | [x a.c y] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, escaped backslash before dot | `$[?search(@, 'a\\\\.c')]` | [x a\ c y] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, escaped left square bracket | `$[?search(@, 'a\\[.c')]` | [x a[ c y] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, search, escaped right square bracket | `$[?search(@, 'a[\\].]c')]` | [x a.c y, x a]c y] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, value, single-value nodelist | `$[?value(@.*)==4]` | [[4], {foo=4}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| functions, value, multi-value nodelist | `$[?value(@.*)==4]` | [] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, space between question mark and expression | `$[? @.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, newline between question mark and expression | `$[?\n@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, tab between question mark and expression | `$[?\t@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, return between question mark and expression | `$[?\r@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, newline between question mark and parenthesized expression | `$[?\n(@.a)]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, tab between question mark and parenthesized expression | `$[?\t(@.a)]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, return between question mark and parenthesized expression | `$[?\r(@.a)]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, newline between parenthesized expression and bracket | `$[?(@.a)\n]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, tab between parenthesized expression and bracket | `$[?(@.a)\t]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, return between parenthesized expression and bracket | `$[?(@.a)\r]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, space between bracket and question mark | `$[ ?@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, newline between bracket and question mark | `$[\n?@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, tab between bracket and question mark | `$[\t?@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, return between bracket and question mark | `$[\r?@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, space between parenthesis and arg | `$[?count( @.*)==1]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, newline between parenthesis and arg | `$[?count(\n@.*)==1]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, tab between parenthesis and arg | `$[?count(\t@.*)==1]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, return between parenthesis and arg | `$[?count(\r@.*)==1]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, space between arg and comma | `$[?search(@ ,'[a-z]+')]` | [foo] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, newline between arg and comma | `$[?search(@\n,'[a-z]+')]` | [foo] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, tab between arg and comma | `$[?search(@\t,'[a-z]+')]` | [foo] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, return between arg and comma | `$[?search(@\r,'[a-z]+')]` | [foo] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, space between comma and arg | `$[?search(@, '[a-z]+')]` | [foo] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, newline between comma and arg | `$[?search(@,\n'[a-z]+')]` | [foo] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, tab between comma and arg | `$[?search(@,\t'[a-z]+')]` | [foo] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, return between comma and arg | `$[?search(@,\r'[a-z]+')]` | [foo] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, space between arg and parenthesis | `$[?count(@.* )==1]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, newline between arg and parenthesis | `$[?count(@.*\n)==1]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, tab between arg and parenthesis | `$[?count(@.*\t)==1]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, return between arg and parenthesis | `$[?count(@.*\r)==1]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, spaces in a relative singular selector | `$[?length(@ .a .b) == 3]` | [{a={b=foo}}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, newlines in a relative singular selector | `$[?length(@\n.a\n.b) == 3]` | [{a={b=foo}}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, tabs in a relative singular selector | `$[?length(@\t.a\t.b) == 3]` | [{a={b=foo}}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, returns in a relative singular selector | `$[?length(@\r.a\r.b) == 3]` | [{a={b=foo}}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, spaces in an absolute singular selector | `$..[?length(@)==length($ [0] .a)]` | [foo] | Jayway: Could not parse token starting at position 3. Expected ?, ', 0-9, *  |
| whitespace, functions, newlines in an absolute singular selector | `$..[?length(@)==length($\n[0]\n.a)]` | [foo] | Jayway: Could not parse token starting at position 3. Expected ?, ', 0-9, *  |
| whitespace, functions, tabs in an absolute singular selector | `$..[?length(@)==length($\t[0]\t.a)]` | [foo] | Jayway: Could not parse token starting at position 3. Expected ?, ', 0-9, *  |
| whitespace, functions, returns in an absolute singular selector | `$..[?length(@)==length($\r[0]\r.a)]` | [foo] | Jayway: Could not parse token starting at position 3. Expected ?, ', 0-9, *  |
| whitespace, operators, space before \|\| | `$[?@.a \|\|@.b]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline before \|\| | `$[?@.a\n\|\|@.b]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab before \|\| | `$[?@.a\t\|\|@.b]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return before \|\| | `$[?@.a\r\|\|@.b]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space after \|\| | `$[?@.a\|\| @.b]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline after \|\| | `$[?@.a\|\|\n@.b]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab after \|\| | `$[?@.a\|\|\t@.b]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return after \|\| | `$[?@.a\|\|\r@.b]` | [{a=1}, {b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space before && | `$[?@.a &&@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline before && | `$[?@.a\n&&@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab before && | `$[?@.a\t&&@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return before && | `$[?@.a\r&&@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space after && | `$[?@.a&& @.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline after && | `$[?@.a&& @.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab after && | `$[?@.a&& @.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return after && | `$[?@.a&& @.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space before == | `$[?@.a ==@.b]` | [{a=1, b=1}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline before == | `$[?@.a\n==@.b]` | [{a=1, b=1}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab before == | `$[?@.a\t==@.b]` | [{a=1, b=1}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return before == | `$[?@.a\r==@.b]` | [{a=1, b=1}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space after == | `$[?@.a== @.b]` | [{a=1, b=1}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline after == | `$[?@.a==\n@.b]` | [{a=1, b=1}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab after == | `$[?@.a==\t@.b]` | [{a=1, b=1}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return after == | `$[?@.a==\r@.b]` | [{a=1, b=1}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space before != | `$[?@.a !=@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline before != | `$[?@.a\n!=@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab before != | `$[?@.a\t!=@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return before != | `$[?@.a\r!=@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space after != | `$[?@.a!= @.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline after != | `$[?@.a!=\n@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab after != | `$[?@.a!=\t@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return after != | `$[?@.a!=\r@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space before < | `$[?@.a <@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline before < | `$[?@.a\n<@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab before < | `$[?@.a\t<@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return before < | `$[?@.a\r<@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space after < | `$[?@.a< @.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline after < | `$[?@.a<\n@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab after < | `$[?@.a<\t@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return after < | `$[?@.a<\r@.b]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space before > | `$[?@.b >@.a]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline before > | `$[?@.b\n>@.a]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab before > | `$[?@.b\t>@.a]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return before > | `$[?@.b\r>@.a]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space after > | `$[?@.b> @.a]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline after > | `$[?@.b>\n@.a]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab after > | `$[?@.b>\t@.a]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return after > | `$[?@.b>\r@.a]` | [{a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space before <= | `$[?@.a <=@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline before <= | `$[?@.a\n<=@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab before <= | `$[?@.a\t<=@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return before <= | `$[?@.a\r<=@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space after <= | `$[?@.a<= @.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline after <= | `$[?@.a<=\n@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab after <= | `$[?@.a<=\t@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return after <= | `$[?@.a<=\r@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space before >= | `$[?@.b >=@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline before >= | `$[?@.b\n>=@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab before >= | `$[?@.b\t>=@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return before >= | `$[?@.b\r>=@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space after >= | `$[?@.b>= @.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline after >= | `$[?@.b>=\n@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab after >= | `$[?@.b>=\t@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return after >= | `$[?@.b>=\r@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space between logical not and test expression | `$[?! @.a]` | [{d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline between logical not and test expression | `$[?!\n@.a]` | [{d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab between logical not and test expression | `$[?!\t@.a]` | [{d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return between logical not and test expression | `$[?!\r@.a]` | [{d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, space between logical not and parenthesized expression | `$[?! (@.a=='b')]` | [{a=a, d=e}, {a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, newline between logical not and parenthesized expression | `$[?!\n(@.a=='b')]` | [{a=a, d=e}, {a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, tab between logical not and parenthesized expression | `$[?!\t(@.a=='b')]` | [{a=a, d=e}, {a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, operators, return between logical not and parenthesized expression | `$[?!\r(@.a=='b')]` | [{a=a, d=e}, {a=d, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, selectors, space between root and bracket | `$ ['a']` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, newline between root and bracket | `$\n['a']` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, tab between root and bracket | `$\t['a']` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, return between root and bracket | `$\r['a']` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, space between bracket and bracket | `$['a'] ['b']` | [ab] | Jayway: Could not parse token starting at position 6 |
| whitespace, selectors, newline between bracket and bracket | `$['a'] \n['b']` | [ab] | Jayway: Could not parse token starting at position 6 |
| whitespace, selectors, tab between bracket and bracket | `$['a'] \t['b']` | [ab] | Jayway: Could not parse token starting at position 6 |
| whitespace, selectors, return between bracket and bracket | `$['a'] \r['b']` | [ab] | Jayway: Could not parse token starting at position 6 |
| whitespace, selectors, space between root and dot | `$ .a` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, newline between root and dot | `$\n.a` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, tab between root and dot | `$\t.a` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, return between root and dot | `$\r.a` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, newline between bracket and selector | `$[\n'a']` | [ab] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, selectors, tab between bracket and selector | `$[\t'a']` | [ab] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, selectors, return between bracket and selector | `$[\r'a']` | [ab] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, selectors, newline between selector and bracket | `$['a'\n]` | [ab] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, tab between selector and bracket | `$['a'\t]` | [ab] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, return between selector and bracket | `$['a'\r]` | [ab] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, newline between selector and comma | `$['a'\n,'b']` | [ab, bc] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, tab between selector and comma | `$['a'\t,'b']` | [ab, bc] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, return between selector and comma | `$['a'\r,'b']` | [ab, bc] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, slice, space between start and colon | `$[1 :5:2]` | [2, 4] | Jayway: Failed to parse SliceOperation: 1 :5:2 |
| whitespace, slice, newline between start and colon | `$[1\n:5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, tab between start and colon | `$[1\t:5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, return between start and colon | `$[1\r:5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, space between colon and end | `$[1: 5:2]` | [2, 4] | Jayway: Failed to parse SliceOperation: 1: 5:2 |
| whitespace, slice, newline between colon and end | `$[1:\n5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, tab between colon and end | `$[1:\t5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, return between colon and end | `$[1:\r5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, space between end and colon | `$[1:5 :2]` | [2, 4] | Jayway: Failed to parse SliceOperation: 1:5 :2 |
| whitespace, slice, newline between end and colon | `$[1:5\n:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, tab between end and colon | `$[1:5\t:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, return between end and colon | `$[1:5\r:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, space between colon and step | `$[1:5: 2]` | [2, 4] | Jayway: Failed to parse SliceOperation: 1:5: 2 |
| whitespace, slice, newline between colon and step | `$[1:5:\n2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, tab between colon and step | `$[1:5:\t2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, return between colon and step | `$[1:5:\r2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |

## Invalid queries Jayway accepts

| Test | Selector |
| --- | --- |
| basic, no trailing whitespace | `$ ` |
| basic, name shorthand, symbol | `$.&` |
| basic, name shorthand, number | `$.1` |
| filter, relative non-singular query, index, equal | `$[?(@[0, 0]==42)]` |
| filter, relative non-singular query, index, not equal | `$[?(@[0, 0]!=42)]` |
| filter, relative non-singular query, index, less-or-equal | `$[?(@[0, 0]<=42)]` |
| filter, relative non-singular query, name, equal | `$[?(@['a', 'a']==42)]` |
| filter, relative non-singular query, name, not equal | `$[?(@['a', 'a']!=42)]` |
| filter, relative non-singular query, name, less-or-equal | `$[?(@['a', 'a']<=42)]` |
| filter, relative non-singular query, wildcard, equal | `$[?(@.*==42)]` |
| filter, relative non-singular query, wildcard, not equal | `$[?(@.*!=42)]` |
| filter, relative non-singular query, wildcard, less-or-equal | `$[?(@.*<=42)]` |
| filter, relative non-singular query, slice, equal | `$[?(@[0:0]==42)]` |
| filter, relative non-singular query, slice, not equal | `$[?(@[0:0]!=42)]` |
| filter, relative non-singular query, slice, less-or-equal | `$[?(@[0:0]<=42)]` |
| filter, absolute non-singular query, index, equal | `$[?($[0, 0]==42)]` |
| filter, absolute non-singular query, index, not equal | `$[?($[0, 0]!=42)]` |
| filter, absolute non-singular query, index, less-or-equal | `$[?($[0, 0]<=42)]` |
| filter, absolute non-singular query, name, equal | `$[?($['a', 'a']==42)]` |
| filter, absolute non-singular query, name, not equal | `$[?($['a', 'a']!=42)]` |
| filter, absolute non-singular query, name, less-or-equal | `$[?($['a', 'a']<=42)]` |
| filter, absolute non-singular query, wildcard, equal | `$[?($.*==42)]` |
| filter, absolute non-singular query, wildcard, not equal | `$[?($.*!=42)]` |
| filter, absolute non-singular query, wildcard, less-or-equal | `$[?($.*<=42)]` |
| filter, absolute non-singular query, slice, equal | `$[?($[0:0]==42)]` |
| filter, absolute non-singular query, slice, not equal | `$[?($[0:0]!=42)]` |
| filter, absolute non-singular query, slice, less-or-equal | `$[?($[0:0]<=42)]` |
| index selector, leading 0 | `$[01]` |
| index selector, -0 | `$[-0]` |
| index selector, leading -0 | `$[-01]` |
| name selector, double quotes, embedded U+0000 | `$[" "]` |
| name selector, double quotes, embedded U+0001 | `$[""]` |
| name selector, double quotes, embedded U+0002 | `$[""]` |
| name selector, double quotes, embedded U+0003 | `$[""]` |
| name selector, double quotes, embedded U+0004 | `$[""]` |
| name selector, double quotes, embedded U+0005 | `$[""]` |
| name selector, double quotes, embedded U+0006 | `$[""]` |
| name selector, double quotes, embedded U+0007 | `$[""]` |
| name selector, double quotes, embedded U+0008 | `$[""]` |
| name selector, double quotes, embedded U+0009 | `$["\t"]` |
| name selector, double quotes, embedded U+000A | `$["\n"]` |
| name selector, double quotes, embedded U+000B | `$[""]` |
| name selector, double quotes, embedded U+000C | `$[""]` |
| name selector, double quotes, embedded U+000D | `$["\r"]` |
| name selector, double quotes, embedded U+000E | `$[""]` |
| name selector, double quotes, embedded U+000F | `$[""]` |
| name selector, double quotes, embedded U+0010 | `$[""]` |
| name selector, double quotes, embedded U+0011 | `$[""]` |
| name selector, double quotes, embedded U+0012 | `$[""]` |
| name selector, double quotes, embedded U+0013 | `$[""]` |
| name selector, double quotes, embedded U+0014 | `$[""]` |
| name selector, double quotes, embedded U+0015 | `$[""]` |
| name selector, double quotes, embedded U+0016 | `$[""]` |
| name selector, double quotes, embedded U+0017 | `$[""]` |
| name selector, double quotes, embedded U+0018 | `$[""]` |
| name selector, double quotes, embedded U+0019 | `$[""]` |
| name selector, double quotes, embedded U+001A | `$[""]` |
| name selector, double quotes, embedded U+001B | `$[""]` |
| name selector, double quotes, embedded U+001C | `$[""]` |
| name selector, double quotes, embedded U+001D | `$[""]` |
| name selector, double quotes, embedded U+001E | `$[""]` |
| name selector, double quotes, embedded U+001F | `$[""]` |
| name selector, double quotes, invalid escaped single quote | `$["\'"]` |
| name selector, double quotes, escape at end of line | `$["\\n"]` |
| name selector, double quotes, question mark escape | `$["\?"]` |
| name selector, double quotes, bell escape | `$["\a"]` |
| name selector, double quotes, vertical tab escape | `$["\v"]` |
| name selector, double quotes, 0 escape | `$["\0"]` |
| name selector, double quotes, x escape | `$["\x12"]` |
| name selector, double quotes, n escape | `$["\N{LATIN CAPITAL LETTER A}"]` |
| name selector, double quotes, unicode escape no hex | `$["\u"]` |
| name selector, double quotes, unicode escape too few hex | `$["\u123"]` |
| name selector, double quotes, unicode escape upper u | `$["\U1234"]` |
| name selector, double quotes, unicode escape upper u long | `$["\U0010FFFF"]` |
| name selector, double quotes, unicode escape plus | `$["\u+1234"]` |
| name selector, double quotes, single high surrogate | `$["\uD800"]` |
| name selector, double quotes, single low surrogate | `$["\uDC00"]` |
| name selector, double quotes, high high surrogate | `$["\uD800\uD800"]` |
| name selector, double quotes, low low surrogate | `$["\uDC00\uDC00"]` |
| name selector, double quotes, surrogate non-surrogate | `$["\uD800\u1234"]` |
| name selector, double quotes, non-surrogate surrogate | `$["\u1234\uDC00"]` |
| name selector, double quotes, surrogate supplementary | `$["\uD800𝄞"]` |
| name selector, double quotes, supplementary surrogate | `$["𝄞\uDC00"]` |
| name selector, double quotes, surrogate incomplete low | `$["\uD800\uDC0"]` |
| name selector, single quotes, embedded U+0000 | `$[' ']` |
| name selector, single quotes, embedded U+0001 | `$['']` |
| name selector, single quotes, embedded U+0002 | `$['']` |
| name selector, single quotes, embedded U+0003 | `$['']` |
| name selector, single quotes, embedded U+0004 | `$['']` |
| name selector, single quotes, embedded U+0005 | `$['']` |
| name selector, single quotes, embedded U+0006 | `$['']` |
| name selector, single quotes, embedded U+0007 | `$['']` |
| name selector, single quotes, embedded U+0008 | `$['']` |
| name selector, single quotes, embedded U+0009 | `$['\t']` |
| name selector, single quotes, embedded U+000A | `$['\n']` |
| name selector, single quotes, embedded U+000B | `$['']` |
| name selector, single quotes, embedded U+000C | `$['']` |
| name selector, single quotes, embedded U+000D | `$['\r']` |
| name selector, single quotes, embedded U+000E | `$['']` |
| name selector, single quotes, embedded U+000F | `$['']` |
| name selector, single quotes, embedded U+0010 | `$['']` |
| name selector, single quotes, embedded U+0011 | `$['']` |
| name selector, single quotes, embedded U+0012 | `$['']` |
| name selector, single quotes, embedded U+0013 | `$['']` |
| name selector, single quotes, embedded U+0014 | `$['']` |
| name selector, single quotes, embedded U+0015 | `$['']` |
| name selector, single quotes, embedded U+0016 | `$['']` |
| name selector, single quotes, embedded U+0017 | `$['']` |
| name selector, single quotes, embedded U+0018 | `$['']` |
| name selector, single quotes, embedded U+0019 | `$['']` |
| name selector, single quotes, embedded U+001A | `$['']` |
| name selector, single quotes, embedded U+001B | `$['']` |
| name selector, single quotes, embedded U+001C | `$['']` |
| name selector, single quotes, embedded U+001D | `$['']` |
| name selector, single quotes, embedded U+001E | `$['']` |
| name selector, single quotes, embedded U+001F | `$['']` |
| name selector, single quotes, invalid escaped double quote | `$['\"']` |
| slice selector, too many colons | `$[1:2:3:4]` |
| slice selector, overflowing step | `$[1:10:231584178474632390847141970017375815706539969331281128078915168015826259279872]` |
| slice selector, underflowing step | `$[-1:-10:-231584178474632390847141970017375815706539969331281128078915168015826259279872]` |
| slice selector, start, leading 0 | `$[01::]` |
| slice selector, start, -0 | `$[-0::]` |
| slice selector, start, leading -0 | `$[-01::]` |
| slice selector, end, leading 0 | `$[:01:]` |
| slice selector, end, -0 | `$[:-0:]` |
| slice selector, end, leading -0 | `$[:-01:]` |
| whitespace, selectors, newline between dot and name | `$.\na` |
| whitespace, selectors, tab between dot and name | `$.\ta` |
| whitespace, selectors, return between dot and name | `$.\ra` |
| whitespace, selectors, newline between recursive descent and name | `$..\na` |
| whitespace, selectors, tab between recursive descent and name | `$..\ta` |
| whitespace, selectors, return between recursive descent and name | `$..\ra` |

# Part 2: filters wrapped in parentheses for Jayway

## Valid RFC 9535 queries: 459

| Outcome | Cases |
| --- | --- |
| SAME | 176 |
| DIFFERENT_VALUES | 82 |
| ONLY_RFC_ACCEPTS | 201 |

## Invalid RFC 9535 queries: 247

Jayway accepts 142 of them.

## DIFFERENT_VALUES

| Test | Selector | Expected (RFC 9535) | Jayway |
| --- | --- | --- | --- |
| basic, descendant segment, multiple selectors | `$..['a','d']` | [b, e, c, f] | [{a=b, d=e}, {a=c, d=f}] |
| basic, descendant segment, object traversal, multiple selectors | `$..['a','d']` | [b, e, c, f] | [{a=b, d=e}, {a=c, d=f}] |
| filter, existence, without segments | `$[?@]` | [1, null] | [{a=1, b=null}] |
| filter, absolute existence, without segments | `$[?$]` | [1, null] | [{a=1, b=null}] |
| filter, equals numeric string, single quotes | `$[?@.a=='1']` | [{a=1, d=e}] | [{a=1, d=e}, {a=1, d=f}] |
| filter, equals numeric string, double quotes | `$[?@.a=="1"]` | [{a=1, d=e}] | [{a=1, d=e}, {a=1, d=f}] |
| filter, equals number | `$[?@.a==1]` | [{a=1, d=e}] | [{a=1, d=e}, {a=1, d=f}] |
| filter, equals, absent from index selector equals absent from name selector | `$[?@.absent==@.list[9]]` | [{list=[1]}] | [] |
| filter, not-equals numeric string, single quotes | `$[?@.a!='1']` | [{a=1, d=f}] | [] |
| filter, not-equals numeric string, double quotes | `$[?@.a!="1"]` | [{a=1, d=f}] | [] |
| filter, not-equals number | `$[?@.a!=1]` | [{a=2, d=f}, {a=1, d=f}] | [{a=2, d=f}] |
| filter, less than or equal to null | `$[?@.a<=null]` | [{a=null, d=e}] | [] |
| filter, less than or equal to true | `$[?@.a<=true]` | [{a=true, d=e}] | [] |
| filter, less than or equal to false | `$[?@.a<=false]` | [{a=false, d=e}] | [] |
| filter, greater than or equal to null | `$[?@.a>=null]` | [{a=null, d=e}] | [] |
| filter, greater than or equal to true | `$[?@.a>=true]` | [{a=true, d=e}] | [] |
| filter, greater than or equal to false | `$[?@.a>=false]` | [{a=false, d=e}] | [] |
| filter, exists and not-equals null, absent from data | `$[?@.a&&@.a!=null]` | [{a=c, d=f}] | [{d=e}, {a=c, d=f}] |
| filter, exists and exists, data false | `$[?@.a&&@.b]` | [{a=false, b=false}] | [] |
| filter, exists or exists, data false | `$[?@.a\|\|@.b]` | [{a=false, b=false}, {b=false}] | [] |
| filter, non-singular existence, wildcard | `$[?@.*]` | [[2], {a=3}] | [1, [], [2], {}, {a=3}] |
| filter, non-singular existence, slice | `$[?@[0:2]]` | [[2], [2, 3, 4]] | [[], [2], [2,3,4]] |
| filter, non-singular existence, negated | `$[?!@.*]` | [1, [], {}] | [] |
| filter, nested | `$[?@[?@>1]]` | [[0, 1, 2], [42]] | [[0], [0,1], [0,1,2], [42]] |
| filter, name segment on primitive, selects nothing | `$[?@.a == 1]` | [] | [{a=1}] |
| filter, followed by child segment that selects multiple elements | `$[?@.z=='_']['x','y']` | [1, null] | [{x=1, y=null}] |
| filter, equals number, zero and negative zero | `$[?@.a==0]` | [{a=0, d=e}] | [{a=0, d=e}, {a=0, d=g}] |
| filter, equals number, negative zero and zero | `$[?@.a==-0]` | [{a=0, d=e}] | [{a=0, d=e}, {a=0, d=g}] |
| filter, equals number, negative exponent | `$[?@.a==1e-2]` | [{a=0.01, d=e}] | [{a=0.01, d=e}, {a=0.01, d=g}] |
| filter, equals number, exponent 0 | `$[?@.a==1e0]` | [{a=1, d=e}] | [{a=1, d=e}, {a=1, d=g}] |
| filter, equals number, exponent -0 | `$[?@.a==1e-0]` | [{a=1, d=e}] | [{a=1, d=e}, {a=1, d=g}] |
| filter, equals number, exponent leading -0 | `$[?@.a==1e-02]` | [{a=0.01, d=e}] | [{a=0.01, d=e}, {a=0.01, d=g}] |
| filter, equals number, decimal fraction | `$[?@.a==1.1]` | [{a=1.1, d=e}] | [{a=1.1, d=e}, {a=1.1, d=g}] |
| filter, equals number, decimal fraction, negative exponent | `$[?@.a==1.1e-2]` | [{a=0.011, d=e}] | [{a=0.011, d=e}, {a=0.011, d=g}] |
| filter, equals, empty node list and empty node list | `$[?@.a == @.b]` | [{c=3}] | [] |
| filter, object data | `$[?@<3]` | [1, 2] | [] |
| slice selector, slice selector with step | `$[1:6:2]` | [1, 3, 5] | [1, 2, 3, 4, 5] |
| slice selector, negative step with default start | `$[:0:-1]` | [3, 2, 1] | [] |
| slice selector, negative step with default end | `$[2::-1]` | [2, 1, 0] | [2, 3] |
| slice selector, negative range with negative step | `$[-1:-3:-1]` | [9, 8] | [] |
| slice selector, negative range with larger negative step | `$[-1:-6:-2]` | [9, 7, 5] | [] |
| slice selector, larger negative range with larger negative step | `$[-1:-7:-2]` | [9, 7, 5] | [] |
| slice selector, negative from, positive to | `$[-5:7]` | [5, 6] | [5, 6, 7, 8, 9, 0, 1, 2, 3, 4, 5, 6] |
| slice selector, positive from, negative to | `$[1:-1]` | [1, 2, 3, 4, 5, 6, 7, 8] | [] |
| slice selector, negative from, positive to, negative step | `$[-1:1:-1]` | [9, 8, 7, 6, 5, 4, 3, 2] | [9, 0] |
| slice selector, positive from, negative to, negative step | `$[7:-5:-1]` | [7, 6] | [] |
| slice selector, zero step | `$[1:2:0]` | [] | [1] |
| slice selector, maximal range with negative step | `$[9:0:-1]` | [9, 8, 7, 6, 5, 4, 3, 2, 1] | [] |
| slice selector, excessively large step | `$[1:10:113667776004]` | [1] | [1, 2, 3, 4, 5, 6, 7, 8, 9] |
| slice selector, excessively small step | `$[-1:-10:-113667776004]` | [9] | [] |
| whitespace, operators, newline before \|\| | `$[?@.a\n\|\|@.b]` | [{a=1}, {b=2}] | [] |
| whitespace, operators, tab before \|\| | `$[?@.a\t\|\|@.b]` | [{a=1}, {b=2}] | [] |
| whitespace, operators, return before \|\| | `$[?@.a\r\|\|@.b]` | [{a=1}, {b=2}] | [] |
| whitespace, operators, newline after \|\| | `$[?@.a\|\|\n@.b]` | [{a=1}, {b=2}] | [] |
| whitespace, operators, tab after \|\| | `$[?@.a\|\|\t@.b]` | [{a=1}, {b=2}] | [] |
| whitespace, operators, return after \|\| | `$[?@.a\|\|\r@.b]` | [{a=1}, {b=2}] | [] |
| whitespace, operators, newline before && | `$[?@.a\n&&@.b]` | [{a=1, b=2}] | [] |
| whitespace, operators, tab before && | `$[?@.a\t&&@.b]` | [{a=1, b=2}] | [] |
| whitespace, operators, return before && | `$[?@.a\r&&@.b]` | [{a=1, b=2}] | [] |
| whitespace, operators, newline before == | `$[?@.a\n==@.b]` | [{a=1, b=1}] | [] |
| whitespace, operators, tab before == | `$[?@.a\t==@.b]` | [{a=1, b=1}] | [] |
| whitespace, operators, return before == | `$[?@.a\r==@.b]` | [{a=1, b=1}] | [] |
| whitespace, operators, newline before != | `$[?@.a\n!=@.b]` | [{a=1, b=2}] | [{a=1, b=1}, {a=1, b=2}] |
| whitespace, operators, tab before != | `$[?@.a\t!=@.b]` | [{a=1, b=2}] | [{a=1, b=1}, {a=1, b=2}] |
| whitespace, operators, return before != | `$[?@.a\r!=@.b]` | [{a=1, b=2}] | [{a=1, b=1}, {a=1, b=2}] |
| whitespace, operators, newline before < | `$[?@.a\n<@.b]` | [{a=1, b=2}] | [] |
| whitespace, operators, tab before < | `$[?@.a\t<@.b]` | [{a=1, b=2}] | [] |
| whitespace, operators, return before < | `$[?@.a\r<@.b]` | [{a=1, b=2}] | [] |
| whitespace, operators, newline before > | `$[?@.b\n>@.a]` | [{a=1, b=2}] | [] |
| whitespace, operators, tab before > | `$[?@.b\t>@.a]` | [{a=1, b=2}] | [] |
| whitespace, operators, return before > | `$[?@.b\r>@.a]` | [{a=1, b=2}] | [] |
| whitespace, operators, newline before <= | `$[?@.a\n<=@.b]` | [{a=1, b=1}, {a=1, b=2}] | [] |
| whitespace, operators, tab before <= | `$[?@.a\t<=@.b]` | [{a=1, b=1}, {a=1, b=2}] | [] |
| whitespace, operators, return before <= | `$[?@.a\r<=@.b]` | [{a=1, b=1}, {a=1, b=2}] | [] |
| whitespace, operators, newline before >= | `$[?@.b\n>=@.a]` | [{a=1, b=1}, {a=1, b=2}] | [] |
| whitespace, operators, tab before >= | `$[?@.b\t>=@.a]` | [{a=1, b=1}, {a=1, b=2}] | [] |
| whitespace, operators, return before >= | `$[?@.b\r>=@.a]` | [{a=1, b=1}, {a=1, b=2}] | [] |
| whitespace, selectors, space between selector and comma | `$['a' ,'b']` | [ab, bc] | [{a=ab, b=bc}] |
| whitespace, selectors, space between comma and selector | `$['a', 'b']` | [ab, bc] | [{a=ab, b=bc}] |
| whitespace, selectors, newline between comma and selector | `$['a',\n'b']` | [ab, bc] | [{a=ab, b=bc}] |
| whitespace, selectors, tab between comma and selector | `$['a',\t'b']` | [ab, bc] | [{a=ab, b=bc}] |
| whitespace, selectors, return between comma and selector | `$['a',\r'b']` | [ab, bc] | [{a=ab, b=bc}] |

## ONLY_RFC_ACCEPTS

| Test | Selector | Expected (RFC 9535) | Jayway |
| --- | --- | --- | --- |
| basic, multiple selectors, name and index, array data | `$['a',1]` | [1] | Jayway: Found empty property at index 7 |
| basic, multiple selectors, name and index, object data | `$['a',1]` | [1] | Jayway: Found empty property at index 7 |
| basic, multiple selectors, index and slice | `$[1,5:7]` | [1, 5, 6] | Jayway: Failed to parse SliceOperation: 1,5:7 |
| basic, multiple selectors, index and slice, overlapping | `$[1,0:3]` | [1, 0, 1, 2] | Jayway: Failed to parse SliceOperation: 1,0:3 |
| basic, multiple selectors, wildcard and index | `$[*,1]` | [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 1] | Jayway: Expected wildcard token to end with ']' on position 3 |
| basic, multiple selectors, wildcard and name | `$[*,'a']` | [A, B, A] | Jayway: Expected wildcard token to end with ']' on position 3 |
| basic, multiple selectors, wildcard and slice | `$[*,0:2]` | [0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 0, 1] | Jayway: Expected wildcard token to end with ']' on position 3 |
| basic, multiple selectors, multiple wildcards | `$[*,*]` | [0, 1, 2, 0, 1, 2] | Jayway: Expected wildcard token to end with ']' on position 3 |
| filter, non-singular existence, multiple | `$[?@[0, 0, 'a']]` | [[2], [2, 3], {a=3}, {a=3, b=4}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors | `$[?@.a,?@.b]` | [{a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, comparison | `$[?@.a=='b',?@.b=='x']` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, overlapping | `$[?@.a,?@.d]` | [{a=b, d=e}, {a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, filter and index | `$[?@.a,1]` | [{a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, filter and wildcard | `$[?@.a,*]` | [{a=b, d=e}, {a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, filter and slice | `$[?@.a,1:]` | [{a=b, d=e}, {b=c, d=f}, {g=h}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, multiple selectors, comparison filter, index and slice | `$[1, ?@.a=='b', 1:]` | [{b=c, d=f}, {a=b, d=e}, {b=c, d=f}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| filter, equals number, positive exponent | `$[?@.a==1e+2]` | [{a=100, d=e}] | Jayway: Failed to parse filter: [?(@.a==1e+2)], error on position: 10, char: + |
| filter, equals number, exponent +0 | `$[?@.a==1e+0]` | [{a=1, d=e}] | Jayway: Failed to parse filter: [?(@.a==1e+0)], error on position: 10, char: + |
| filter, equals number, exponent +00 | `$[?@.a==1e+00]` | [{a=1, d=e}] | Jayway: Failed to parse filter: [?(@.a==1e+00)], error on position: 10, char: + |
| filter, equals number, decimal fraction, positive exponent | `$[?@.a==1.1e+2]` | [{a=110, d=e}] | Jayway: Failed to parse filter: [?(@.a==1.1e+2)], error on position: 12, char: + |
| filter, equals, special nothing | `$.values[?length(@.a) == value($..c)]` | [{c=d}, {a=null}] | Jayway: Failed to parse filter: [?(length(@.a) == value($..c))], error on position: 3, char: l |
| filter, equals, empty node list and special nothing | `$[?@.a == length(@.b)]` | [{b=2}, {c=3}] | Jayway: Failed to parse filter: [?(@.a == length(@.b))], error on position: 10, char: l |
| index selector, min exact index | `$[-9007199254740991]` | [] | Jayway: Failed to parse token in ArrayIndexOperation: -9007199254740991 |
| index selector, max exact index | `$[9007199254740991]` | [] | Jayway: Failed to parse token in ArrayIndexOperation: 9007199254740991 |
| slice selector, slice selector with everything omitted, short form | `$[:]` | [0, 1, 2, 3] | Jayway: Failed to parse SliceOperation: : |
| slice selector, slice selector with everything omitted, long form | `$[::]` | [0, 1, 2, 3] | Jayway: Failed to parse SliceOperation: :: |
| slice selector, slice selector with start and end omitted | `$[::2]` | [0, 2, 4, 6, 8] | Jayway: Failed to parse SliceOperation: ::2 |
| slice selector, negative step with default start and end | `$[::-1]` | [3, 2, 1, 0] | Jayway: Failed to parse SliceOperation: ::-1 |
| slice selector, larger negative step | `$[::-2]` | [3, 1] | Jayway: Failed to parse SliceOperation: ::-2 |
| slice selector, in serial, on flat array | `$[1:3][::]` | [] | Jayway: Failed to parse SliceOperation: :: |
| slice selector, slice selector with everything omitted with empty array | `$[:]` | [] | Jayway: Failed to parse SliceOperation: : |
| slice selector, negative step with empty array | `$[::-1]` | [] | Jayway: Failed to parse SliceOperation: ::-1 |
| slice selector, excessively large to value | `$[2:113667776004]` | [2, 3, 4, 5, 6, 7, 8, 9] | Jayway: java.lang.NumberFormatException: For input string: "113667776004" |
| slice selector, excessively small from value | `$[-113667776004:1]` | [0] | Jayway: java.lang.NumberFormatException: For input string: "-113667776004" |
| slice selector, excessively large from value with negative step | `$[113667776004:0:-1]` | [9, 8, 7, 6, 5, 4, 3, 2, 1] | Jayway: java.lang.NumberFormatException: For input string: "113667776004" |
| slice selector, excessively small to value with negative step | `$[3:-113667776004:-1]` | [3, 2, 1, 0] | Jayway: java.lang.NumberFormatException: For input string: "-113667776004" |
| slice selector, start, min exact | `$[-9007199254740991::]` | [] | Jayway: java.lang.NumberFormatException: For input string: "-9007199254740991" |
| slice selector, start, max exact | `$[9007199254740991::]` | [] | Jayway: java.lang.NumberFormatException: For input string: "9007199254740991" |
| slice selector, end, min exact | `$[:-9007199254740991:]` | [] | Jayway: java.lang.NumberFormatException: For input string: "-9007199254740991" |
| slice selector, end, max exact | `$[:9007199254740991:]` | [] | Jayway: java.lang.NumberFormatException: For input string: "9007199254740991" |
| slice selector, step, min exact | `$[::-9007199254740991]` | [] | Jayway: Failed to parse SliceOperation: ::-9007199254740991 |
| slice selector, step, max exact | `$[::9007199254740991]` | [] | Jayway: Failed to parse SliceOperation: ::9007199254740991 |
| functions, count, count function | `$[?count(@..*)>2]` | [{a=[1, 2, 3]}, {a=[1], d=f}] | Jayway: Failed to parse filter: [?(count(@..*)>2)], error on position: 3, char: c |
| functions, count, single-node arg | `$[?count(@.a)>1]` | [] | Jayway: Failed to parse filter: [?(count(@.a)>1)], error on position: 3, char: c |
| functions, count, multiple-selector arg | `$[?count(@['a','d'])>1]` | [{a=[1], d=f}, {a=1, d=f}] | Jayway: Failed to parse filter: [?(count(@['a','d'])>1)], error on position: 3, char: c |
| functions, length, string data | `$[?length(@.a)>=2]` | [{a=ab}] | Jayway: Failed to parse filter: [?(length(@.a)>=2)], error on position: 3, char: l |
| functions, length, string data, unicode | `$[?length(@)==2]` | [☺☺, жж, 阿美] | Jayway: Failed to parse filter: [?(length(@)==2)], error on position: 3, char: l |
| functions, length, array data | `$[?length(@.a)>=2]` | [{a=[1, 2, 3]}] | Jayway: Failed to parse filter: [?(length(@.a)>=2)], error on position: 3, char: l |
| functions, length, missing data | `$[?length(@.a)>=2]` | [] | Jayway: Failed to parse filter: [?(length(@.a)>=2)], error on position: 3, char: l |
| functions, length, number arg | `$[?length(1)>=2]` | [] | Jayway: Failed to parse filter: [?(length(1)>=2)], error on position: 3, char: l |
| functions, length, true arg | `$[?length(true)>=2]` | [] | Jayway: Failed to parse filter: [?(length(true)>=2)], error on position: 3, char: l |
| functions, length, false arg | `$[?length(false)>=2]` | [] | Jayway: Failed to parse filter: [?(length(false)>=2)], error on position: 3, char: l |
| functions, length, null arg | `$[?length(null)>=2]` | [] | Jayway: Failed to parse filter: [?(length(null)>=2)], error on position: 3, char: l |
| functions, length, arg is a function expression | `$.values[?length(@.a)==length(value($..c))]` | [{a=ab}] | Jayway: Failed to parse filter: [?(length(@.a)==length(value($..c)))], error on position: 3, char: l |
| functions, length, arg is special nothing | `$[?length(value(@.a))>0]` | [{a=ab}] | Jayway: Failed to parse filter: [?(length(value(@.a))>0)], error on position: 3, char: l |
| functions, match, found match | `$[?match(@.a, 'a.*')]` | [{a=ab}] | Jayway: Failed to parse filter: [?(match(@.a, 'a.*'))], error on position: 3, char: m |
| functions, match, double quotes | `$[?match(@.a, "a.*")]` | [{a=ab}] | Jayway: Failed to parse filter: [?(match(@.a, "a.*"))], error on position: 3, char: m |
| functions, match, regex from the document | `$.values[?match(@, $.regex)]` | [bab] | Jayway: Failed to parse filter: [?(match(@, $.regex))], error on position: 3, char: m |
| functions, match, don't select match | `$[?!match(@.a, 'a.*')]` | [] | Jayway: Failed to parse filter: [?(!match(@.a, 'a.*'))], error on position: 4, char: m |
| functions, match, not a match | `$[?match(@.a, 'a.*')]` | [] | Jayway: Failed to parse filter: [?(match(@.a, 'a.*'))], error on position: 3, char: m |
| functions, match, select non-match | `$[?!match(@.a, 'a.*')]` | [{a=bc}] | Jayway: Failed to parse filter: [?(!match(@.a, 'a.*'))], error on position: 4, char: m |
| functions, match, non-string first arg | `$[?match(1, 'a.*')]` | [] | Jayway: Failed to parse filter: [?(match(1, 'a.*'))], error on position: 3, char: m |
| functions, match, non-string second arg | `$[?match(@.a, 1)]` | [] | Jayway: Failed to parse filter: [?(match(@.a, 1))], error on position: 3, char: m |
| functions, match, filter, match function, unicode char class, uppercase | `$[?match(@, '\\p{Lu}')]` | [Ж] | Jayway: Failed to parse filter: [?(match(@, '\\p{Lu}'))], error on position: 3, char: m |
| functions, match, filter, match function, unicode char class negated, uppercase | `$[?match(@, '\\P{Lu}')]` | [ж, 1] | Jayway: Failed to parse filter: [?(match(@, '\\P{Lu}'))], error on position: 3, char: m |
| functions, match, filter, match function, unicode, surrogate pair | `$[?match(@, 'a.b')]` | [a𐄁b] | Jayway: Failed to parse filter: [?(match(@, 'a.b'))], error on position: 3, char: m |
| functions, match, dot matcher on \u2028 | `$[?match(@, '.')]` | [ ] | Jayway: Failed to parse filter: [?(match(@, '.'))], error on position: 3, char: m |
| functions, match, dot matcher on \u2029 | `$[?match(@, '.')]` | [ ] | Jayway: Failed to parse filter: [?(match(@, '.'))], error on position: 3, char: m |
| functions, match, arg is a function expression | `$.values[?match(@.a, value($..['regex']))]` | [{a=ab}] | Jayway: Failed to parse filter: [?(match(@.a, value($..['regex'])))], error on position: 3, char: m |
| functions, match, dot in character class | `$[?match(@, 'a[.b]c')]` | [abc, a.c] | Jayway: Failed to parse filter: [?(match(@, 'a[.b]c'))], error on position: 3, char: m |
| functions, match, escaped dot | `$[?match(@, 'a\\.c')]` | [a.c] | Jayway: Failed to parse filter: [?(match(@, 'a\\.c'))], error on position: 3, char: m |
| functions, match, escaped backslash before dot | `$[?match(@, 'a\\\\.c')]` | [a\ c] | Jayway: Failed to parse filter: [?(match(@, 'a\\\\.c'))], error on position: 3, char: m |
| functions, match, escaped left square bracket | `$[?match(@, 'a\\[.c')]` | [a[ c] | Jayway: Failed to parse filter: [?(match(@, 'a\\[.c'))], error on position: 3, char: m |
| functions, match, escaped right square bracket | `$[?match(@, 'a[\\].]c')]` | [a.c, a]c] | Jayway: Failed to parse filter: [?(match(@, 'a[\\].]c'))], error on position: 3, char: m |
| functions, match, explicit caret | `$[?match(@, '^ab.*')]` | [abc, ab] | Jayway: Failed to parse filter: [?(match(@, '^ab.*'))], error on position: 3, char: m |
| functions, match, explicit dollar | `$[?match(@, '.*bc$')]` | [abc] | Jayway: Failed to parse filter: [?(match(@, '.*bc$'))], error on position: 3, char: m |
| functions, search, at the end | `$[?search(@.a, 'a.*')]` | [{a=the end is ab}] | Jayway: Failed to parse filter: [?(search(@.a, 'a.*'))], error on position: 3, char: s |
| functions, search, double quotes | `$[?search(@.a, "a.*")]` | [{a=the end is ab}] | Jayway: Failed to parse filter: [?(search(@.a, "a.*"))], error on position: 3, char: s |
| functions, search, at the start | `$[?search(@.a, 'a.*')]` | [{a=ab is at the start}] | Jayway: Failed to parse filter: [?(search(@.a, 'a.*'))], error on position: 3, char: s |
| functions, search, in the middle | `$[?search(@.a, 'a.*')]` | [{a=contains two matches}] | Jayway: Failed to parse filter: [?(search(@.a, 'a.*'))], error on position: 3, char: s |
| functions, search, regex from the document | `$.values[?search(@, $.regex)]` | [bab, bba, bbab] | Jayway: Failed to parse filter: [?(search(@, $.regex))], error on position: 3, char: s |
| functions, search, don't select match | `$[?!search(@.a, 'a.*')]` | [] | Jayway: Failed to parse filter: [?(!search(@.a, 'a.*'))], error on position: 4, char: s |
| functions, search, not a match | `$[?search(@.a, 'a.*')]` | [] | Jayway: Failed to parse filter: [?(search(@.a, 'a.*'))], error on position: 3, char: s |
| functions, search, select non-match | `$[?!search(@.a, 'a.*')]` | [{a=bc}] | Jayway: Failed to parse filter: [?(!search(@.a, 'a.*'))], error on position: 4, char: s |
| functions, search, non-string first arg | `$[?search(1, 'a.*')]` | [] | Jayway: Failed to parse filter: [?(search(1, 'a.*'))], error on position: 3, char: s |
| functions, search, non-string second arg | `$[?search(@.a, 1)]` | [] | Jayway: Failed to parse filter: [?(search(@.a, 1))], error on position: 3, char: s |
| functions, search, filter, search function, unicode char class, uppercase | `$[?search(@, '\\p{Lu}')]` | [Ж, жЖ] | Jayway: Failed to parse filter: [?(search(@, '\\p{Lu}'))], error on position: 3, char: s |
| functions, search, filter, search function, unicode char class negated, uppercase | `$[?search(@, '\\P{Lu}')]` | [ж, 1] | Jayway: Failed to parse filter: [?(search(@, '\\P{Lu}'))], error on position: 3, char: s |
| functions, search, filter, search function, unicode, surrogate pair | `$[?search(@, 'a.b')]` | [a𐄁bc] | Jayway: Failed to parse filter: [?(search(@, 'a.b'))], error on position: 3, char: s |
| functions, search, dot matcher on \u2028 | `$[?search(@, '.')]` | [ , \r \n] | Jayway: Failed to parse filter: [?(search(@, '.'))], error on position: 3, char: s |
| functions, search, dot matcher on \u2029 | `$[?search(@, '.')]` | [ , \r \n] | Jayway: Failed to parse filter: [?(search(@, '.'))], error on position: 3, char: s |
| functions, search, arg is a function expression | `$.values[?search(@, value($..['regex']))]` | [bab, bba, bbab] | Jayway: Failed to parse filter: [?(search(@, value($..['regex'])))], error on position: 3, char: s |
| functions, search, dot in character class | `$[?search(@, 'a[.b]c')]` | [x abc y, x a.c y] | Jayway: Failed to parse filter: [?(search(@, 'a[.b]c'))], error on position: 3, char: s |
| functions, search, escaped dot | `$[?search(@, 'a\\.c')]` | [x a.c y] | Jayway: Failed to parse filter: [?(search(@, 'a\\.c'))], error on position: 3, char: s |
| functions, search, escaped backslash before dot | `$[?search(@, 'a\\\\.c')]` | [x a\ c y] | Jayway: Failed to parse filter: [?(search(@, 'a\\\\.c'))], error on position: 3, char: s |
| functions, search, escaped left square bracket | `$[?search(@, 'a\\[.c')]` | [x a[ c y] | Jayway: Failed to parse filter: [?(search(@, 'a\\[.c'))], error on position: 3, char: s |
| functions, search, escaped right square bracket | `$[?search(@, 'a[\\].]c')]` | [x a.c y, x a]c y] | Jayway: Failed to parse filter: [?(search(@, 'a[\\].]c'))], error on position: 3, char: s |
| functions, value, single-value nodelist | `$[?value(@.*)==4]` | [[4], {foo=4}] | Jayway: Failed to parse filter: [?(value(@.*)==4)], error on position: 3, char: v |
| functions, value, multi-value nodelist | `$[?value(@.*)==4]` | [] | Jayway: Failed to parse filter: [?(value(@.*)==4)], error on position: 3, char: v |
| whitespace, filter, newline between question mark and expression | `$[?\n@.a]` | [{a=b, d=e}] | Jayway: Failed to parse filter: [?(\n@.a)], error on position: 3, char: \n |
| whitespace, filter, tab between question mark and expression | `$[?\t@.a]` | [{a=b, d=e}] | Jayway: Failed to parse filter: [?(\t@.a)], error on position: 3, char: \t |
| whitespace, filter, return between question mark and expression | `$[?\r@.a]` | [{a=b, d=e}] | Jayway: Failed to parse filter: [?(\r@.a)], error on position: 3, char: \r |
| whitespace, filter, newline between question mark and parenthesized expression | `$[?\n(@.a)]` | [{a=b, d=e}] | Jayway: Failed to parse filter: [?(\n(@.a))], error on position: 3, char: \n |
| whitespace, filter, tab between question mark and parenthesized expression | `$[?\t(@.a)]` | [{a=b, d=e}] | Jayway: Failed to parse filter: [?(\t(@.a))], error on position: 3, char: \t |
| whitespace, filter, return between question mark and parenthesized expression | `$[?\r(@.a)]` | [{a=b, d=e}] | Jayway: Failed to parse filter: [?(\r(@.a))], error on position: 3, char: \r |
| whitespace, filter, newline between parenthesized expression and bracket | `$[?(@.a)\n]` | [{a=b, d=e}] | Jayway: Expected character: ) |
| whitespace, filter, tab between parenthesized expression and bracket | `$[?(@.a)\t]` | [{a=b, d=e}] | Jayway: Expected character: ) |
| whitespace, filter, return between parenthesized expression and bracket | `$[?(@.a)\r]` | [{a=b, d=e}] | Jayway: Expected character: ) |
| whitespace, filter, newline between bracket and question mark | `$[\n?@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, tab between bracket and question mark | `$[\t?@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, filter, return between bracket and question mark | `$[\r?@.a]` | [{a=b, d=e}] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, functions, space between parenthesis and arg | `$[?count( @.*)==1]` | [{a=1}, {b=2}] | Jayway: Failed to parse filter: [?(count( @.*)==1)], error on position: 3, char: c |
| whitespace, functions, newline between parenthesis and arg | `$[?count(\n@.*)==1]` | [{a=1}, {b=2}] | Jayway: Failed to parse filter: [?(count(\n@.*)==1)], error on position: 3, char: c |
| whitespace, functions, tab between parenthesis and arg | `$[?count(\t@.*)==1]` | [{a=1}, {b=2}] | Jayway: Failed to parse filter: [?(count(\t@.*)==1)], error on position: 3, char: c |
| whitespace, functions, return between parenthesis and arg | `$[?count(\r@.*)==1]` | [{a=1}, {b=2}] | Jayway: Failed to parse filter: [?(count(\r@.*)==1)], error on position: 3, char: c |
| whitespace, functions, space between arg and comma | `$[?search(@ ,'[a-z]+')]` | [foo] | Jayway: Failed to parse filter: [?(search(@ ,'[a-z]+'))], error on position: 3, char: s |
| whitespace, functions, newline between arg and comma | `$[?search(@\n,'[a-z]+')]` | [foo] | Jayway: Failed to parse filter: [?(search(@\n,'[a-z]+'))], error on position: 3, char: s |
| whitespace, functions, tab between arg and comma | `$[?search(@\t,'[a-z]+')]` | [foo] | Jayway: Failed to parse filter: [?(search(@\t,'[a-z]+'))], error on position: 3, char: s |
| whitespace, functions, return between arg and comma | `$[?search(@\r,'[a-z]+')]` | [foo] | Jayway: Failed to parse filter: [?(search(@\r,'[a-z]+'))], error on position: 3, char: s |
| whitespace, functions, space between comma and arg | `$[?search(@, '[a-z]+')]` | [foo] | Jayway: Failed to parse filter: [?(search(@, '[a-z]+'))], error on position: 3, char: s |
| whitespace, functions, newline between comma and arg | `$[?search(@,\n'[a-z]+')]` | [foo] | Jayway: Failed to parse filter: [?(search(@,\n'[a-z]+'))], error on position: 3, char: s |
| whitespace, functions, tab between comma and arg | `$[?search(@,\t'[a-z]+')]` | [foo] | Jayway: Failed to parse filter: [?(search(@,\t'[a-z]+'))], error on position: 3, char: s |
| whitespace, functions, return between comma and arg | `$[?search(@,\r'[a-z]+')]` | [foo] | Jayway: Failed to parse filter: [?(search(@,\r'[a-z]+'))], error on position: 3, char: s |
| whitespace, functions, space between arg and parenthesis | `$[?count(@.* )==1]` | [{a=1}, {b=2}] | Jayway: Failed to parse filter: [?(count(@.* )==1)], error on position: 3, char: c |
| whitespace, functions, newline between arg and parenthesis | `$[?count(@.*\n)==1]` | [{a=1}, {b=2}] | Jayway: Failed to parse filter: [?(count(@.*\n)==1)], error on position: 3, char: c |
| whitespace, functions, tab between arg and parenthesis | `$[?count(@.*\t)==1]` | [{a=1}, {b=2}] | Jayway: Failed to parse filter: [?(count(@.*\t)==1)], error on position: 3, char: c |
| whitespace, functions, return between arg and parenthesis | `$[?count(@.*\r)==1]` | [{a=1}, {b=2}] | Jayway: Failed to parse filter: [?(count(@.*\r)==1)], error on position: 3, char: c |
| whitespace, functions, spaces in a relative singular selector | `$[?length(@ .a .b) == 3]` | [{a={b=foo}}] | Jayway: Failed to parse filter: [?(length(@ .a .b) == 3)], error on position: 3, char: l |
| whitespace, functions, newlines in a relative singular selector | `$[?length(@\n.a\n.b) == 3]` | [{a={b=foo}}] | Jayway: Failed to parse filter: [?(length(@\n.a\n.b) == 3)], error on position: 3, char: l |
| whitespace, functions, tabs in a relative singular selector | `$[?length(@\t.a\t.b) == 3]` | [{a={b=foo}}] | Jayway: Failed to parse filter: [?(length(@\t.a\t.b) == 3)], error on position: 3, char: l |
| whitespace, functions, returns in a relative singular selector | `$[?length(@\r.a\r.b) == 3]` | [{a={b=foo}}] | Jayway: Failed to parse filter: [?(length(@\r.a\r.b) == 3)], error on position: 3, char: l |
| whitespace, functions, spaces in an absolute singular selector | `$..[?length(@)==length($ [0] .a)]` | [foo] | Jayway: Failed to parse filter: [?(length(@)==length($ [0] .a))], error on position: 3, char: l |
| whitespace, functions, newlines in an absolute singular selector | `$..[?length(@)==length($\n[0]\n.a)]` | [foo] | Jayway: Failed to parse filter: [?(length(@)==length($\n[0]\n.a))], error on position: 3, char: l |
| whitespace, functions, tabs in an absolute singular selector | `$..[?length(@)==length($\t[0]\t.a)]` | [foo] | Jayway: Failed to parse filter: [?(length(@)==length($\t[0]\t.a))], error on position: 3, char: l |
| whitespace, functions, returns in an absolute singular selector | `$..[?length(@)==length($\r[0]\r.a)]` | [foo] | Jayway: Failed to parse filter: [?(length(@)==length($\r[0]\r.a))], error on position: 3, char: l |
| whitespace, operators, space after \|\| | `$[?@.a\|\| @.b]` | [{a=1}, {b=2}] | Jayway: Expected character: ) |
| whitespace, operators, space after && | `$[?@.a&& @.b]` | [{a=1, b=2}] | Jayway: Expected character: ) |
| whitespace, operators, newline after && | `$[?@.a&& @.b]` | [{a=1, b=2}] | Jayway: Expected character: ) |
| whitespace, operators, tab after && | `$[?@.a&& @.b]` | [{a=1, b=2}] | Jayway: Expected character: ) |
| whitespace, operators, return after && | `$[?@.a&& @.b]` | [{a=1, b=2}] | Jayway: Expected character: ) |
| whitespace, operators, newline after == | `$[?@.a==\n@.b]` | [{a=1, b=1}] | Jayway: Failed to parse filter: [?(@.a==\n@.b)], error on position: 8, char: \n |
| whitespace, operators, tab after == | `$[?@.a==\t@.b]` | [{a=1, b=1}] | Jayway: Failed to parse filter: [?(@.a==\t@.b)], error on position: 8, char: \t |
| whitespace, operators, return after == | `$[?@.a==\r@.b]` | [{a=1, b=1}] | Jayway: Failed to parse filter: [?(@.a==\r@.b)], error on position: 8, char: \r |
| whitespace, operators, newline after != | `$[?@.a!=\n@.b]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a!=\n@.b)], error on position: 8, char: \n |
| whitespace, operators, tab after != | `$[?@.a!=\t@.b]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a!=\t@.b)], error on position: 8, char: \t |
| whitespace, operators, return after != | `$[?@.a!=\r@.b]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a!=\r@.b)], error on position: 8, char: \r |
| whitespace, operators, newline after < | `$[?@.a<\n@.b]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a<\n@.b)], error on position: 7, char: \n |
| whitespace, operators, tab after < | `$[?@.a<\t@.b]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a<\t@.b)], error on position: 7, char: \t |
| whitespace, operators, return after < | `$[?@.a<\r@.b]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a<\r@.b)], error on position: 7, char: \r |
| whitespace, operators, newline after > | `$[?@.b>\n@.a]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.b>\n@.a)], error on position: 7, char: \n |
| whitespace, operators, tab after > | `$[?@.b>\t@.a]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.b>\t@.a)], error on position: 7, char: \t |
| whitespace, operators, return after > | `$[?@.b>\r@.a]` | [{a=1, b=2}] | Jayway: Failed to parse filter: [?(@.b>\r@.a)], error on position: 7, char: \r |
| whitespace, operators, newline after <= | `$[?@.a<=\n@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a<=\n@.b)], error on position: 8, char: \n |
| whitespace, operators, tab after <= | `$[?@.a<=\t@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a<=\t@.b)], error on position: 8, char: \t |
| whitespace, operators, return after <= | `$[?@.a<=\r@.b]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Failed to parse filter: [?(@.a<=\r@.b)], error on position: 8, char: \r |
| whitespace, operators, newline after >= | `$[?@.b>=\n@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Failed to parse filter: [?(@.b>=\n@.a)], error on position: 8, char: \n |
| whitespace, operators, tab after >= | `$[?@.b>=\t@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Failed to parse filter: [?(@.b>=\t@.a)], error on position: 8, char: \t |
| whitespace, operators, return after >= | `$[?@.b>=\r@.a]` | [{a=1, b=1}, {a=1, b=2}] | Jayway: Failed to parse filter: [?(@.b>=\r@.a)], error on position: 8, char: \r |
| whitespace, operators, newline between logical not and test expression | `$[?!\n@.a]` | [{d=f}] | Jayway: Failed to parse filter: [?(!\n@.a)], error on position: 4, char: \n |
| whitespace, operators, tab between logical not and test expression | `$[?!\t@.a]` | [{d=f}] | Jayway: Failed to parse filter: [?(!\t@.a)], error on position: 4, char: \t |
| whitespace, operators, return between logical not and test expression | `$[?!\r@.a]` | [{d=f}] | Jayway: Failed to parse filter: [?(!\r@.a)], error on position: 4, char: \r |
| whitespace, operators, newline between logical not and parenthesized expression | `$[?!\n(@.a=='b')]` | [{a=a, d=e}, {a=d, d=f}] | Jayway: Failed to parse filter: [?(!\n(@.a=='b'))], error on position: 4, char: \n |
| whitespace, operators, tab between logical not and parenthesized expression | `$[?!\t(@.a=='b')]` | [{a=a, d=e}, {a=d, d=f}] | Jayway: Failed to parse filter: [?(!\t(@.a=='b'))], error on position: 4, char: \t |
| whitespace, operators, return between logical not and parenthesized expression | `$[?!\r(@.a=='b')]` | [{a=a, d=e}, {a=d, d=f}] | Jayway: Failed to parse filter: [?(!\r(@.a=='b'))], error on position: 4, char: \r |
| whitespace, selectors, space between root and bracket | `$ ['a']` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, newline between root and bracket | `$\n['a']` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, tab between root and bracket | `$\t['a']` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, return between root and bracket | `$\r['a']` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, space between bracket and bracket | `$['a'] ['b']` | [ab] | Jayway: Could not parse token starting at position 6 |
| whitespace, selectors, newline between bracket and bracket | `$['a'] \n['b']` | [ab] | Jayway: Could not parse token starting at position 6 |
| whitespace, selectors, tab between bracket and bracket | `$['a'] \t['b']` | [ab] | Jayway: Could not parse token starting at position 6 |
| whitespace, selectors, return between bracket and bracket | `$['a'] \r['b']` | [ab] | Jayway: Could not parse token starting at position 6 |
| whitespace, selectors, space between root and dot | `$ .a` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, newline between root and dot | `$\n.a` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, tab between root and dot | `$\t.a` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, return between root and dot | `$\r.a` | [ab] | Jayway: Illegal character at position 1 expected '.' or '[' |
| whitespace, selectors, newline between bracket and selector | `$[\n'a']` | [ab] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, selectors, tab between bracket and selector | `$[\t'a']` | [ab] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, selectors, return between bracket and selector | `$[\r'a']` | [ab] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, selectors, newline between selector and bracket | `$['a'\n]` | [ab] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, tab between selector and bracket | `$['a'\t]` | [ab] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, return between selector and bracket | `$['a'\r]` | [ab] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, newline between selector and comma | `$['a'\n,'b']` | [ab, bc] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, tab between selector and comma | `$['a'\t,'b']` | [ab, bc] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, selectors, return between selector and comma | `$['a'\r,'b']` | [ab, bc] | Jayway: Property must be separated by comma or Property must be terminated close square bracket at index 4 |
| whitespace, slice, space between start and colon | `$[1 :5:2]` | [2, 4] | Jayway: Failed to parse SliceOperation: 1 :5:2 |
| whitespace, slice, newline between start and colon | `$[1\n:5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, tab between start and colon | `$[1\t:5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, return between start and colon | `$[1\r:5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, space between colon and end | `$[1: 5:2]` | [2, 4] | Jayway: Failed to parse SliceOperation: 1: 5:2 |
| whitespace, slice, newline between colon and end | `$[1:\n5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, tab between colon and end | `$[1:\t5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, return between colon and end | `$[1:\r5:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, space between end and colon | `$[1:5 :2]` | [2, 4] | Jayway: Failed to parse SliceOperation: 1:5 :2 |
| whitespace, slice, newline between end and colon | `$[1:5\n:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, tab between end and colon | `$[1:5\t:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, return between end and colon | `$[1:5\r:2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, space between colon and step | `$[1:5: 2]` | [2, 4] | Jayway: Failed to parse SliceOperation: 1:5: 2 |
| whitespace, slice, newline between colon and step | `$[1:5:\n2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, tab between colon and step | `$[1:5:\t2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |
| whitespace, slice, return between colon and step | `$[1:5:\r2]` | [2, 4] | Jayway: Could not parse token starting at position 1. Expected ?, ', 0-9, *  |

## Invalid queries Jayway accepts

| Test | Selector |
| --- | --- |
| basic, no trailing whitespace | `$ ` |
| basic, name shorthand, symbol | `$.&` |
| basic, name shorthand, number | `$.1` |
| filter, non-singular query in comparison, slice | `$[?@[0:0]==0]` |
| filter, non-singular query in comparison, all children | `$[?@[*]==0]` |
| filter, non-singular query in comparison, descendants | `$[?@..a==0]` |
| filter, non-singular query in comparison, combined | `$[?@.a[*].a==0]` |
| filter, relative non-singular query, index, equal | `$[?(@[0, 0]==42)]` |
| filter, relative non-singular query, index, not equal | `$[?(@[0, 0]!=42)]` |
| filter, relative non-singular query, index, less-or-equal | `$[?(@[0, 0]<=42)]` |
| filter, relative non-singular query, name, equal | `$[?(@['a', 'a']==42)]` |
| filter, relative non-singular query, name, not equal | `$[?(@['a', 'a']!=42)]` |
| filter, relative non-singular query, name, less-or-equal | `$[?(@['a', 'a']<=42)]` |
| filter, relative non-singular query, wildcard, equal | `$[?(@.*==42)]` |
| filter, relative non-singular query, wildcard, not equal | `$[?(@.*!=42)]` |
| filter, relative non-singular query, wildcard, less-or-equal | `$[?(@.*<=42)]` |
| filter, relative non-singular query, slice, equal | `$[?(@[0:0]==42)]` |
| filter, relative non-singular query, slice, not equal | `$[?(@[0:0]!=42)]` |
| filter, relative non-singular query, slice, less-or-equal | `$[?(@[0:0]<=42)]` |
| filter, absolute non-singular query, index, equal | `$[?($[0, 0]==42)]` |
| filter, absolute non-singular query, index, not equal | `$[?($[0, 0]!=42)]` |
| filter, absolute non-singular query, index, less-or-equal | `$[?($[0, 0]<=42)]` |
| filter, absolute non-singular query, name, equal | `$[?($['a', 'a']==42)]` |
| filter, absolute non-singular query, name, not equal | `$[?($['a', 'a']!=42)]` |
| filter, absolute non-singular query, name, less-or-equal | `$[?($['a', 'a']<=42)]` |
| filter, absolute non-singular query, wildcard, equal | `$[?($.*==42)]` |
| filter, absolute non-singular query, wildcard, not equal | `$[?($.*!=42)]` |
| filter, absolute non-singular query, wildcard, less-or-equal | `$[?($.*<=42)]` |
| filter, absolute non-singular query, slice, equal | `$[?($[0:0]==42)]` |
| filter, absolute non-singular query, slice, not equal | `$[?($[0:0]!=42)]` |
| filter, absolute non-singular query, slice, less-or-equal | `$[?($[0:0]<=42)]` |
| filter, equals number, invalid no int digit | `$[?@.a==.1]` |
| filter, equals number, invalid minus no int digit | `$[?@.a==-.1]` |
| filter, equals number, invalid 00 | `$[?@.a==00]` |
| filter, equals number, invalid leading 0 | `$[?@.a==01]` |
| filter, equals number, invalid no fractional digit | `$[?@.a==1.]` |
| filter, equals number, invalid no fractional digit e | `$[?@.a==1.e1]` |
| index selector, leading 0 | `$[01]` |
| index selector, -0 | `$[-0]` |
| index selector, leading -0 | `$[-01]` |
| name selector, double quotes, embedded U+0000 | `$[" "]` |
| name selector, double quotes, embedded U+0001 | `$[""]` |
| name selector, double quotes, embedded U+0002 | `$[""]` |
| name selector, double quotes, embedded U+0003 | `$[""]` |
| name selector, double quotes, embedded U+0004 | `$[""]` |
| name selector, double quotes, embedded U+0005 | `$[""]` |
| name selector, double quotes, embedded U+0006 | `$[""]` |
| name selector, double quotes, embedded U+0007 | `$[""]` |
| name selector, double quotes, embedded U+0008 | `$[""]` |
| name selector, double quotes, embedded U+0009 | `$["\t"]` |
| name selector, double quotes, embedded U+000A | `$["\n"]` |
| name selector, double quotes, embedded U+000B | `$[""]` |
| name selector, double quotes, embedded U+000C | `$[""]` |
| name selector, double quotes, embedded U+000D | `$["\r"]` |
| name selector, double quotes, embedded U+000E | `$[""]` |
| name selector, double quotes, embedded U+000F | `$[""]` |
| name selector, double quotes, embedded U+0010 | `$[""]` |
| name selector, double quotes, embedded U+0011 | `$[""]` |
| name selector, double quotes, embedded U+0012 | `$[""]` |
| name selector, double quotes, embedded U+0013 | `$[""]` |
| name selector, double quotes, embedded U+0014 | `$[""]` |
| name selector, double quotes, embedded U+0015 | `$[""]` |
| name selector, double quotes, embedded U+0016 | `$[""]` |
| name selector, double quotes, embedded U+0017 | `$[""]` |
| name selector, double quotes, embedded U+0018 | `$[""]` |
| name selector, double quotes, embedded U+0019 | `$[""]` |
| name selector, double quotes, embedded U+001A | `$[""]` |
| name selector, double quotes, embedded U+001B | `$[""]` |
| name selector, double quotes, embedded U+001C | `$[""]` |
| name selector, double quotes, embedded U+001D | `$[""]` |
| name selector, double quotes, embedded U+001E | `$[""]` |
| name selector, double quotes, embedded U+001F | `$[""]` |
| name selector, double quotes, invalid escaped single quote | `$["\'"]` |
| name selector, double quotes, escape at end of line | `$["\\n"]` |
| name selector, double quotes, question mark escape | `$["\?"]` |
| name selector, double quotes, bell escape | `$["\a"]` |
| name selector, double quotes, vertical tab escape | `$["\v"]` |
| name selector, double quotes, 0 escape | `$["\0"]` |
| name selector, double quotes, x escape | `$["\x12"]` |
| name selector, double quotes, n escape | `$["\N{LATIN CAPITAL LETTER A}"]` |
| name selector, double quotes, unicode escape no hex | `$["\u"]` |
| name selector, double quotes, unicode escape too few hex | `$["\u123"]` |
| name selector, double quotes, unicode escape upper u | `$["\U1234"]` |
| name selector, double quotes, unicode escape upper u long | `$["\U0010FFFF"]` |
| name selector, double quotes, unicode escape plus | `$["\u+1234"]` |
| name selector, double quotes, single high surrogate | `$["\uD800"]` |
| name selector, double quotes, single low surrogate | `$["\uDC00"]` |
| name selector, double quotes, high high surrogate | `$["\uD800\uD800"]` |
| name selector, double quotes, low low surrogate | `$["\uDC00\uDC00"]` |
| name selector, double quotes, surrogate non-surrogate | `$["\uD800\u1234"]` |
| name selector, double quotes, non-surrogate surrogate | `$["\u1234\uDC00"]` |
| name selector, double quotes, surrogate supplementary | `$["\uD800𝄞"]` |
| name selector, double quotes, supplementary surrogate | `$["𝄞\uDC00"]` |
| name selector, double quotes, surrogate incomplete low | `$["\uD800\uDC0"]` |
| name selector, single quotes, embedded U+0000 | `$[' ']` |
| name selector, single quotes, embedded U+0001 | `$['']` |
| name selector, single quotes, embedded U+0002 | `$['']` |
| name selector, single quotes, embedded U+0003 | `$['']` |
| name selector, single quotes, embedded U+0004 | `$['']` |
| name selector, single quotes, embedded U+0005 | `$['']` |
| name selector, single quotes, embedded U+0006 | `$['']` |
| name selector, single quotes, embedded U+0007 | `$['']` |
| name selector, single quotes, embedded U+0008 | `$['']` |
| name selector, single quotes, embedded U+0009 | `$['\t']` |
| name selector, single quotes, embedded U+000A | `$['\n']` |
| name selector, single quotes, embedded U+000B | `$['']` |
| name selector, single quotes, embedded U+000C | `$['']` |
| name selector, single quotes, embedded U+000D | `$['\r']` |
| name selector, single quotes, embedded U+000E | `$['']` |
| name selector, single quotes, embedded U+000F | `$['']` |
| name selector, single quotes, embedded U+0010 | `$['']` |
| name selector, single quotes, embedded U+0011 | `$['']` |
| name selector, single quotes, embedded U+0012 | `$['']` |
| name selector, single quotes, embedded U+0013 | `$['']` |
| name selector, single quotes, embedded U+0014 | `$['']` |
| name selector, single quotes, embedded U+0015 | `$['']` |
| name selector, single quotes, embedded U+0016 | `$['']` |
| name selector, single quotes, embedded U+0017 | `$['']` |
| name selector, single quotes, embedded U+0018 | `$['']` |
| name selector, single quotes, embedded U+0019 | `$['']` |
| name selector, single quotes, embedded U+001A | `$['']` |
| name selector, single quotes, embedded U+001B | `$['']` |
| name selector, single quotes, embedded U+001C | `$['']` |
| name selector, single quotes, embedded U+001D | `$['']` |
| name selector, single quotes, embedded U+001E | `$['']` |
| name selector, single quotes, embedded U+001F | `$['']` |
| name selector, single quotes, invalid escaped double quote | `$['\"']` |
| slice selector, too many colons | `$[1:2:3:4]` |
| slice selector, overflowing step | `$[1:10:231584178474632390847141970017375815706539969331281128078915168015826259279872]` |
| slice selector, underflowing step | `$[-1:-10:-231584178474632390847141970017375815706539969331281128078915168015826259279872]` |
| slice selector, start, leading 0 | `$[01::]` |
| slice selector, start, -0 | `$[-0::]` |
| slice selector, start, leading -0 | `$[-01::]` |
| slice selector, end, leading 0 | `$[:01:]` |
| slice selector, end, -0 | `$[:-0:]` |
| slice selector, end, leading -0 | `$[:-01:]` |
| whitespace, selectors, newline between dot and name | `$.\na` |
| whitespace, selectors, tab between dot and name | `$.\ta` |
| whitespace, selectors, return between dot and name | `$.\ra` |
| whitespace, selectors, newline between recursive descent and name | `$..\na` |
| whitespace, selectors, tab between recursive descent and name | `$..\ta` |
| whitespace, selectors, return between recursive descent and name | `$..\ra` |
