@echo off
cd /d C:\Users\Yves\.openclaw\workspace\AnimeReader
git add .
git commit -m "Fix Android 14 crash: Add POST_NOTIFICATIONS permission and explicit mediaProjection type in startForeground"
git push origin master
