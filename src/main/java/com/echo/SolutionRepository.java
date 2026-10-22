package com.echo;

import com.echo.SolutionDrafts.ProjectDraft;
import com.echo.SolutionDrafts.SolutionDraft;
import com.echo.SolutionModels.Lookups;
import com.echo.SolutionModels.Project;
import com.echo.SolutionModels.Solution;

import java.util.List;

/**
 * Storage for solutions, their projects and the family's lookup sets (Story
 * 3493). {@link JdbcSolutionRepository} EXECs the stp_Solution* procs;
 * {@link InMemorySolutionRepository} is the demo store. Rule refusals surface as
 * {@link SolutionRuleException} from either.
 */
public interface SolutionRepository {

    Lookups lookups();

    /** @param apiClientId when not null, only the solutions that client may see */
    List<Solution> listSolutions(boolean includeArchived, Integer apiClientId);

    Solution findSolution(int id);

    int saveSolution(SolutionDraft draft, String modifiedBy);

    boolean deleteSolution(int id);

    /** Where the solution's NEXT attachment is saved. Refused (rule) while the solution is shared. */
    boolean setSaveFilesToRepo(int id, boolean saveToRepo, String modifiedBy);

    List<Project> listProjects(int solutionId);

    Project findProject(int id);

    int saveProject(ProjectDraft draft, String modifiedBy);

    boolean deleteProject(int id);

    int saveMember(int id, String code, String name, boolean agent, boolean active, int sortOrder);

    /** The note kind with that name (or code), made at the end of the order if nobody has used it. Returns its id. */
    int findOrCreateNoteType(String name);

    /** As {@link #findOrCreateNoteType}, for a log entry's kind. */
    int findOrCreateLogType(String name);

    /** The note kinds in the order given; the listed kinds trade the places they hold, the rest keep theirs. */
    void reorderNoteTypes(List<Integer> ids);
}
