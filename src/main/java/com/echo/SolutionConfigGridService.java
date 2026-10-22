package com.echo;

import com.echo.SolutionConfigGridModels.Axis;
import com.echo.SolutionConfigGridModels.AxisValue;
import com.echo.SolutionConfigGridModels.Cell;
import com.echo.SolutionConfigGridModels.CellState;
import com.echo.SolutionConfigGridModels.Column;
import com.echo.SolutionConfigGridModels.Grid;
import com.echo.SolutionConfigGridModels.Group;
import com.echo.SolutionConfigGridModels.Match;
import com.echo.SolutionConfigGridModels.Property;
import com.echo.SolutionConfigGridModels.Tag;
import com.echo.SolutionConfigGridModels.Type;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * A solution's Configuration II tab (Story 5611). Every call asks where the thing lives and passes the solution
 * through {@link SolutionAccess}, so a scoped client reaches only the configuration of a solution it was
 * given -- where, like everywhere else in the family, it has full rights. Axes and their values are per
 * solution, so an agent may find-or-create them freely: nothing it invents can reach another solution.
 *
 * Every write answers with the whole tab. Every PUT is a merge: a field left out keeps its value.
 */
@Service
public class SolutionConfigGridService {

    private static final int KEY_MAX = 200, TITLE_MAX = 200, NAME_MAX = 100, TEXT_MAX = 2000, SOURCE_MAX = 200;
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}$");

    private final SolutionConfigGridRepository repo;
    private final SolutionConfigRepository oldTab;
    private final SolutionAccess access;

    public SolutionConfigGridService(SolutionConfigGridRepository repo, SolutionConfigRepository oldTab, SolutionAccess access) {
        this.repo = repo; this.oldTab = oldTab; this.access = access;
    }

    // ---- read -------------------------------------------------------------------

    public Grid grid(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return assemble(solutionId);
    }

    /** Which solution a property lives on -- what a Find link needs before it can open the tab. */
    public Map<String, Object> locateProperty(SolutionCaller caller, int propertyId) {
        int solutionId = require(caller, "property", propertyId, "Property");
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("solutionId", solutionId);
        m.put("propertyId", propertyId);
        return m;
    }

    private Grid assemble(int solutionId) {
        // Axes and their values.
        Map<Integer, List<AxisValue>> valuesByAxis = new LinkedHashMap<>();
        for (Map<String, Object> v : repo.axisValues(solutionId)) {
            AxisValue av = AxisValue.of(v);
            valuesByAxis.computeIfAbsent(av.axisId(), k -> new ArrayList<>()).add(av);
        }
        List<Axis> axes = new ArrayList<>();
        Map<Integer, String> axisTitle = new HashMap<>();
        Map<Integer, List<Integer>> axisValueIds = new LinkedHashMap<>();
        for (Map<String, Object> a : repo.axes(solutionId)) {
            int aid = intOf(a, "SolutionConfigGridAxisID");
            List<AxisValue> vals = valuesByAxis.getOrDefault(aid, List.of());
            axes.add(new Axis(aid, strOf(a, "AxisTitle"), intOf(a, "SortOrder"), vals));
            axisTitle.put(aid, strOf(a, "AxisTitle"));
            axisValueIds.put(aid, vals.stream().map(AxisValue::id).toList());
        }

        // Columns with their tags.
        Map<Integer, Map<Integer, Integer>> tagIds = new HashMap<>();
        Map<Integer, List<Tag>> tagsByColumn = new HashMap<>();
        for (Map<String, Object> t : repo.tags(solutionId)) {
            int col = intOf(t, "SolutionConfigGridColumnID"), aid = intOf(t, "SolutionConfigGridAxisID"), vid = intOf(t, "SolutionConfigGridAxisValueID");
            tagIds.computeIfAbsent(col, k -> new HashMap<>()).put(aid, vid);
            tagsByColumn.computeIfAbsent(col, k -> new ArrayList<>()).add(new Tag(aid, axisTitle.get(aid), vid, strOf(t, "ValueTitle")));
        }
        List<Column> columns = new ArrayList<>();
        List<Integer> compared = new ArrayList<>();
        int defaultId = 0;
        for (Map<String, Object> c : repo.columns(solutionId)) {
            int cid = intOf(c, "SolutionConfigGridColumnID");
            boolean isDefault = boolOf(c, "IsDefaultColumn");
            List<Tag> tags = new ArrayList<>(tagsByColumn.getOrDefault(cid, List.of()));
            tags.sort((x, y) -> Integer.compare(axisOrder(axes, x.axisId()), axisOrder(axes, y.axisId())));
            columns.add(new Column(cid, strOf(c, "ColumnTitle"), strOf(c, "ColorHex"), isDefault, intOf(c, "SortOrder"), tags));
            if (isDefault) defaultId = cid; else compared.add(cid);
        }

        // Cells.
        Map<Integer, List<String>> alsoByValue = new HashMap<>();
        for (Map<String, Object> a : repo.alsoSet(solutionId))
            alsoByValue.computeIfAbsent(intOf(a, "SolutionConfigGridValueID"), k -> new ArrayList<>()).add(strOf(a, "SourceTitle"));
        Map<Integer, List<Cell>> cellsByProperty = new HashMap<>();
        for (Map<String, Object> v : repo.values(solutionId))
            cellsByProperty.computeIfAbsent(intOf(v, "SolutionConfigGridPropertyID"), k -> new ArrayList<>())
                .add(Cell.of(v, alsoByValue.getOrDefault(intOf(v, "SolutionConfigGridValueID"), List.of())));

        // Properties, matched; and the counters.
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<Integer, List<Property>> propsByGroup = new HashMap<>();
        Map<Integer, Map<String, Integer>> statesByGroup = new HashMap<>();
        int total = 0;
        for (Map<String, Object> p : repo.properties(solutionId)) {
            int pid = intOf(p, "SolutionConfigGridPropertyID"), gid = intOf(p, "SolutionConfigGridGroupID");
            boolean secret = boolOf(p, "IsSecret");
            List<Cell> cells = cellsByProperty.getOrDefault(pid, List.of());
            Map<Integer, String> text = new HashMap<>();
            for (Cell cell : cells) text.put(cell.columnId(), cell.value());
            Map<String, Match> matches = SolutionConfigGridMatcher.matches(secret, compared, axisValueIds, tagIds, text, defaultId);
            Property prop = new Property(pid, gid, strOf(p, "PropertyKey"), strOf(p, "PropertyDescription"), strOf(p, "TypeCode"),
                strOf(p, "TypeName"), secret, intOf(p, "SortOrder"), cells, matches);
            propsByGroup.computeIfAbsent(gid, k -> new ArrayList<>()).add(prop);
            total++;
            Map<String, Integer> gs = statesByGroup.computeIfAbsent(gid, k -> new LinkedHashMap<>());
            for (Map.Entry<String, Match> m : matches.entrySet()) {
                String key = m.getKey() + "." + m.getValue().state();
                counts.merge("state:" + key, 1, Integer::sum);
                gs.merge(key, 1, Integer::sum);
            }
        }
        counts.put("properties", total);
        List<Group> groups = new ArrayList<>();
        for (Map<String, Object> g : repo.groups(solutionId)) {
            int gid = intOf(g, "SolutionConfigGridGroupID");
            List<Property> props = propsByGroup.getOrDefault(gid, List.of());
            groups.add(new Group(gid, strOf(g, "GroupTitle"), intOf(g, "SortOrder"), props.size(),
                statesByGroup.getOrDefault(gid, Map.of()), props));
        }

        boolean canImport = columns.isEmpty() && groups.isEmpty() && !oldTab.environments(solutionId).isEmpty();
        return new Grid(solutionId, columns, axes, groups, repo.types().stream().map(Type::of).toList(),
            repo.cellStates().stream().map(CellState::of).toList(), repo.sources(solutionId), counts, canImport);
    }

    private static int axisOrder(List<Axis> axes, int axisId) {
        for (int i = 0; i < axes.size(); i++) if (axes.get(i).id() == axisId) return i;
        return Integer.MAX_VALUE;
    }

    /** The owning solution, provided the thing exists and this caller may see it. */
    private int require(SolutionCaller caller, String kind, int id, String what) {
        Integer solutionId = id <= 0 ? null : repo.locate(kind, id);
        if (solutionId == null) throw new SolutionNotFoundException(what);
        access.requireSolution(caller, solutionId);
        return solutionId;
    }

    private static Map<String, Object> rowById(List<Map<String, Object>> rows, String idColumn, int id) {
        return rows.stream().filter(r -> intOf(r, idColumn) == id).findFirst().orElse(null);
    }

    // ---- import -------------------------------------------------------------------

    /** Copy the Configuration tab into this one while this one is empty. The Configuration tab is only read. */
    public Grid importFromConfig(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        repo.importFromConfig(solutionId);
        return assemble(solutionId);
    }

    // ---- axes ---------------------------------------------------------------------

    public Grid addAxis(SolutionCaller caller, int solutionId, String title) {
        access.requireSolution(caller, solutionId);
        repo.saveAxis(0, solutionId, limit(title, NAME_MAX, "An axis name"), null);
        return assemble(solutionId);
    }

    public Grid updateAxis(SolutionCaller caller, int axisId, String title, Integer sortOrder) {
        int solutionId = require(caller, "axis", axisId, "Axis");
        Map<String, Object> current = rowById(repo.axes(solutionId), "SolutionConfigGridAxisID", axisId);
        if (current == null) throw new SolutionNotFoundException("Axis");
        repo.saveAxis(axisId, solutionId, limit(SolutionService.keep(title, strOf(current, "AxisTitle"), true), NAME_MAX, "An axis name"), sortOrder);
        return assemble(solutionId);
    }

    public Grid deleteAxis(SolutionCaller caller, int axisId) {
        int solutionId = require(caller, "axis", axisId, "Axis");
        if (!repo.deleteAxis(axisId)) throw new SolutionNotFoundException("Axis");
        return assemble(solutionId);
    }

    public Grid addAxisValue(SolutionCaller caller, int axisId, String title, String color) {
        int solutionId = require(caller, "axis", axisId, "Axis");
        repo.saveAxisValue(0, axisId, limit(title, NAME_MAX, "An axis value"), color(color, null), null);
        return assemble(solutionId);
    }

    public Grid updateAxisValue(SolutionCaller caller, int valueId, String title, String color, Integer sortOrder) {
        int solutionId = require(caller, "axisvalue", valueId, "Axis value");
        Map<String, Object> current = rowById(repo.axisValues(solutionId), "SolutionConfigGridAxisValueID", valueId);
        if (current == null) throw new SolutionNotFoundException("Axis value");
        repo.saveAxisValue(valueId, intOf(current, "SolutionConfigGridAxisID"),
            limit(SolutionService.keep(title, strOf(current, "ValueTitle"), true), NAME_MAX, "An axis value"),
            color(color, strOf(current, "ColorHex")), sortOrder);
        return assemble(solutionId);
    }

    public Grid deleteAxisValue(SolutionCaller caller, int valueId) {
        int solutionId = require(caller, "axisvalue", valueId, "Axis value");
        if (!repo.deleteAxisValue(valueId)) throw new SolutionNotFoundException("Axis value");
        return assemble(solutionId);
    }

    // ---- columns ------------------------------------------------------------------

    public Grid addColumn(SolutionCaller caller, int solutionId, String title, String color, Boolean isDefault, Map<String, String> tags) {
        access.requireSolution(caller, solutionId);
        checkTags(tags);
        int id = repo.saveColumn(0, solutionId, limit(title, NAME_MAX, "A column name"), color(color, null), Boolean.TRUE.equals(isDefault), null);
        applyTags(id, tags);
        return assemble(solutionId);
    }

    /** A merge. {@code tags} names only the axes to change: {"Environment": "QA"}; a blank value removes that tag. */
    public Grid updateColumn(SolutionCaller caller, int columnId, String title, String color, Boolean isDefault, Integer sortOrder,
                             Map<String, String> tags) {
        int solutionId = require(caller, "column", columnId, "Column");
        Map<String, Object> current = rowById(repo.columns(solutionId), "SolutionConfigGridColumnID", columnId);
        if (current == null) throw new SolutionNotFoundException("Column");
        checkTags(tags);
        repo.saveColumn(columnId, solutionId, limit(SolutionService.keep(title, strOf(current, "ColumnTitle"), true), NAME_MAX, "A column name"),
            color(color, strOf(current, "ColorHex")), isDefault != null ? isDefault : boolOf(current, "IsDefaultColumn"), sortOrder);
        applyTags(columnId, tags);
        return assemble(solutionId);
    }

    public Grid deleteColumn(SolutionCaller caller, int columnId) {
        int solutionId = require(caller, "column", columnId, "Column");
        if (!repo.deleteColumn(columnId)) throw new SolutionNotFoundException("Column");
        return assemble(solutionId);
    }

    private static void checkTags(Map<String, String> tags) {
        if (tags == null) return;
        for (Map.Entry<String, String> t : tags.entrySet()) {
            limit(required(t.getKey(), "An axis name"), NAME_MAX, "An axis name");
            limit(t.getValue(), NAME_MAX, "An axis value");
        }
    }

    private void applyTags(int columnId, Map<String, String> tags) {
        if (tags == null) return;
        for (Map.Entry<String, String> t : tags.entrySet()) repo.setTag(columnId, t.getKey().trim(), t.getValue() == null ? "" : t.getValue().trim());
    }

    // ---- groups -------------------------------------------------------------------

    public Grid addGroup(SolutionCaller caller, int solutionId, String title) {
        access.requireSolution(caller, solutionId);
        repo.saveGroup(0, solutionId, limit(title, TITLE_MAX, "A group name"), null);
        return assemble(solutionId);
    }

    public Grid updateGroup(SolutionCaller caller, int groupId, String title, Integer sortOrder) {
        int solutionId = require(caller, "group", groupId, "Group");
        Map<String, Object> current = rowById(repo.groups(solutionId), "SolutionConfigGridGroupID", groupId);
        if (current == null) throw new SolutionNotFoundException("Group");
        repo.saveGroup(groupId, solutionId, limit(SolutionService.keep(title, strOf(current, "GroupTitle"), true), TITLE_MAX, "A group name"), sortOrder);
        return assemble(solutionId);
    }

    public Grid deleteGroup(SolutionCaller caller, int groupId) {
        int solutionId = require(caller, "group", groupId, "Group");
        if (!repo.deleteGroup(groupId)) throw new SolutionNotFoundException("Group");
        return assemble(solutionId);
    }

    // ---- properties ---------------------------------------------------------------

    public Grid addProperty(SolutionCaller caller, int groupId, String key, String description, String typeCode) {
        int solutionId = require(caller, "group", groupId, "Group");
        repo.saveProperty(0, groupId, limit(key, KEY_MAX, "A property name"), limit(description, TEXT_MAX, "A description"),
            typeCode, null, caller.memberCode());
        return assemble(solutionId);
    }

    public Grid updateProperty(SolutionCaller caller, int propertyId, String key, String description, String typeCode,
                               Integer groupId, Integer sortOrder) {
        int solutionId = require(caller, "property", propertyId, "Property");
        Map<String, Object> current = rowById(repo.properties(solutionId), "SolutionConfigGridPropertyID", propertyId);
        if (current == null) throw new SolutionNotFoundException("Property");
        int from = intOf(current, "SolutionConfigGridGroupID");
        int target = (groupId == null || groupId <= 0) ? from : groupId;
        if (target != from) {
            Integer to = repo.locate("group", target);
            if (to == null || to != solutionId) throw new SolutionRuleException("A property can only move to a group of the same solution.");
        }
        repo.saveProperty(propertyId, target,
            limit(SolutionService.keep(key, strOf(current, "PropertyKey"), true), KEY_MAX, "A property name"),
            limit(SolutionService.keep(description, strOf(current, "PropertyDescription"), false), TEXT_MAX, "A description"),
            SolutionService.keep(typeCode, strOf(current, "TypeCode"), true), sortOrder, caller.memberCode());
        return assemble(solutionId);
    }

    public Grid deleteProperty(SolutionCaller caller, int propertyId) {
        int solutionId = require(caller, "property", propertyId, "Property");
        if (!repo.deleteProperty(propertyId)) throw new SolutionNotFoundException("Property");
        return assemble(solutionId);
    }

    // ---- a cell -------------------------------------------------------------------

    /**
     * Set one property's value in one column. A merge: a field left out (null) keeps what the cell has; an
     * empty string or an empty list clears it. A cell left with no value, source, note or other place is removed.
     */
    public Grid setValue(SolutionCaller caller, int propertyId, int columnId, String value, String source, String note, String state,
                         List<String> alsoSetIn) {
        int solutionId = require(caller, "property", propertyId, "Property");
        if (require(caller, "column", columnId, "Column") != solutionId)
            throw new SolutionRuleException("That column belongs to a different solution.");
        Map<String, Object> current = repo.values(solutionId).stream()
            .filter(r -> intOf(r, "SolutionConfigGridPropertyID") == propertyId && intOf(r, "SolutionConfigGridColumnID") == columnId)
            .findFirst().orElse(null);
        List<String> currentAlso = new ArrayList<>();
        if (current != null)
            for (Map<String, Object> a : repo.alsoSet(solutionId))
                if (intOf(a, "SolutionConfigGridValueID") == intOf(current, "SolutionConfigGridValueID")) currentAlso.add(strOf(a, "SourceTitle"));
        List<String> also = alsoSetIn == null ? currentAlso : alsoSetIn;
        for (String a : also) {
            if (a != null && a.contains("|")) throw new IllegalArgumentException("A place name cannot contain |.");
            limit(a, SOURCE_MAX, "A place name");
        }
        repo.saveValue(propertyId, columnId,
            limit(value != null ? value : current == null ? null : strOf(current, "ValueText"), TEXT_MAX, "A value"),
            limit(source != null ? source : current == null ? null : strOf(current, "SourceTitle"), SOURCE_MAX, "Where it is read from"),
            limit(note != null ? note : current == null ? null : strOf(current, "ValueNote"), TEXT_MAX, "A note"),
            state != null && !state.isBlank() ? state.trim().toLowerCase() : current == null ? null : strOf(current, "StateCode"),
            also, caller.memberCode());
        return assemble(solutionId);
    }

    // ---- helpers ------------------------------------------------------------------

    /** null keeps the current Color; "" clears it; anything else must be #RRGGBB. */
    private static String color(String asked, String current) {
        if (asked == null) return current;
        String c = asked.trim();
        if (c.isEmpty()) return null;
        if (!COLOR.matcher(c).matches()) throw new IllegalArgumentException("A Color is written #RRGGBB, for example #2563EB.");
        return c.toUpperCase();
    }

    private static String required(String v, String what) {
        if (v == null || v.trim().isEmpty()) throw new IllegalArgumentException(what + " is required.");
        return v;
    }

    /** Refuse a value longer than its column, with a message that names the field (never a SQL truncation 500). */
    private static String limit(String v, int max, String what) {
        if (v != null && v.trim().length() > max)
            throw new IllegalArgumentException(what + " may be at most " + max + " characters (this one is " + v.trim().length() + ").");
        return v;
    }
}
