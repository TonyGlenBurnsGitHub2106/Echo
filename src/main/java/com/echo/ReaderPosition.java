package com.echo;

import java.time.LocalDateTime;

/**
 * Where the reader stopped reading one document. Row in dbo.ReaderPosition
 * (Story 2422).
 *
 * <p>{@code docKey} is the same document identity the marks use
 * ({@link ReaderHighlight}), so a document's place and its highlights travel
 * together and moving a Reading Nook file between buckets loses neither. A
 * document has exactly one place you stopped -- saving a new one replaces it.
 *
 * <p>The place is recorded three ways, most precise first, because a document
 * can be re-rendered differently from one visit to the next:
 *
 * <ul>
 *   <li>{@code blockIndex} + {@code charOffset} -- the paragraph and character
 *       reached in the reflowed render. Exact, and used when the render matches.</li>
 *   <li>{@code pageNumber} -- the PDF page reached, when there was one.</li>
 *   <li>{@code scrollRatio} -- how far down the document (0..1). The fallback
 *       that always works, including in Fidelity view.</li>
 * </ul>
 *
 * <p>{@code percentRead} is the same journey expressed for a human ("46%"),
 * stored so the readout is right the instant a document opens rather than only
 * after it has finished rendering.
 */
public class ReaderPosition {

    private int           id;
    private String        docKey;
    private String        docTitle;
    private String        viewMode;
    private int           blockIndex;
    private int           charOffset;
    private Integer       pageNumber;
    private double        scrollRatio;
    private int           percentRead;
    private LocalDateTime createdDate;
    private LocalDateTime modifiedDate;

    public ReaderPosition() {}

    public int getId()                           { return id; }
    public void setId(int v)                     { this.id = v; }
    public String getDocKey()                    { return docKey; }
    public void setDocKey(String v)              { this.docKey = v; }
    public String getDocTitle()                  { return docTitle; }
    public void setDocTitle(String v)            { this.docTitle = v; }
    public String getViewMode()                  { return viewMode; }
    public void setViewMode(String v)            { this.viewMode = v; }
    public int getBlockIndex()                   { return blockIndex; }
    public void setBlockIndex(int v)             { this.blockIndex = v; }
    public int getCharOffset()                   { return charOffset; }
    public void setCharOffset(int v)             { this.charOffset = v; }
    public Integer getPageNumber()               { return pageNumber; }
    public void setPageNumber(Integer v)         { this.pageNumber = v; }
    public double getScrollRatio()               { return scrollRatio; }
    public void setScrollRatio(double v)         { this.scrollRatio = v; }
    public int getPercentRead()                  { return percentRead; }
    public void setPercentRead(int v)            { this.percentRead = v; }
    public LocalDateTime getCreatedDate()        { return createdDate; }
    public void setCreatedDate(LocalDateTime v)  { this.createdDate = v; }
    public LocalDateTime getModifiedDate()       { return modifiedDate; }
    public void setModifiedDate(LocalDateTime v) { this.modifiedDate = v; }
}
