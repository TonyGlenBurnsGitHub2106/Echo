package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The read models of a solution's CONFIGURATION tab (Story 3516). Every property that means something,
 * in GROUPS, with its value in each ENVIRONMENT (Local, QA, Production by default) and where that value
 * is read from -- plus any other places it is also set. A property's {@code matchState} says how well its
 * values agree across the environments; the rule lives in one place, dbo.vw_SolutionConfigPropertyState:
 *
 *   all (green) | some (orange) | none (red) | incomplete (an environment has no value) | empty | secret
 *
 * A SECRET property never carries a value -- only where it is set.
 */
public final class SolutionConfigModels {

    private SolutionConfigModels() {}

    public record Environment(int id, int environmentNameId, String title, int sortOrder) {
        static Environment of(Map<String, Object> r) {
            return new Environment(intOf(r, "SolutionConfigEnvironmentID"), intOf(r, "SolutionEnvironmentID"),
                strOf(r, "EnvironmentName"), intOf(r, "SortOrder"));
        }
    }

    public record Type(String code, String name, boolean secret, int sortOrder) {
        static Type of(Map<String, Object> r) {
            return new Type(strOf(r, "TypeCode"), strOf(r, "TypeName"), boolOf(r, "IsSecret"), intOf(r, "SortOrder"));
        }
    }

    /** One cell: a property's value in one environment, where it is read from, and where else it is set. */
    public record Cell(int environmentId, String value, String source, List<String> alsoSetIn, String note,
                       String modifiedByCode, String modifiedByName, LocalDateTime modifiedDate) {
        static Cell of(Map<String, Object> r, List<String> alsoSetIn) {
            LocalDateTime changed = dateOf(r, "ModifiedDate");
            return new Cell(intOf(r, "SolutionConfigEnvironmentID"), strOf(r, "ValueText"), strOf(r, "SourceTitle"), alsoSetIn,
                strOf(r, "ValueNote"), strOf(r, "ModifiedByCode"), strOf(r, "ModifiedByName"),
                changed != null ? changed : dateOf(r, "CreatedDate"));
        }
    }

    public record Property(int id, int groupId, String key, String description, String typeCode, String typeName,
                           boolean secret, String matchState, int setCount, int sortOrder, List<Cell> values) {
        static Property of(Map<String, Object> r, List<Cell> values) {
            return new Property(intOf(r, "SolutionConfigPropertyID"), intOf(r, "SolutionConfigGroupID"), strOf(r, "PropertyKey"),
                strOf(r, "PropertyDescription"), strOf(r, "TypeCode"), strOf(r, "TypeName"), boolOf(r, "IsSecret"),
                strOf(r, "MatchState"), intOf(r, "SetCount"), intOf(r, "SortOrder"), values);
        }
    }

    /** A group; {@code states} counts its properties by match state. */
    public record Group(int id, String title, int sortOrder, int propertyCount, Map<String, Integer> states,
                        List<Property> properties) {}

    /** The whole tab. {@code counts} is the raw counter set: properties, state:x, group:g, groupstate:g.x. */
    public record Config(int solutionId, List<Environment> environments, List<Group> groups, List<Type> types,
                         List<String> sources, Map<String, Integer> counts) {}
}
