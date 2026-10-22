package com.echo;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * SQL Server backed {@link SolutionFileFilingRepository} (Story 5594). Every call EXECs a proc the
 * Leonardo desktop tree owns (SQL Procs Logic); no inline SQL. Schema: Schema_SolutionFileFiling.sql;
 * list shape: vw_SolutionFileFiled.
 */
@Repository
public class JdbcSolutionFileFilingRepository implements SolutionFileFilingRepository {

    private final JdbcTemplate jdbc;

    public JdbcSolutionFileFilingRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Override
    public List<Map<String, Object>> listFiled(int solutionId, String groupBy, String sortKey, boolean sortDesc,
                                               String search, int offset, int pageSize) {
        return jdbc.queryForList(
            "EXEC dbo.stp_SolutionFile_ListFiled @prmSolutionID=?, @prmGroupBy=?, @prmSortKey=?, @prmSortDesc=?, " +
                "@prmSearch=?, @prmOffset=?, @prmPageSize=?",
            solutionId, groupBy, sortKey, sortDesc, search, offset, pageSize);
    }

    @Override
    public List<Map<String, Object>> countsFiled(int solutionId, String groupBy, String search) {
        return jdbc.queryForList(
            "EXEC dbo.stp_SolutionFile_CountsFiled @prmSolutionID=?, @prmGroupBy=?, @prmSearch=?",
            solutionId, groupBy, search);
    }

    @Override
    public Map<String, Object> setFiling(int solutionId, int fileId, Integer categoryId, LocalDate documentDate,
                                         boolean important, String modifiedBy) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                "EXEC dbo.stp_SolutionFileFiling_Set @prmSolutionID=?, @prmSolutionFileID=?, @prmFileCategoryID=?, " +
                    "@prmDocumentDate=?, @prmIsImportant=?, @prmModifiedBy=?",
                solutionId, fileId, categoryId, documentDate == null ? null : Date.valueOf(documentDate),
                important, modifiedBy);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (DataAccessException ex) {
            throw asRule(ex);
        }
    }

    @Override
    public boolean setImportant(int solutionId, int fileId, boolean important, String modifiedBy) {
        Integer n = jdbc.query(
            "EXEC dbo.stp_SolutionFileFiling_SetImportant @prmSolutionID=?, @prmSolutionFileID=?, @prmIsImportant=?, " +
                "@prmModifiedBy=?",
            rs -> rs.next() ? rs.getInt("RowsAffected") : 0, solutionId, fileId, important, modifiedBy);
        return n != null && n > 0;
    }

    @Override
    public int fileMany(int solutionId, List<Integer> fileIds, Integer categoryId, String modifiedBy) {
        try {
            Integer n = jdbc.query(
                "EXEC dbo.stp_SolutionFileFiling_FileMany @prmSolutionID=?, @prmSolutionFileIDs=?, " +
                    "@prmFileCategoryID=?, @prmModifiedBy=?",
                rs -> rs.next() ? rs.getInt("Filed") : 0, solutionId, csv(fileIds), categoryId, modifiedBy);
            return n == null ? 0 : n;
        } catch (DataAccessException ex) {
            throw asRule(ex);
        }
    }

    /** A proc RAISERROR (error 50000) becomes an IllegalArgumentException carrying its text; anything else rethrows. */
    private static RuntimeException asRule(DataAccessException ex) {
        for (Throwable t = ex; t != null; t = t.getCause()) {
            if (t instanceof SQLException sql && sql.getErrorCode() == 50000) {
                return new IllegalArgumentException(sql.getMessage());
            }
        }
        return ex;
    }

    private static String csv(List<Integer> ids) {
        return ids == null ? "" : ids.stream().map(String::valueOf).collect(Collectors.joining(","));
    }
}
