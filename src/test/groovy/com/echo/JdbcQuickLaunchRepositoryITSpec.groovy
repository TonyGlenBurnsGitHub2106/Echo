package com.echo

import org.springframework.dao.DataAccessException
import org.springframework.jdbc.core.JdbcTemplate

import spock.lang.PendingFeature

// Story 5653: JdbcQuickLaunchRepository against a real EchoTest_ database (stp_QuickLaunch_*).
class JdbcQuickLaunchRepositoryITSpec extends EchoDbITSpec {

    JdbcQuickLaunchRepository repo

    def setup() {
        repo = new JdbcQuickLaunchRepository(new JdbcTemplate(dataSource()))
    }

    def "save should store every field and place the pin 10 after the last when a pin is added"() {

        given:
        int max = count('SELECT ISNULL(MAX(SortOrder), 0) FROM dbo.QuickLaunch')

        when:
        int id = repo.save(0, 'IT Pin', '/solutions/index.html?id=1', 'S')
        QuickLaunch q = pinOf(repo, id)

        then:
        id > 0
        q.label == 'IT Pin'
        q.url == '/solutions/index.html?id=1'
        q.iconKey == 'S'
        q.sortOrder == max + 10
        q.createdUtc != null
        q.modifiedUtc != null
    }

    def "list should read a null icon back as an empty string when a pin has no icon"() {

        when:
        int id = repo.save(0, 'No icon', '/x', null)

        then:
        pinOf(repo, id).iconKey == ''
    }

    def "save should change label, url and icon and keep the id and sort order when a pin is updated"() {

        given:
        int id = repo.save(0, 'Before', '/before', 'B')
        int sort = pinOf(repo, id).sortOrder

        when:
        int again = repo.save(id, 'After', '/after', 'A')
        QuickLaunch q = pinOf(repo, id)

        then:
        again == id
        q.label == 'After'
        q.url == '/after'
        q.iconKey == 'A'
        q.sortOrder == sort
    }

    def "setSortOrder should move a pin and list should order by sort order then id when pins share a sort order"() {

        given:
        int a = repo.save(0, 'A', '/a', null)
        int b = repo.save(0, 'B', '/b', null)
        int c = repo.save(0, 'C', '/c', null)

        when:
        repo.setSortOrder(c, -1000)
        repo.setSortOrder(a, -999)
        repo.setSortOrder(b, -999)
        List<Integer> ids = repo.list()*.quickLaunchId

        then:
        ids.take(3) == [c, a, b]
        pinOf(repo, c).sortOrder == -1000
    }

    def "delete should remove a pin and stay silent when the id is gone or unknown"() {

        given:
        int id = repo.save(0, 'Gone', '/gone', null)

        when:
        repo.delete(id)
        repo.delete(id)
        repo.setSortOrder(-1, 5)

        then:
        repo.list().every { it.quickLaunchId != id }
    }

    def "save should fail with a database error when the label is null"() {

        when:
        repo.save(0, null, '/x', null)

        then:
        thrown(DataAccessException)
    }

    @PendingFeature(reason = 'BUG: stp_QuickLaunch_Save with an id that does not exist updates nothing but still returns that id, so the caller is told the pin was saved')
    def "save should report that nothing was saved when the id does not exist"() {

        expect:
        repo.save(987654321, 'Ghost', '/ghost', null) == 0
    }

    @PendingFeature(reason = 'BUG: QuickLaunch has no QuickLaunch_LOG audit table or triggers, unlike every other Echo table')
    def "save should audit the pin in QuickLaunch_LOG when a pin is added"() {

        when:
        int id = repo.save(0, 'Audited', '/a', null)

        then:
        logRows('QuickLaunch', 'QuickLaunchID', id).size() == 1
    }

    private static QuickLaunch pinOf(JdbcQuickLaunchRepository repo, int id) {
        repo.list().find { QuickLaunch q -> q.quickLaunchId == id }
    }
}
