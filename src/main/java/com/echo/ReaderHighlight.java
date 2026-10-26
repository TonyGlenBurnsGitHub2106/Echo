package com.echo;

import java.time.LocalDateTime;

/**
 * One stretch of text a reader marked in the Leonardo Reader. Row in
 * dbo.ReaderHighlight (Story 2307).
 *
 * <p>{@code docKey} identifies the document independently of where the file
 * currently sits, so moving a Reading Nook file between buckets does not orphan
 * its marks. The anchor is {@code blockIndex} (which paragraph of the reflowed
 * render) plus {@code startOffset} / {@code endOffset} (the character range
 * inside that paragraph); {@code selectedText} is kept alongside so the mark can
 * still be shown and re-verified if the render ever shifts.
 *
 * <p>{@code researchProjectId} / {@code researchNoteId} are set only when the
 * mark was also saved to the Research shelf as an excerpt; both are null for a
 * plain highlight.
 */
public class ReaderHighlight {

    private int           id;
    private String        docKey;
    private String        docTitle;
    private String        viewMode;
    private Integer       pageNumber;
    private int           blockIndex;
    private int           startOffset;
    private int           endOffset;
    private String        selectedText;
    private String        colorCode;
    private Integer       researchProjectId;
    private Integer       researchNoteId;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

    public ReaderHighlight() {}

    public int getId()                           { return id; }
    public void setId(int v)                     { this.id = v; }
    public String getDocKey()                    { return docKey; }
    public void setDocKey(String v)              { this.docKey = v; }
    public String getDocTitle()                  { return docTitle; }
    public void setDocTitle(String v)            { this.docTitle = v; }
    public String getViewMode()                  { return viewMode; }
    public void setViewMode(String v)            { this.viewMode = v; }
    public Integer getPageNumber()               { return pageNumber; }
    public void setPageNumber(Integer v)         { this.pageNumber = v; }
    public int getBlockIndex()                   { return blockIndex; }
    public void setBlockIndex(int v)             { this.blockIndex = v; }
    public int getStartOffset()                  { return startOffset; }
    public void setStartOffset(int v)            { this.startOffset = v; }
    public int getEndOffset()                    { return endOffset; }
    public void setEndOffset(int v)              { this.endOffset = v; }
    public String getSelectedText()              { return selectedText; }
    public void setSelectedText(String v)        { this.selectedText = v; }
    public String getColorCode()                 { return colorCode; }
    public void setColorCode(String v)           { this.colorCode = v; }
    public Integer getResearchProjectId()        { return researchProjectId; }
    public void setResearchProjectId(Integer v)  { this.researchProjectId = v; }
    public Integer getResearchNoteId()           { return researchNoteId; }
    public void setResearchNoteId(Integer v)     { this.researchNoteId = v; }
    public LocalDateTime getCreatedDate()        { return createdDate; }
    public void setCreatedDate(LocalDateTime v)  { this.createdDate = v; }
    public LocalDateTime getModifiedDate()       { return modifiedDate; }
    public void setModifiedDate(LocalDateTime v) { this.modifiedDate = v; }
}
