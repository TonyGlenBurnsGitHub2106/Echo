package com.echo;

import com.echo.SolutionLifecycleModels.OpenThing;
import com.echo.SolutionLifecycleModels.ShapeResult;

import java.util.List;

/**
 * Storage for the start and the end of a solution (Story 5695 Release 6): stp_Solution_ApplyShape,
 * stp_Solution_CloseOutList.
 */
public interface SolutionLifecycleRepository {

    ShapeResult applyShape(int solutionId, String shapeCode, Integer fromSolutionId, String memberCode);

    List<OpenThing> closeOut(int solutionId);
}
