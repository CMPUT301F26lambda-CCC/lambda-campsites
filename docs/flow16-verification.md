# Flow 16 — Scan a campsite QR code

> AI use: GPT-6 (OpenAI), through Codex, assisted me with Flow 16.

Last updated: November 3, 2026.

Scope: [Flow 16 #133](https://github.com/CMPUT301F26lambda-CCC/lambda-campsites/issues/133)
and [US 01.12.01 #25](https://github.com/CMPUT301F26lambda-CCC/lambda-campsites/issues/25).
The story author and issue assignments remain unchanged.

## Acceptance and implementation

| Requirement | Implementation | Verification |
| --- | --- | --- |
| Start from Home | The entrant Home action opens a full-screen scanner. | Scanner Compose test checks title, hint and torch action. |
| Request camera access | The scanner requests Android camera permission. Denial explains why access is needed and offers Open Settings and Not now. | Compose test checks denied actions. The manifest marks the camera optional so devices without one can still install. |
| Open a valid campsite | CameraX feeds QR frames to the existing Flow 3 `QRCode` format. The controller checks the current published campsite, then opens the existing campsite details route. Its lottery actions remain on that details screen. | Decoder tests cover contiguous and padded camera planes. Controller tests cover published lookup and one-time navigation. |
| Handle bad or missing codes | Invalid payloads, missing/unpublished campsites and lookup failures keep the scanner open with a message. A camera binding failure gives a safe exit. | Controller tests cover invalid, missing, unpublished and failed lookups; Compose test covers unavailable camera. |

## Figma alignment

The [team's Figma file](https://www.figma.com/design/usfTYZWNV25DRKPhZJjPdE/Lambda-Campsite-UI?node-id=59370-9856)
was inspected read-only. The Home scan action follows `59262:1273`; the scanner
follows `59262:1366`; denied permission follows `59263:976`; and the invalid-code
message follows `59263:1040`. The three scanner icons come from those Figma
frames as transparent PNGs. The live camera preview replaces the mockup's sample
QR image. Colors, 263dp viewfinder brackets, rounded permission surface and
action labels follow the mockups.

## Code structure

The scanner screen owns camera permission and presentation. `QrCameraPreview`
binds CameraX preview and analysis to the Compose lifecycle and closes each
frame. `QrFrameDecoder` reads the frame's Y plane with its row and pixel strides.
`QrScanController` owns payload validation, campsite lookup and navigation
state; it uses the existing `CampsiteRepository` rather than duplicating
Firestore code. No owner QR generation or Search screen code changes are needed.

## Local verification

- The full debug unit suite passed on current `main`: 198 tests, no failures.
- All three scanner Compose tests passed on the Android 17 emulator.
- The debug APK assembled successfully.
- Team review and passing CI are required before merge, per `docs/CONTRIBUTING.md`.
