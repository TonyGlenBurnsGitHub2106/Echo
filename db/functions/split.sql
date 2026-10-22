SET ANSI_NULLS ON
GO
SET QUOTED_IDENTIFIER ON
GO
IF OBJECT_ID('dbo.split') IS NOT NULL DROP FUNCTION dbo.split
GO
/*

Function Split.

VERSION HISTORY

Date        Developer      Description
----------  -------------  ----------------------------------------------------------
2026-09-11  Stewart Vance  Scripted out of the live database (Story 3487). The object
                           existed only inside the server; this file is now its source.

*/

CREATE FUNCTION dbo.Split(@String varchar(8000), @Delimiter char(1))
	returns @temptable TABLE (items varchar(8000))
as

/*
	select *
	from dbo.Split('Tony is a wonderful guy', ' ')

*/

begin
    declare @idx int
    declare @slice varchar(8000)

    select @idx = 1
        if len(@String)<1 or @String is null  return

    while @idx!= 0
    begin
        set @idx = charindex(@Delimiter,@String)
        if @idx!=0
            set @slice = left(@String,@idx - 1)
        else
            set @slice = @String

        if(len(@slice)>0)
            insert into @temptable(Items) values(@slice)

        set @String = right(@String,len(@String) - @idx)
        if len(@String) = 0 break
    end
return

end

GO
