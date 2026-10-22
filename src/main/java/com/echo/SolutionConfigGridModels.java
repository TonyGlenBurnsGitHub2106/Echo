package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The read models of a solution's CONFIGURATION II tab (Story 5611) -- the generic successor to the
 * Configuration tab. The user defines the COLUMNS (as many as wanted, each with an optional Color; one may
 * be the DEFAULT column whose value stands in for a blank anywhere else) and the AXES that describe them
 * ("Environment": Local / QA / Prod, "Server": Tomcat 9 / Tomcat 11; each value may have a Color too).
 *
 * Every property carries {@code matches}: for each way of comparing -- {@code "all"} (every column) and each
 * axis id (columns compared only with the columns that share their value on that axis) -- the sets and how
 * well each set agrees, plus the property's overall {@code state} for that comparison. The rule is
 * {@link SolutionConfigGridMatcher}; the page only paints what it is told.
 */
public final class SolutionConfigGridModels {

    private SolutionConfigGridModels() {}

    public record AxisValue(int id, int axisId, String title, String color, int sortOrder) {
        static AxisValue of(Map<String, Object> r) {
            return new AxisValue(intOf(r, "SolutionConfigGridAxisValueID"), intOf(r, "SolutionConfigGridAxisID"), strOf(r, "ValueTitle"),
                strOf(r, "ColorHex"), intOf(r, "SortOrder"));
        }
    }

    public record Axis(int id, String title, int sortOrder, List<AxisValue> values) {}

    /** A column's value on one axis. */
    public record Tag(int axisId, String axisTitle, int valueId, String value) {}

    public record Column(int id, String title, String color, boolean isDefault, int sortOrder, List<Tag> tags) {}

    public record Type(String code, String name, boolean secret, int sortOrder) {
        static Type of(Map<String, Object> r) {
            return new Type(strOf(r, "TypeCode"), strOf(r, "TypeName"), boolOf(r, "IsSecret"), intOf(r, "SortOrder"));
        }
    }

    public record CellState(String code, String name, String description, int sortOrder) {
        static CellState of(Map<String, Object> r) {
            return new CellState(strOf(r, "StateCode"), strOf(r, "StateName"), strOf(r, "StateDescription"), intOf(r, "SortOrder"));
        }
    }

    /** One cell: a property's value in one column, where it is read from, where else it is set, its state. */
    public record Cell(int columnId, String value, String source, List<String> alsoSetIn, String note, String state,
                       String modifiedByCode, String modifiedByName, LocalDateTime modifiedDate) {
        static Cell of(Map<String, Object> r, List<String> alsoSetIn) {
            LocalDateTime changed = dateOf(r, "ModifiedDate");
            return new Cell(intOf(r, "SolutionConfigGridColumnID"), strOf(r, "ValueText"), strOf(r, "SourceTitle"), alsoSetIn,
                strOf(r, "ValueNote"), strOf(r, "StateCode"), strOf(r, "ModifiedByCode"), strOf(r, "ModifiedByName"),
                changed != null ? changed : dateOf(r, "CreatedDate"));
        }
    }

    /**
     * One comparison set of a property: the columns compared together ({@code valueId} = the axis value they
     * share, 0 = untagged on that axis or the whole row), how many have a value (their own, or the default
     * column's), and the verdict: all | some | none | incomplete | empty | single | secret.
     */
    public record MatchSet(int valueId, List<Integer> columnIds, int setCount, int distinctCount, String state) {}

    /** How a property agrees under one way of comparing: its overall state and each set. */
    public record Match(String state, List<MatchSet> sets) {}

    public record Property(int id, int groupId, String key, String description, String typeCode, String typeName,
                           boolean secret, int sortOrder, List<Cell> values, Map<String, Match> matches) {}

    /** A group; {@code states} counts its properties by state, per comparison: "all.some" -> 3, "12.none" -> 1. */
    public record Group(int id, String title, int sortOrder, int propertyCount, Map<String, Integer> states,
                        List<Property> properties) {}

    /**
     * The whole tab. {@code counts}: "properties", "state:<comparison>.<state>". A comparison key is "all" or
     * an axis id. {@code canImport} is true while the tab is empty and the Configuration tab has something.
     */
    public record Grid(int solutionId, List<Column> columns, List<Axis> axes, List<Group> groups, List<Type> types,
                       List<CellState> states, List<String> sources, Map<String, Integer> counts, boolean canImport) {}
}
