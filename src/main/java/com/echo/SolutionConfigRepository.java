package com.echo;

import java.util.List;
import java.util.Map;

/**
 * Storage for a solution's Configuration tab (Story 3516). Reads hand back result rows (column-name
 * maps) so ONE set of mappers in {@link SolutionConfigModels} serves both implementations. Jdbc = the
 * stp_SolutionConfig* procs; demo = {@link InMemorySolutionConfigRepository}. The tab is shown whole,
 * so none of these reads is paged.
 */
public interface SolutionConfigRepository {

    /** kind = environment | group | property. The owning solution id, or null when it does not exist. */
    Integer locate(String kind, int id);

    List<Map<String, Object>> environments(int solutionId);

    List<Map<String, Object>> groups(int solutionId);

    /** With TypeCode, TypeName, IsSecret, MatchState, SetCount. */
    List<Map<String, Object>> properties(int solutionId);

    /** The filled-in cells, with SourceTitle. */
    List<Map<String, Object>> values(int solutionId);

    /** SolutionConfigValueID + SourceTitle: the other places each cell is set. */
    List<Map<String, Object>> alsoSet(int solutionId);

    /** "properties", "state:x", "group:g", "groupstate:g.x". */
    Map<String, Integer> counts(int solutionId);

    List<Map<String, Object>> types();

    /** The "where it is set" names in use on this solution. */
    List<String> sources(int solutionId);

    /** Local, QA, Production -- only when the solution has no environments yet. Returns how many were added. */
    int seedDefaultEnvironments(int solutionId);

    int saveEnvironment(int id, int solutionId, String title, Integer sortOrder);

    boolean deleteEnvironment(int id);

    int saveGroup(int id, int solutionId, String title, Integer sortOrder);

    boolean deleteGroup(int id);

    int saveProperty(int id, int groupId, String key, String description, String typeCode, Integer sortOrder, String modifiedBy);

    boolean deleteProperty(int id);

    /** Set one cell; a cell with nothing in it is removed. alsoSetIn is joined with | for the proc. */
    void saveValue(int propertyId, int environmentId, String value, String source, String note, List<String> alsoSetIn, String memberCode);
}
