-- ---- ApiClient ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ApiClient]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ApiClient](
	[ApiClientID] [int] IDENTITY(1,1) NOT NULL,
	[ClientCode] [varchar](40) NOT NULL,
	[ClientName] [nvarchar](200) NOT NULL,
	[KeyHash] [char](64) NULL,
	[KeyHint] [varchar](8) NULL,
	[IsEnabled] [bit] NOT NULL,
	[AllowedPathPrefixes] [nvarchar](1000) NOT NULL,
	[ClientNotes] [nvarchar](1000) NULL,
	[LastUsedDate] [datetime] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_ApiClient] PRIMARY KEY CLUSTERED 
(
	[ApiClientID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[ApiClient]') AND name = N'UX_ApiClient_ClientCode')
CREATE UNIQUE NONCLUSTERED INDEX [UX_ApiClient_ClientCode] ON [dbo].[ApiClient]
(
	[ClientCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_ApiClient_IsEnabled]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[ApiClient] ADD  CONSTRAINT [DF_ApiClient_IsEnabled]  DEFAULT ((1)) FOR [IsEnabled]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_ApiClient_AllowedPathPrefixes]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[ApiClient] ADD  CONSTRAINT [DF_ApiClient_AllowedPathPrefixes]  DEFAULT ('/api/solutions/') FOR [AllowedPathPrefixes]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_ApiClient_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[ApiClient] ADD  CONSTRAINT [DF_ApiClient_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- ApiClient_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ApiClient_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ApiClient_LOG](
	[ApiClient_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[ApiClientID] [int] NULL,
	[ClientCode] [varchar](40) NULL,
	[ClientName] [nvarchar](200) NULL,
	[KeyHash] [char](64) NULL,
	[KeyHint] [varchar](8) NULL,
	[IsEnabled] [bit] NULL,
	[AllowedPathPrefixes] [nvarchar](1000) NULL,
	[ClientNotes] [nvarchar](1000) NULL,
	[LastUsedDate] [datetime] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_ApiClient_LOG] PRIMARY KEY CLUSTERED 
(
	[ApiClient_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_ApiClient_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[ApiClient_LOG] ADD  CONSTRAINT [DF_ApiClient_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_ApiClient_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[ApiClient_LOG] ADD  CONSTRAINT [DF_ApiClient_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- ApiClientAccessLog ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ApiClientAccessLog]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ApiClientAccessLog](
	[ApiClientAccessLogID] [int] IDENTITY(1,1) NOT NULL,
	[ApiClientID] [int] NULL,
	[ClientCode] [varchar](40) NULL,
	[RequestPath] [nvarchar](500) NOT NULL,
	[HttpMethod] [varchar](10) NOT NULL,
	[StatusCode] [int] NOT NULL,
	[IsAllowed] [bit] NOT NULL,
	[ClientIp] [varchar](64) NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_ApiClientAccessLog] PRIMARY KEY CLUSTERED 
(
	[ApiClientAccessLogID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[ApiClientAccessLog]') AND name = N'IX_ApiClientAccessLog_Client_Date')
CREATE NONCLUSTERED INDEX [IX_ApiClientAccessLog_Client_Date] ON [dbo].[ApiClientAccessLog]
(
	[ApiClientID] ASC,
	[CreatedDate] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_ApiClientAccessLog_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[ApiClientAccessLog] ADD  CONSTRAINT [DF_ApiClientAccessLog_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- Conversation ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Conversation]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[Conversation](
	[ConversationID] [int] IDENTITY(1,1) NOT NULL,
	[Title] [nvarchar](200) NOT NULL,
	[Status] [nvarchar](20) NOT NULL,
	[ConversationType] [nvarchar](50) NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ClosedDate] [datetime] NULL,
	[ClosedBy] [nvarchar](100) NULL,
	[LinkedEntityID] [int] NULL,
	[LinkedTaskID] [int] NULL,
	[LinkedChecklistID] [int] NULL,
	[ModifiedDate] [datetime] NULL,
	[LinkedStoryID] [int] NULL,
 CONSTRAINT [PK_Conversation] PRIMARY KEY CLUSTERED 
(
	[ConversationID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[Conversation]') AND name = N'IX_Conversation_Status_Modified')
CREATE NONCLUSTERED INDEX [IX_Conversation_Status_Modified] ON [dbo].[Conversation]
(
	[Status] ASC,
	[ModifiedDate] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Conversation_Status]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Conversation] ADD  CONSTRAINT [DF_Conversation_Status]  DEFAULT ('active') FOR [Status]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Conversation_Type]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Conversation] ADD  CONSTRAINT [DF_Conversation_Type]  DEFAULT ('general') FOR [ConversationType]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Conversation_Created]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Conversation] ADD  CONSTRAINT [DF_Conversation_Created]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- ConversationMessage ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ConversationMessage]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ConversationMessage](
	[ConversationMessageID] [int] IDENTITY(1,1) NOT NULL,
	[ConversationID] [int] NOT NULL,
	[Sender] [nvarchar](20) NOT NULL,
	[Body] [nvarchar](max) NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[AwaitingResponseFrom] [nvarchar](20) NULL,
	[TokensIn] [int] NULL,
	[TokensOut] [int] NULL,
	[DurationMs] [int] NULL,
	[ModelUsed] [nvarchar](50) NULL,
	[ErrorText] [nvarchar](max) NULL,
	[PickedUpAt] [datetime2](3) NULL,
 CONSTRAINT [PK_ConversationMessage] PRIMARY KEY CLUSTERED 
(
	[ConversationMessageID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[ConversationMessage]') AND name = N'IX_ConversationMessage_Awaiting')
CREATE NONCLUSTERED INDEX [IX_ConversationMessage_Awaiting] ON [dbo].[ConversationMessage]
(
	[AwaitingResponseFrom] ASC,
	[ConversationID] ASC,
	[ConversationMessageID] DESC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_ConversationMessage_Created]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[ConversationMessage] ADD  CONSTRAINT [DF_ConversationMessage_Created]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_ConversationMessage_Conversation]') AND parent_object_id = OBJECT_ID(N'[dbo].[ConversationMessage]'))
ALTER TABLE [dbo].[ConversationMessage]  WITH CHECK ADD  CONSTRAINT [FK_ConversationMessage_Conversation] FOREIGN KEY([ConversationID])
REFERENCES [dbo].[Conversation] ([ConversationID])
ON DELETE CASCADE
GO
IF  EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_ConversationMessage_Conversation]') AND parent_object_id = OBJECT_ID(N'[dbo].[ConversationMessage]'))
ALTER TABLE [dbo].[ConversationMessage] CHECK CONSTRAINT [FK_ConversationMessage_Conversation]
GO
-- ---- FileCategory ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[FileCategory]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[FileCategory](
	[FileCategoryID] [int] IDENTITY(1,1) NOT NULL,
	[CategoryName] [nvarchar](100) NOT NULL,
	[ColorHex] [varchar](7) NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_FileCategory] PRIMARY KEY CLUSTERED 
(
	[FileCategoryID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[FileCategory]') AND name = N'UX_FileCategory_CategoryName')
CREATE UNIQUE NONCLUSTERED INDEX [UX_FileCategory_CategoryName] ON [dbo].[FileCategory]
(
	[CategoryName] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_FileCategory_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[FileCategory] ADD  CONSTRAINT [DF_FileCategory_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_FileCategory_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[FileCategory] ADD  CONSTRAINT [DF_FileCategory_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- FileCategory_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[FileCategory_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[FileCategory_LOG](
	[FileCategory_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[FileCategoryID] [int] NULL,
	[CategoryName] [nvarchar](100) NULL,
	[ColorHex] [varchar](7) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_FileCategory_LOG] PRIMARY KEY CLUSTERED 
(
	[FileCategory_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_FileCategory_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[FileCategory_LOG] ADD  CONSTRAINT [DF_FileCategory_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_FileCategory_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[FileCategory_LOG] ADD  CONSTRAINT [DF_FileCategory_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- QuickLaunch ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[QuickLaunch]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[QuickLaunch](
	[QuickLaunchID] [int] IDENTITY(1,1) NOT NULL,
	[Label] [nvarchar](80) NOT NULL,
	[URL] [nvarchar](400) NOT NULL,
	[IconKey] [nvarchar](8) NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedUtc] [datetime2](7) NOT NULL,
	[ModifiedUtc] [datetime2](7) NULL,
 CONSTRAINT [PK_QuickLaunch] PRIMARY KEY CLUSTERED 
(
	[QuickLaunchID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[QuickLaunch]') AND name = N'IX_QuickLaunch_SortOrder')
CREATE NONCLUSTERED INDEX [IX_QuickLaunch_SortOrder] ON [dbo].[QuickLaunch]
(
	[SortOrder] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_QuickLaunch_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[QuickLaunch] ADD  CONSTRAINT [DF_QuickLaunch_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_QuickLaunch_Created]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[QuickLaunch] ADD  CONSTRAINT [DF_QuickLaunch_Created]  DEFAULT (sysutcdatetime()) FOR [CreatedUtc]
END

GO
-- ---- Solution ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Solution]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[Solution](
	[SolutionID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionName] [nvarchar](200) NOT NULL,
	[SolutionDescription] [nvarchar](max) NULL,
	[SolutionStatusID] [int] NOT NULL,
	[OwnerMemberID] [int] NULL,
	[StatusSummary] [nvarchar](2000) NULL,
	[TargetDate] [datetime] NULL,
	[IsArchived] [bit] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](20) NULL,
	[SaveFilesToRepo] [bit] NOT NULL,
	[SolutionAlias] [varchar](40) NULL,
	[EntityID] [int] NULL,
 CONSTRAINT [PK_Solution] PRIMARY KEY CLUSTERED 
(
	[SolutionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[Solution]') AND name = N'UX_Solution_EntityID')
CREATE UNIQUE NONCLUSTERED INDEX [UX_Solution_EntityID] ON [dbo].[Solution]
(
	[EntityID] ASC
)
WHERE ([EntityID] IS NOT NULL)
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[Solution]') AND name = N'UX_Solution_SolutionAlias')
CREATE UNIQUE NONCLUSTERED INDEX [UX_Solution_SolutionAlias] ON [dbo].[Solution]
(
	[SolutionAlias] ASC
)
WHERE ([SolutionAlias] IS NOT NULL)
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[Solution]') AND name = N'UX_Solution_SolutionName')
CREATE UNIQUE NONCLUSTERED INDEX [UX_Solution_SolutionName] ON [dbo].[Solution]
(
	[SolutionName] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Solution_IsArchived]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Solution] ADD  CONSTRAINT [DF_Solution_IsArchived]  DEFAULT ((0)) FOR [IsArchived]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Solution_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Solution] ADD  CONSTRAINT [DF_Solution_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Solution_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Solution] ADD  CONSTRAINT [DF_Solution_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Solution_SaveFilesToRepo]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Solution] ADD  CONSTRAINT [DF_Solution_SaveFilesToRepo]  DEFAULT ((0)) FOR [SaveFilesToRepo]
END

GO
-- ---- Solution_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[Solution_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[Solution_LOG](
	[Solution_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionID] [int] NULL,
	[SolutionName] [nvarchar](200) NULL,
	[SolutionDescription] [nvarchar](max) NULL,
	[SolutionStatusID] [int] NULL,
	[OwnerMemberID] [int] NULL,
	[StatusSummary] [nvarchar](2000) NULL,
	[TargetDate] [datetime] NULL,
	[IsArchived] [bit] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](20) NULL,
	[SaveFilesToRepo] [bit] NULL,
	[SolutionAlias] [varchar](40) NULL,
	[EntityID] [int] NULL,
 CONSTRAINT [PK_Solution_LOG] PRIMARY KEY CLUSTERED 
(
	[Solution_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Solution_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Solution_LOG] ADD  CONSTRAINT [DF_Solution_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_Solution_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[Solution_LOG] ADD  CONSTRAINT [DF_Solution_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionApiClient ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionApiClient]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionApiClient](
	[SolutionApiClientID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[ApiClientID] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionApiClient] PRIMARY KEY CLUSTERED 
(
	[SolutionApiClientID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionApiClient]') AND name = N'UX_SolutionApiClient_Pair')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionApiClient_Pair] ON [dbo].[SolutionApiClient]
(
	[SolutionID] ASC,
	[ApiClientID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionApiClient_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionApiClient] ADD  CONSTRAINT [DF_SolutionApiClient_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionApiClient_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionApiClient_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionApiClient_LOG](
	[SolutionApiClient_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionApiClientID] [int] NULL,
	[SolutionID] [int] NULL,
	[ApiClientID] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionApiClient_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionApiClient_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionApiClient_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionApiClient_LOG] ADD  CONSTRAINT [DF_SolutionApiClient_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionApiClient_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionApiClient_LOG] ADD  CONSTRAINT [DF_SolutionApiClient_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigEnvironment ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigEnvironment]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigEnvironment](
	[SolutionConfigEnvironmentID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[SolutionEnvironmentID] [int] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionConfigEnvironment] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigEnvironmentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigEnvironment]') AND name = N'UX_SolutionConfigEnvironment_Solution_Env')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigEnvironment_Solution_Env] ON [dbo].[SolutionConfigEnvironment]
(
	[SolutionID] ASC,
	[SolutionEnvironmentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigEnvironment_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigEnvironment] ADD  CONSTRAINT [DF_SolutionConfigEnvironment_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigEnvironment_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigEnvironment] ADD  CONSTRAINT [DF_SolutionConfigEnvironment_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigEnvironment_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigEnvironment_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigEnvironment_LOG](
	[SolutionConfigEnvironment_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigEnvironmentID] [int] NULL,
	[SolutionID] [int] NULL,
	[SolutionEnvironmentID] [int] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigEnvironment_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigEnvironment_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigEnvironment_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigEnvironment_LOG] ADD  CONSTRAINT [DF_SolutionConfigEnvironment_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigEnvironment_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigEnvironment_LOG] ADD  CONSTRAINT [DF_SolutionConfigEnvironment_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridAxis ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridAxis]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridAxis](
	[SolutionConfigGridAxisID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[AxisTitle] [nvarchar](100) NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridAxis] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridAxisID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridAxis]') AND name = N'UX_SolutionConfigGridAxis_Solution_Title')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridAxis_Solution_Title] ON [dbo].[SolutionConfigGridAxis]
(
	[SolutionID] ASC,
	[AxisTitle] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridAxis_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridAxis] ADD  CONSTRAINT [DF_SolutionConfigGridAxis_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridAxis_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridAxis] ADD  CONSTRAINT [DF_SolutionConfigGridAxis_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridAxis_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridAxis_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridAxis_LOG](
	[SolutionConfigGridAxis_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridAxisID] [int] NULL,
	[SolutionID] [int] NULL,
	[AxisTitle] [nvarchar](100) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridAxis_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridAxis_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridAxis_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridAxis_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridAxis_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridAxis_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridAxis_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridAxis_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridAxisValue ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridAxisValue]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridAxisValue](
	[SolutionConfigGridAxisValueID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionConfigGridAxisID] [int] NOT NULL,
	[ValueTitle] [nvarchar](100) NOT NULL,
	[ColorHex] [varchar](7) NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridAxisValue] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridAxisValueID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridAxisValue]') AND name = N'UX_SolutionConfigGridAxisValue_Axis_Title')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridAxisValue_Axis_Title] ON [dbo].[SolutionConfigGridAxisValue]
(
	[SolutionConfigGridAxisID] ASC,
	[ValueTitle] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridAxisValue_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridAxisValue] ADD  CONSTRAINT [DF_SolutionConfigGridAxisValue_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridAxisValue_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridAxisValue] ADD  CONSTRAINT [DF_SolutionConfigGridAxisValue_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridAxisValue_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridAxisValue_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridAxisValue_LOG](
	[SolutionConfigGridAxisValue_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridAxisValueID] [int] NULL,
	[SolutionConfigGridAxisID] [int] NULL,
	[ValueTitle] [nvarchar](100) NULL,
	[ColorHex] [varchar](7) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridAxisValue_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridAxisValue_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridAxisValue_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridAxisValue_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridAxisValue_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridAxisValue_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridAxisValue_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridAxisValue_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridCellState ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridCellState]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridCellState](
	[SolutionConfigGridCellStateID] [int] IDENTITY(1,1) NOT NULL,
	[StateCode] [varchar](20) NOT NULL,
	[StateName] [nvarchar](100) NOT NULL,
	[StateDescription] [nvarchar](400) NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionConfigGridCellState] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridCellStateID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridCellState]') AND name = N'UX_SolutionConfigGridCellState_StateCode')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridCellState_StateCode] ON [dbo].[SolutionConfigGridCellState]
(
	[StateCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridCellState_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridCellState] ADD  CONSTRAINT [DF_SolutionConfigGridCellState_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridCellState_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridCellState] ADD  CONSTRAINT [DF_SolutionConfigGridCellState_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridCellState_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridCellState_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridCellState_LOG](
	[SolutionConfigGridCellState_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridCellStateID] [int] NULL,
	[StateCode] [varchar](20) NULL,
	[StateName] [nvarchar](100) NULL,
	[StateDescription] [nvarchar](400) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridCellState_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridCellState_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridCellState_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridCellState_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridCellState_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridCellState_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridCellState_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridCellState_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridColumn ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridColumn]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridColumn](
	[SolutionConfigGridColumnID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[ColumnTitle] [nvarchar](100) NOT NULL,
	[ColorHex] [varchar](7) NULL,
	[IsDefaultColumn] [bit] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridColumn] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridColumnID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridColumn]') AND name = N'UX_SolutionConfigGridColumn_Solution_Default')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridColumn_Solution_Default] ON [dbo].[SolutionConfigGridColumn]
(
	[SolutionID] ASC
)
WHERE ([IsDefaultColumn]=(1))
WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridColumn]') AND name = N'UX_SolutionConfigGridColumn_Solution_Title')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridColumn_Solution_Title] ON [dbo].[SolutionConfigGridColumn]
(
	[SolutionID] ASC,
	[ColumnTitle] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridColumn_IsDefaultColumn]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridColumn] ADD  CONSTRAINT [DF_SolutionConfigGridColumn_IsDefaultColumn]  DEFAULT ((0)) FOR [IsDefaultColumn]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridColumn_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridColumn] ADD  CONSTRAINT [DF_SolutionConfigGridColumn_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridColumn_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridColumn] ADD  CONSTRAINT [DF_SolutionConfigGridColumn_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridColumn_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridColumn_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridColumn_LOG](
	[SolutionConfigGridColumn_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridColumnID] [int] NULL,
	[SolutionID] [int] NULL,
	[ColumnTitle] [nvarchar](100) NULL,
	[ColorHex] [varchar](7) NULL,
	[IsDefaultColumn] [bit] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridColumn_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridColumn_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridColumn_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridColumn_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridColumn_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridColumn_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridColumn_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridColumn_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridColumnTag ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridColumnTag]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridColumnTag](
	[SolutionConfigGridColumnTagID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionConfigGridColumnID] [int] NOT NULL,
	[SolutionConfigGridAxisID] [int] NOT NULL,
	[SolutionConfigGridAxisValueID] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionConfigGridColumnTag] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridColumnTagID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridColumnTag]') AND name = N'IX_SolutionConfigGridColumnTag_AxisValue')
CREATE NONCLUSTERED INDEX [IX_SolutionConfigGridColumnTag_AxisValue] ON [dbo].[SolutionConfigGridColumnTag]
(
	[SolutionConfigGridAxisValueID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridColumnTag]') AND name = N'UX_SolutionConfigGridColumnTag_Column_Axis')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridColumnTag_Column_Axis] ON [dbo].[SolutionConfigGridColumnTag]
(
	[SolutionConfigGridColumnID] ASC,
	[SolutionConfigGridAxisID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridColumnTag_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridColumnTag] ADD  CONSTRAINT [DF_SolutionConfigGridColumnTag_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridColumnTag_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridColumnTag_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridColumnTag_LOG](
	[SolutionConfigGridColumnTag_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridColumnTagID] [int] NULL,
	[SolutionConfigGridColumnID] [int] NULL,
	[SolutionConfigGridAxisID] [int] NULL,
	[SolutionConfigGridAxisValueID] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridColumnTag_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridColumnTag_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridColumnTag_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridColumnTag_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridColumnTag_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridColumnTag_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridColumnTag_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridColumnTag_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridGroup ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridGroup]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridGroup](
	[SolutionConfigGridGroupID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[GroupTitle] [nvarchar](200) NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridGroup] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridGroupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridGroup]') AND name = N'IX_SolutionConfigGridGroup_Solution')
CREATE NONCLUSTERED INDEX [IX_SolutionConfigGridGroup_Solution] ON [dbo].[SolutionConfigGridGroup]
(
	[SolutionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridGroup_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridGroup] ADD  CONSTRAINT [DF_SolutionConfigGridGroup_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridGroup_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridGroup] ADD  CONSTRAINT [DF_SolutionConfigGridGroup_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridGroup_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridGroup_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridGroup_LOG](
	[SolutionConfigGridGroup_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridGroupID] [int] NULL,
	[SolutionID] [int] NULL,
	[GroupTitle] [nvarchar](200) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridGroup_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridGroup_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridGroup_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridGroup_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridGroup_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridGroup_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridGroup_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridGroup_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridProperty ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridProperty]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridProperty](
	[SolutionConfigGridPropertyID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[SolutionConfigGridGroupID] [int] NOT NULL,
	[PropertyKey] [nvarchar](200) NOT NULL,
	[PropertyDescription] [nvarchar](2000) NULL,
	[SolutionConfigTypeID] [int] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](40) NULL,
 CONSTRAINT [PK_SolutionConfigGridProperty] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridPropertyID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridProperty]') AND name = N'IX_SolutionConfigGridProperty_Group')
CREATE NONCLUSTERED INDEX [IX_SolutionConfigGridProperty_Group] ON [dbo].[SolutionConfigGridProperty]
(
	[SolutionConfigGridGroupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridProperty]') AND name = N'UX_SolutionConfigGridProperty_Solution_Key')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridProperty_Solution_Key] ON [dbo].[SolutionConfigGridProperty]
(
	[SolutionID] ASC,
	[PropertyKey] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridProperty_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridProperty] ADD  CONSTRAINT [DF_SolutionConfigGridProperty_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridProperty_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridProperty] ADD  CONSTRAINT [DF_SolutionConfigGridProperty_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridProperty_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridProperty_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridProperty_LOG](
	[SolutionConfigGridProperty_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridPropertyID] [int] NULL,
	[SolutionID] [int] NULL,
	[SolutionConfigGridGroupID] [int] NULL,
	[PropertyKey] [nvarchar](200) NULL,
	[PropertyDescription] [nvarchar](2000) NULL,
	[SolutionConfigTypeID] [int] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](40) NULL,
 CONSTRAINT [PK_SolutionConfigGridProperty_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridProperty_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridProperty_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridProperty_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridProperty_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridProperty_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridProperty_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridProperty_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridValue ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridValue]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridValue](
	[SolutionConfigGridValueID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionConfigGridPropertyID] [int] NOT NULL,
	[SolutionConfigGridColumnID] [int] NOT NULL,
	[ValueText] [nvarchar](2000) NULL,
	[SolutionConfigSourceID] [int] NULL,
	[ValueNote] [nvarchar](2000) NULL,
	[SolutionConfigGridCellStateID] [int] NOT NULL,
	[ModifiedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridValue] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridValueID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridValue]') AND name = N'IX_SolutionConfigGridValue_Column')
CREATE NONCLUSTERED INDEX [IX_SolutionConfigGridValue_Column] ON [dbo].[SolutionConfigGridValue]
(
	[SolutionConfigGridColumnID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridValue]') AND name = N'UX_SolutionConfigGridValue_Property_Column')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridValue_Property_Column] ON [dbo].[SolutionConfigGridValue]
(
	[SolutionConfigGridPropertyID] ASC,
	[SolutionConfigGridColumnID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridValue_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridValue] ADD  CONSTRAINT [DF_SolutionConfigGridValue_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridValue_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridValue_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridValue_LOG](
	[SolutionConfigGridValue_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridValueID] [int] NULL,
	[SolutionConfigGridPropertyID] [int] NULL,
	[SolutionConfigGridColumnID] [int] NULL,
	[ValueText] [nvarchar](2000) NULL,
	[SolutionConfigSourceID] [int] NULL,
	[ValueNote] [nvarchar](2000) NULL,
	[SolutionConfigGridCellStateID] [int] NULL,
	[ModifiedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridValue_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridValue_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridValue_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridValue_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridValue_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridValue_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridValue_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridValue_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGridValueAlsoSet ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridValueAlsoSet]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridValueAlsoSet](
	[SolutionConfigGridValueAlsoSetID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionConfigGridValueID] [int] NOT NULL,
	[SolutionConfigSourceID] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionConfigGridValueAlsoSet] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridValueAlsoSetID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridValueAlsoSet]') AND name = N'UX_SolutionConfigGridValueAlsoSet_Value_Source')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigGridValueAlsoSet_Value_Source] ON [dbo].[SolutionConfigGridValueAlsoSet]
(
	[SolutionConfigGridValueID] ASC,
	[SolutionConfigSourceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridValueAlsoSet_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridValueAlsoSet] ADD  CONSTRAINT [DF_SolutionConfigGridValueAlsoSet_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGridValueAlsoSet_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGridValueAlsoSet_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGridValueAlsoSet_LOG](
	[SolutionConfigGridValueAlsoSet_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGridValueAlsoSetID] [int] NULL,
	[SolutionConfigGridValueID] [int] NULL,
	[SolutionConfigSourceID] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGridValueAlsoSet_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGridValueAlsoSet_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridValueAlsoSet_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridValueAlsoSet_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridValueAlsoSet_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGridValueAlsoSet_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGridValueAlsoSet_LOG] ADD  CONSTRAINT [DF_SolutionConfigGridValueAlsoSet_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigGroup ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGroup]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGroup](
	[SolutionConfigGroupID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[GroupTitle] [nvarchar](200) NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGroup] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGroupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGroup]') AND name = N'IX_SolutionConfigGroup_Solution')
CREATE NONCLUSTERED INDEX [IX_SolutionConfigGroup_Solution] ON [dbo].[SolutionConfigGroup]
(
	[SolutionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGroup_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGroup] ADD  CONSTRAINT [DF_SolutionConfigGroup_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGroup_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGroup] ADD  CONSTRAINT [DF_SolutionConfigGroup_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigGroup_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigGroup_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigGroup_LOG](
	[SolutionConfigGroup_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigGroupID] [int] NULL,
	[SolutionID] [int] NULL,
	[GroupTitle] [nvarchar](200) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigGroup_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigGroup_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGroup_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGroup_LOG] ADD  CONSTRAINT [DF_SolutionConfigGroup_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigGroup_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigGroup_LOG] ADD  CONSTRAINT [DF_SolutionConfigGroup_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigProperty ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigProperty]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigProperty](
	[SolutionConfigPropertyID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionConfigGroupID] [int] NOT NULL,
	[PropertyKey] [nvarchar](200) NOT NULL,
	[PropertyDescription] [nvarchar](2000) NULL,
	[SolutionConfigTypeID] [int] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](40) NULL,
 CONSTRAINT [PK_SolutionConfigProperty] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigPropertyID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigProperty]') AND name = N'IX_SolutionConfigProperty_Group')
CREATE NONCLUSTERED INDEX [IX_SolutionConfigProperty_Group] ON [dbo].[SolutionConfigProperty]
(
	[SolutionConfigGroupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigProperty_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigProperty] ADD  CONSTRAINT [DF_SolutionConfigProperty_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigProperty_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigProperty] ADD  CONSTRAINT [DF_SolutionConfigProperty_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigProperty_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigProperty_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigProperty_LOG](
	[SolutionConfigProperty_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigPropertyID] [int] NULL,
	[SolutionConfigGroupID] [int] NULL,
	[PropertyKey] [nvarchar](200) NULL,
	[PropertyDescription] [nvarchar](2000) NULL,
	[SolutionConfigTypeID] [int] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](40) NULL,
 CONSTRAINT [PK_SolutionConfigProperty_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigProperty_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigProperty_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigProperty_LOG] ADD  CONSTRAINT [DF_SolutionConfigProperty_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigProperty_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigProperty_LOG] ADD  CONSTRAINT [DF_SolutionConfigProperty_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigSource ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigSource]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigSource](
	[SolutionConfigSourceID] [int] IDENTITY(1,1) NOT NULL,
	[SourceTitle] [nvarchar](200) NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionConfigSource] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigSourceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigSource]') AND name = N'UX_SolutionConfigSource_SourceTitle')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigSource_SourceTitle] ON [dbo].[SolutionConfigSource]
(
	[SourceTitle] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigSource_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigSource] ADD  CONSTRAINT [DF_SolutionConfigSource_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigSource_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigSource_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigSource_LOG](
	[SolutionConfigSource_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigSourceID] [int] NULL,
	[SourceTitle] [nvarchar](200) NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigSource_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigSource_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigSource_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigSource_LOG] ADD  CONSTRAINT [DF_SolutionConfigSource_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigSource_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigSource_LOG] ADD  CONSTRAINT [DF_SolutionConfigSource_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigType ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigType]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigType](
	[SolutionConfigTypeID] [int] IDENTITY(1,1) NOT NULL,
	[TypeCode] [varchar](20) NOT NULL,
	[TypeName] [nvarchar](100) NOT NULL,
	[IsSecret] [bit] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionConfigType] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigType]') AND name = N'UX_SolutionConfigType_TypeCode')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigType_TypeCode] ON [dbo].[SolutionConfigType]
(
	[TypeCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigType_IsSecret]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigType] ADD  CONSTRAINT [DF_SolutionConfigType_IsSecret]  DEFAULT ((0)) FOR [IsSecret]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigType_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigType] ADD  CONSTRAINT [DF_SolutionConfigType_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigType_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigType] ADD  CONSTRAINT [DF_SolutionConfigType_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigType_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigType_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigType_LOG](
	[SolutionConfigType_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigTypeID] [int] NULL,
	[TypeCode] [varchar](20) NULL,
	[TypeName] [nvarchar](100) NULL,
	[IsSecret] [bit] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigType_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigType_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigType_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigType_LOG] ADD  CONSTRAINT [DF_SolutionConfigType_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigType_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigType_LOG] ADD  CONSTRAINT [DF_SolutionConfigType_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigValue ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigValue]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigValue](
	[SolutionConfigValueID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionConfigPropertyID] [int] NOT NULL,
	[SolutionConfigEnvironmentID] [int] NOT NULL,
	[ValueText] [nvarchar](2000) NULL,
	[SolutionConfigSourceID] [int] NULL,
	[ValueNote] [nvarchar](2000) NULL,
	[ModifiedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigValue] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigValueID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigValue]') AND name = N'IX_SolutionConfigValue_Env')
CREATE NONCLUSTERED INDEX [IX_SolutionConfigValue_Env] ON [dbo].[SolutionConfigValue]
(
	[SolutionConfigEnvironmentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigValue]') AND name = N'UX_SolutionConfigValue_Property_Env')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigValue_Property_Env] ON [dbo].[SolutionConfigValue]
(
	[SolutionConfigPropertyID] ASC,
	[SolutionConfigEnvironmentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigValue_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigValue] ADD  CONSTRAINT [DF_SolutionConfigValue_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigValue_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigValue_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigValue_LOG](
	[SolutionConfigValue_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigValueID] [int] NULL,
	[SolutionConfigPropertyID] [int] NULL,
	[SolutionConfigEnvironmentID] [int] NULL,
	[ValueText] [nvarchar](2000) NULL,
	[SolutionConfigSourceID] [int] NULL,
	[ValueNote] [nvarchar](2000) NULL,
	[ModifiedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigValue_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigValue_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigValue_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigValue_LOG] ADD  CONSTRAINT [DF_SolutionConfigValue_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigValue_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigValue_LOG] ADD  CONSTRAINT [DF_SolutionConfigValue_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConfigValueAlsoSet ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigValueAlsoSet]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigValueAlsoSet](
	[SolutionConfigValueAlsoSetID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionConfigValueID] [int] NOT NULL,
	[SolutionConfigSourceID] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionConfigValueAlsoSet] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigValueAlsoSetID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigValueAlsoSet]') AND name = N'UX_SolutionConfigValueAlsoSet_Value_Source')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConfigValueAlsoSet_Value_Source] ON [dbo].[SolutionConfigValueAlsoSet]
(
	[SolutionConfigValueID] ASC,
	[SolutionConfigSourceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigValueAlsoSet_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigValueAlsoSet] ADD  CONSTRAINT [DF_SolutionConfigValueAlsoSet_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConfigValueAlsoSet_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConfigValueAlsoSet_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConfigValueAlsoSet_LOG](
	[SolutionConfigValueAlsoSet_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConfigValueAlsoSetID] [int] NULL,
	[SolutionConfigValueID] [int] NULL,
	[SolutionConfigSourceID] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConfigValueAlsoSet_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConfigValueAlsoSet_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigValueAlsoSet_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigValueAlsoSet_LOG] ADD  CONSTRAINT [DF_SolutionConfigValueAlsoSet_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConfigValueAlsoSet_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConfigValueAlsoSet_LOG] ADD  CONSTRAINT [DF_SolutionConfigValueAlsoSet_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionConversation ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConversation]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConversation](
	[SolutionConversationID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[ConversationID] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionConversation] PRIMARY KEY CLUSTERED 
(
	[SolutionConversationID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConversation]') AND name = N'UX_SolutionConversation_Pair')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionConversation_Pair] ON [dbo].[SolutionConversation]
(
	[SolutionID] ASC,
	[ConversationID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConversation_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConversation] ADD  CONSTRAINT [DF_SolutionConversation_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionConversation_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionConversation_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionConversation_LOG](
	[SolutionConversation_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionConversationID] [int] NULL,
	[SolutionID] [int] NULL,
	[ConversationID] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionConversation_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionConversation_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConversation_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConversation_LOG] ADD  CONSTRAINT [DF_SolutionConversation_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionConversation_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionConversation_LOG] ADD  CONSTRAINT [DF_SolutionConversation_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionEnvironment ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionEnvironment]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionEnvironment](
	[SolutionEnvironmentID] [int] IDENTITY(1,1) NOT NULL,
	[EnvironmentName] [nvarchar](100) NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionEnvironment] PRIMARY KEY CLUSTERED 
(
	[SolutionEnvironmentID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionEnvironment]') AND name = N'UX_SolutionEnvironment_EnvironmentName')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionEnvironment_EnvironmentName] ON [dbo].[SolutionEnvironment]
(
	[EnvironmentName] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionEnvironment_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionEnvironment] ADD  CONSTRAINT [DF_SolutionEnvironment_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionEnvironment_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionEnvironment_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionEnvironment_LOG](
	[SolutionEnvironment_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionEnvironmentID] [int] NULL,
	[EnvironmentName] [nvarchar](100) NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionEnvironment_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionEnvironment_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionEnvironment_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionEnvironment_LOG] ADD  CONSTRAINT [DF_SolutionEnvironment_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionEnvironment_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionEnvironment_LOG] ADD  CONSTRAINT [DF_SolutionEnvironment_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionFile ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionFile]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionFile](
	[SolutionFileID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[SolutionProjectItemID] [int] NULL,
	[FileTitle] [nvarchar](300) NOT NULL,
	[OriginalFileName] [nvarchar](300) NOT NULL,
	[StorageCode] [varchar](20) NOT NULL,
	[LeonardoFileID] [int] NULL,
	[StoredFileName] [nvarchar](400) NULL,
	[ContentType] [nvarchar](200) NULL,
	[FileSizeBytes] [bigint] NOT NULL,
	[FileNotes] [nvarchar](1000) NULL,
	[UploadedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionFile] PRIMARY KEY CLUSTERED 
(
	[SolutionFileID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionFile]') AND name = N'IX_SolutionFile_Solution')
CREATE NONCLUSTERED INDEX [IX_SolutionFile_Solution] ON [dbo].[SolutionFile]
(
	[SolutionID] ASC,
	[SolutionProjectItemID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionFile_FileSizeBytes]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionFile] ADD  CONSTRAINT [DF_SolutionFile_FileSizeBytes]  DEFAULT ((0)) FOR [FileSizeBytes]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionFile_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionFile] ADD  CONSTRAINT [DF_SolutionFile_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionFile_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionFile_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionFile_LOG](
	[SolutionFile_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionFileID] [int] NULL,
	[SolutionID] [int] NULL,
	[SolutionProjectItemID] [int] NULL,
	[FileTitle] [nvarchar](300) NULL,
	[OriginalFileName] [nvarchar](300) NULL,
	[StorageCode] [varchar](20) NULL,
	[LeonardoFileID] [int] NULL,
	[StoredFileName] [nvarchar](400) NULL,
	[ContentType] [nvarchar](200) NULL,
	[FileSizeBytes] [bigint] NULL,
	[FileNotes] [nvarchar](1000) NULL,
	[UploadedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionFile_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionFile_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionFile_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionFile_LOG] ADD  CONSTRAINT [DF_SolutionFile_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionFile_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionFile_LOG] ADD  CONSTRAINT [DF_SolutionFile_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionFileFiling ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionFileFiling]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionFileFiling](
	[SolutionFileFilingID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionFileID] [int] NOT NULL,
	[FileCategoryID] [int] NULL,
	[IsImportant] [bit] NOT NULL,
	[DocumentDate] [date] NULL,
	[FiledDate] [datetime] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](50) NULL,
 CONSTRAINT [PK_SolutionFileFiling] PRIMARY KEY CLUSTERED 
(
	[SolutionFileFilingID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionFileFiling]') AND name = N'UX_SolutionFileFiling_File')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionFileFiling_File] ON [dbo].[SolutionFileFiling]
(
	[SolutionFileID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionFileFiling_IsImportant]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionFileFiling] ADD  CONSTRAINT [DF_SolutionFileFiling_IsImportant]  DEFAULT ((0)) FOR [IsImportant]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionFileFiling_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionFileFiling] ADD  CONSTRAINT [DF_SolutionFileFiling_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionFileFiling_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionFileFiling_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionFileFiling_LOG](
	[SolutionFileFiling_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionFileFilingID] [int] NULL,
	[SolutionFileID] [int] NULL,
	[FileCategoryID] [int] NULL,
	[IsImportant] [bit] NULL,
	[DocumentDate] [date] NULL,
	[FiledDate] [datetime] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](50) NULL,
 CONSTRAINT [PK_SolutionFileFiling_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionFileFiling_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionFileFiling_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionFileFiling_LOG] ADD  CONSTRAINT [DF_SolutionFileFiling_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionFileFiling_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionFileFiling_LOG] ADD  CONSTRAINT [DF_SolutionFileFiling_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionLog ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionLog]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionLog](
	[SolutionLogID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[SolutionProjectID] [int] NULL,
	[SolutionProjectItemID] [int] NULL,
	[SequenceNumber] [int] NOT NULL,
	[SolutionLogTypeID] [int] NOT NULL,
	[AuthorMemberID] [int] NULL,
	[LogText] [nvarchar](max) NOT NULL,
	[LogDate] [datetime] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionLog] PRIMARY KEY CLUSTERED 
(
	[SolutionLogID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionLog]') AND name = N'IX_SolutionLog_Item')
CREATE NONCLUSTERED INDEX [IX_SolutionLog_Item] ON [dbo].[SolutionLog]
(
	[SolutionProjectItemID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionLog]') AND name = N'UX_SolutionLog_Solution_Seq')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionLog_Solution_Seq] ON [dbo].[SolutionLog]
(
	[SolutionID] ASC,
	[SequenceNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionLog_LogDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionLog] ADD  CONSTRAINT [DF_SolutionLog_LogDate]  DEFAULT (getdate()) FOR [LogDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionLog_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionLog] ADD  CONSTRAINT [DF_SolutionLog_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionLog_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionLog_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionLog_LOG](
	[SolutionLog_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionLogID] [int] NULL,
	[SolutionID] [int] NULL,
	[SolutionProjectID] [int] NULL,
	[SolutionProjectItemID] [int] NULL,
	[SequenceNumber] [int] NULL,
	[SolutionLogTypeID] [int] NULL,
	[AuthorMemberID] [int] NULL,
	[LogText] [nvarchar](max) NULL,
	[LogDate] [datetime] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionLog_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionLog_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionLog_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionLog_LOG] ADD  CONSTRAINT [DF_SolutionLog_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionLog_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionLog_LOG] ADD  CONSTRAINT [DF_SolutionLog_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionLogType ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionLogType]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionLogType](
	[SolutionLogTypeID] [int] IDENTITY(1,1) NOT NULL,
	[LogTypeCode] [varchar](20) NOT NULL,
	[LogTypeName] [nvarchar](100) NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionLogType] PRIMARY KEY CLUSTERED 
(
	[SolutionLogTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionLogType]') AND name = N'UX_SolutionLogType_LogTypeCode')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionLogType_LogTypeCode] ON [dbo].[SolutionLogType]
(
	[LogTypeCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionLogType_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionLogType] ADD  CONSTRAINT [DF_SolutionLogType_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionLogType_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionLogType] ADD  CONSTRAINT [DF_SolutionLogType_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionLogType_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionLogType_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionLogType_LOG](
	[SolutionLogType_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionLogTypeID] [int] NULL,
	[LogTypeCode] [varchar](20) NULL,
	[LogTypeName] [nvarchar](100) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionLogType_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionLogType_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionLogType_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionLogType_LOG] ADD  CONSTRAINT [DF_SolutionLogType_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionLogType_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionLogType_LOG] ADD  CONSTRAINT [DF_SolutionLogType_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionMember ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionMember]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionMember](
	[SolutionMemberID] [int] IDENTITY(1,1) NOT NULL,
	[MemberCode] [varchar](20) NOT NULL,
	[MemberName] [nvarchar](100) NOT NULL,
	[IsAgent] [bit] NOT NULL,
	[IsActive] [bit] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionMember] PRIMARY KEY CLUSTERED 
(
	[SolutionMemberID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionMember]') AND name = N'UX_SolutionMember_MemberCode')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionMember_MemberCode] ON [dbo].[SolutionMember]
(
	[MemberCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionMember_IsAgent]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionMember] ADD  CONSTRAINT [DF_SolutionMember_IsAgent]  DEFAULT ((0)) FOR [IsAgent]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionMember_IsActive]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionMember] ADD  CONSTRAINT [DF_SolutionMember_IsActive]  DEFAULT ((1)) FOR [IsActive]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionMember_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionMember] ADD  CONSTRAINT [DF_SolutionMember_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionMember_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionMember] ADD  CONSTRAINT [DF_SolutionMember_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionMember_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionMember_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionMember_LOG](
	[SolutionMember_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionMemberID] [int] NULL,
	[MemberCode] [varchar](20) NULL,
	[MemberName] [nvarchar](100) NULL,
	[IsAgent] [bit] NULL,
	[IsActive] [bit] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionMember_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionMember_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionMember_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionMember_LOG] ADD  CONSTRAINT [DF_SolutionMember_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionMember_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionMember_LOG] ADD  CONSTRAINT [DF_SolutionMember_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionNote ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNote]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionNote](
	[SolutionNoteID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[SolutionProjectID] [int] NULL,
	[SolutionNoteTypeID] [int] NOT NULL,
	[NoteTitle] [nvarchar](300) NOT NULL,
	[CurrentVersionNumber] [int] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](20) NULL,
 CONSTRAINT [PK_SolutionNote] PRIMARY KEY CLUSTERED 
(
	[SolutionNoteID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNote]') AND name = N'IX_SolutionNote_Solution')
CREATE NONCLUSTERED INDEX [IX_SolutionNote_Solution] ON [dbo].[SolutionNote]
(
	[SolutionID] ASC,
	[SolutionNoteTypeID] ASC,
	[SortOrder] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNote_CurrentVersionNumber]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNote] ADD  CONSTRAINT [DF_SolutionNote_CurrentVersionNumber]  DEFAULT ((0)) FOR [CurrentVersionNumber]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNote_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNote] ADD  CONSTRAINT [DF_SolutionNote_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNote_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNote] ADD  CONSTRAINT [DF_SolutionNote_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionNote_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNote_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionNote_LOG](
	[SolutionNote_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionNoteID] [int] NULL,
	[SolutionID] [int] NULL,
	[SolutionProjectID] [int] NULL,
	[SolutionNoteTypeID] [int] NULL,
	[NoteTitle] [nvarchar](300) NULL,
	[CurrentVersionNumber] [int] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](20) NULL,
 CONSTRAINT [PK_SolutionNote_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionNote_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNote_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNote_LOG] ADD  CONSTRAINT [DF_SolutionNote_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNote_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNote_LOG] ADD  CONSTRAINT [DF_SolutionNote_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionNoteStep ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteStep]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionNoteStep](
	[SolutionNoteStepID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionNoteID] [int] NOT NULL,
	[StepNumber] [int] NOT NULL,
	[StepTitle] [nvarchar](400) NOT NULL,
	[StepCommand] [nvarchar](max) NULL,
	[ExpectedResult] [nvarchar](max) NULL,
	[StepNotes] [nvarchar](max) NULL,
	[SourceSolutionLogID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionNoteStep] PRIMARY KEY CLUSTERED 
(
	[SolutionNoteStepID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteStep]') AND name = N'IX_SolutionNoteStep_Note')
CREATE NONCLUSTERED INDEX [IX_SolutionNoteStep_Note] ON [dbo].[SolutionNoteStep]
(
	[SolutionNoteID] ASC,
	[StepNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteStep_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteStep] ADD  CONSTRAINT [DF_SolutionNoteStep_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionNoteStep_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteStep_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionNoteStep_LOG](
	[SolutionNoteStep_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionNoteStepID] [int] NULL,
	[SolutionNoteID] [int] NULL,
	[StepNumber] [int] NULL,
	[StepTitle] [nvarchar](400) NULL,
	[StepCommand] [nvarchar](max) NULL,
	[ExpectedResult] [nvarchar](max) NULL,
	[StepNotes] [nvarchar](max) NULL,
	[SourceSolutionLogID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionNoteStep_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionNoteStep_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteStep_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteStep_LOG] ADD  CONSTRAINT [DF_SolutionNoteStep_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteStep_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteStep_LOG] ADD  CONSTRAINT [DF_SolutionNoteStep_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionNoteType ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteType]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionNoteType](
	[SolutionNoteTypeID] [int] IDENTITY(1,1) NOT NULL,
	[NoteTypeCode] [varchar](20) NOT NULL,
	[NoteTypeName] [nvarchar](100) NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionNoteType] PRIMARY KEY CLUSTERED 
(
	[SolutionNoteTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteType]') AND name = N'UX_SolutionNoteType_NoteTypeCode')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionNoteType_NoteTypeCode] ON [dbo].[SolutionNoteType]
(
	[NoteTypeCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteType_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteType] ADD  CONSTRAINT [DF_SolutionNoteType_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteType_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteType] ADD  CONSTRAINT [DF_SolutionNoteType_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionNoteType_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteType_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionNoteType_LOG](
	[SolutionNoteType_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionNoteTypeID] [int] NULL,
	[NoteTypeCode] [varchar](20) NULL,
	[NoteTypeName] [nvarchar](100) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionNoteType_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionNoteType_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteType_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteType_LOG] ADD  CONSTRAINT [DF_SolutionNoteType_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteType_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteType_LOG] ADD  CONSTRAINT [DF_SolutionNoteType_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionNoteVersion ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteVersion]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionNoteVersion](
	[SolutionNoteVersionID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionNoteID] [int] NOT NULL,
	[VersionNumber] [int] NOT NULL,
	[NoteBody] [nvarchar](max) NULL,
	[ChangeNote] [nvarchar](500) NULL,
	[AuthorMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionNoteVersion] PRIMARY KEY CLUSTERED 
(
	[SolutionNoteVersionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteVersion]') AND name = N'UX_SolutionNoteVersion_Note_Ver')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionNoteVersion_Note_Ver] ON [dbo].[SolutionNoteVersion]
(
	[SolutionNoteID] ASC,
	[VersionNumber] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteVersion_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteVersion] ADD  CONSTRAINT [DF_SolutionNoteVersion_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionNoteVersion_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionNoteVersion_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionNoteVersion_LOG](
	[SolutionNoteVersion_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionNoteVersionID] [int] NULL,
	[SolutionNoteID] [int] NULL,
	[VersionNumber] [int] NULL,
	[NoteBody] [nvarchar](max) NULL,
	[ChangeNote] [nvarchar](500) NULL,
	[AuthorMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionNoteVersion_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionNoteVersion_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteVersion_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteVersion_LOG] ADD  CONSTRAINT [DF_SolutionNoteVersion_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionNoteVersion_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionNoteVersion_LOG] ADD  CONSTRAINT [DF_SolutionNoteVersion_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionProject ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionProject]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionProject](
	[SolutionProjectID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[ProjectName] [nvarchar](200) NOT NULL,
	[ProjectDescription] [nvarchar](max) NULL,
	[SolutionStatusID] [int] NOT NULL,
	[OwnerMemberID] [int] NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](20) NULL,
 CONSTRAINT [PK_SolutionProject] PRIMARY KEY CLUSTERED 
(
	[SolutionProjectID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionProject]') AND name = N'UX_SolutionProject_Solution_Name')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionProject_Solution_Name] ON [dbo].[SolutionProject]
(
	[SolutionID] ASC,
	[ProjectName] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProject_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProject] ADD  CONSTRAINT [DF_SolutionProject_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProject_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProject] ADD  CONSTRAINT [DF_SolutionProject_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionProject_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionProject_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionProject_LOG](
	[SolutionProject_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionProjectID] [int] NULL,
	[SolutionID] [int] NULL,
	[ProjectName] [nvarchar](200) NULL,
	[ProjectDescription] [nvarchar](max) NULL,
	[SolutionStatusID] [int] NULL,
	[OwnerMemberID] [int] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](20) NULL,
 CONSTRAINT [PK_SolutionProject_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionProject_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProject_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProject_LOG] ADD  CONSTRAINT [DF_SolutionProject_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProject_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProject_LOG] ADD  CONSTRAINT [DF_SolutionProject_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionProjectItem ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionProjectItem]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionProjectItem](
	[SolutionProjectItemID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionProjectID] [int] NOT NULL,
	[ParentItemID] [int] NULL,
	[ItemTitle] [nvarchar](400) NOT NULL,
	[ItemDetail] [nvarchar](max) NULL,
	[SolutionStatusID] [int] NOT NULL,
	[AssigneeMemberID] [int] NULL,
	[Priority] [int] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[DueDate] [datetime] NULL,
	[CompletedDate] [datetime] NULL,
	[CreatedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](20) NULL,
 CONSTRAINT [PK_SolutionProjectItem] PRIMARY KEY CLUSTERED 
(
	[SolutionProjectItemID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionProjectItem]') AND name = N'IX_SolutionProjectItem_Project')
CREATE NONCLUSTERED INDEX [IX_SolutionProjectItem_Project] ON [dbo].[SolutionProjectItem]
(
	[SolutionProjectID] ASC,
	[ParentItemID] ASC,
	[SortOrder] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProjectItem_Priority]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProjectItem] ADD  CONSTRAINT [DF_SolutionProjectItem_Priority]  DEFAULT ((3)) FOR [Priority]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProjectItem_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProjectItem] ADD  CONSTRAINT [DF_SolutionProjectItem_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProjectItem_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProjectItem] ADD  CONSTRAINT [DF_SolutionProjectItem_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionProjectItem_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionProjectItem_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionProjectItem_LOG](
	[SolutionProjectItem_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionProjectItemID] [int] NULL,
	[SolutionProjectID] [int] NULL,
	[ParentItemID] [int] NULL,
	[ItemTitle] [nvarchar](400) NULL,
	[ItemDetail] [nvarchar](max) NULL,
	[SolutionStatusID] [int] NULL,
	[AssigneeMemberID] [int] NULL,
	[Priority] [int] NULL,
	[SortOrder] [int] NULL,
	[DueDate] [datetime] NULL,
	[CompletedDate] [datetime] NULL,
	[CreatedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](20) NULL,
 CONSTRAINT [PK_SolutionProjectItem_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionProjectItem_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProjectItem_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProjectItem_LOG] ADD  CONSTRAINT [DF_SolutionProjectItem_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionProjectItem_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionProjectItem_LOG] ADD  CONSTRAINT [DF_SolutionProjectItem_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionStatus ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionStatus]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionStatus](
	[SolutionStatusID] [int] IDENTITY(1,1) NOT NULL,
	[StatusCode] [varchar](20) NOT NULL,
	[StatusName] [nvarchar](100) NOT NULL,
	[IsClosed] [bit] NOT NULL,
	[ColorHex] [varchar](7) NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionStatus] PRIMARY KEY CLUSTERED 
(
	[SolutionStatusID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionStatus]') AND name = N'UX_SolutionStatus_StatusCode')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionStatus_StatusCode] ON [dbo].[SolutionStatus]
(
	[StatusCode] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionStatus_IsClosed]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionStatus] ADD  CONSTRAINT [DF_SolutionStatus_IsClosed]  DEFAULT ((0)) FOR [IsClosed]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionStatus_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionStatus] ADD  CONSTRAINT [DF_SolutionStatus_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionStatus_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionStatus] ADD  CONSTRAINT [DF_SolutionStatus_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionStatus_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionStatus_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionStatus_LOG](
	[SolutionStatus_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionStatusID] [int] NULL,
	[StatusCode] [varchar](20) NULL,
	[StatusName] [nvarchar](100) NULL,
	[IsClosed] [bit] NULL,
	[ColorHex] [varchar](7) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionStatus_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionStatus_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionStatus_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionStatus_LOG] ADD  CONSTRAINT [DF_SolutionStatus_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionStatus_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionStatus_LOG] ADD  CONSTRAINT [DF_SolutionStatus_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTerm ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTerm]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTerm](
	[SolutionTermID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[Term] [nvarchar](400) NOT NULL,
	[NormalizedTerm] [nvarchar](400) NOT NULL,
	[Definition] [nvarchar](max) NOT NULL,
	[SolutionTermTypeID] [int] NULL,
	[TermSource] [nvarchar](500) NULL,
	[TermNotes] [nvarchar](max) NULL,
	[IsFavorite] [bit] NOT NULL,
	[AuthorMemberID] [int] NULL,
	[ModifiedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTerm] PRIMARY KEY CLUSTERED 
(
	[SolutionTermID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTerm]') AND name = N'IX_SolutionTerm_Type')
CREATE NONCLUSTERED INDEX [IX_SolutionTerm_Type] ON [dbo].[SolutionTerm]
(
	[SolutionTermTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTerm]') AND name = N'UX_SolutionTerm_Term')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTerm_Term] ON [dbo].[SolutionTerm]
(
	[SolutionID] ASC,
	[NormalizedTerm] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTerm_IsFavorite]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTerm] ADD  CONSTRAINT [DF_SolutionTerm_IsFavorite]  DEFAULT ((0)) FOR [IsFavorite]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTerm_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTerm] ADD  CONSTRAINT [DF_SolutionTerm_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTerm_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTerm_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTerm_LOG](
	[SolutionTerm_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTermID] [int] NULL,
	[SolutionID] [int] NULL,
	[Term] [nvarchar](400) NULL,
	[NormalizedTerm] [nvarchar](400) NULL,
	[Definition] [nvarchar](max) NULL,
	[SolutionTermTypeID] [int] NULL,
	[TermSource] [nvarchar](500) NULL,
	[TermNotes] [nvarchar](max) NULL,
	[IsFavorite] [bit] NULL,
	[AuthorMemberID] [int] NULL,
	[ModifiedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTerm_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTerm_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTerm_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTerm_LOG] ADD  CONSTRAINT [DF_SolutionTerm_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTerm_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTerm_LOG] ADD  CONSTRAINT [DF_SolutionTerm_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTermCategory ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermCategory]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTermCategory](
	[SolutionTermCategoryID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[CategoryName] [nvarchar](200) NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTermCategory] PRIMARY KEY CLUSTERED 
(
	[SolutionTermCategoryID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermCategory]') AND name = N'UX_SolutionTermCategory_Name')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTermCategory_Name] ON [dbo].[SolutionTermCategory]
(
	[SolutionID] ASC,
	[CategoryName] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermCategory_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermCategory] ADD  CONSTRAINT [DF_SolutionTermCategory_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTermCategory_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermCategory_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTermCategory_LOG](
	[SolutionTermCategory_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTermCategoryID] [int] NULL,
	[SolutionID] [int] NULL,
	[CategoryName] [nvarchar](200) NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTermCategory_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTermCategory_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermCategory_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermCategory_LOG] ADD  CONSTRAINT [DF_SolutionTermCategory_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermCategory_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermCategory_LOG] ADD  CONSTRAINT [DF_SolutionTermCategory_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTermCategoryXRef ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermCategoryXRef]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTermCategoryXRef](
	[SolutionTermCategoryXRefID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionTermID] [int] NOT NULL,
	[SolutionTermCategoryID] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionTermCategoryXRef] PRIMARY KEY CLUSTERED 
(
	[SolutionTermCategoryXRefID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermCategoryXRef]') AND name = N'IX_SolutionTermCategoryXRef_Category')
CREATE NONCLUSTERED INDEX [IX_SolutionTermCategoryXRef_Category] ON [dbo].[SolutionTermCategoryXRef]
(
	[SolutionTermCategoryID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermCategoryXRef]') AND name = N'UX_SolutionTermCategoryXRef_Pair')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTermCategoryXRef_Pair] ON [dbo].[SolutionTermCategoryXRef]
(
	[SolutionTermID] ASC,
	[SolutionTermCategoryID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermCategoryXRef_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermCategoryXRef] ADD  CONSTRAINT [DF_SolutionTermCategoryXRef_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTermCategoryXRef_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermCategoryXRef_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTermCategoryXRef_LOG](
	[SolutionTermCategoryXRef_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTermCategoryXRefID] [int] NULL,
	[SolutionTermID] [int] NULL,
	[SolutionTermCategoryID] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTermCategoryXRef_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTermCategoryXRef_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermCategoryXRef_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermCategoryXRef_LOG] ADD  CONSTRAINT [DF_SolutionTermCategoryXRef_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermCategoryXRef_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermCategoryXRef_LOG] ADD  CONSTRAINT [DF_SolutionTermCategoryXRef_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTermType ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermType]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTermType](
	[SolutionTermTypeID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[TypeName] [nvarchar](200) NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTermType] PRIMARY KEY CLUSTERED 
(
	[SolutionTermTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermType]') AND name = N'UX_SolutionTermType_Name')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTermType_Name] ON [dbo].[SolutionTermType]
(
	[SolutionID] ASC,
	[TypeName] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermType_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermType] ADD  CONSTRAINT [DF_SolutionTermType_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermType_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermType] ADD  CONSTRAINT [DF_SolutionTermType_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTermType_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTermType_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTermType_LOG](
	[SolutionTermType_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTermTypeID] [int] NULL,
	[SolutionID] [int] NULL,
	[TypeName] [nvarchar](200) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTermType_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTermType_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermType_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermType_LOG] ADD  CONSTRAINT [DF_SolutionTermType_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTermType_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTermType_LOG] ADD  CONSTRAINT [DF_SolutionTermType_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTestCase ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestCase]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestCase](
	[SolutionTestCaseID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionTestGroupID] [int] NOT NULL,
	[CaseText] [nvarchar](1000) NOT NULL,
	[CaseNote] [nvarchar](2000) NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[IncludeInPrint] [bit] NOT NULL,
 CONSTRAINT [PK_SolutionTestCase] PRIMARY KEY CLUSTERED 
(
	[SolutionTestCaseID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestCase]') AND name = N'IX_SolutionTestCase_Group')
CREATE NONCLUSTERED INDEX [IX_SolutionTestCase_Group] ON [dbo].[SolutionTestCase]
(
	[SolutionTestGroupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCase_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCase] ADD  CONSTRAINT [DF_SolutionTestCase_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCase_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCase] ADD  CONSTRAINT [DF_SolutionTestCase_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCase_IncludeInPrint]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCase] ADD  CONSTRAINT [DF_SolutionTestCase_IncludeInPrint]  DEFAULT ((1)) FOR [IncludeInPrint]
END

GO
-- ---- SolutionTestCase_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestCase_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestCase_LOG](
	[SolutionTestCase_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTestCaseID] [int] NULL,
	[SolutionTestGroupID] [int] NULL,
	[CaseText] [nvarchar](1000) NULL,
	[CaseNote] [nvarchar](2000) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[IncludeInPrint] [bit] NULL,
 CONSTRAINT [PK_SolutionTestCase_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTestCase_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCase_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCase_LOG] ADD  CONSTRAINT [DF_SolutionTestCase_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCase_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCase_LOG] ADD  CONSTRAINT [DF_SolutionTestCase_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTestCheck ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestCheck]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestCheck](
	[SolutionTestCheckID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionTestCaseID] [int] NOT NULL,
	[SolutionTestPlanLevelID] [int] NOT NULL,
	[IsChecked] [bit] NOT NULL,
	[CheckedDate] [datetime] NULL,
	[CheckedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestCheck] PRIMARY KEY CLUSTERED 
(
	[SolutionTestCheckID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestCheck]') AND name = N'IX_SolutionTestCheck_PlanLevel')
CREATE NONCLUSTERED INDEX [IX_SolutionTestCheck_PlanLevel] ON [dbo].[SolutionTestCheck]
(
	[SolutionTestPlanLevelID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestCheck]') AND name = N'UX_SolutionTestCheck_Case_Level')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTestCheck_Case_Level] ON [dbo].[SolutionTestCheck]
(
	[SolutionTestCaseID] ASC,
	[SolutionTestPlanLevelID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCheck_IsChecked]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCheck] ADD  CONSTRAINT [DF_SolutionTestCheck_IsChecked]  DEFAULT ((0)) FOR [IsChecked]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCheck_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCheck] ADD  CONSTRAINT [DF_SolutionTestCheck_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTestCheck_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestCheck_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestCheck_LOG](
	[SolutionTestCheck_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTestCheckID] [int] NULL,
	[SolutionTestCaseID] [int] NULL,
	[SolutionTestPlanLevelID] [int] NULL,
	[IsChecked] [bit] NULL,
	[CheckedDate] [datetime] NULL,
	[CheckedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestCheck_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTestCheck_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCheck_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCheck_LOG] ADD  CONSTRAINT [DF_SolutionTestCheck_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestCheck_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestCheck_LOG] ADD  CONSTRAINT [DF_SolutionTestCheck_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTestGroup ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestGroup]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestGroup](
	[SolutionTestGroupID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionTestPlanID] [int] NOT NULL,
	[GroupTitle] [nvarchar](200) NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestGroup] PRIMARY KEY CLUSTERED 
(
	[SolutionTestGroupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestGroup]') AND name = N'IX_SolutionTestGroup_Plan')
CREATE NONCLUSTERED INDEX [IX_SolutionTestGroup_Plan] ON [dbo].[SolutionTestGroup]
(
	[SolutionTestPlanID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestGroup_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestGroup] ADD  CONSTRAINT [DF_SolutionTestGroup_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestGroup_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestGroup] ADD  CONSTRAINT [DF_SolutionTestGroup_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTestGroup_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestGroup_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestGroup_LOG](
	[SolutionTestGroup_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTestGroupID] [int] NULL,
	[SolutionTestPlanID] [int] NULL,
	[GroupTitle] [nvarchar](200) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestGroup_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTestGroup_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestGroup_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestGroup_LOG] ADD  CONSTRAINT [DF_SolutionTestGroup_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestGroup_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestGroup_LOG] ADD  CONSTRAINT [DF_SolutionTestGroup_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTestLevel ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestLevel]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestLevel](
	[SolutionTestLevelID] [int] IDENTITY(1,1) NOT NULL,
	[LevelTitle] [nvarchar](100) NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionTestLevel] PRIMARY KEY CLUSTERED 
(
	[SolutionTestLevelID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestLevel]') AND name = N'UX_SolutionTestLevel_LevelTitle')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTestLevel_LevelTitle] ON [dbo].[SolutionTestLevel]
(
	[LevelTitle] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestLevel_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestLevel] ADD  CONSTRAINT [DF_SolutionTestLevel_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTestLevel_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestLevel_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestLevel_LOG](
	[SolutionTestLevel_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTestLevelID] [int] NULL,
	[LevelTitle] [nvarchar](100) NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestLevel_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTestLevel_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestLevel_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestLevel_LOG] ADD  CONSTRAINT [DF_SolutionTestLevel_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestLevel_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestLevel_LOG] ADD  CONSTRAINT [DF_SolutionTestLevel_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTestPlan ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestPlan]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestPlan](
	[SolutionTestPlanID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionID] [int] NOT NULL,
	[PlanTitle] [nvarchar](200) NOT NULL,
	[PlanDescription] [nvarchar](2000) NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](40) NULL,
	[PlanKindCode] [varchar](10) NOT NULL,
 CONSTRAINT [PK_SolutionTestPlan] PRIMARY KEY CLUSTERED 
(
	[SolutionTestPlanID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestPlan]') AND name = N'IX_SolutionTestPlan_Solution')
CREATE NONCLUSTERED INDEX [IX_SolutionTestPlan_Solution] ON [dbo].[SolutionTestPlan]
(
	[SolutionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlan_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlan] ADD  CONSTRAINT [DF_SolutionTestPlan_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlan_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlan] ADD  CONSTRAINT [DF_SolutionTestPlan_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlan_PlanKindCode]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlan] ADD  CONSTRAINT [DF_SolutionTestPlan_PlanKindCode]  DEFAULT ('dev') FOR [PlanKindCode]
END

GO
IF NOT EXISTS (SELECT * FROM sys.check_constraints WHERE object_id = OBJECT_ID(N'[dbo].[CK_SolutionTestPlan_PlanKindCode]') AND parent_object_id = OBJECT_ID(N'[dbo].[SolutionTestPlan]'))
ALTER TABLE [dbo].[SolutionTestPlan]  WITH CHECK ADD  CONSTRAINT [CK_SolutionTestPlan_PlanKindCode] CHECK  (([PlanKindCode]='qa' OR [PlanKindCode]='dev'))
GO
IF  EXISTS (SELECT * FROM sys.check_constraints WHERE object_id = OBJECT_ID(N'[dbo].[CK_SolutionTestPlan_PlanKindCode]') AND parent_object_id = OBJECT_ID(N'[dbo].[SolutionTestPlan]'))
ALTER TABLE [dbo].[SolutionTestPlan] CHECK CONSTRAINT [CK_SolutionTestPlan_PlanKindCode]
GO
-- ---- SolutionTestPlan_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestPlan_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestPlan_LOG](
	[SolutionTestPlan_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTestPlanID] [int] NULL,
	[SolutionID] [int] NULL,
	[PlanTitle] [nvarchar](200) NULL,
	[PlanDescription] [nvarchar](2000) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
	[ModifiedBy] [varchar](40) NULL,
	[PlanKindCode] [varchar](10) NULL,
 CONSTRAINT [PK_SolutionTestPlan_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTestPlan_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlan_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlan_LOG] ADD  CONSTRAINT [DF_SolutionTestPlan_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlan_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlan_LOG] ADD  CONSTRAINT [DF_SolutionTestPlan_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTestPlanLevel ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestPlanLevel]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestPlanLevel](
	[SolutionTestPlanLevelID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionTestPlanID] [int] NOT NULL,
	[SolutionTestLevelID] [int] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_SolutionTestPlanLevel] PRIMARY KEY CLUSTERED 
(
	[SolutionTestPlanLevelID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestPlanLevel]') AND name = N'UX_SolutionTestPlanLevel_Plan_Level')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTestPlanLevel_Plan_Level] ON [dbo].[SolutionTestPlanLevel]
(
	[SolutionTestPlanID] ASC,
	[SolutionTestLevelID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlanLevel_SortOrder]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlanLevel] ADD  CONSTRAINT [DF_SolutionTestPlanLevel_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlanLevel_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlanLevel] ADD  CONSTRAINT [DF_SolutionTestPlanLevel_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTestPlanLevel_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestPlanLevel_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestPlanLevel_LOG](
	[SolutionTestPlanLevel_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTestPlanLevelID] [int] NULL,
	[SolutionTestPlanID] [int] NULL,
	[SolutionTestLevelID] [int] NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestPlanLevel_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTestPlanLevel_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlanLevel_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlanLevel_LOG] ADD  CONSTRAINT [DF_SolutionTestPlanLevel_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestPlanLevel_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestPlanLevel_LOG] ADD  CONSTRAINT [DF_SolutionTestPlanLevel_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTestResult ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestResult]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestResult](
	[SolutionTestResultID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionTestCaseID] [int] NOT NULL,
	[IsFailing] [bit] NOT NULL,
	[FailingDate] [datetime] NULL,
	[ResultNote] [nvarchar](4000) NULL,
	[ModifiedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestResult] PRIMARY KEY CLUSTERED 
(
	[SolutionTestResultID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestResult]') AND name = N'UX_SolutionTestResult_Case')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTestResult_Case] ON [dbo].[SolutionTestResult]
(
	[SolutionTestCaseID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestResult_IsFailing]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestResult] ADD  CONSTRAINT [DF_SolutionTestResult_IsFailing]  DEFAULT ((0)) FOR [IsFailing]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestResult_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestResult] ADD  CONSTRAINT [DF_SolutionTestResult_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTestResult_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestResult_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestResult_LOG](
	[SolutionTestResult_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTestResultID] [int] NULL,
	[SolutionTestCaseID] [int] NULL,
	[IsFailing] [bit] NULL,
	[FailingDate] [datetime] NULL,
	[ResultNote] [nvarchar](4000) NULL,
	[ModifiedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestResult_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTestResult_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestResult_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestResult_LOG] ADD  CONSTRAINT [DF_SolutionTestResult_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestResult_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestResult_LOG] ADD  CONSTRAINT [DF_SolutionTestResult_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- SolutionTestSkip ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestSkip]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestSkip](
	[SolutionTestSkipID] [int] IDENTITY(1,1) NOT NULL,
	[SolutionTestCaseID] [int] NOT NULL,
	[SolutionTestPlanLevelID] [int] NOT NULL,
	[IsSkipped] [bit] NOT NULL,
	[SkippedDate] [datetime] NULL,
	[SkippedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestSkip] PRIMARY KEY CLUSTERED 
(
	[SolutionTestSkipID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestSkip]') AND name = N'IX_SolutionTestSkip_Level')
CREATE NONCLUSTERED INDEX [IX_SolutionTestSkip_Level] ON [dbo].[SolutionTestSkip]
(
	[SolutionTestPlanLevelID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestSkip]') AND name = N'UX_SolutionTestSkip_Cell')
CREATE UNIQUE NONCLUSTERED INDEX [UX_SolutionTestSkip_Cell] ON [dbo].[SolutionTestSkip]
(
	[SolutionTestCaseID] ASC,
	[SolutionTestPlanLevelID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestSkip_IsSkipped]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestSkip] ADD  CONSTRAINT [DF_SolutionTestSkip_IsSkipped]  DEFAULT ((0)) FOR [IsSkipped]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestSkip_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestSkip] ADD  CONSTRAINT [DF_SolutionTestSkip_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- SolutionTestSkip_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[SolutionTestSkip_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[SolutionTestSkip_LOG](
	[SolutionTestSkip_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[SolutionTestSkipID] [int] NULL,
	[SolutionTestCaseID] [int] NULL,
	[SolutionTestPlanLevelID] [int] NULL,
	[IsSkipped] [bit] NULL,
	[SkippedDate] [datetime] NULL,
	[SkippedByMemberID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_SolutionTestSkip_LOG] PRIMARY KEY CLUSTERED 
(
	[SolutionTestSkip_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestSkip_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestSkip_LOG] ADD  CONSTRAINT [DF_SolutionTestSkip_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_SolutionTestSkip_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[SolutionTestSkip_LOG] ADD  CONSTRAINT [DF_SolutionTestSkip_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- UserViewState ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[UserViewState]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[UserViewState](
	[UserViewStateID] [int] IDENTITY(1,1) NOT NULL,
	[UserID] [int] NOT NULL,
	[StateKey] [nvarchar](200) NOT NULL,
	[StateValue] [nvarchar](4000) NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_UserViewState] PRIMARY KEY CLUSTERED 
(
	[UserViewStateID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[UserViewState]') AND name = N'UX_UserViewState_UserID_StateKey')
CREATE UNIQUE NONCLUSTERED INDEX [UX_UserViewState_UserID_StateKey] ON [dbo].[UserViewState]
(
	[UserID] ASC,
	[StateKey] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_UserViewState_CreatedDate]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[UserViewState] ADD  CONSTRAINT [DF_UserViewState_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- UserViewState_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[UserViewState_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[UserViewState_LOG](
	[UserViewState_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[UserViewStateID] [int] NULL,
	[UserID] [int] NULL,
	[StateKey] [nvarchar](200) NULL,
	[StateValue] [nvarchar](4000) NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_UserViewState_LOG] PRIMARY KEY CLUSTERED 
(
	[UserViewState_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_UserViewState_LOG_LCD]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[UserViewState_LOG] ADD  CONSTRAINT [DF_UserViewState_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[DF_UserViewState_LOG_INS]') AND type = 'D')
BEGIN
ALTER TABLE [dbo].[UserViewState_LOG] ADD  CONSTRAINT [DF_UserViewState_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ==== FOREIGN KEYS ====
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_ConversationMessage_Conversation]') AND parent_object_id = OBJECT_ID(N'[dbo].[ConversationMessage]'))
ALTER TABLE [dbo].[ConversationMessage]  WITH CHECK ADD  CONSTRAINT [FK_ConversationMessage_Conversation] FOREIGN KEY([ConversationID])
REFERENCES [dbo].[Conversation] ([ConversationID])
ON DELETE CASCADE
GO
IF  EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_ConversationMessage_Conversation]') AND parent_object_id = OBJECT_ID(N'[dbo].[ConversationMessage]'))
ALTER TABLE [dbo].[ConversationMessage] CHECK CONSTRAINT [FK_ConversationMessage_Conversation]
GO
-- ==== WEB TAB (Story 5653 parity, 2026-10-26) ====
-- Scripted from Leonardo: the tables behind a record's Web tab -- entities, web addresses, website types,
-- logins and the lookup registry -- each with its _LOG twin. FKs to tables Echo does not have are left out.
-- ---- EntityLoginSetupXRef ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[EntityLoginSetupXRef]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[EntityLoginSetupXRef](
	[EntityLoginSetupXRefID] [int] IDENTITY(1,1) NOT NULL,
	[EntityID] [int] NOT NULL,
	[LoginSetupID] [int] NOT NULL,
	[EffectiveDate] [datetime] NULL,
	[ThroughDate] [datetime] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_EntityLoginSetupXRef] PRIMARY KEY CLUSTERED 
(
	[EntityLoginSetupXRefID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[EntityLoginSetupXRef]') AND name = N'UX_EntityLoginSetupXRef_EntityID_LoginSetupID')
CREATE UNIQUE NONCLUSTERED INDEX [UX_EntityLoginSetupXRef_EntityID_LoginSetupID] ON [dbo].[EntityLoginSetupXRef]
(
	[EntityID] ASC,
	[LoginSetupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
-- ---- EntityWebAddressFlag ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[EntityWebAddressFlag]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[EntityWebAddressFlag](
	[EntityWebAddressXRefID] [int] NOT NULL,
	[IsImportant] [bit] NOT NULL,
	[ModifiedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_EntityWebAddressFlag] PRIMARY KEY CLUSTERED 
(
	[EntityWebAddressXRefID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[EntityWebAddressFlag]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[EntityWebAddressFlag]'), N'IsImportant', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[EntityWebAddressFlag] ADD  CONSTRAINT [DF_EntityWebAddressFlag_IsImportant]  DEFAULT ((0)) FOR [IsImportant]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[EntityWebAddressFlag]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[EntityWebAddressFlag]'), N'ModifiedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[EntityWebAddressFlag] ADD  CONSTRAINT [DF_EntityWebAddressFlag_ModifiedDate]  DEFAULT (getdate()) FOR [ModifiedDate]
END

GO
-- ---- EntityWebAddressFlag_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[EntityWebAddressFlag_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[EntityWebAddressFlag_LOG](
	[EntityWebAddressFlag_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[EntityWebAddressXRefID] [int] NULL,
	[IsImportant] [bit] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_EntityWebAddressFlag_LOG] PRIMARY KEY CLUSTERED 
(
	[EntityWebAddressFlag_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[EntityWebAddressFlag_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[EntityWebAddressFlag_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[EntityWebAddressFlag_LOG] ADD  CONSTRAINT [DF_EntityWebAddressFlag_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[EntityWebAddressFlag_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[EntityWebAddressFlag_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[EntityWebAddressFlag_LOG] ADD  CONSTRAINT [DF_EntityWebAddressFlag_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- EntityWebAddressSequence ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[EntityWebAddressSequence]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[EntityWebAddressSequence](
	[EntityWebAddressXRefID] [int] NOT NULL,
	[SortOrder] [int] NOT NULL,
	[ModifiedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_EntityWebAddressSequence] PRIMARY KEY CLUSTERED 
(
	[EntityWebAddressXRefID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[EntityWebAddressSequence]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[EntityWebAddressSequence]'), N'ModifiedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[EntityWebAddressSequence] ADD  CONSTRAINT [DF_EntityWebAddressSequence_ModifiedDate]  DEFAULT (getdate()) FOR [ModifiedDate]
END

GO
-- ---- EntityWebAddressSequence_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[EntityWebAddressSequence_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[EntityWebAddressSequence_LOG](
	[EntityWebAddressSequence_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[EntityWebAddressXRefID] [int] NULL,
	[SortOrder] [int] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_EntityWebAddressSequence_LOG] PRIMARY KEY CLUSTERED 
(
	[EntityWebAddressSequence_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[EntityWebAddressSequence_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[EntityWebAddressSequence_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[EntityWebAddressSequence_LOG] ADD  CONSTRAINT [DF_EntityWebAddressSequence_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[EntityWebAddressSequence_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[EntityWebAddressSequence_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[EntityWebAddressSequence_LOG] ADD  CONSTRAINT [DF_EntityWebAddressSequence_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- LoginSecret ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LoginSecret]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LoginSecret](
	[LoginSecretID] [int] IDENTITY(1,1) NOT NULL,
	[LoginSetupID] [int] NOT NULL,
	[PassCipher] [varbinary](max) NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_LoginSecret] PRIMARY KEY CLUSTERED 
(
	[LoginSecretID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[LoginSecret]') AND name = N'UX_LoginSecret_LoginSetupID')
CREATE UNIQUE NONCLUSTERED INDEX [UX_LoginSecret_LoginSetupID] ON [dbo].[LoginSecret]
(
	[LoginSetupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LoginSecret]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LoginSecret]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LoginSecret] ADD  CONSTRAINT [DF_LoginSecret_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- LoginSecret_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LoginSecret_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LoginSecret_LOG](
	[LoginSecret_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[LoginSecretID] [int] NULL,
	[LoginSetupID] [int] NULL,
	[PassCipher] [varbinary](max) NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_LoginSecret_LOG] PRIMARY KEY CLUSTERED 
(
	[LoginSecret_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LoginSecret_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LoginSecret_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LoginSecret_LOG] ADD  CONSTRAINT [DF_LoginSecret_LOG_LogCreatedDate]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LoginSecret_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LoginSecret_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LoginSecret_LOG] ADD  CONSTRAINT [DF_LoginSecret_LOG_IsInsertion]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- LoginSetup ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LoginSetup]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LoginSetup](
	[LoginSetupID] [int] IDENTITY(1,1) NOT NULL,
	[EffectiveDate] [datetime] NOT NULL,
	[ThroughDate] [datetime] NULL,
	[UserID] [int] NOT NULL,
	[Pass] [varchar](50) NOT NULL,
	[VendorID] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[EntityID] [int] NOT NULL,
	[Notes] [varchar](5500) NULL,
	[LoginName] [varchar](50) NOT NULL,
	[WebAddressID] [int] NULL,
	[LoginUserName] [varchar](50) NOT NULL,
 CONSTRAINT [PK_LoginSetup] PRIMARY KEY CLUSTERED 
(
	[LoginSetupID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[LoginSetup]') AND name = N'IX_EntityID')
CREATE UNIQUE NONCLUSTERED INDEX [IX_EntityID] ON [dbo].[LoginSetup]
(
	[EntityID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LoginSetup]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LoginSetup]'), N'LoginUserName', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LoginSetup] ADD  CONSTRAINT [DF_LoginSetup_LoginUserName]  DEFAULT ('') FOR [LoginUserName]
END

GO
-- ---- LoginSetup_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LoginSetup_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LoginSetup_LOG](
	[LoginSetup_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[CreatedDate] [datetime] NULL,
	[EffectiveDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[LoginSetupID] [int] NULL,
	[Notes] [varchar](5500) NULL,
	[Pass] [varchar](50) NULL,
	[ThroughDate] [datetime] NULL,
	[UserID] [int] NULL,
	[VendorID] [int] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LoginSetup_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LoginSetup_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LoginSetup_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- LoginSetup_UPDATED_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LoginSetup_UPDATED_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LoginSetup_UPDATED_LOG](
	[LoginSetup_UPDATED_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[CreatedDate] [datetime] NULL,
	[EffectiveDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[LoginSetupID] [int] NULL,
	[Notes] [varchar](5500) NULL,
	[Pass] [varchar](50) NULL,
	[ThroughDate] [datetime] NULL,
	[UserID] [int] NULL,
	[VendorID] [int] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LoginSetup_UPDATED_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LoginSetup_UPDATED_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LoginSetup_UPDATED_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- LookupReference ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LookupReference]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LookupReference](
	[LookupReferenceID] [int] IDENTITY(1,1) NOT NULL,
	[LookupKey] [varchar](60) NOT NULL,
	[RefTable] [sysname] NOT NULL,
	[RefColumn] [sysname] NOT NULL,
	[RefKind] [varchar](10) NOT NULL,
	[XrefOwnerColumn] [sysname] NULL,
	[UsageNoun] [nvarchar](60) NOT NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_LookupReference] PRIMARY KEY CLUSTERED 
(
	[LookupReferenceID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[LookupReference]') AND name = N'UX_LookupReference_Target')
CREATE UNIQUE NONCLUSTERED INDEX [UX_LookupReference_Target] ON [dbo].[LookupReference]
(
	[LookupKey] ASC,
	[RefTable] ASC,
	[RefColumn] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LookupReference]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LookupReference]'), N'SortOrder', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LookupReference] ADD  CONSTRAINT [DF_LookupReference_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LookupReference]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LookupReference]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LookupReference] ADD  CONSTRAINT [DF_LookupReference_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.check_constraints WHERE object_id = OBJECT_ID(N'[dbo].[CK_LookupReference_RefKind]') AND parent_object_id = OBJECT_ID(N'[dbo].[LookupReference]'))
ALTER TABLE [dbo].[LookupReference]  WITH CHECK ADD  CONSTRAINT [CK_LookupReference_RefKind] CHECK  (([RefKind]='required' OR [RefKind]='nullable' OR [RefKind]='xref'))
GO
IF  EXISTS (SELECT * FROM sys.check_constraints WHERE object_id = OBJECT_ID(N'[dbo].[CK_LookupReference_RefKind]') AND parent_object_id = OBJECT_ID(N'[dbo].[LookupReference]'))
ALTER TABLE [dbo].[LookupReference] CHECK CONSTRAINT [CK_LookupReference_RefKind]
GO
-- ---- LookupReference_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LookupReference_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LookupReference_LOG](
	[LookupReference_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[LookupReferenceID] [int] NULL,
	[LookupKey] [varchar](60) NULL,
	[RefTable] [sysname] NULL,
	[RefColumn] [sysname] NULL,
	[RefKind] [varchar](10) NULL,
	[XrefOwnerColumn] [sysname] NULL,
	[UsageNoun] [nvarchar](60) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_LookupReference_LOG] PRIMARY KEY CLUSTERED 
(
	[LookupReference_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LookupReference_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LookupReference_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LookupReference_LOG] ADD  CONSTRAINT [DF_LookupReference_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LookupReference_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LookupReference_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LookupReference_LOG] ADD  CONSTRAINT [DF_LookupReference_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- LookupRegistry ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LookupRegistry]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LookupRegistry](
	[LookupRegistryID] [int] IDENTITY(1,1) NOT NULL,
	[LookupKey] [varchar](60) NOT NULL,
	[LookupLabel] [nvarchar](100) NOT NULL,
	[ShelfLabel] [nvarchar](100) NOT NULL,
	[ItemNoun] [nvarchar](60) NOT NULL,
	[TableName] [sysname] NOT NULL,
	[IdColumn] [sysname] NULL,
	[NameColumn] [sysname] NOT NULL,
	[CodeColumn] [sysname] NULL,
	[ProtectedCodes] [varchar](1000) NULL,
	[ReservedNames] [nvarchar](500) NULL,
	[PageUrl] [varchar](300) NULL,
	[SortOrder] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_LookupRegistry] PRIMARY KEY CLUSTERED 
(
	[LookupRegistryID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[LookupRegistry]') AND name = N'UX_LookupRegistry_LookupKey')
CREATE UNIQUE NONCLUSTERED INDEX [UX_LookupRegistry_LookupKey] ON [dbo].[LookupRegistry]
(
	[LookupKey] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LookupRegistry]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LookupRegistry]'), N'SortOrder', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LookupRegistry] ADD  CONSTRAINT [DF_LookupRegistry_SortOrder]  DEFAULT ((0)) FOR [SortOrder]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LookupRegistry]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LookupRegistry]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LookupRegistry] ADD  CONSTRAINT [DF_LookupRegistry_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- LookupRegistry_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[LookupRegistry_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[LookupRegistry_LOG](
	[LookupRegistry_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[LookupRegistryID] [int] NULL,
	[LookupKey] [varchar](60) NULL,
	[LookupLabel] [nvarchar](100) NULL,
	[ShelfLabel] [nvarchar](100) NULL,
	[ItemNoun] [nvarchar](60) NULL,
	[TableName] [sysname] NULL,
	[IdColumn] [sysname] NULL,
	[NameColumn] [sysname] NULL,
	[CodeColumn] [sysname] NULL,
	[ProtectedCodes] [varchar](1000) NULL,
	[ReservedNames] [nvarchar](500) NULL,
	[PageUrl] [varchar](300) NULL,
	[SortOrder] [int] NULL,
	[CreatedDate] [datetime] NULL,
 CONSTRAINT [PK_LookupRegistry_LOG] PRIMARY KEY CLUSTERED 
(
	[LookupRegistry_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LookupRegistry_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LookupRegistry_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LookupRegistry_LOG] ADD  CONSTRAINT [DF_LookupRegistry_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[LookupRegistry_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[LookupRegistry_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[LookupRegistry_LOG] ADD  CONSTRAINT [DF_LookupRegistry_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- tblEntity ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblEntity]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblEntity](
	[EntityID] [int] IDENTITY(1,1) NOT NULL,
	[EntityTypeID] [int] NOT NULL,
	[TempTypeDescription] [varchar](500) NULL,
	[CreatedDate] [datetime] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[tblEntity]') AND name = N'IX_EntityID')
CREATE UNIQUE NONCLUSTERED INDEX [IX_EntityID] ON [dbo].[tblEntity]
(
	[EntityID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblEntity]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblEntity]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblEntity] ADD  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- tblEntity_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblEntity_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblEntity_LOG](
	[Entity_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[EntityTypeID] [int] NULL,
	[TempTypeDescription] [varchar](500) NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblEntity_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblEntity_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblEntity_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- tblEntity_UPDATED_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblEntity_UPDATED_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblEntity_UPDATED_LOG](
	[Entity_UPDATED_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[EntityTypeID] [int] NULL,
	[TempTypeDescription] [varchar](500) NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblEntity_UPDATED_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblEntity_UPDATED_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblEntity_UPDATED_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- tblEntityType ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblEntityType]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblEntityType](
	[EntityTypeID] [int] IDENTITY(1,1) NOT NULL,
	[EntityType] [varchar](500) NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[EntityTable] [varchar](50) NOT NULL,
	[EntityIdentityColumnName] [varchar](50) NOT NULL,
	[EntityCrossRefTable] [varchar](50) NULL,
	[EntityCrossRefTableIdentityColumnName] [varchar](50) NULL,
	[Notes] [varchar](5000) NULL,
	[UserSearchable] [bit] NOT NULL,
	[EntityDisplayColumnName] [varchar](50) NOT NULL,
	[EntityIDRequiredToSave] [bit] NOT NULL,
	[EntitySuppressList] [varchar](50) NULL,
	[DeletedRowsArchived] [bit] NOT NULL,
	[UpdatedRowsArchived] [bit] NOT NULL,
	[EditFormName] [varchar](50) NULL,
	[ListFormName] [varchar](50) NULL,
	[XREFFormName] [varchar](50) NULL,
	[EntityID] [int] NOT NULL,
	[UserControlForEditName] [varchar](50) NULL,
 CONSTRAINT [PK_EntityTypeID] PRIMARY KEY CLUSTERED 
(
	[EntityTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[tblEntityType]') AND name = N'IX_EntityTable')
CREATE UNIQUE NONCLUSTERED INDEX [IX_EntityTable] ON [dbo].[tblEntityType]
(
	[EntityTable] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[tblEntityType]') AND name = N'IX_EntityType')
CREATE UNIQUE NONCLUSTERED INDEX [IX_EntityType] ON [dbo].[tblEntityType]
(
	[EntityType] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblEntityType]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblEntityType]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblEntityType] ADD  CONSTRAINT [DF__tblEntity__Creat__39788055]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- tblEntityType_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblEntityType_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblEntityType_LOG](
	[EntityType_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[CreatedDate] [datetime] NULL,
	[DeletedRowsArchived] [bit] NULL,
	[EntityCrossRefTable] [varchar](50) NULL,
	[EntityCrossRefTableIdentityColumnName] [varchar](50) NULL,
	[EntityDisplayColumnName] [varchar](50) NULL,
	[EntityIdentityColumnName] [varchar](50) NULL,
	[EntityIDRequiredToSave] [bit] NULL,
	[EntitySuppressList] [varchar](50) NULL,
	[EntityTable] [varchar](50) NULL,
	[EntityType] [varchar](500) NULL,
	[EntityTypeID] [int] NULL,
	[Notes] [varchar](5000) NULL,
	[UserSearchable] [bit] NULL,
	[UpdatedRowsArchived] [bit] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblEntityType_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblEntityType_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblEntityType_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- tblEntityWebAddressXRef ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblEntityWebAddressXRef]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblEntityWebAddressXRef](
	[EntityWebAddressXRefID] [int] IDENTITY(1,1) NOT NULL,
	[EntityID] [int] NOT NULL,
	[WebAddressID] [int] NOT NULL,
	[CreatedDate] [datetime] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[tblEntityWebAddressXRef]') AND name = N'IX_EntityWebAddressID')
CREATE UNIQUE NONCLUSTERED INDEX [IX_EntityWebAddressID] ON [dbo].[tblEntityWebAddressXRef]
(
	[EntityID] ASC,
	[WebAddressID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblEntityWebAddressXRef]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblEntityWebAddressXRef]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblEntityWebAddressXRef] ADD  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- tblEntityWebAddressXRef_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblEntityWebAddressXRef_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblEntityWebAddressXRef_LOG](
	[EntityWebAddressXRef_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[CreatedDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[EntityWebAddressXRefID] [int] NULL,
	[WebAddressID] [int] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblEntityWebAddressXRef_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblEntityWebAddressXRef_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblEntityWebAddressXRef_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- tblWebAddress ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblWebAddress]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblWebAddress](
	[WebAddressID] [int] IDENTITY(1,1) NOT NULL,
	[WebAddress] [varchar](900) NOT NULL,
	[WebAddressTypeID] [int] NOT NULL,
	[EntityID] [int] NOT NULL,
	[Notes] [varchar](5000) NULL,
	[WebAddressName] [varchar](500) NOT NULL,
	[WebSiteTypeID] [int] NOT NULL,
 CONSTRAINT [PK_tblWebAddress] PRIMARY KEY CLUSTERED 
(
	[WebAddressID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[tblWebAddress]') AND name = N'IX_EntityID')
CREATE UNIQUE NONCLUSTERED INDEX [IX_EntityID] ON [dbo].[tblWebAddress]
(
	[EntityID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[tblWebAddress]') AND name = N'IX_WebAddress')
CREATE UNIQUE NONCLUSTERED INDEX [IX_WebAddress] ON [dbo].[tblWebAddress]
(
	[WebAddress] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[tblWebAddress]') AND name = N'IX_WebAddressName')
CREATE UNIQUE NONCLUSTERED INDEX [IX_WebAddressName] ON [dbo].[tblWebAddress]
(
	[WebAddressName] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
-- ---- tblWebAddress_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblWebAddress_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblWebAddress_LOG](
	[WebAddress_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[Notes] [varchar](5000) NULL,
	[WebAddress] [varchar](900) NULL,
	[WebAddressID] [int] NULL,
	[WebAddressName] [varchar](500) NULL,
	[WebAddressTypeID] [int] NULL,
	[WebSiteTypeID] [int] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblWebAddress_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblWebAddress_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblWebAddress_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- tblWebAddress_UPDATED_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblWebAddress_UPDATED_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblWebAddress_UPDATED_LOG](
	[WebAddress_UPDATED_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[Notes] [varchar](5000) NULL,
	[WebAddress] [varchar](900) NULL,
	[WebAddressID] [int] NULL,
	[WebAddressName] [varchar](500) NULL,
	[WebAddressTypeID] [int] NULL,
	[WebSiteTypeID] [int] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblWebAddress_UPDATED_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblWebAddress_UPDATED_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblWebAddress_UPDATED_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- tblWebAddressType ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblWebAddressType]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblWebAddressType](
	[WebAddressTypeID] [int] NOT NULL,
	[WebAddressType] [varchar](50) NULL,
 CONSTRAINT [PK_WebAddressTypeID] PRIMARY KEY CLUSTERED 
(
	[WebAddressTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
-- ---- tblWebAddressType_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[tblWebAddressType_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[tblWebAddressType_LOG](
	[WebAddressType_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[WebAddressType] [varchar](50) NULL,
	[WebAddressTypeID] [int] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[tblWebAddressType_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[tblWebAddressType_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[tblWebAddressType_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- WebSiteType ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteType]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[WebSiteType](
	[WebSiteTypeID] [int] IDENTITY(1,1) NOT NULL,
	[EntityID] [int] NOT NULL,
	[WebSiteType] [varchar](50) NOT NULL,
	[Notes] [varchar](6000) NULL,
 CONSTRAINT [PK_WebSiteTypeID] PRIMARY KEY CLUSTERED 
(
	[WebSiteTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteType]') AND name = N'ix_comp')
CREATE NONCLUSTERED INDEX [ix_comp] ON [dbo].[WebSiteType]
(
	[WebSiteTypeID] ASC,
	[EntityID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteType]') AND name = N'ix_comp2')
CREATE NONCLUSTERED INDEX [ix_comp2] ON [dbo].[WebSiteType]
(
	[Notes] ASC,
	[EntityID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteType]') AND name = N'IX_EntityID')
CREATE UNIQUE NONCLUSTERED INDEX [IX_EntityID] ON [dbo].[WebSiteType]
(
	[EntityID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteType]') AND name = N'IX_WebSiteType')
CREATE UNIQUE NONCLUSTERED INDEX [IX_WebSiteType] ON [dbo].[WebSiteType]
(
	[WebSiteType] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
-- ---- WebSiteType_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteType_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[WebSiteType_LOG](
	[WebSiteType_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[Notes] [varchar](6000) NULL,
	[WebSiteType] [varchar](50) NULL,
	[WebSiteTypeID] [int] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[WebSiteType_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[WebSiteType_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[WebSiteType_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- WebSiteType_UPDATED_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteType_UPDATED_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[WebSiteType_UPDATED_LOG](
	[WebSiteType_UPDATED_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NULL,
	[EntityID] [int] NULL,
	[Notes] [varchar](6000) NULL,
	[WebSiteType] [varchar](50) NULL,
	[WebSiteTypeID] [int] NULL
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[WebSiteType_UPDATED_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[WebSiteType_UPDATED_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[WebSiteType_UPDATED_LOG] ADD  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
-- ---- WebSiteTypeStyle ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteTypeStyle]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[WebSiteTypeStyle](
	[WebSiteTypeID] [int] NOT NULL,
	[ColorHex] [varchar](7) NULL,
	[SortOrder] [int] NULL,
	[ModifiedDate] [datetime] NOT NULL,
 CONSTRAINT [PK_WebSiteTypeStyle] PRIMARY KEY CLUSTERED 
(
	[WebSiteTypeID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[WebSiteTypeStyle]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[WebSiteTypeStyle]'), N'ModifiedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[WebSiteTypeStyle] ADD  CONSTRAINT [DF_WebSiteTypeStyle_ModifiedDate]  DEFAULT (getdate()) FOR [ModifiedDate]
END

GO
-- ---- WebSiteTypeStyle_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[WebSiteTypeStyle_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[WebSiteTypeStyle_LOG](
	[WebSiteTypeStyle_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[WebSiteTypeID] [int] NULL,
	[ColorHex] [varchar](7) NULL,
	[SortOrder] [int] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_WebSiteTypeStyle_LOG] PRIMARY KEY CLUSTERED 
(
	[WebSiteTypeStyle_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[WebSiteTypeStyle_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[WebSiteTypeStyle_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[WebSiteTypeStyle_LOG] ADD  CONSTRAINT [DF_WebSiteTypeStyle_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[WebSiteTypeStyle_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[WebSiteTypeStyle_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[WebSiteTypeStyle_LOG] ADD  CONSTRAINT [DF_WebSiteTypeStyle_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ==== FOREIGN KEYS (Web tab) ====
-- SKIPPED FK FK_LoginSetup_tblUser -> tblUser
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_tblEntity_tblEntityType]') AND parent_object_id = OBJECT_ID(N'[dbo].[tblEntity]'))
ALTER TABLE [dbo].[tblEntity]  WITH CHECK ADD  CONSTRAINT [FK_tblEntity_tblEntityType] FOREIGN KEY([EntityTypeID])
REFERENCES [dbo].[tblEntityType] ([EntityTypeID])
GO
IF  EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_tblEntity_tblEntityType]') AND parent_object_id = OBJECT_ID(N'[dbo].[tblEntity]'))
ALTER TABLE [dbo].[tblEntity] CHECK CONSTRAINT [FK_tblEntity_tblEntityType]
GO
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_tblWebAddress_tblWebAddressType]') AND parent_object_id = OBJECT_ID(N'[dbo].[tblWebAddress]'))
ALTER TABLE [dbo].[tblWebAddress]  WITH CHECK ADD  CONSTRAINT [FK_tblWebAddress_tblWebAddressType] FOREIGN KEY([WebAddressTypeID])
REFERENCES [dbo].[tblWebAddressType] ([WebAddressTypeID])
GO
IF  EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_tblWebAddress_tblWebAddressType]') AND parent_object_id = OBJECT_ID(N'[dbo].[tblWebAddress]'))
ALTER TABLE [dbo].[tblWebAddress] CHECK CONSTRAINT [FK_tblWebAddress_tblWebAddressType]
GO
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_tblWebAddress_WebSiteType]') AND parent_object_id = OBJECT_ID(N'[dbo].[tblWebAddress]'))
ALTER TABLE [dbo].[tblWebAddress]  WITH CHECK ADD  CONSTRAINT [FK_tblWebAddress_WebSiteType] FOREIGN KEY([WebSiteTypeID])
REFERENCES [dbo].[WebSiteType] ([WebSiteTypeID])
GO
IF  EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_tblWebAddress_WebSiteType]') AND parent_object_id = OBJECT_ID(N'[dbo].[tblWebAddress]'))
ALTER TABLE [dbo].[tblWebAddress] CHECK CONSTRAINT [FK_tblWebAddress_WebSiteType]
GO
IF NOT EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_WebSiteTypeStyle_WebSiteType]') AND parent_object_id = OBJECT_ID(N'[dbo].[WebSiteTypeStyle]'))
ALTER TABLE [dbo].[WebSiteTypeStyle]  WITH CHECK ADD  CONSTRAINT [FK_WebSiteTypeStyle_WebSiteType] FOREIGN KEY([WebSiteTypeID])
REFERENCES [dbo].[WebSiteType] ([WebSiteTypeID])
ON DELETE CASCADE
GO
IF  EXISTS (SELECT * FROM sys.foreign_keys WHERE object_id = OBJECT_ID(N'[dbo].[FK_WebSiteTypeStyle_WebSiteType]') AND parent_object_id = OBJECT_ID(N'[dbo].[WebSiteTypeStyle]'))
ALTER TABLE [dbo].[WebSiteTypeStyle] CHECK CONSTRAINT [FK_WebSiteTypeStyle_WebSiteType]
GO
-- Story 5653 (Echo): Leonardo binds four old-style defaults (CREATE DEFAULT DF_GetDate / DF_IntZero /
-- DF_BitTrue + sp_bindefault), which SMO does not script. Echo gives the same columns the same values as
-- ordinary named default constraints.
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[LoginSetup]') AND name = N'CreatedDate' AND default_object_id <> 0)
    ALTER TABLE [dbo].[LoginSetup] ADD CONSTRAINT [DF_LoginSetup_CreatedDate] DEFAULT (GETDATE()) FOR [CreatedDate]
GO
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[LoginSetup]') AND name = N'WebAddressID' AND default_object_id <> 0)
    ALTER TABLE [dbo].[LoginSetup] ADD CONSTRAINT [DF_LoginSetup_WebAddressID] DEFAULT (0) FOR [WebAddressID]
GO
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[EntityLoginSetupXRef]') AND name = N'CreatedDate' AND default_object_id <> 0)
    ALTER TABLE [dbo].[EntityLoginSetupXRef] ADD CONSTRAINT [DF_EntityLoginSetupXRef_CreatedDate] DEFAULT (GETDATE()) FOR [CreatedDate]
GO
IF NOT EXISTS (SELECT * FROM sys.columns WHERE object_id = OBJECT_ID(N'[dbo].[tblEntityType]') AND name = N'DeletedRowsArchived' AND default_object_id <> 0)
    ALTER TABLE [dbo].[tblEntityType] ADD CONSTRAINT [DF_tblEntityType_DeletedRowsArchived] DEFAULT (1) FOR [DeletedRowsArchived]
GO
-- ==== READER (Story 5653 parity, 2026-10-26) ====
-- Scripted from Leonardo: what the Reader remembers -- highlights, notes on them, and the place
-- you stopped -- each with its _LOG twin.
-- ---- ReaderHighlight ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ReaderHighlight](
	[ReaderHighlightID] [int] IDENTITY(1,1) NOT NULL,
	[DocKey] [nvarchar](450) NOT NULL,
	[DocTitle] [nvarchar](400) NULL,
	[ViewMode] [nvarchar](20) NOT NULL,
	[PageNumber] [int] NULL,
	[BlockIndex] [int] NOT NULL,
	[StartOffset] [int] NOT NULL,
	[EndOffset] [int] NOT NULL,
	[SelectedText] [nvarchar](max) NOT NULL,
	[ColorCode] [nvarchar](20) NOT NULL,
	[ResearchProjectID] [int] NULL,
	[ResearchNoteID] [int] NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_ReaderHighlight] PRIMARY KEY CLUSTERED 
(
	[ReaderHighlightID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND name = N'IX_ReaderHighlight_Doc')
CREATE NONCLUSTERED INDEX [IX_ReaderHighlight_Doc] ON [dbo].[ReaderHighlight]
(
	[DocKey] ASC
)
INCLUDE ( 	[BlockIndex],
	[StartOffset]) WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND name = N'IX_ReaderHighlight_Project')
CREATE NONCLUSTERED INDEX [IX_ReaderHighlight_Project] ON [dbo].[ReaderHighlight]
(
	[ResearchProjectID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderHighlight]'), N'ViewMode', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderHighlight] ADD  CONSTRAINT [DF_ReaderHighlight_ViewMode]  DEFAULT ('reader') FOR [ViewMode]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderHighlight]'), N'BlockIndex', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderHighlight] ADD  CONSTRAINT [DF_ReaderHighlight_BlockIndex]  DEFAULT ((0)) FOR [BlockIndex]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderHighlight]'), N'StartOffset', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderHighlight] ADD  CONSTRAINT [DF_ReaderHighlight_StartOffset]  DEFAULT ((0)) FOR [StartOffset]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderHighlight]'), N'EndOffset', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderHighlight] ADD  CONSTRAINT [DF_ReaderHighlight_EndOffset]  DEFAULT ((0)) FOR [EndOffset]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderHighlight]'), N'ColorCode', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderHighlight] ADD  CONSTRAINT [DF_ReaderHighlight_ColorCode]  DEFAULT ('yellow') FOR [ColorCode]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderHighlight]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderHighlight]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderHighlight] ADD  CONSTRAINT [DF_ReaderHighlight_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- ReaderHighlight_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ReaderHighlight_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ReaderHighlight_LOG](
	[ReaderHighlight_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[ReaderHighlightID] [int] NULL,
	[DocKey] [nvarchar](450) NULL,
	[DocTitle] [nvarchar](400) NULL,
	[ViewMode] [nvarchar](20) NULL,
	[PageNumber] [int] NULL,
	[BlockIndex] [int] NULL,
	[StartOffset] [int] NULL,
	[EndOffset] [int] NULL,
	[SelectedText] [nvarchar](max) NULL,
	[ColorCode] [nvarchar](20) NULL,
	[ResearchProjectID] [int] NULL,
	[ResearchNoteID] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_ReaderHighlight_LOG] PRIMARY KEY CLUSTERED 
(
	[ReaderHighlight_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderHighlight_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderHighlight_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderHighlight_LOG] ADD  CONSTRAINT [DF_ReaderHighlight_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderHighlight_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderHighlight_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderHighlight_LOG] ADD  CONSTRAINT [DF_ReaderHighlight_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- ReaderMarkNote ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ReaderMarkNote]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ReaderMarkNote](
	[ReaderMarkNoteID] [int] IDENTITY(1,1) NOT NULL,
	[ReaderHighlightID] [int] NOT NULL,
	[NoteText] [nvarchar](max) NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_ReaderMarkNote] PRIMARY KEY CLUSTERED 
(
	[ReaderMarkNoteID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[ReaderMarkNote]') AND name = N'IX_ReaderMarkNote_Mark')
CREATE NONCLUSTERED INDEX [IX_ReaderMarkNote_Mark] ON [dbo].[ReaderMarkNote]
(
	[ReaderHighlightID] ASC
)
INCLUDE ( 	[CreatedDate]) WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderMarkNote]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderMarkNote]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderMarkNote] ADD  CONSTRAINT [DF_ReaderMarkNote_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- ReaderMarkNote_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ReaderMarkNote_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ReaderMarkNote_LOG](
	[ReaderMarkNote_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[ReaderMarkNoteID] [int] NULL,
	[ReaderHighlightID] [int] NULL,
	[NoteText] [nvarchar](max) NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_ReaderMarkNote_LOG] PRIMARY KEY CLUSTERED 
(
	[ReaderMarkNote_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY] TEXTIMAGE_ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderMarkNote_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderMarkNote_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderMarkNote_LOG] ADD  CONSTRAINT [DF_ReaderMarkNote_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderMarkNote_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderMarkNote_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderMarkNote_LOG] ADD  CONSTRAINT [DF_ReaderMarkNote_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ---- ReaderPosition ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ReaderPosition]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ReaderPosition](
	[ReaderPositionID] [int] IDENTITY(1,1) NOT NULL,
	[DocKey] [nvarchar](450) NOT NULL,
	[DocTitle] [nvarchar](400) NULL,
	[ViewMode] [nvarchar](40) NULL,
	[BlockIndex] [int] NOT NULL,
	[CharOffset] [int] NOT NULL,
	[PageNumber] [int] NULL,
	[ScrollRatio] [decimal](9, 6) NOT NULL,
	[PercentRead] [int] NOT NULL,
	[CreatedDate] [datetime] NOT NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_ReaderPosition] PRIMARY KEY CLUSTERED 
(
	[ReaderPositionID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
SET ANSI_PADDING ON

GO
IF NOT EXISTS (SELECT * FROM sys.indexes WHERE object_id = OBJECT_ID(N'[dbo].[ReaderPosition]') AND name = N'UX_ReaderPosition_DocKey')
CREATE UNIQUE NONCLUSTERED INDEX [UX_ReaderPosition_DocKey] ON [dbo].[ReaderPosition]
(
	[DocKey] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, SORT_IN_TEMPDB = OFF, IGNORE_DUP_KEY = OFF, DROP_EXISTING = OFF, ONLINE = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderPosition]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderPosition]'), N'BlockIndex', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderPosition] ADD  CONSTRAINT [DF_ReaderPosition_BlockIndex]  DEFAULT ((0)) FOR [BlockIndex]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderPosition]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderPosition]'), N'CharOffset', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderPosition] ADD  CONSTRAINT [DF_ReaderPosition_CharOffset]  DEFAULT ((0)) FOR [CharOffset]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderPosition]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderPosition]'), N'ScrollRatio', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderPosition] ADD  CONSTRAINT [DF_ReaderPosition_ScrollRatio]  DEFAULT ((0)) FOR [ScrollRatio]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderPosition]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderPosition]'), N'PercentRead', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderPosition] ADD  CONSTRAINT [DF_ReaderPosition_PercentRead]  DEFAULT ((0)) FOR [PercentRead]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderPosition]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderPosition]'), N'CreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderPosition] ADD  CONSTRAINT [DF_ReaderPosition_CreatedDate]  DEFAULT (getdate()) FOR [CreatedDate]
END

GO
-- ---- ReaderPosition_LOG ----
SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF NOT EXISTS (SELECT * FROM sys.objects WHERE object_id = OBJECT_ID(N'[dbo].[ReaderPosition_LOG]') AND type in (N'U'))
BEGIN
CREATE TABLE [dbo].[ReaderPosition_LOG](
	[ReaderPosition_LOGID] [int] IDENTITY(1,1) NOT NULL,
	[LogCreatedDate] [datetime] NOT NULL,
	[IsInsertion] [bit] NOT NULL,
	[DeletedDate] [datetime] NULL,
	[ReaderPositionID] [int] NULL,
	[DocKey] [nvarchar](450) NULL,
	[DocTitle] [nvarchar](400) NULL,
	[ViewMode] [nvarchar](40) NULL,
	[BlockIndex] [int] NULL,
	[CharOffset] [int] NULL,
	[PageNumber] [int] NULL,
	[ScrollRatio] [decimal](9, 6) NULL,
	[PercentRead] [int] NULL,
	[CreatedDate] [datetime] NULL,
	[ModifiedDate] [datetime] NULL,
 CONSTRAINT [PK_ReaderPosition_LOG] PRIMARY KEY CLUSTERED 
(
	[ReaderPosition_LOGID] ASC
)WITH (PAD_INDEX = OFF, STATISTICS_NORECOMPUTE = OFF, IGNORE_DUP_KEY = OFF, ALLOW_ROW_LOCKS = ON, ALLOW_PAGE_LOCKS = ON) ON [PRIMARY]
) ON [PRIMARY]
END
GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderPosition_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderPosition_LOG]'), N'LogCreatedDate', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderPosition_LOG] ADD  CONSTRAINT [DF_ReaderPosition_LOG_LCD]  DEFAULT (getdate()) FOR [LogCreatedDate]
END

GO
IF NOT EXISTS (SELECT * FROM sys.default_constraints WHERE parent_object_id = OBJECT_ID(N'[dbo].[ReaderPosition_LOG]') AND parent_column_id = COLUMNPROPERTY(OBJECT_ID(N'[dbo].[ReaderPosition_LOG]'), N'IsInsertion', 'ColumnId'))
BEGIN
ALTER TABLE [dbo].[ReaderPosition_LOG] ADD  CONSTRAINT [DF_ReaderPosition_LOG_INS]  DEFAULT ((0)) FOR [IsInsertion]
END

GO
-- ==== FOREIGN KEYS (Reader) ====
