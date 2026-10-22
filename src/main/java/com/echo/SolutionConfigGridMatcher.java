package com.echo;

import com.echo.SolutionConfigGridModels.Match;
import com.echo.SolutionConfigGridModels.MatchSet;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * The ONE place the Configuration II Colors are decided (Story 5611): how well a property's values agree.
 * The live store and the demo store both feed it, so the Color of a cell and the number in the summary can
 * never disagree.
 *
 * A comparison splits the (non-default) columns into SETS and judges each set on its own:
 *   "all"      one set: every column.
 *   an axis    one set per value of that axis -- the columns that share it (Tomcat 9 vs Tomcat 11 inside QA);
 *              columns with no value on the axis form one more set, last.
 *
 * A column's value is its own, or -- when it has none -- the DEFAULT column's ("applies to all"). Values are
 * compared trimmed and ignoring case, as the database collation does. A set's verdict:
 *
 *   secret      the property is secret: nothing is stored, nothing to compare
 *   single      a set of one column with a value -- nothing to compare it with
 *   empty       no column of the set has a value
 *   incomplete  some do, some do not -- an unknown is not a match
 *   all         every column of the set has the same value      (green)
 *   some        at least two share a value, not all              (orange)
 *   none        every column of the set differs                  (red)
 *
 * The property's state for the comparison is its worst set: none, then some, then incomplete (any gap
 * beside a known value counts as one), then all; empty when nothing is known, single when every set is.
 */
public final class SolutionConfigGridMatcher {

    public static final String ALL = "all";
    public static final List<String> STATES = List.of("all", "some", "none", "incomplete", "empty", "single", "secret");

    private SolutionConfigGridMatcher() {}

    /**
     * @param columnIds   the non-default columns, in order
     * @param axisValues  per axis id: its value ids in order
     * @param tags        per column id: axis id -> value id
     * @param values      per column id (default column included): the value text
     * @param defaultId   the default column's id, 0 when there is none
     */
    public static Map<String, Match> matches(boolean secret, List<Integer> columnIds, Map<Integer, List<Integer>> axisValues,
                                             Map<Integer, Map<Integer, Integer>> tags, Map<Integer, String> values, int defaultId) {
        Map<Integer, String> effective = new LinkedHashMap<>();
        String fallback = defaultId > 0 ? clean(values.get(defaultId)) : null;
        for (int c : columnIds) {
            String own = clean(values.get(c));
            effective.put(c, own != null ? own : fallback);
        }
        Map<String, Match> out = new LinkedHashMap<>();
        out.put(ALL, judge(secret, List.of(new Bucket(0, columnIds)), effective));
        for (Map.Entry<Integer, List<Integer>> axis : axisValues.entrySet()) {
            int axisId = axis.getKey();
            List<Bucket> buckets = new ArrayList<>();
            for (int valueId : axis.getValue()) {
                List<Integer> members = new ArrayList<>();
                for (int c : columnIds) if (valueOf(tags, c, axisId) == valueId) members.add(c);
                if (!members.isEmpty()) buckets.add(new Bucket(valueId, members));
            }
            List<Integer> untagged = new ArrayList<>();
            for (int c : columnIds) if (valueOf(tags, c, axisId) == 0) untagged.add(c);
            if (!untagged.isEmpty()) buckets.add(new Bucket(0, untagged));
            out.put(String.valueOf(axisId), judge(secret, buckets, effective));
        }
        return out;
    }

    private record Bucket(int valueId, List<Integer> columnIds) {}

    private static int valueOf(Map<Integer, Map<Integer, Integer>> tags, int columnId, int axisId) {
        Map<Integer, Integer> t = tags.get(columnId);
        Integer v = t == null ? null : t.get(axisId);
        return v == null ? 0 : v;
    }

    private static Match judge(boolean secret, List<Bucket> buckets, Map<Integer, String> effective) {
        List<MatchSet> sets = new ArrayList<>();
        for (Bucket b : buckets) {
            int set = 0;
            Set<String> distinct = new HashSet<>();
            for (int c : b.columnIds()) {
                String v = effective.get(c);
                if (v == null) continue;
                set++;
                distinct.add(v.toLowerCase(Locale.ROOT));
            }
            sets.add(new MatchSet(b.valueId(), List.copyOf(b.columnIds()), set, distinct.size(), setState(secret, b.columnIds().size(), set, distinct.size())));
        }
        return new Match(overall(secret, sets), sets);
    }

    static String setState(boolean secret, int size, int set, int distinct) {
        if (secret) return "secret";
        if (set == 0) return "empty";
        if (size == 1) return "single";
        if (set < size) return "incomplete";
        if (distinct == 1) return "all";
        if (distinct == size) return "none";
        return "some";
    }

    static String overall(boolean secret, List<MatchSet> sets) {
        if (secret) return "secret";
        if (sets.isEmpty()) return "empty";
        boolean none = false, some = false, incomplete = false, all = false, empty = false, single = false;
        for (MatchSet s : sets) {
            switch (s.state()) {
                case "none" -> none = true;
                case "some" -> some = true;
                case "incomplete" -> incomplete = true;
                case "all" -> all = true;
                case "empty" -> empty = true;
                default -> single = true;
            }
        }
        if (none) return "none";
        if (some) return "some";
        if (incomplete || (empty && (all || single))) return "incomplete";
        if (all) return "all";
        if (single) return "single";
        return "empty";
    }

    private static String clean(String v) {
        if (v == null) return null;
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
