package com.echo;

import com.echo.SolutionTopicKindModels.OpenItem;
import com.echo.SolutionTopicKindModels.TopicType;

import java.util.List;

/**
 * Storage for topic kinds (Story 5695): the kinds of part of a solution (stp_SolutionTopicType*), the order of
 * its parts (stp_SolutionTopic_Reorder) and the open items linked to a topic (stp_SolutionTopic_OpenItems).
 */
public interface SolutionTopicKindRepository {

    List<TopicType> types(int solutionId);

    TopicType findType(int typeId);

    /** typeId 0 = find by name or add; else rename and / or change what its number counts. */
    int saveType(int typeId, int solutionId, String name, String figureLabel);

    boolean deleteType(int typeId);

    /** The topics named take SortOrder 1, 2, 3 ... in the order given. */
    void reorder(int solutionId, List<Integer> topicIds);

    List<OpenItem> openItems(int topicId);
}
