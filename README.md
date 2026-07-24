# SmartApartment

A smart apartment control system triggered by a **Shelly BLU RC Button 4** (4-button remote), orchestrated by a **Java backend** and **Home Assistant**.

## What it does

Each of the remote's 4 physical buttons maps directly to one action — no multi-click detection needed.

| Button | Action |
|--------|--------|
| **1** (single tap) | Play if idle, pause if playing, resume (not restart) if paused |
| **1** (double tap) | Skip to the next track — only meaningful while something's playing |
| **2** | Activate a Philips Hue scene |
| **3** | Activate a second Hue scene |
| **4** | Activate a third Hue scene, and say "Hello Bailey, welcome home" if you've just arrived (presence-gated; will later be triggered automatically on arrival instead of by button) |

## Architecture

```
┌─────────────────┐     BLE      ┌──────────────────┐
│ Shelly BLU      │ ───────────► │ Shelly BLU       │
│ Button          │              │ Gateway (USB)    │
└─────────────────┘              └────────┬─────────┘
                                          │ Wi-Fi
                                          ▼
                                 ┌──────────────────┐
                                 │ Home Assistant   │
                                 │  • BTHome        │
                                 │  • Hue / Sonos   │
                                 │  • Spotify       │
                                 │  • iPhone presence│
                                 └────────┬─────────┘
                                          │ webhook
                                          ▼
                                 ┌──────────────────┐
                                 │ Java Backend       │
                                 │  • Action routing  │
                                 │  • Welcome home    │
                                 │  • Playlist env    │
                                 └────────┬─────────┘
                                          │ HA REST API
                          ┌───────────────┼───────────────┐
                          ▼               ▼               ▼
                     Spotify          Sonos           Philips Hue
```

### Why this split?

- **Home Assistant** handles device integrations natively (Shelly BTHome, Hue, Sonos, Spotify OAuth, iPhone tracking). Rebuilding these in Java would mean reimplementing OAuth, UPnP, and BLE.
- **Java backend** (Spring Boot) owns business logic you can extend in code: welcome-home state, playlist URL from environment variables, future features.

## Stack

| Component | Choice |
|-----------|--------|
| Button | Shelly BLU RC Button 4 (paired in Bluetooth mode — it also supports Zigbee, which this project doesn't use) |
| Gateway | Shelly BLU Gateway |
| Hub | Home Assistant (Docker) |
| Backend | Java 21 + Spring Boot 3 |
| Music | Spotify Premium |
| Speaker | Sonos |
| Lights | Philips Hue Bridge + bulb |
| Presence | iPhone via HA Companion app |

## Quick start

```bash
cp .env.example .env
cp home-assistant/secrets.yaml.example home-assistant/secrets.yaml
# Edit .env and secrets.yaml with your values

docker compose up -d --build
```

Both containers run with `network_mode: host` — required for Sonos/Hue/Shelly discovery,
which relies on multicast traffic that Docker's default bridge network blocks. **On Docker
Desktop for Mac or Windows**, this needs a fairly recent version with host networking
enabled (Settings → Resources → Network → "Enable host networking"); see
**[docs/SETUP.md](docs/SETUP.md)** if discovery doesn't find your devices even with that on.
On Linux hosts (including a Raspberry Pi), host networking works natively — nothing to enable.

Open Home Assistant at http://localhost:8123 and follow **[docs/SETUP.md](docs/SETUP.md)** for integration setup.

Once HA integrations are wired up, open **http://localhost:8080** for a browser prototype of the physical button —
click once/twice/three-times or press-and-hold to trigger the same actions the real Shelly BLU Button will.

## Configuration

All secrets and entity IDs live in `.env` (backend) and `home-assistant/secrets.yaml` (HA webhooks). See `.env.example` for the full list.

Key variables:

- `SPOTIFY_PLAYLIST_URL` — playlist to play on button 1 (toggles play/pause if already playing)
- `HA_SONOS_ENTITY` — Sonos media player entity in HA
- `HA_HUE_SCENE_DOUBLE` / `HA_HUE_SCENE_TRIPLE` / `HA_HUE_SCENE_LONG` — Hue scene entity IDs for buttons 2, 3, and 4 (`HA_HUE_SCENE_LONG` is optional — leave blank to skip activating a scene on button 4)
- `HA_PERSON_ENTITY` — person entity for welcome-home logic. HA names this after your
  account (e.g. `person.jane_doe`, not just `person.jane`) — check
  **Settings → People** for the actual entity ID rather than assuming the default
- `HA_TTS_ENTITY` — TTS engine entity used to speak the welcome message
  (defaults to `tts.google_translate_en_com`, which HA ships out of the box)
- `WELCOME_HOME_MESSAGE` — TTS greeting text

## API endpoints

Called by Home Assistant `rest_command` automations:

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/actions/button/single` | Play / pause / resume Spotify |
| `POST` | `/api/actions/button/skip` | Skip to next track |
| `POST` | `/api/actions/button/double` | Hue scene 1 |
| `POST` | `/api/actions/button/triple` | Hue scene 2 |
| `POST` | `/api/actions/button/long` | Hue scene 3 + welcome home |
| `POST` | `/api/actions/presence/away` | Reset welcome state |
| `GET` | `/health` | Health check |

All POST endpoints accept header `X-Webhook-Secret` when `BACKEND_WEBHOOK_SECRET` is set.

## Local development

```bash
cd backend
mvn test
export $(grep -v '^#' ../.env | xargs)
mvn spring-boot:run
```

Requires Java 21.
