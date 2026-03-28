@echo off
cd /d C:\Users\Yves\.openclaw\workspace\AnimeReader
git add .
git commit -m "Fix Android 14 crash: Call startForeground AFTER getMediaProjection token is acquired"
git push origin master
