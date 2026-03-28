@echo off
cd /d C:\Users\Yves\.openclaw\workspace\AnimeReader
git init
git add .
git commit -m "Initial commit"
gh repo create AnimeReader --public --source=. --remote=origin --push
