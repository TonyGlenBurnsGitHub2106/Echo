package com.echo;

import com.echo.SolutionLifecycleModels.CloseOut;
import com.echo.SolutionLifecycleModels.OpenThing;
import com.echo.SolutionLifecycleModels.Shape;
import com.echo.SolutionLifecycleModels.ShapeResult;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The start and the end of a solution (Story 5695, Echo first, Release 6). Anyone who can see the solution may
 * give it a shape or read its close-out; copying another solution's shape needs that one to be visible too.
 */
@Service
public class SolutionLifecycleService {

    private final SolutionLifecycleRepository repo;
    private final SolutionAccess              access;

    public SolutionLifecycleService(SolutionLifecycleRepository repo, SolutionAccess access) {
        this.repo = repo; this.access = access;
    }

    public List<Shape> shapes() { return SolutionLifecycleModels.SHAPES; }

    /** A new, empty solution takes a shape: one of the starting frames, or another solution's skeleton. */
    public ShapeResult applyShape(SolutionCaller caller, int solutionId, String shape, Integer fromSolutionId) {
        access.requireSolution(caller, solutionId);
        String s = shape == null ? "" : shape.trim().toLowerCase();
        if (!SolutionLifecycleModels.isShape(s))
            throw new IllegalArgumentException("shape must be one of programming, writing, life, copy.");
        Integer from = null;
        if (s.equals("copy")) {
            if (fromSolutionId == null || fromSolutionId <= 0)
                throw new IllegalArgumentException("Say which solution to copy the shape of (fromSolutionId).");
            access.requireSolution(caller, fromSolutionId);
            from = fromSolutionId;
        }
        return repo.applyShape(solutionId, s, from, caller.memberCode());
    }

    /** Everything still open, by group, before the solution is called done. */
    public CloseOut closeOut(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        List<OpenThing> open = repo.closeOut(solutionId);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String g : SolutionLifecycleModels.GROUPS) counts.put(g, 0);
        for (OpenThing t : open) counts.merge(t.group(), 1, Integer::sum);
        return new CloseOut(open, counts, open.size());
    }
}
