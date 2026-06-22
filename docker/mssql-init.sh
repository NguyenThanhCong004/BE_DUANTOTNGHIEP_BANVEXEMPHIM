#!/usr/bin/env bash
set -euo pipefail

DB_NAME="${DB_NAME:-JAVA6}"
MSSQL_SA_PASSWORD="${MSSQL_SA_PASSWORD:?MSSQL_SA_PASSWORD is required}"

SQLCMD="/opt/mssql-tools18/bin/sqlcmd"
if [ ! -x "$SQLCMD" ]; then
  SQLCMD="/opt/mssql-tools/bin/sqlcmd"
fi

"$SQLCMD" -C -S sqlserver -U sa -P "$MSSQL_SA_PASSWORD" -Q "
IF DB_ID(N'$DB_NAME') IS NULL
BEGIN
    DECLARE @sql nvarchar(max) = N'CREATE DATABASE ' + QUOTENAME(N'$DB_NAME');
    EXEC(@sql);
END
"

echo "Database '$DB_NAME' is ready."
