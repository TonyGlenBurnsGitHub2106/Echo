package com.echo;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The file categories (Story 3520), the stp_FileCategory_* procs. LeonardoWeb's
 * JdbcFileFilingRepository, category half only (Story 5653): Echo files only solution files.
 */
@Repository
public class FileFilingRepository {

    private final JdbcTemplate jdbc;

    public FileFilingRepository(DataSource ds) { this.jdbc = new JdbcTemplate(ds); }

    public List<Map<String, Object>> listCategories() {
        return jdbc.queryForList("EXEC dbo.stp_FileCategory_List");
    }

    public Map<String, Object> saveCategory(int id, String name, String colorHex, Integer sortOrder) {
        try {
            return jdbc.queryForMap(
                "EXEC dbo.stp_FileCategory_Save @prmFileCategoryID=?, @prmCategoryName=?, @prmColorHex=?, @prmSortOrder=?",
                id, name, colorHex, sortOrder);
        } catch (DataAccessException ex) {
            throw asRule(ex);
        }
    }

    public Map<String, Object> findOrCreateCategory(String name) {
        try {
            return jdbc.queryForMap("EXEC dbo.stp_FileCategory_FindOrCreate @prmCategoryName=?", name);
        } catch (DataAccessException ex) {
            throw asRule(ex);
        }
    }

    public int deleteCategory(int id) {
        Integer n = jdbc.query("EXEC dbo.stp_FileCategory_Delete @prmFileCategoryID=?",
            rs -> rs.next() ? rs.getInt("FilesReturned") : 0, id);
        return n == null ? 0 : n;
    }

    public List<Map<String, Object>> reorderCategories(List<Integer> ids) {
        return jdbc.queryForList("EXEC dbo.stp_FileCategory_Reorder @prmFileCategoryIDs=?", csv(ids));
    }

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
