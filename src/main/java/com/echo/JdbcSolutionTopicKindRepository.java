package com.echo;

import com.echo.SolutionTopicKindModels.OpenItem;
import com.echo.SolutionTopicKindModels.TopicType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.stream.Collectors;

/**
 * SQL Server backed {@link SolutionTopicKindRepository} (Story 5695).
 */
@Repository
public class JdbcSolutionTopicKindRepository implements SolutionTopicKindRepository {

    private final SolutionJdbc db;

    public JdbcSolutionTopicKindRepository(DataSource ds) { this.db = new SolutionJdbc(new JdbcTemplate(ds)); }

    @Override
    public List<TopicType> types(int solutionId) {
        return db.list("EXEC dbo.stp_SolutionTopicType_ListForSolution ?", TopicType::of, solutionId);
    }

    @Override
    public TopicType findType(int typeId) {
        return db.one("EXEC dbo.stp_SolutionTopicType_Get ?", TopicType::of, typeId);
    }

    @Override
    public int saveType(int typeId, int solutionId, String name, String figureLabel) {
        return db.number("EXEC dbo.stp_SolutionTopicType_Save ?, ?, ?, ?", "SolutionTopicTypeID", typeId, solutionId, name, figureLabel);
    }

    @Override
    public boolean deleteType(int typeId) {
        return db.affected("EXEC dbo.stp_SolutionTopicType_Delete ?", typeId);
    }

    @Override
    public void reorder(int solutionId, List<Integer> topicIds) {
        db.row("EXEC dbo.stp_SolutionTopic_Reorder ?, ?", solutionId,
            topicIds.stream().map(String::valueOf).collect(Collectors.joining(",")));
    }

    @Override
    public List<OpenItem> openItems(int topicId) {
        return db.list("EXEC dbo.stp_SolutionTopic_OpenItems ?", OpenItem::of, topicId);
    }
}
