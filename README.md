# Lumen — Liquid Glass NAS music player

A personal music app for the Galaxy Z Fold7 that streams straight from your NAS. It has three switchable designs (Aurora, Obsidian and Fluid), each in light and dark.

## Install
1. On the phone, open **Releases → latest** in this repo and download `Lumen-<n>.apk`. Every push to `main` builds a new one.
2. Allow "Install unknown apps" for your browser when Android asks, then install. Later builds install over the old one and keep your library.

## First run
1. **Connect your NAS.** Lumen looks for SMB servers on your Wi-Fi.
   - Pick your UGREEN NAS and enter your NAS username and password.
   - Tap **List** to choose the shared folder.
   - Set **Music folder** to the folder that holds your music (`Music` in your personal folder). **Browse** lets you pick it.
2. **Away from home:** install Tailscale on the phone, signed in to the same account as the NAS's Tailscale app. Put the NAS's Tailscale IP (`100.x.y.z`) in **Away-from-home address**. Lumen switches to it automatically when the home address can't be reached.
3. Tap **Connect & scan**. Folders under `Music/Playlists` (Emotional, Hip Hop, Party…) become collections. Tags are read from the files, and missing info and artwork are matched online. Only confident matches are applied; everything else waits for you in **Edit info**.

## Switching designs
**Settings → Appearance → Design** offers Aurora, Obsidian and Fluid. **Theme** is System, Dark or Light.

## YouTube → MP3
Open the **Import** tab, or share a YouTube link to Lumen from the YouTube app.
1. Lumen fetches the title, thumbnail and length.
2. Confirm you own or are authorized to download the audio.
3. Pick MP3, M4A or Opus and a destination (a NAS folder or this phone).
4. The import is converted with FFmpeg, saved, tagged and added to your library.

If YouTube changes and imports start failing, use **Settings → YouTube import → Update import engine**.

## Building it yourself
`./scripts/fetch_fonts.sh && ./gradlew assembleRelease` (JDK 17, Android SDK 35). Kotlin, Jetpack Compose, Media3, Room, WorkManager, smbj, Haze (real backdrop blur) and yt-dlp/FFmpeg.
