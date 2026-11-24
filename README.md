# UGV Control for Android

A native Android app for driving a **Waveshare UGV Rover** (Raspberry Pi running `ugv_rpi/app.py`
plus the ESP32 sub-controller), built for phones in landscape. It's the phone sibling of the macOS
app [ugv-rover-mac](https://github.com/mikhailbartashevich/ugv-rover-mac), and it speaks the same protocol.

**Requires Android 16 (API 36) or newer** (targets API 37). Kotlin 2.4, Jetpack Compose (BOM 2026.11),
Material 3, OkHttp 5, coroutines, and [Haze](https://github.com/chrisbanes/haze) for the liquid-glass blur.

The UI is "liquid glass" like macOS: every panel is frosted glass that blurs the live video behind it,
with a light-catching rim, a top sheen and a soft shadow. The sticks are glass discs with glossy knobs that spring back.

## Features

- **Connection**: the address is saved. *Find rover* tries the saved address, then the rover's own
  hotspot (AccessPopup, `192.168.50.5`), then scans the phone's /24 networks on port 5000 for the rover's web app.
  When the rover hotspot has no internet, the app pins itself to that Wi‑Fi so Android doesn't send rover traffic over mobile data.
- **Live video**: full-screen H.264 over RTSP from the rover's mediamtx (`rtsp://<ip>:8554/cam`, 1280×960),
  played by Media3 ExoPlayer over TCP with a near-zero buffer for low latency. It reconnects on errors and stalls.
  There's a centre reticle, and fill/fit is a toggle in the connection dialog.
- **Telemetry**: battery voltage and % for the 3S pack (9.6 V empty … 12.6 V full, **LOW** below 10 V), CPU,
  temperature, Wi‑Fi RSSI, and video fps.
- **Driving**:
  - **Left thumb drive stick**: touch anywhere on the left half and the stick appears under your thumb.
    It sends `T:13` (X = m/s, Z = rad/s), scaled by the speed preset, re-sent every 150 ms while held,
    with a stop on release. There's a dead zone and an expo curve for fine control.
  - **Right thumb camera stick**: rate control for pan/tilt (`T:133`). The camera stays where you leave it.
    Double-tap to look ahead.
  - Big red **STOP** button (sends over the socket and HTTP), and **Slow / Mid / Fast** presets (0.39 / 0.86 / 1.3 m/s).
  - Haptics on grab, at full deflection, on release, on buttons, and on STOP.
  - The screen stays awake while connected. The rover stops when the app goes to the background.
- **Lights and media**: head light Off → Auto → On, chassis light, photo, record start/stop with a timer, zoom 1×/2×/4×.
- **Bluetooth/USB gamepad**: left stick or D-pad drives, the right stick moves the camera. A photo · B STOP · X head light ·
  Y record · L1/R1 speed · R3 look ahead · Select base light · Start zoom.
- Every control has a content description, and a long press shows a tooltip. The **?** button explains all controls.

## Permissions

- **Nearby devices / local network access** (`ACCESS_LOCAL_NETWORK`). From Android 17, an app can't reach
  devices on the local network, such as the rover, without it. Without it, traffic just times out. The app asks on first launch.
  If you deny it, the offline card shows a **Grant access** button.
- Internet and network state (both granted automatically).

## Project layout

```
roverkit/   Android library, no UI: protocol + network + control logic (unit-tested on the JVM)
  protocol/   RoverCodes (CtrlCommand), Esp32Command (JSON encoding), SocketIo (frames), Telemetry, RoverAddress
  net/        RoverSocket (Socket.IO over OkHttp WebSocket), RoverHttp, RoverScanner
  control/    RoverController (drive/camera loops, lights, media), DriveMath (stick → command)
app/        Compose UI
  control/    RoverViewModel, RoverVideoPlayer (Media3 RTSP), NetworkMonitor, GamepadInput
  ui/screen/  ControlScreen
  ui/components/  FloatingStick, TelemetryChips, ControlButtons, StatusViews, Hinted, Haptics
  ui/dialogs/ ConnectionDialog, HelpDialog
  ui/theme/   colours, theme, Glass.kt (the liquid-glass modifier and glossy knob drawing)
```

## Protocol

The app speaks to Waveshare **ugv_rpi on the `refactor/debian12-2025.10.01-py3.11` branch**, the same as the macOS app.

- **Socket.IO** v5 / Engine.IO v4 at `ws://<ip>:5000/socket.io/?EIO=4&transport=websocket`. On `0{…}` the app joins `/ctrl` and `/json`,
  and answers ping `2` with `3`.
- **ESP32 commands** (driving T:13, wheels T:1, gimbal T:133) go out as `42/json,["json",{"T":13,"X":0.30,"Z":0}]`.
  The HTTP fallback is `POST /send_command` with `command=base -c {json}`.
- **Mode commands** go out as `42/ctrl,["message","{\"cmd\":X,\"mode\":Y}"]`, like the web UI's `cmdSend`:

  | Action | cmd | mode |
  |---|---|---|
  | Head light | 102 | 10404 off · 10405 auto · 10406 on |
  | Chassis light | 115 | PWM 0…255 |
  | Zoom | 10104 | 1 · 2 · 4 |
  | Photo | 10201 | 0 |
  | Record | 10203 | 1 start · 0 stop |
  | Movement lock | 114 | 10501 lock · 10502 unlock |
  | Vision mode / reaction | 101 / 103 | 10301…10309 / 10401…10403 |

- **Telemetry** arrives as `42/ctrl,["update",{…}]`: 106 CPU, 107 temperature, 108 RAM, 111 RSSI, 112 volts×100, 113 fps,
  102 head light (0/1/2), 115 chassis PWM. 101 and 103 arrive as floats. Updates with 112 = 0 are ignored.
- **Video** is H.264 at `rtsp://<ip>:8554/cam`. The RTSP port is the web port + 3554, so a mock on :5055 streams on :8609.
  The old MJPEG `/video_feed` and `video -q` are gone.
- **Media**: photos are at `GET /media/pictures/<name>`, videos at `/videos/<name>`, and the lists come from `/get_photo_names` and `/get_video_names`.

On the Android emulator, the goldfish hardware H.264 decoder fails on this stream, so the app tries goldfish codecs last.
Real phones use their hardware decoder.

## Build

You need Android Studio's JDK and SDK. The Gradle wrapper downloads everything else, including SDK Platform 37.2 the first time.

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

```bash
./gradlew assembleDebug
```

The APK lands in `app/build/outputs/apk/debug/app-debug.apk`. To run the unit tests:

```bash
./gradlew :roverkit:testDebugUnitTest
```

## Install on a phone with adb

1. On the phone, open **Settings → About phone** and tap **Build number** 7 times.
   Then open **Settings → System → Developer options** and turn on **USB debugging**.
2. Connect the phone by USB and accept the "Allow USB debugging?" prompt. Or pair over Wi‑Fi with
   **Wireless debugging** → `adb pair <ip:port>`, then `adb connect <ip:port>`.
3. Install the app:

```bash
~/Library/Android/sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
```

4. On first launch, allow **Nearby devices** (local network access).
5. Join the rover's Wi‑Fi (or the same network as the rover) and open **UGV Control**. It connects to the saved
   address or searches for the rover.

To start the app pointed at a specific address (handy for testing):

```bash
adb shell am start -n io.github.mikhailbartashevich.ugvcontrol/.MainActivity --es host 192.168.50.5
```

## Testing with the mock rover

The macOS repo ships a stand-in rover (`Tools/mock-rover`). Run it on the Mac, listening on all interfaces:

```bash
python3 -m venv .venv && .venv/bin/pip install -r ../ugv-rover-mac/Tools/mock-rover/requirements.txt
```

```bash
.venv/bin/python ../ugv-rover-mac/Tools/mock-rover/mock_rover.py 5055 --host 0.0.0.0
```

From the Android emulator, the Mac is `10.0.2.2`:

```bash
adb shell am start -n io.github.mikhailbartashevich.ugvcontrol/.MainActivity --es host 10.0.2.2:5055
```

It serves H.264 over RTSP at web port + 3554 via ffmpeg (needs `ffmpeg` on the Mac). The mock logs every command it receives to `Tools/mock-rover/data/commands.log`.
