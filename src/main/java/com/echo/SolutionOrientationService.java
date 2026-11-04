package com.echo;

import com.echo.SolutionOrientationModels.Activity;
import com.echo.SolutionOrientationModels.ActivityPage;
import com.echo.SolutionOrientationModels.LeftOff;
import com.echo.SolutionOrientationModels.Lens;
import com.echo.SolutionOrientationModels.LensItem;
import com.echo.SolutionOrientationModels.SummaryAge;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * "Where am I" in a solution (Story 5695, Echo first): how old "Where things stand" is, so a summary that
 * has stopped being true says so. Anyone who can see the solution may read it.
 */
@Service
public class SolutionOrientationService {

    private final SolutionOrientationRepository repo;
    private final SolutionAccess                access;

    public SolutionOrientationService(SolutionOrientationRepository repo, SolutionAccess access) {
        this.repo = repo; this.access = access;
    }

    public SummaryAge summaryAge(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return repo.summaryAge(solutionId);
    }

    /**
     * Release 4: the Now / Next / Waiting lens over the open items, for the caller (their items lead Now). With no
     * bucket, the first page of each; with one, a later page of it. counts holds every bucket's total.
     */
    public Lens lens(SolutionCaller caller, int solutionId, String bucket, int offset, int pageSize) {
        access.requireSolution(caller, solutionId);
        String b = bucket == null || bucket.isBlank() ? null : bucket.trim().toLowerCase();
        if (b != null && !SolutionOrientationModels.BUCKETS.contains(b))
            throw new IllegalArgumentException("The lens has three buckets: now, next and waiting.");
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 500), from = b == null ? 0 : Math.max(0, offset);
        List<LensItem> items = repo.lens(solutionId, caller.memberCode(), b, from, size,
            SolutionOrientationModels.SOON_DAYS, SolutionOrientationModels.IDLE_DAYS);
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String k : List.of("now", "waiting", "next")) counts.put(k, 0);
        for (LensItem i : items) counts.put(i.bucket(), i.totalInBucket());
        if (b != null) {                           // a later page of one bucket: the others' counts come from page one
            for (LensItem i : repo.lens(solutionId, caller.memberCode(), null, 0, 1,
                    SolutionOrientationModels.SOON_DAYS, SolutionOrientationModels.IDLE_DAYS)) counts.put(i.bucket(), i.totalInBucket());
        }
        return new Lens(items, counts, b, from, size);
    }

    /** Release 4: the caller's note for next time on this solution. */
    public LeftOff leftOff(SolutionCaller caller, int solutionId) {
        access.requireSolution(caller, solutionId);
        return repo.leftOff(solutionId, caller.memberCode());
    }

    /** Release 4: writes (or, blank, takes away) the caller's note for next time. */
    public LeftOff saveLeftOff(SolutionCaller caller, int solutionId, String note) {
        access.requireSolution(caller, solutionId);
        String n = note == null ? "" : note.trim();
        if (n.length() > 1000) throw new IllegalArgumentException("A note for next time can be at most 1,000 characters.");
        repo.saveLeftOff(solutionId, caller.memberCode(), n);
        return repo.leftOff(solutionId, caller.memberCode());
    }

    /**
     * Release 4: the activity feed, everyone's changes (the caller's own included), newest first, over the last
     * days (default 30, at most 3650), narrowed by who, kind and topic.
     */
    public ActivityPage feed(SolutionCaller caller, int solutionId, Integer days, String who, String kind, Integer topicId,
                             int offset, int pageSize) {
        access.requireSolution(caller, solutionId);
        int d = days == null || days <= 0 ? 30 : Math.min(days, 3650);
        LocalDateTime since = LocalDateTime.now().minusDays(d).withNano(0);
        String w = who == null || who.isBlank() ? null : who.trim().toLowerCase();
        String k = kind == null || kind.isBlank() ? null : kind.trim().toLowerCase();
        if (k != null && !k.equals("message") && !SolutionLinkModels.KINDS.contains(k))
            throw new IllegalArgumentException("kind must be one of item, note, log, file, term, topic, question, decision, message.");
        Integer t = topicId == null || topicId <= 0 ? null : topicId;
        int size = pageSize <= 0 ? 50 : Math.min(pageSize, 500), from = Math.max(0, offset);
        List<Activity> rows = repo.feed(solutionId, since, w, k, t, from, size);
        int total = rows.isEmpty() ? (from > 0 ? repo.feedTotal(solutionId, since, w, k, t) : 0) : repo.feedTotal(solutionId, since, w, k, t);
        return new ActivityPage(rows, since, from, size, total);
    }
}
