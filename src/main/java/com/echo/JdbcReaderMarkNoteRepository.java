package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

/**
 * SQL Server backed {@link ReaderMarkNoteRepository}. All access goes through
 * the stp_ReaderMarkNote_* stored procedures. Story 2411.
 */
@Repository
public class JdbcReaderMarkNoteRepository implements ReaderMarkNoteRepository {

    private static final RowMapper<ReaderMarkNote> ROW = (ResultSet rs, int i) -> {
        ReaderMarkNote n = new ReaderMarkNote();
        n.setId(rs.getInt("ReaderMarkNoteID"));
        n.setMarkId(rs.getInt("ReaderHighlightID"));
        n.setNoteText(rs.getString("NoteText"));
        Timestamp c = rs.getTimestamp("CreatedDate");
        n.setCreatedDate(c == null ? null : c.toLocalDateTime());
        Timestamp m = rs.getTimestamp("ModifiedDate");
        n.setModifiedDate(m == null ? null : m.toLocalDateTime());
        return n;
    };

    private final JdbcTemplate jdbc;

    public JdbcReaderMarkNoteRepository(DataSource ds) { this.jdbc = new JdbcTemplate(ds); }

    @Override
    public List<ReaderMarkNote> listForDocument(String docKey) {
        return jdbc.query("EXEC dbo.stp_ReaderMarkNote_ListForDocument ?", ROW, docKey);
    }

    @Override
    public ReaderMarkNote add(int markId, String noteText) {
        List<ReaderMarkNote> saved = jdbc.query(
            "EXEC dbo.stp_ReaderMarkNote_Add ?, ?", ROW, markId, noteText);
        return saved.isEmpty() ? null : saved.get(0);
    }

    @Override
    public ReaderMarkNote update(int noteId, String noteText) {
        List<ReaderMarkNote> saved = jdbc.query(
            "EXEC dbo.stp_ReaderMarkNote_Update ?, ?", ROW, noteId, noteText);
        return saved.isEmpty() ? null : saved.get(0);
    }

    @Override
    public void delete(int noteId) {
        jdbc.update("EXEC dbo.stp_ReaderMarkNote_Delete ?", noteId);
    }

    @Override
    public void deleteForMark(int markId) {
        // stp_ReaderHighlight_Delete already clears the mark's notes, so this is
        // belt and braces for the web path -- and the only cascade demo mode has.
        jdbc.update("EXEC dbo.stp_ReaderMarkNote_DeleteForMark ?", markId);
    }
}
