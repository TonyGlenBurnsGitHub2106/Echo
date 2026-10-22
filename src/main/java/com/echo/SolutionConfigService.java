package com.echo;

import com.echo.SolutionConfigModels.Cell;
import com.echo.SolutionConfigModels.Config;
import com.echo.SolutionConfigModels.Environment;
import com.echo.SolutionConfigModels.Group;
import com.echo.SolutionConfigModels.Property;
import com.echo.SolutionConfigModels.Type;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * A solution's Configuration tab (Story 3516). Every call asks where the thing lives and passes the
 * solution through {@link SolutionAccess}, so a scoped client reaches only the configuration of a
 * solution it was given -- where, like everywhere else in the family, it has full rights.
 *
 * Every write answers with the whole tab, counters included. Adding a group or a property first gives
 * the solution its three standard environments if it has none yet.
 */
@Service
public class SolutionConfigService {

    private static final int KEY_MAX = 200, TITLE_MAX = 200, ENV_MAX = 100, TEXT_MAX = 2000, SOURCE_MAX = 200;

    private final SolutionConfigRepository repo;
    private final SolutionAccess access;

    public SolutionConfigService(SolutionConfigRepository repo, SolutionAccess access) {
        this.repo = repo; this.access = access;
    }

    // ---- read -------------------------------------------------------------------

