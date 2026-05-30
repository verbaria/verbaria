@REM Called by verbaria.bat before it detects Java.
@REM
@REM If VERBARIA_JRE is set, use that JRE to run the CLI; otherwise leave Java
@REM selection to the launcher's default logic (JAVA_HOME, then java on PATH).
@if not "%VERBARIA_JRE%"=="" set "JAVA_HOME=%VERBARIA_JRE%"
@if not "%VERBARIA_JRE%"=="" set "JAVACMD=%VERBARIA_JRE%\bin\java.exe"
