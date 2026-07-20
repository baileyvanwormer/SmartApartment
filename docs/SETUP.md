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
| `HA_URL` | Leave as `http://localhost:8123` — both containers share host networking (see Step 2) |
| `HA_TOKEN` | HA → Profile → Security → Long-Lived Access Tokens |
| `HA_SONOS_ENTITY` | HA → Settings → Devices & services → Entities → your Sonos speaker. Check the actual entity ID after Step 3 — it's often not `media_player.living_room` |
| `HA_HUE_SCENE_DOUBLE` | Entity ID for your relax scene (e.g. `scene.living_room_relax`) |
| `HA_HUE_SCENE_TRIPLE` | Entity ID for your party scene |
| `HA_PERSON_ENTITY` | HA names this after your account, not just your first name — check **Settings → People** for the real entity ID (e.g. `person.bailey_van_wormer`) after Step 3 |
| `HA_TTS_ENTITY` | Defaults to `tts.google_translate_en_com`, which HA ships by default — only change if you've set up a different TTS integration |
| `SPOTIFY_PLAYLIST_URL` | Full Spotify playlist URL |
| `BACKEND_WEBHOOK_SECRET` | Any random string — must match `secrets.yaml` |

Set the same webhook secret in `home-assistant/secrets.yaml`:

```yaml
backend_webhook_secret: your-random-string
```

---

## Step 2 — Start the stack

Both containers run with `network_mode: host`, which Sonos/Hue/Shelly discovery needs
(it relies on multicast traffic Docker's default bridge network blocks).

- **Linux (including Raspberry Pi)**: host networking works natively, nothing to do.
- **Docker Desktop for Mac/Windows**: needs a recent version with host networking enabled —
  **Settings → Resources → Network → "Enable host networking"**. If your version doesn't
  have that option, update Docker Desktop first (via the app's own update check, or a fresh
  download from docker.com — this needs your own admin password, so it's on you to run).

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
1. Add **Sonos** integration — it should auto-discover your speaker now that host
   networking is enabled
2. **If it says "no devices found"** even with host networking on, add it by IP instead —
   see the "Sonos not found" troubleshooting entry below
3. Update `HA_SONOS_ENTITY` in `.env` with the correct entity ID (**Settings → Devices &
   services → Entities**, filter for `media_player.`)

### 5. Spotify
1. Add **Spotify** integration. HA routes the OAuth callback through
   `https://my.home-assistant.io/redirect/oauth` (a fixed URL for all HA instances, not
   your local address) — if your Spotify Developer Dashboard app needs a redirect URI,
   register exactly that one, not `localhost`/`127.0.0.1`
2. Also confirm Spotify is linked as a **Content Service** directly in the Sonos app
   (Account → Content Services) — this is what actually lets Sonos play Spotify content;
   the HA integration alone isn't enough. If linking fails with a generic Sonos error,
   check your country setting at sonos.com, or reboot router → speaker → phone in that order
3. The Java backend plays media through HA — no separate Spotify credentials in Java

### 6. iPhone presence (Person entity)
1. Install **Home Assistant Companion** on your iPhone
2. Sign in to your HA instance
3. Enable **Location** permissions (Always or While Using + Background)
4. In HA: **Settings → People → Add person "Bailey"** — note the entity ID it creates
   (HA derives it from your HA account name, e.g. `person.bailey_van_wormer`, not just
   `person.bailey`)
5. Link your iPhone device tracker to the person
6. Confirm the entity shows `home` / `not_home`, and update `HA_PERSON_ENTITY` in `.env`
   to match

After updating `.env`, recreate the backend (not `docker compose restart` — that reuses
the container's original environment and won't pick up changes):

```bash
docker compose up -d backend
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

**Browser prototype:** open **http://localhost:8080** — a mock button that mimics the real Shelly BLU Button.
Click once, twice, three times in quick succession, or press-and-hold, and it calls the same backend endpoints
the real button will. Handy while the hardware isn't wired up yet. If `BACKEND_WEBHOOK_SECRET` is set, expand
**Testing options** on the page and paste it in first (stored only in your browser's local storage).

The long-press "welcome home" greeting only fires if `HA_PERSON_ENTITY` is `home` and changed to `home` within
the arrival window — for testing, set that entity to `home` under **Developer tools → States**, then use the
**Reset welcome-home state** button on the page between tries (it calls `presence/away` without requiring you
to actually leave and come back).

Other ways to trigger actions manually, in Home Assistant → **Developer tools → Services**, or scripts:

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
| `.env` changes don't seem to apply | You need `docker compose up -d <service>` to recreate the container, not `docker compose restart` — the latter reuses the original environment |
| Sonos/Hue/Shelly integration says "no devices found" | Host networking isn't reaching your LAN (Docker Desktop needs it enabled — see Step 2). As a fallback for Sonos specifically, add it by IP: in `home-assistant/secrets.yaml` set `sonos_host` (the speaker's IP, from the Sonos app's device details) and `sonos_advertise_addr` (your Docker host machine's own LAN IP), then add to `configuration.yaml`: `sonos: media_player: hosts: [!secret sonos_host]` and `advertise_addr: !secret sonos_advertise_addr` |
| Sonos commands intermittently time out | Battery-powered speakers (e.g. Sonos Roam) can drop Wi-Fi more often than plugged-in ones. Also check the speaker didn't get a new DHCP IP — a router-side DHCP reservation avoids this permanently |
| Spotify won't play, `UPnP Error 800` in HA logs | Almost always a Sonos-side Spotify account issue, not HA/this project. Remove and re-add Spotify under Sonos app → Account → Content Services; also check for duplicate Spotify accounts linked there |
| Spotify OAuth: Spotify says redirect URI is invalid | Your Spotify Developer Dashboard app must have `https://my.home-assistant.io/redirect/oauth` registered exactly — not your instance's own URL |
| Welcome home skipped | Confirm your person entity (check the real ID under **Settings → People**, not necessarily `person.bailey`) is `home` and changed within the arrival window |
| TTS call succeeds (HTTP 200) but no audio, music just pauses/resumes | Classic Docker Desktop for Mac symptom: HA is advertising its internal VM address (something like `192.168.65.x`) in the generated audio URL, which devices on your real LAN can't reach. Fix: set `ha_internal_url: http://<your-mac-lan-ip>:8123` in `secrets.yaml` and add `homeassistant: internal_url: !secret ha_internal_url` to `configuration.yaml`, then `docker compose restart homeassistant`. Verify by calling `POST /api/tts_get_url` and checking the returned URL's host matches your real LAN IP |

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
