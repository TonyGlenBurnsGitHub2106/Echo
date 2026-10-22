package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.util.Map;

/** The stp_AppUser_* procs (Story 5653). */
@Repository
public class AppUserRepository {

    private final SolutionJdbc db;

    public AppUserRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    AppUser byUsername(String username) {
        return db.one("EXEC dbo.stp_AppUser_GetForLogin @prmUsername = ?", AppUserRepository::of, username);
    }

    AppUser byId(int id) {
        return db.one("EXEC dbo.stp_AppUser_GetForLogin @prmAppUserID = ?", AppUserRepository::of, id);
    }

    void recordLogin(int id, boolean succeeded) {
        db.number("EXEC dbo.stp_AppUser_RecordLogin ?, ?", "RowsAffected", id, succeeded);
    }

    int save(String username, String displayName, String passwordHash) {
        return db.number("EXEC dbo.stp_AppUser_Save ?, ?, ?", "AppUserID", username, displayName, passwordHash);
    }

    void endSessions(int id) { db.number("EXEC dbo.stp_AppUser_EndSessions ?", "RowsAffected", id); }

    private static AppUser of(Map<String, Object> r) {
        Timestamp locked = (Timestamp) r.get("LockedUntil");
        return new AppUser(SolutionRows.intOf(r, "AppUserID"), SolutionRows.strOf(r, "Username"),
            SolutionRows.strOf(r, "DisplayName"), SolutionRows.strOf(r, "PasswordHash"),
            Boolean.TRUE.equals(r.get("IsActive")), Boolean.TRUE.equals(r.get("IsAdmin")),
            SolutionRows.intOf(r, "FailedAttempts"), locked == null ? null : locked.toLocalDateTime(),
            SolutionRows.intOf(r, "SessionGeneration"));
    }
}
