# itachi-cleanyourtv

A clean, dark home screen for the **Chromecast with Google TV 4K**, with the ads, tracking and recommendation rows removed.

## Features

- White logo-only app icons, with no tiles or dock bar
- A spotlight in each app's own colour that follows your selection
- Rolling seconds on the clock
- A red eye startup animation with a spinning fan ring, in one replaceable method
- Top bar with Wi-Fi, a clear-apps brush and Settings, each named when selected
- A clear button that force-stops apps opened since boot
- A picture screensaver that can differ from your Home wallpaper
- YouTube and Netflix remote buttons mapped to their apps

No wallpaper or screensaver images are included. Add your own.

## Get started

Follow **[the setup guide](MY-SETUP.md)**. You'll need a computer on the same Wi-Fi as the Chromecast, plus JDK 17, Android SDK 34, Python 3 and `adb`.

Everything is reversible: packages are disabled, never uninstalled, and `setup/restore.sh` puts the stock home back.

## Before you install

- Phone casting stops after the next reboot.
- Holding the Home button does nothing.
- It has been tested on one Chromecast with Google TV 4K (`sabrina`, Google TV 14).
- The clear button relies on the English Settings wording.

The guide lists every limit in full.

## Credit and licence

Built on [cleanyourtv](https://github.com/Gathin23/cleanyourtv) by Gathin, and released under the same [MIT licence](LICENSE).
