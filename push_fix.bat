@echo off
cd /d C:\Users\Yves\.openclaw\workspace\AnimeReader
git add app/build.gradle.kts
git commit -m "Fix JVM target version for GitHub Actions"
git push origin master
