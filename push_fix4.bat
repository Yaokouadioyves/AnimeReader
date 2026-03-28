@echo off
cd /d C:\Users\Yves\.openclaw\workspace\AnimeReader
git add .
git commit -m "Fix Fatal Crash Android 14: startForeground must be strictly called BEFORE getMediaProjection"
git push origin master
