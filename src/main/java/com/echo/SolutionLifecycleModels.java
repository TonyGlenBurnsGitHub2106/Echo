package com.echo;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The start and the end of a solution (Story 5695, Echo first, Release 6): the shapes a new solution can start
 * from, and the close-out list of everything still open before it is called done.
 */
final class SolutionLifecycleModels {

    private SolutionLifecycleModels() {}

    /** A shape a new, empty solution can start from. */
    public record Shape(String code, String name, String description) {}

    static final List<Shape> SHAPES = List.of(
        new Shape("programming", "Programming effort",
            "Projects Design, Build, Test and Release. Environments Dev, QA and Production as parts. Tags Risk and Idea."),
        new Shape("writing", "Writing effort",
            "Projects Draft, Revise and Publish. Chapters (counting words) and Characters as kinds of part. Tags Idea and Cut."),
        new Shape("life", "Life matter",
            "Projects Gather the facts, Decide and Do it. Accounts (counting a balance) as a kind of part. Tags Important and Waiting on someone."),
        new Shape("copy", "The shape of another solution",
            "Its projects, kinds of part, parts, tags, term types and categories. None of its items, notes, log, files or terms."));

    static boolean isShape(String code) { return SHAPES.stream().anyMatch(s -> s.code().equals(code)); }

    /** What a shape made. */
    public record ShapeResult(int projects, int kindsOfPart, int parts, int tags, int termTypes, int termCategories) {
        static ShapeResult of(Map<String, Object> r) {
            return r == null ? new ShapeResult(0, 0, 0, 0, 0, 0)
                : new ShapeResult(intOf(r, "ProjectCount"), intOf(r, "KindOfPartCount"), intOf(r, "PartCount"), intOf(r, "TagCount"),
                                  intOf(r, "TermTypeCount"), intOf(r, "TermCategoryCount"));
        }
    }

    /**
     * One thing still open. group is project | item | question | decision | part | assumption | thread; kind and id
     * say how to open it (a part or an assumption is a topic; a thread is kind message, id its conversation).
     */
    public record OpenThing(String group, String kind, int id, String title, String sub, LocalDateTime changedDate) {
        static OpenThing of(Map<String, Object> r) {
            return new OpenThing(strOf(r, "GroupCode"), strOf(r, "Kind"), intOf(r, "RecordID"), strOf(r, "Title"), strOf(r, "Sub"),
                dateOf(r, "ChangedDate"));
        }
    }

    /** Everything still open, and how many of each group (every group named, zeros included). */
    public record CloseOut(List<OpenThing> open, Map<String, Integer> counts, int total) {}

    static final List<String> GROUPS = List.of("project", "item", "question", "decision", "part", "assumption", "thread");
}
