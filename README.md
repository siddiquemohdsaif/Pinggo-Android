# PingGo-Android

## Call engines

PingGo keeps the original direct WebRTC audio/custom WebSocket video stack and
adds LiveKit as an opt-in engine. The remote `AppConfiguration` document selects
the engine:

```json
{ "callEngine": "legacy" }
```

Use `"livekit"` to route new direct and group voice/video calls through LiveKit.
Missing or invalid values always fall back to `legacy`. QA builds can override
the remote value with `CallEngineToggle.setOverride(context, "livekit")`; pass
`null` to clear the override.

LiveKit requires the companion Node server to expose `POST /calls/livekit/token`
and to have `LIVEKIT_URL`, `LIVEKIT_API_KEY`, and `LIVEKIT_API_SECRET` configured.
