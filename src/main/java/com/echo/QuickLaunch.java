package com.echo;

import java.time.LocalDateTime;

/**
 * One row from dbo.QuickLaunch. Single-user pinned-favorite on the
 * LeonardoWeb dashboard. SortOrder is server-managed; the UI never shows it.
 */
public class QuickLaunch {

    public int           quickLaunchId;
    public String        label;
    public String        url;
    public String        iconKey;       // optional emoji or single character; may be null/empty
    public int           sortOrder;
    public LocalDateTime createdUtc;
    public LocalDateTime modifiedUtc;
}
