# PingGo Android

PingGo Android is the native client for PingGo, a realtime messaging, voice-call, and video-call application. It combines a local-first Room database with REST synchronization, authenticated WebSockets, Firebase Cloud Messaging (FCM), and selectable legacy or LiveKit call engines.

The app is written primarily in Java. Its legacy camera pipeline also contains C/C++, Camera2 NDK, Vulkan, and libjpeg-turbo code.

## Features

- Phone/SMS OTP, email OTP, and Google authentication
- One-to-one and group chats
- Text, image, video, audio, file, and location messages
- Replies, forwarding, editing, deleting, pinning, and delivery/read receipts
- Presence and typing indicators
- Voice and video calls through legacy or LiveKit engines
- Room-backed offline cache, reconnect, and synchronization
- Resumable background attachment uploads and downloads
- FCM notifications
- QR-based companion-device linking and remote logout
- Native camera preprocessing and JPEG transport for legacy video calls

## Requirements

- Android Studio and a Java 11-compatible toolchain
- Android SDK 36 (the project compiles against API 36.1)
- Android NDK `28.2.13676358`
- CMake 3.22.1
- An Android 7.0/API 24 or newer device or emulator
- Access to the PingGo backend and configured Firebase project

The native build currently targets only `arm64-v8a`. Use an ARM64 emulator or physical ARM64 device.

## Getting started

1. Open this directory in Android Studio.
2. Allow Gradle to download the wrapper and dependencies.
3. Confirm `app/google-services.json` targets the intended Firebase project.
4. Configure the backend addresses described below.
5. Build and install the debug application.

Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

macOS or Linux:

```sh
./gradlew :app:assembleDebug
```

The APK is generated under `app/build/outputs/apk/debug/`.

## Backend configuration

REST and WebSocket addresses are defined in:

- `app/src/main/java/com/w3n/pinggo/Database/CloudFunction/RestApi/API.java`
- `app/src/main/java/com/w3n/pinggo/Database/CloudFunction/RestApi/APIAuth.java`

The checked-in production REST base URL is:

```text
https://function.cloudsw3.com/pinggo-app-api/
```

`APIAuth` selects development or release `/ws` and `/media` addresses. An emulator cannot normally reach the development computer through `localhost`; use the emulator host alias or a reachable LAN address.

The manifest currently permits cleartext traffic for development. Production deployments should use HTTPS and WSS.

## Startup and call engines

`SplashScreenActivity` is the launcher. It reads a versioned `AppConfiguration` document, initializes authenticated services for returning users, and routes to login or home.

The remote configuration selects the call engine:

```json
{ "callEngine": "legacy" }
```

- `legacy`: WebRTC audio/signalling plus PingGo's custom WebSocket video path
- `livekit`: LiveKit rooms for voice and video

Missing or invalid values fall back to `legacy`. QA code can override the choice with:

```java
CallEngineToggle.setOverride(context, "livekit");
```

Pass `null` to clear the override. LiveKit requires the companion server's `POST /calls/livekit/token` endpoint and valid LiveKit credentials.

## Architecture

### Presentation

Activities under `activity/` host custom native views under `views/`. Important entry points include:

- `HomeActivity`: chats, calls, and meets navigation
- `ChatActivity`: conversation lifecycle and message actions
- `NewChatActivity`: discovery, groups, forwarding, and call-member selection
- `VoiceCallActivity` and `VideoCallActivity`: legacy calls
- `LiveKitCallActivity`: LiveKit calls
- `LinkedDevicesActivity` and `CompanionLinkActivity`: multi-device flows

### Data and synchronization

`ChatRepository` coordinates the UI with:

- Room entities and DAOs in `data/local/`
- Retrofit REST calls
- realtime events through `ChatWebSocketClient`
- WorkManager attachment transfers
- observable chat, message, presence, and transfer state

The UI renders local state while network responses update it in the background. A typical outgoing message flow is:

1. Create an optimistic local message with a client message ID.
2. Send `send_message` or `send_group_message` through `/ws`.
3. Store and fan out the message on the backend.
4. Receive an acknowledgement and update local state.
5. Resend unacknowledged messages after reconnect when required.

Large attachments use resumable 3 MiB chunks. WorkManager retries background transfers when network conditions allow.

### Realtime channels

- `/ws`: authentication, messages, receipts, edits, deletes, typing, presence, groups, call signalling, and ICE candidates
- `/media`: legacy call rooms, media state, and binary JPEG frames
- FCM: offline message, call, account, and linked-device notifications

### Native video pipeline

`app/src/main/cpp/` builds the `camera_pipeline` shared library. It uses Camera2 NDK, Vulkan, Android media APIs, and libjpeg-turbo for frame conversion, resizing, filtering, rendering, and JPEG encoding. Shader sources live in `app/src/main/shaders/`; compiled SPIR-V assets live in `app/src/main/assets/`.

## Project layout

```text
app/src/main/
├── AndroidManifest.xml
├── java/com/w3n/pinggo/
│   ├── activity/       screen controllers
│   ├── call/           call engines and controllers
│   ├── contacts/       device-contact resolution
│   ├── data/           Room, repositories, workers, caches
│   ├── Database/       REST, WebSocket, Firestore clients
│   ├── notification/   FCM and system notifications
│   └── views/          custom native UI
├── cpp/                camera/Vulkan/JPEG pipeline
├── shaders/            Vulkan shader sources
├── assets/             SPIR-V, emoji, animation assets
└── res/                Android resources
```

## Testing

```powershell
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Current unit coverage includes login flow resolution, call-log behavior, and message-type encoding.

## Troubleshooting

- **No backend connection:** verify the REST, `/ws`, and `/media` addresses and device reachability.
- **WebSocket closes:** verify the stored credential/device ID; revoked companion credentials are rejected.
- **LiveKit call fails:** configure `LIVEKIT_URL`, `LIVEKIT_API_KEY`, and `LIVEKIT_API_SECRET` on the server.
- **Native build fails:** install the exact NDK/CMake versions and use an ARM64 target.
- **Notifications fail:** check `google-services.json`, notification permission, the FCM token, and server Firebase credentials.
- **Contacts are missing:** grant `READ_CONTACTS`; existing direct chats still populate group/call selectors from Room.

## Security notes

- Do not commit signing keys, production credentials, or Firebase service-account files.
- Treat the custom encrypted session credential as sensitive authentication material.
- Use TLS for production REST and WebSocket traffic.
- Review broad media/storage permissions before a public Play Store release.
