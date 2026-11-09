# My Chromecast with Google TV setup: the whole guide

A clean home screen for the **Chromecast with Google TV 4K** (`sabrina`, Google TV 14, build `UTTC.250917.004`), built on
[Gathin23/cleanyourtv](https://github.com/Gathin23/cleanyourtv). It should be followed on this device; other TVs need
their own list from [`examples/`](examples/) and [`PROMPT.md`](PROMPT.md).

## What it adds to the stock project

| Feature | How it works | Switch |
|---|---|---|
| White logos | Lifts each app's logo out of its banner; apps with no clean logo show their name | `white_logos` |
| Spotlight | A beam and pool of light in the focused app's colour | `spotlight`, `app_glow` |
| Rolling seconds | Digits roll upward beside the clock, only while Home is visible | `rolling_seconds` |
| Startup animation | A red eye with a spinning fan ring, once per boot; any remote key skips it | `boot_spin` |
| Clear button | The brush in the top bar force-stops apps opened since boot | Always on |
| Top button names | A small label under the focused top button | Always on |
| Short date | `SUN 8 NOV` | `strings.xml` |
| Picture screensaver | Full-screen picture with no clock; can differ from the Home wallpaper | `clock_wallpaper_only` in `config.xml` |

The wallpaper is not included: add your own image.

## Before you start

- A Mac or Linux computer on the same Wi-Fi as the Chromecast, with JDK 17, Android SDK platform 34, build-tools 34.0.0,
  Python 3 and Google's `adb`. On macOS: `brew install openjdk@17 android-platform-tools` and `brew install --cask android-commandlinetools`.
- On the TV: Settings > System > About > press OK on **Build** 7 times, then turn on **USB debugging** and
  **Wireless debugging** in Developer options.

**What you give up:** phone casting stops after the next reboot because the setup wizard is disabled, holding Home does
nothing, and the stock Google TV home is gone until you undo this.

## 1. Connect

```bash
python3 -m venv tools/venv && tools/venv/bin/pip install -r tools/requirements.txt
adb pair <tv ip>:<pairing port> <code>
adb connect <tv ip>:<wireless debugging port>
adb tcpip 5555
```

The ports and code are on the Wireless debugging screen. If your assistant or sandbox cannot reach the TV, run
`tools/venv/bin/python -I tools/relay.py <tv ip>` in an ordinary terminal and use `TV_HOST=127.0.0.1 TV_PORT=15555` below.
Otherwise use `TV_HOST=<tv ip>`.

## 2. Back up

```bash
mkdir -p backup
tools/venv/bin/python -I tools/tv.py 'pm list packages -d' > backup/disabled.txt
tools/venv/bin/python -I tools/tv.py 'settings list secure' > backup/settings-secure.txt
tools/venv/bin/python -I tools/tv.py 'settings list global' > backup/settings-global.txt
```

## 3. Debloat, in batches

```bash
tools/disable.sh setup/lists/01-telemetry.txt
tools/disable.sh setup/lists/02-preloaded.txt
```

Comment out any line you want to keep first. The second list disables Play Games, the Google TV app, the print spooler and TalkBack.

## 4. Install the launcher, then take over Home

Edit `dock_apps` in [`setup/theme/config.json`](setup/theme/config.json) for your apps, and add a JPG at
`setup/theme/wallpapers/01.jpg`.

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17 ANDROID_HOME=$HOME/Library/Android/sdk
STOCK_LAUNCHER=none launcher/build-and-install.sh
```

The script stops at its Home check here. That is expected while the stock home is still enabled.

Check who owns your saved Wi-Fi networks. Android deletes a network when the app that created it is disabled:

```bash
tools/venv/bin/python -I tools/tv.py "dumpsys wifi | grep -o 'cname=[^ ]*' | sort -u"
```

If `setupwraith` is listed, forget that network and join it again in Settings before continuing. Then:

```bash
tools/venv/bin/python -I tools/tv.py 'settings put secure screensaver_components com.gathin.tvhome/.ClockDream'
tools/disable.sh setup/lists/03-home-ambient.txt
SKIP_BUILD=1 HOME_KEY_SERVICE=1 launcher/build-and-install.sh
tools/theme.sh setup/theme
```

## 5. Turn on the helpers

**Every launcher install switches these off, so run this after each one:**

```bash
tools/venv/bin/python -I tools/tv.py 'appops set com.gathin.tvhome GET_USAGE_STATS allow; settings put secure enabled_accessibility_services com.gathin.tvhome/.CleanerService:com.gathin.tvhome/.HomeKeyService; settings put secure accessibility_enabled 1'
```

Test the YouTube and Netflix remote buttons on the real remote: they are mapped in `app_keys` in
`launcher/app/src/main/res/values/config.xml`, and an injected key proves nothing.

## 6. Close ADB

Port 5555 is unencrypted, so do not leave it open.

1. Turn off Wireless debugging, and check its port: `tools/venv/bin/python -I tools/adb-closed.py <tv ip> <wireless port>`
2. In Developer options, choose **Revoke USB debugging authorizations**, then turn off **USB debugging**.
3. Restart the Chromecast, then check: `tools/venv/bin/python -I tools/adb-closed.py <tv ip>`. Only its "closed" counts.

## Customising

- **Your own startup animation:** replace `drawAnimation()` in `BootSpinView.java`. Add `"boot_spin_every_start": true` to the theme while working on it.
- **Wallpaper, dock:** edit `setup/theme/`, then `tools/theme.sh setup/theme`.
- **Screensaver picture:** put an image whose file name starts with `screensaver` in `setup/theme/wallpapers/`, next to your Home wallpaper, then run `tools/theme.sh setup/theme`. It is used only by the screensaver and never appears on Home. Without one, the screensaver shows the Home wallpaper.
- **Remote volume, mute, Source:** these are infrared and go to the TV. Use Settings > Remotes & Accessories > Set up remote buttons.

## Undo

```bash
TV_HOST=<tv ip> setup/restore.sh
```

Then reboot. It re-enables everything in the lists plus the stock home, and restores the screensaver.

## Known limits

- The Clear button presses **Force stop** in Settings behind a black screen, one app at a time. It matches the English
  wording, so another language or a Google TV update can stop it working. It skips Surfshark while a VPN is connected.
- Android does not tell a launcher which apps are truly running, so "opened since boot and not stopped since" is the closest match.
- A real boot animation needs root, so the startup animation plays after the manufacturer's logo.
- Tested on one Chromecast only. These launcher changes are heavier than upstream's 1 GB target.

## Credit and licence

Built on [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by Gathin23, under the [MIT licence](LICENSE), which also covers these changes.