    public Config config(SolutionCaller caller, int solutionId) {
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

    private Config assemble(int solutionId) {
        Map<String, Integer> counts = repo.counts(solutionId);
        List<Environment> envs = repo.environments(solutionId).stream().map(Environment::of).toList();

        Map<Integer, List<String>> alsoByValue = new HashMap<>();
        for (Map<String, Object> a : repo.alsoSet(solutionId))
            alsoByValue.computeIfAbsent(intOf(a, "SolutionConfigValueID"), k -> new ArrayList<>()).add(strOf(a, "SourceTitle"));
        Map<Integer, List<Cell>> cellsByProperty = new HashMap<>();
        for (Map<String, Object> v : repo.values(solutionId))
            cellsByProperty.computeIfAbsent(intOf(v, "SolutionConfigPropertyID"), k -> new ArrayList<>())
                .add(Cell.of(v, alsoByValue.getOrDefault(intOf(v, "SolutionConfigValueID"), List.of())));

        Map<Integer, List<Property>> propsByGroup = new HashMap<>();
        for (Map<String, Object> p : repo.properties(solutionId)) {
            Property prop = Property.of(p, cellsByProperty.getOrDefault(intOf(p, "SolutionConfigPropertyID"), List.of()));
            propsByGroup.computeIfAbsent(prop.groupId(), k -> new ArrayList<>()).add(prop);
        }
        List<Group> groups = new ArrayList<>();
        for (Map<String, Object> g : repo.groups(solutionId)) {
            int gid = intOf(g, "SolutionConfigGroupID");
            Map<String, Integer> states = new LinkedHashMap<>();
            for (String s : List.of("all", "some", "none", "incomplete", "empty", "secret")) {
                int n = counts.getOrDefault("groupstate:" + gid + "." + s, 0);
                if (n > 0) states.put(s, n);
            }
            groups.add(new Group(gid, strOf(g, "GroupTitle"), intOf(g, "SortOrder"), counts.getOrDefault("group:" + gid, 0), states,
                propsByGroup.getOrDefault(gid, List.of())));
        }
        List<Type> types = repo.types().stream().map(Type::of).toList();
        return new Config(solutionId, envs, groups, types, repo.sources(solutionId), counts);
    }

    /** The owning solution, provided the thing exists and this caller may see it. */
    private int require(SolutionCaller caller, String kind, int id, String what) {
        Integer solutionId = id <= 0 ? null : repo.locate(kind, id);
        if (solutionId == null) throw new SolutionNotFoundException(what);
        access.requireSolution(caller, solutionId);
        return solutionId;
    }

    // ---- environments -------------------------------------------------------------

    public Config seedDefaults(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        repo.seedDefaultEnvironments(solutionId);
        return assemble(solutionId);
    }

    public Config addEnvironment(SolutionCaller caller, int solutionId, String title) {
        access.requireSolution(caller, solutionId);
        repo.saveEnvironment(0, solutionId, limit(title, ENV_MAX, "An environment name"), null);
        return assemble(solutionId);
    }

    public Config updateEnvironment(SolutionCaller caller, int environmentId, String title, Integer sortOrder) {
        int solutionId = require(caller, "environment", environmentId, "Environment");
        String current = repo.environments(solutionId).stream().filter(r -> intOf(r, "SolutionConfigEnvironmentID") == environmentId)
            .map(r -> strOf(r, "EnvironmentName")).findFirst().orElse(null);
        repo.saveEnvironment(environmentId, solutionId, limit(SolutionService.keep(title, current, true), ENV_MAX, "An environment name"), sortOrder);
        return assemble(solutionId);
    }

    public Config deleteEnvironment(SolutionCaller caller, int environmentId) {
        int solutionId = require(caller, "environment", environmentId, "Environment");
        if (!repo.deleteEnvironment(environmentId)) throw new SolutionNotFoundException("Environment");
        return assemble(solutionId);
    }

    // ---- groups -------------------------------------------------------------------

    public Config addGroup(SolutionCaller caller, int solutionId, String title) {
        access.requireSolution(caller, solutionId);
        repo.seedDefaultEnvironments(solutionId);
        repo.saveGroup(0, solutionId, limit(title, TITLE_MAX, "A group name"), null);
        return assemble(solutionId);
    }

    public Config updateGroup(SolutionCaller caller, int groupId, String title, Integer sortOrder) {
        int solutionId = require(caller, "group", groupId, "Group");
        String current = repo.groups(solutionId).stream().filter(r -> intOf(r, "SolutionConfigGroupID") == groupId)
            .map(r -> strOf(r, "GroupTitle")).findFirst().orElse(null);
        repo.saveGroup(groupId, solutionId, limit(SolutionService.keep(title, current, true), TITLE_MAX, "A group name"), sortOrder);
        return assemble(solutionId);
    }

    public Config deleteGroup(SolutionCaller caller, int groupId) {
        int solutionId = require(caller, "group", groupId, "Group");
        if (!repo.deleteGroup(groupId)) throw new SolutionNotFoundException("Group");
        return assemble(solutionId);
    }

    // ---- properties ---------------------------------------------------------------

    public Config addProperty(SolutionCaller caller, int groupId, String key, String description, String typeCode) {
        int solutionId = require(caller, "group", groupId, "Group");
        repo.seedDefaultEnvironments(solutionId);
        repo.saveProperty(0, groupId, limit(key, KEY_MAX, "A property name"), limit(description, TEXT_MAX, "A description"),
            typeCode, null, caller.memberCode());
        return assemble(solutionId);
    }

    /** A PUT is a merge: a field left out keeps its value. */
    public Config updateProperty(SolutionCaller caller, int propertyId, String key, String description, String typeCode,
                                 Integer groupId, Integer sortOrder) {
        int solutionId = require(caller, "property", propertyId, "Property");
        Map<String, Object> current = repo.properties(solutionId).stream()
            .filter(r -> intOf(r, "SolutionConfigPropertyID") == propertyId).findFirst().orElse(null);
        if (current == null) throw new SolutionNotFoundException("Property");
        int target = (groupId == null || groupId <= 0) ? intOf(current, "SolutionConfigGroupID") : groupId;
        if (target != intOf(current, "SolutionConfigGroupID")) {
            Integer to = repo.locate("group", target);
            if (to == null || to != solutionId) throw new SolutionRuleException("A property can only move to a group of the same solution.");
        }
        repo.saveProperty(propertyId, target,
            limit(SolutionService.keep(key, strOf(current, "PropertyKey"), true), KEY_MAX, "A property name"),
            limit(SolutionService.keep(description, strOf(current, "PropertyDescription"), false), TEXT_MAX, "A description"),
            SolutionService.keep(typeCode, strOf(current, "TypeCode"), true), sortOrder, caller.memberCode());
        return assemble(solutionId);
    }

    public Config deleteProperty(SolutionCaller caller, int propertyId) {
        int solutionId = require(caller, "property", propertyId, "Property");
        if (!repo.deleteProperty(propertyId)) throw new SolutionNotFoundException("Property");
        return assemble(solutionId);
    }

    // ---- a cell -------------------------------------------------------------------

    /**
     * Set one property's value in one environment. A merge: a field left out (null) keeps what the cell
     * has; an empty string or an empty list clears it. A cell left with nothing in it is removed.
     */
    public Config setValue(SolutionCaller caller, int propertyId, int environmentId, String value, String source, String note,
                           List<String> alsoSetIn) {
        int solutionId = require(caller, "property", propertyId, "Property");
        if (require(caller, "environment", environmentId, "Environment") != solutionId)
            throw new SolutionRuleException("That environment belongs to a different solution.");
        Map<String, Object> current = repo.values(solutionId).stream()
            .filter(r -> intOf(r, "SolutionConfigPropertyID") == propertyId && intOf(r, "SolutionConfigEnvironmentID") == environmentId)
            .findFirst().orElse(null);
        List<String> currentAlso = new ArrayList<>();
        if (current != null)
            for (Map<String, Object> a : repo.alsoSet(solutionId))
                if (intOf(a, "SolutionConfigValueID") == intOf(current, "SolutionConfigValueID")) currentAlso.add(strOf(a, "SourceTitle"));
        List<String> also = alsoSetIn == null ? currentAlso : alsoSetIn;
        for (String a : also) {
            if (a != null && a.contains("|")) throw new IllegalArgumentException("A place name cannot contain |.");
            limit(a, SOURCE_MAX, "A place name");
        }
        repo.saveValue(propertyId, environmentId,
            limit(value != null ? value : current == null ? null : strOf(current, "ValueText"), TEXT_MAX, "A value"),
            limit(source != null ? source : current == null ? null : strOf(current, "SourceTitle"), SOURCE_MAX, "Where it is read from"),
            limit(note != null ? note : current == null ? null : strOf(current, "ValueNote"), TEXT_MAX, "A note"),
            also, caller.memberCode());
        return assemble(solutionId);
    }

    /** Refuse a value longer than its column, with a message that names the field (never a SQL truncation 500). */
    private static String limit(String v, int max, String what) {
        if (v != null && v.trim().length() > max)
            throw new IllegalArgumentException(what + " may be at most " + max + " characters (this one is " + v.trim().length() + ").");
        return v;
    }
}
