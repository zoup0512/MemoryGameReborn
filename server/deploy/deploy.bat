@echo off
REM Local deploy for running the PK server ON this Windows box (port 8090;
REM 8080 is taken by SRS on the test LAN). Requires Java 17 in PATH.
REM Usage: deploy.bat

set PORT=8090
set JAR=memorygames-pk-server-0.1.0.jar

echo ==^> stopping any previous instance
taskkill /F /FI "WINDOWTITLE eq memorygames-pk*" >nul 2>&1
for /f "tokens=5" %%p in ('netstat -aon ^| findstr :%PORT% ^| findstr LISTENING') do taskkill /F /PID %%p >nul 2>&1

echo ==^> starting PK server on port %PORT% (H2 file store: .\data\pk)
if not exist data mkdir data
start "memorygames-pk" /min java -Xms128m -Xmx512m ^
  -Dserver.address=0.0.0.0 -Dserver.port=%PORT% ^
  -Dspring.datasource.url="jdbc:h2:file:./data/pk;DB_CLOSE_DELAY=-1" ^
  -jar %JAR% > pk-server.log 2>&1

timeout /t 5 /nobreak >nul
echo ==^> last log lines:
powershell -Command "Get-Content pk-server.log -Tail 5"
echo Done. Client endpoint: ws://192.168.110.140:%PORT%/ws
