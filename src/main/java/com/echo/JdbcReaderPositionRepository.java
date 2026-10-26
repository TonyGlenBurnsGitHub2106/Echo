package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.util.List;

/**
 * SQL Server backed {@link ReaderPositionRepository}. All access goes through
 * the stp_ReaderPosition_* stored procedures. Story 2422.
 */
@Repository
public class JdbcReaderPositionRepository implements ReaderPositionRepository {

    private static final RowMapper<ReaderPosition> ROW = (ResultSet rs, int i) -> {
        ReaderPosition p = new ReaderPosition();
        p.setId(rs.getInt("ReaderPositionID"));
        p.setDocKey(rs.getString("DocKey"));
        p.setDocTitle(rs.getString("DocTitle"));
        p.setViewMode(rs.getString("ViewMode"));
        p.setBlockIndex(rs.getInt("BlockIndex"));
        p.setCharOffset(rs.getInt("CharOffset"));
        int page = rs.getInt("PageNumber");
        p.setPageNumber(rs.wasNull() ? null : Integer.valueOf(page));
        p.setScrollRatio(rs.getDouble("ScrollRatio"));
        p.setPercentRead(rs.getInt("PercentRead"));
        Timestamp c = rs.getTimestamp("CreatedDate");
        p.setCreatedDate(c == null ? null : c.toLocalDateTime());
        Timestamp m = rs.getTimestamp("ModifiedDate");
        p.setModifiedDate(m == null ? null : m.toLocalDateTime());
        return p;
    };

    private final JdbcTemplate jdbc;

    public JdbcReaderPositionRepository(DataSource ds) { this.jdbc = new JdbcTemplate(ds); }

    @Override
    public ReaderPosition find(String docKey) {
        List<ReaderPosition> found = jdbc.query("EXEC dbo.stp_ReaderPosition_Get ?", ROW, docKey);
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public ReaderPosition save(ReaderPosition d) {
        List<ReaderPosition> saved = jdbc.query(
            "EXEC dbo.stp_ReaderPosition_Save ?, ?, ?, ?, ?, ?, ?, ?", ROW,
            d.getDocKey(), d.getDocTitle(), d.getViewMode(),
            d.getBlockIndex(), d.getCharOffset(), d.getPageNumber(),
            d.getScrollRatio(), d.getPercentRead());
        return saved.isEmpty() ? null : saved.get(0);
    }

    @Override
    public void delete(String docKey) {
        jdbc.update("EXEC dbo.stp_ReaderPosition_Delete ?", docKey);
    }
}
