@echo off
setlocal
cd /d "%~dp0\.."

REM Build + run in a NEW terminal window
REM Assumes the JDBC jar lives in src\ and code is in src\*.java
if not exist out mkdir out

start "Rogue In Space" cmd /k ^
  "javac -d out -cp src;src\sqlite-jdbc-3.36.0.3.jar src\*.java && java -cp out;src\sqlite-jdbc-3.36.0.3.jar Game"
