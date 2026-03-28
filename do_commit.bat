@echo off
cd /d C:\Users\Yves\.openclaw\workspace\AnimeReader
git add .
git commit -m "Fix Android 14 crash: Register MediaProjection callback before start"
git push origin master