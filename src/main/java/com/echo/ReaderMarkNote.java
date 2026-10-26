package com.echo;

import java.time.LocalDateTime;

/**
 * One note the reader wrote against a reading mark. Row in dbo.ReaderMarkNote
 * (Story 2411).
 *
 * <p>A "mark" is a row in {@link ReaderHighlight} -- either a highlight (a
 * coloured stretch of text) or a bookmark (a zero-length mark carrying the
 * reserved code "bookmark"). Notes hang off both, because the thing worth
 * writing down is just as often "why I stopped here" as "why this sentence
 * matters".
 *
 * <p>A mark may carry any number of notes; each can be rewritten or thrown away
 * on its own. {@code markId} is the {@link ReaderHighlight#getId()} the note
 * belongs to. There is no foreign key -- removing the mark clears its notes in
 * the mark's own delete proc.
 */
public class ReaderMarkNote {

    private int           id;
    private int           markId;
    private String        noteText;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

    public ReaderMarkNote() {}

    public int getId()                           { return id; }
    public void setId(int v)                     { this.id = v; }
    public int getMarkId()                       { return markId; }
    public void setMarkId(int v)                 { this.markId = v; }
    public String getNoteText()                  { return noteText; }
    public void setNoteText(String v)            { this.noteText = v; }
    public LocalDateTime getCreatedDate()        { return createdDate; }
    public void setCreatedDate(LocalDateTime v)  { this.createdDate = v; }
    public LocalDateTime getModifiedDate()       { return modifiedDate; }
    public void setModifiedDate(LocalDateTime v) { this.modifiedDate = v; }
}
