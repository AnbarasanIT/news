# Market Wire — Android app

A native Android app version of your market-news script. No backend server —
it fetches the RSS feeds and scrapes each article's summary directly on the
phone (same logic as your Python script: feedparser → BeautifulSoup becomes
XmlPullParser → Jsoup here), refreshing every 60 seconds.

## Getting an installable APK — no Android Studio required

This project includes a GitHub Actions workflow that builds the APK in the
cloud. You only need a free GitHub account and a web browser.

1. Go to github.com and create a new **empty** repository (e.g. `market-wire-app`).
2. On the repo page, click **"Add file" → "Upload files"**, then drag in
   *everything* inside this `MarketWireApp` folder (keep the folder structure —
   drag the whole folder, or use "choose your files" and select all of it).
   Commit the upload.
3. Click the **Actions** tab on your repo. A workflow called "Build APK" will
   run automatically (takes 2-4 minutes). If it doesn't start, click
   "Run workflow" manually.
4. When it finishes (green check), open that run, scroll to **Artifacts**,
   and download `market-wire-debug-apk`. It's a `.zip` containing
   `app-debug.apk`.
5. Transfer `app-debug.apk` to your phone (email it to yourself, Google Drive,
   USB — any way you like) and tap it to install. Android will ask you to
   allow "install unknown apps" for whichever app you opened it from the
   first time — allow it, then install.

That's the whole process — no coding, no local build tools.

## Alternative: building locally in Android Studio

If you ever do install Android Studio (it's free): **File → Open**, pick this
folder, let it sync (it will download Gradle itself), then
**Build → Build Bundle(s)/APK(s) → Build APK(s)**. The APK lands in
`app/build/outputs/apk/debug/`.

## Notes

- This is a **debug** build (unsigned), which is fine for installing on your
  own phone but not for the Play Store.
- Some Indian news sites occasionally block scraping or change their page
  structure — if a summary shows "(couldn't fetch article: ...)" that's the
  same soft-fail behavior as your original script, not a bug.
- Refresh interval, feed list, and market-hours (9:15–15:30 IST) are all in
  `FeedFetcher.kt` if you want to tweak them.
