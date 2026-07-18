# SmartApartment Setup Guide

This project uses a **two-layer architecture**:

| Layer | Role |
|-------|------|
| **Home Assistant** | Device integrations — Shelly BLU Button, Hue, Sonos, Spotify, iPhone presence |
| **Java backend** | Business logic — action routing, welcome-home state, playlist from env vars |

```
Shelly BLU Button → BLU Gateway → Home Assistant (BTHome)
                                        ↓ webhook
                                 Java Backend (Spring Boot)
                                        ↓ HA REST API
                              Spotify / Sonos / Hue / TTS
```

---

## Prerequisites

### Hardware
- Shelly BLU Button + Shelly BLU Gateway (USB by your front door)
- Philips Hue Bridge + bulbs
- Sonos speaker
- Spotify Premium
- iPhone with Home Assistant Companion app
- Machine to run Docker (Raspberry Pi 4/5, mini PC, or Mac)

### Software
- [Docker Desktop](https://www.docker.com/products/docker-desktop/) (or Docker Engine on Pi)
- Spotify account linked to Sonos in the Sonos app

---

## Step 1 — Configure environment

```bash
cp .env.example .env
cp home-assistant/secrets.yaml.example home-assistant/secrets.yaml
```

Edit `.env` with your values:

| Variable | Where to find it |
|----------|------------------|
| `HA_TOKEN` | HA → Profile → Security → Long-Lived Access Tokens |
| `HA_SONOS_ENTITY` | HA → Settings → Entities → your Sonos speaker |
| `HA_HUE_SCENE_DOUBLE` | Entity ID for your relax scene (e.g. `scene.living_room_relax`) |
| `HA_HUE_SCENE_TRIPLE` | Entity ID for your party scene |
| `HA_PERSON_ENTITY` | `person.bailey` after iPhone setup |
| `SPOTIFY_PLAYLIST_URL` | Full Spotify playlist URL |
| `BACKEND_WEBHOOK_SECRET` | Any random string — must match `secrets.yaml` |

Set the same webhook secret in `home-assistant/secrets.yaml`:

```yaml
backend_webhook_secret: your-random-string
```

---

## Step 2 — Start the stack

```bash
docker compose up -d --build
```

- Home Assistant: http://localhost:8123
- Java backend health: http://localhost:8080/health

On first HA launch, complete the onboarding wizard.

---

## Step 3 — Home Assistant integrations

Enable these in **Settings → Devices & services → Add integration**:

### 1. Shelly (BLU Gateway Bluetooth proxy)
1. Add **Shelly** integration and discover your BLU Gateway
2. For the gateway device: **Configure → enable Bluetooth proxy** (passive or active)

### 2. BTHome (Shelly BLU Button)
1. Add **BTHome** integration
2. Press the Shelly BLU Button once when prompted to pair
3. Confirm button events appear under the device

### 3. Philips Hue
1. Add **Philips Hue** integration
2. Press the Hue Bridge button when prompted
3. Create two scenes in the Hue app (e.g. "Relax", "Party")
4. Note their entity IDs in HA and update `.env`

### 4. Sonos
1. Add **Sonos** integration (auto-discovers on your network)
2. Update `HA_SONOS_ENTITY` in `.env` with the correct entity ID

### 5. Spotify
1. Add **Spotify** integration and complete OAuth
2. The Java backend plays media through HA — no separate Spotify credentials in Java

### 6. iPhone presence (Person entity)
1. Install **Home Assistant Companion** on your iPhone
2. Sign in to your HA instance
3. Enable **Location** permissions (Always or While Using + Background)
4. In HA: **Settings → People → Add person "Bailey"**
5. Link your iPhone device tracker to the person
6. Confirm `person.bailey` shows `home` / `not_home`

Restart the backend after updating `.env`:

```bash
docker compose restart backend
```

---

## Step 4 — Wire button automations

The repo includes automation templates in `home-assistant/automations/button_actions.yaml`.

**Recommended:** use the HA UI instead of editing YAML blindly:

1. **Settings → Automations → Create automation**
2. Trigger: **Device → Shelly BLU Button → Single press**
3. Action: **Perform action → rest_command.smart_apartment_button_single**
4. Repeat for double, triple, and long press

Or replace `REPLACE_WITH_YOUR_DEVICE_ID` in `button_actions.yaml` with your button's device ID (found in HA device page → URL contains the ID).

### Press map

| Press | Backend endpoint | Action |
|-------|------------------|--------|
| Single | `/api/actions/button/single` | Play Spotify playlist on Sonos |
| Double | `/api/actions/button/double` | Hue relax scene |
| Triple | `/api/actions/button/triple` | Hue party scene |
| Long | `/api/actions/button/long` | Welcome home (if just arrived) |

The **presence away** automation in `presence.yaml` resets welcome-home state when you leave.

---

## Step 5 — Test without the button

In Home Assistant → **Developer tools → Services**, or run scripts:

- `script.play_spotify_test`
- `script.welcome_home_test`

Or curl the backend directly:

```bash
curl -X POST http://localhost:8080/api/actions/button/single \
  -H "X-Webhook-Secret: your-random-string"
```

---

## Step 6 — Run Java locally (optional, without Docker)

Requires Java 21 and Maven:

```bash
cd backend
export $(grep -v '^#' ../.env | xargs)
mvn spring-boot:run
```

Set `HA_URL=http://localhost:8123` when running HA in Docker alongside.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Button not detected | Move BLU Gateway within 10 m of button; re-pair in BTHome |
| Backend 401 | Match `BACKEND_WEBHOOK_SECRET` in `.env` and `secrets.yaml` |
| Spotify won't play | Confirm Spotify linked in HA; check Sonos entity ID; playlist URL valid |
| Welcome home skipped | Confirm `person.bailey` is `home` and changed within 15 min |
| TTS silent on Sonos | In HA, check **Settings → Voice assistants → Text-to-speech** and set a default TTS engine |

---

## Project layout

```
SmartApartment/
├── backend/                  # Java Spring Boot orchestration
├── home-assistant/             # HA config, automations, rest commands
├── docker-compose.yml          # HA + backend
├── .env.example                # Environment template
└── docs/SETUP.md               # This file
```
