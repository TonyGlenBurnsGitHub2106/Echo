package com.echo;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuickLaunchService {

    private final QuickLaunchRepository repo;

    public QuickLaunchService(QuickLaunchRepository repo) { this.repo = repo; }

    public List<QuickLaunch> list() { return repo.list(); }

    public int save(int quickLaunchId, String label, String url, String iconKey) {
        String l = label == null ? "" : label.trim();
        String u = url == null ? "" : url.trim();
        String i = iconKey == null ? "" : iconKey.trim();
        if (i.length() > 8) i = i.substring(0, 8);
        return repo.save(quickLaunchId, l, u, i.isEmpty() ? null : i);
    }

    public void delete(int quickLaunchId) { repo.delete(quickLaunchId); }

    /** Re-numbers SortOrder for every passed ID using 10, 20, 30, ... in array order. */
    public void reorder(List<Integer> orderedIds) {
        if (orderedIds == null) return;
        int sort = 10;
        for (Integer id : orderedIds) {
            if (id != null && id > 0) {
                repo.setSortOrder(id, sort);
                sort += 10;
            }
        }
    }
}
