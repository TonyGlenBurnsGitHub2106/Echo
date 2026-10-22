package com.echo;

import java.util.List;
import java.util.Map;

/**
 * Storage for a solution's Configuration II tab (Story 5611). Reads hand back result rows (column-name maps)
 * so ONE set of mappers in {@link SolutionConfigGridModels} serves both implementations. Jdbc = the
 * stp_SolutionConfigGrid* procs; demo = {@link InMemorySolutionConfigGridRepository}. The tab is shown whole,
 * so none of these reads is paged.
 */
public interface SolutionConfigGridRepository {

    /** kind = axis | axisvalue | column | group | property. The owning solution id, or null when it does not exist. */
    Integer locate(String kind, int id);

    List<Map<String, Object>> axes(int solutionId);

    List<Map<String, Object>> axisValues(int solutionId);

    List<Map<String, Object>> columns(int solutionId);

    /** SolutionConfigGridColumnID, SolutionConfigGridAxisID, SolutionConfigGridAxisValueID, ValueTitle. */
    List<Map<String, Object>> tags(int solutionId);

    List<Map<String, Object>> groups(int solutionId);

    /** With TypeCode, TypeName, IsSecret. */
    List<Map<String, Object>> properties(int solutionId);

    /** The filled-in cells, with SourceTitle and StateCode. */
    List<Map<String, Object>> values(int solutionId);

    /** SolutionConfigGridValueID + SourceTitle. */
    List<Map<String, Object>> alsoSet(int solutionId);

    List<Map<String, Object>> types();

    List<Map<String, Object>> cellStates();

    /** The "where it is set" names in use on this solution's grid. */
    List<String> sources(int solutionId);

    int saveAxis(int id, int solutionId, String title, Integer sortOrder);

    boolean deleteAxis(int id);

    int saveAxisValue(int id, int axisId, String title, String color, Integer sortOrder);

    boolean deleteAxisValue(int id);

    int saveColumn(int id, int solutionId, String title, String color, boolean isDefault, Integer sortOrder);

    boolean deleteColumn(int id);

    /** By name; the axis and value are found or created on the column's solution. A blank value removes the tag. */
    void setTag(int columnId, String axisTitle, String valueTitle);

    int saveGroup(int id, int solutionId, String title, Integer sortOrder);

    boolean deleteGroup(int id);

    int saveProperty(int id, int groupId, String key, String description, String typeCode, Integer sortOrder, String modifiedBy);

    boolean deleteProperty(int id);

    /** Set one cell; a cell with nothing in it is removed. alsoSetIn is joined with | for the proc. */
    void saveValue(int propertyId, int columnId, String value, String source, String note, String state, List<String> alsoSetIn, String memberCode);

    /** Copy the Configuration tab into this empty tab. Returns ColumnCount, GroupCount, PropertyCount, ValueCount. */
    Map<String, Object> importFromConfig(int solutionId);
}
