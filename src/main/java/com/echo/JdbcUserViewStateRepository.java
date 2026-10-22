package com.echo;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.Map;

/** SQL Server backed {@link UserViewStateRepository}; EXECs the stp_UserViewState_* procs. */
@Repository
public class JdbcUserViewStateRepository implements UserViewStateRepository {

    private final JdbcTemplate jdbc;

    public JdbcUserViewStateRepository(DataSource ds) { this.jdbc = new JdbcTemplate(ds); }

    @Override
    public Map<String, String> list(int userId, String prefix) {
        Map<String, String> out = new LinkedHashMap<>();
        jdbc.query("EXEC dbo.stp_UserViewState_List ?, ?",
            rs -> { out.put(rs.getString("StateKey"), rs.getString("StateValue")); },
            userId, prefix);
        return out;
    }

    @Override
    public void save(int userId, String key, String value) {
        jdbc.queryForMap("EXEC dbo.stp_UserViewState_Save ?, ?, ?", userId, key, value);
    }
}
