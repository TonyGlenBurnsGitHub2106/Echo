package com.echo;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * How the files on a solution are ORGANIZED (Story 5594): category or Staging, Important, document
 * date, and the grouped / sorted / searched list built from them. The organized Files tab of a
 * record (Story 3520) does the same for a record's documents; this is its twin for a solution.
 *
 * HOUSE-ONLY. Nothing behind the outside-agent door (/api/solutions) reads it: a category name is
 * house information. Jdbc = vw_SolutionFileFiled + stp_SolutionFile_ListFiled / _CountsFiled and
 * stp_SolutionFileFiling_Set / _SetImportant / _FileMany.
 */
public interface SolutionFileFilingRepository {

    /** One page, ordered by group then Important then the sort; rows carry GroupKey / GroupLabel / GroupColor. */
    List<Map<String, Object>> listFiled(int solutionId, String groupBy, String sortKey, boolean sortDesc,
                                        String search, int offset, int pageSize);

    /** One row per group under the same grouping and search: GroupKey, GroupLabel, GroupColor, Cnt, StagedCnt. */
    List<Map<String, Object>> countsFiled(int solutionId, String groupBy, String search);

    /** Category (null = Staging), document date and Important for one file; null when the file is not on the solution. */
    Map<String, Object> setFiling(int solutionId, int fileId, Integer categoryId, LocalDate documentDate,
                                  boolean important, String modifiedBy);

    /** Flag one file Important (or not); false when the file is not on the solution. */
    boolean setImportant(int solutionId, int fileId, boolean important, String modifiedBy);

    /** File several of the solution's files under one category (null = back to Staging); returns how many. */
    int fileMany(int solutionId, List<Integer> fileIds, Integer categoryId, String modifiedBy);
}
