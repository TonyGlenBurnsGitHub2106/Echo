package com.echo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Story 6852 (Daily Work): the shapes behind /api/daily-work. A quick log of what I did at work:
 * only {@code workDone} is required, everything else is optional.
 */
public final class DailyWorkModels {

    private DailyWorkModels() { }

    /** A team at work (the Team dropdown). */
    public record Team(int id, String name) { }

    /** A person at work, with the team they belong to (null when not known yet). */
    public record Contact(int id, String name, Integer teamId, String teamName) { }

    /** A kind of work (Jira, support call, meeting ...). */
    public record Kind(int id, String name) { }

    /** A contact as it hangs on an entry, in the order it was picked. */
    public record EntryContact(int id, String name) { }

    /**
     * One entry. {@code notes} is the full text on a single read and null in a list, where
     * {@code notesPreview} (the first 300 characters) and {@code notesCut} stand in for it.
     */
    public record Entry(int id, LocalDateTime workDate, String workDone, String storyRef,
                        Integer teamId, String teamName, Integer kindId, String kindName,
                        Integer minutesSpent, boolean followUp, String notes, String notesPreview,
                        boolean notesCut, List<EntryContact> contacts, List<String> links,
                        String createdBy, LocalDateTime createdDate, String modifiedBy,
                        LocalDateTime modifiedDate) { }

    /**
     * What the page sends to add or change an entry. A null workDate means "now". followUp is a
     * Boolean so a body that leaves it out still reads (Jackson 3 refuses a missing primitive);
     * null means false.
     */
    public record EntryDraft(LocalDateTime workDate, String workDone, String storyRef, Integer teamId,
                             Integer kindId, Integer minutesSpent, Boolean followUp, String notes,
                             List<Integer> contactIds, List<String> links) {

        public boolean isFollowUp() { return Boolean.TRUE.equals(followUp); }
    }

    /** The list's filters. Every field may be null (no filter); top is clamped by the proc. */
    public record Filter(LocalDate fromDate, LocalDate toDate, String text, Integer contactId,
                         Integer teamId, boolean followUpOnly, int top) { }

    /** Everything the page's dropdowns need, in one call. */
    public record Lookups(List<Team> teams, List<Contact> contacts, List<Kind> kinds) { }
}
