package com.echo;

import java.time.LocalDateTime;
import java.util.Map;

import static com.echo.SolutionRows.boolOf;
import static com.echo.SolutionRows.dateOf;
import static com.echo.SolutionRows.intOf;
import static com.echo.SolutionRows.strOf;

/**
 * The shapes of what an item waits on (Story 5695, Echo first). A wait has a direction -- item A waits on
 * item B -- so it is not a link (a link has none). From one item's side, "waitsOn" rows are what it waits
 * on and "blocks" rows are the items waiting on it.
 */
final class SolutionDependencyModels {

    private SolutionDependencyModels() {}

    /** One wait, seen from an item: the OTHER item, its status, and which way the wait runs. */
    public record Wait(int id, String direction, int itemId, String title, String projectName, String statusCode,
                       String statusName, String colorHex, boolean closed, LocalDateTime createdDate) {
        static Wait of(Map<String, Object> r) {
            return new Wait(intOf(r, "SolutionItemDependencyID"), strOf(r, "Direction"), intOf(r, "ItemID"), strOf(r, "ItemTitle"),
                strOf(r, "ProjectName"), strOf(r, "StatusCode"), strOf(r, "StatusName"), strOf(r, "ColorHex"),
                boolOf(r, "IsClosed"), dateOf(r, "CreatedDate"));
        }
    }

    /** One wait as stored, with its solution (for the access check). */
    public record StoredWait(int id, int itemId, int dependsOnItemId, int solutionId) {
        static StoredWait of(Map<String, Object> r) {
            return new StoredWait(intOf(r, "SolutionItemDependencyID"), intOf(r, "SolutionProjectItemID"),
                intOf(r, "DependsOnSolutionProjectItemID"), intOf(r, "SolutionID"));
        }
    }

    /** For the Items tab: an item that waits, how many it waits on, and how many of those are still open. */
    public record WaitCount(int itemId, int waits, int openWaits) {
        static WaitCount of(Map<String, Object> r) {
            return new WaitCount(intOf(r, "SolutionProjectItemID"), intOf(r, "WaitCount"), intOf(r, "OpenWaitCount"));
        }
    }
}
