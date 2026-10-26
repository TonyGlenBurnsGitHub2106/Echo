package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;

/**
 * SQL Server backed {@link ReaderHighlightRepository}. All access goes through
 * the stp_ReaderHighlight_* stored procedures. Story 2307.
 */
@Repository
public class JdbcReaderHighlightRepository implements ReaderHighlightRepository {

    private static final RowMapper<ReaderHighlight> ROW = (ResultSet rs, int i) -> {
        ReaderHighlight h = new ReaderHighlight();
        h.setId(rs.getInt("ReaderHighlightID"));
        h.setDocKey(rs.getString("DocKey"));
        h.setDocTitle(rs.getString("DocTitle"));
        h.setViewMode(rs.getString("ViewMode"));
        h.setPageNumber(nullableInt(rs, "PageNumber"));
        h.setBlockIndex(rs.getInt("BlockIndex"));
        h.setStartOffset(rs.getInt("StartOffset"));
        h.setEndOffset(rs.getInt("EndOffset"));
        h.setSelectedText(rs.getString("SelectedText"));
        h.setColorCode(rs.getString("ColorCode"));
        h.setResearchProjectId(nullableInt(rs, "ResearchProjectID"));
        h.setResearchNoteId(nullableInt(rs, "ResearchNoteID"));
        Timestamp c = rs.getTimestamp("CreatedDate");
        h.setCreatedDate(c == null ? null : c.toLocalDateTime());
        Timestamp m = rs.getTimestamp("ModifiedDate");
        h.setModifiedDate(m == null ? null : m.toLocalDateTime());
        return h;
    };

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int v = rs.getInt(column);
        return rs.wasNull() ? null : Integer.valueOf(v);
    }

    private final JdbcTemplate jdbc;

    public JdbcReaderHighlightRepository(DataSource ds) { this.jdbc = new JdbcTemplate(ds); }

    @Override
    public List<ReaderHighlight> listForDocument(String docKey) {
        return jdbc.query("EXEC dbo.stp_ReaderHighlight_ListForDocument ?", ROW, docKey);
    }

    @Override
    public ReaderHighlight add(ReaderHighlight d) {
        List<ReaderHighlight> saved = jdbc.query(
            "EXEC dbo.stp_ReaderHighlight_Add ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?",
            ROW,
            d.getDocKey(), d.getDocTitle(), d.getViewMode(), d.getPageNumber(),
            d.getBlockIndex(), d.getStartOffset(), d.getEndOffset(),
            d.getSelectedText(), d.getColorCode(),
            d.getResearchProjectId(), d.getResearchNoteId());
        return saved.isEmpty() ? null : saved.get(0);
    }

    @Override
    public ReaderHighlight setColor(int id, String colorCode) {
        List<ReaderHighlight> saved = jdbc.query(
            "EXEC dbo.stp_ReaderHighlight_SetColor ?, ?", ROW, id, colorCode);
        return saved.isEmpty() ? null : saved.get(0);
    }

    @Override
    public void delete(int id) {
        jdbc.update("EXEC dbo.stp_ReaderHighlight_Delete ?", id);
    }
}
