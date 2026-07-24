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
- Shelly BLU RC Button 4 (US ZB) + Shelly BLU Gateway (USB by your front door). This button
  supports both Bluetooth and Zigbee — **pair it in Bluetooth mode** (blue flashing light,
  not purple) so it works through the Gateway; this project doesn't use its Zigbee mode
- Philips Hue Bridge + bulb
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
1. Plug the Gateway into any USB power source (it only needs power, not a data
   connection — configure its Wi-Fi via the Shelly app first if you haven't)
2. Add the **Shelly** integration in HA — it should discover the Gateway automatically
3. On the Gateway's device page: **Configure → enable Bluetooth proxy**, set to **passive**
   (that's what BTHome needs)

### 2. BTHome (Shelly BLU RC Button 4)
1. Put the button into pairing mode (see its included instructions for the exact sequence)
   — confirm the light flashes **blue** (Bluetooth), not purple (Zigbee)
2. In HA, check **Settings → Devices & services** for a "Discovered" BTHome card, or
   add the **BTHome** integration manually and let it find the button via the Gateway's proxy
3. **Press each of the 4 buttons at least once** — each button's event entity
   (`event.*_button_1` through `_4`) is only created once that specific button has fired,
   so pressing just one won't register the other three
4. Confirm all 4 show up: **Settings → Devices & services → Entities**, filter for "button"

### 3. Philips Hue
1. Plug the Bridge into your router via ethernet, power it on
2. In the Philips Hue app, let it find and set up the Bridge
3. Since your bulb may already be paired directly via Bluetooth, the app should offer to
   move it under the Bridge — follow that prompt (direct-BLE and Bridge-based are separate
   pairing modes)
4. Add the **Philips Hue** integration in HA — press the physical button on the Bridge
   when prompted
5. Create a scene per button you want it wired to (this project uses buttons 2, 3, and 4)
   in the Hue app — any names are fine, they don't need to match "relax"/"party" literally
6. Once synced to HA, find their entity IDs (**Settings → Devices & services → Entities**,
   filter `scene.`) and update `HA_HUE_SCENE_DOUBLE` / `HA_HUE_SCENE_TRIPLE` /
   `HA_HUE_SCENE_LONG` in `.env` to match

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

The repo includes automations in `home-assistant/automations/button_actions.yaml`, already
written for the 4-button remote's BTHome event entities. Each button press updates its
entity's `event_type` attribute (`press`, `double_press`, `triple_press`, `long_press`, etc.)
— automations trigger on a **state trigger filtered by that attribute**, not a generic
device trigger, since the button already fired once during Step 3's pairing.

**You'll almost certainly need to edit the entity IDs** — they're derived from your
specific device's ID (e.g. `event.bthome_sensor_d37d_button_1`), so copy the real ones from
**Settings → Devices & services → Entities** (filter "button") into `button_actions.yaml`,
replacing the placeholders already there.

### Press map

| Button / gesture | Backend endpoint | Action |
|-------------------|------------------|--------|
| Button 1, single tap | `/api/actions/button/single` | Play if idle, pause if playing, resume if paused |
| Button 1, double tap | `/api/actions/button/skip` | Skip to next track |
| Button 2 | `/api/actions/button/double` | Hue scene 1 |
| Button 3 | `/api/actions/button/triple` | Hue scene 2 |
| Button 4 | `/api/actions/button/long` | Hue scene 3 + welcome home (if just arrived) |

After editing `button_actions.yaml` or `rest_commands.yaml`, reload:

```bash
# automations only (fast, no downtime):
curl -X POST -H "Authorization: Bearer $HA_TOKEN" http://localhost:8123/api/services/automation/reload

# rest_commands.yaml changes need a full restart — automation.reload doesn't cover it:
docker compose restart homeassistant
```

The **presence away** automation in `presence.yaml` resets welcome-home state when you leave.

---

## Step 5 — Test without the button

**Browser prototype:** open **http://localhost:8080** — a mock button that calls the same backend endpoints
the real hardware does. Handy for testing without pressing anything physical. Note this UI still simulates the
original single-button/multi-click design (click once/twice/three-times/hold) rather than 4 discrete buttons —
it still exercises the same endpoints correctly, just via a different (older) input mapping than the real
remote now uses. If `BACKEND_WEBHOOK_SECRET` is set, expand **Testing options** on the page and paste it in
first (stored only in your browser's local storage).

The long-press "welcome home" greeting only fires if `HA_PERSON_ENTITY` is `home` and changed to `home` within
the arrival window (the Hue scene on that same button fires regardless) — for testing, set that entity to
`home` under **Developer tools → States**, then use the **Reset welcome-home state** button on the page between
tries (it calls `presence/away` without requiring you to actually leave and come back).

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
| Button pairs, but stuck flashing purple | That's Zigbee mode — this project needs Bluetooth. Check the button's manual for the mode-cycle sequence to get it flashing blue instead, then re-pair |
| Backend 401 | Match `BACKEND_WEBHOOK_SECRET` in `.env` and `secrets.yaml` |
| `.env` changes don't seem to apply | You need `docker compose up -d <service>` to recreate the container, not `docker compose restart` — the latter reuses the original environment |
| Automations show `last_triggered` updating (so the button press is detected), but nothing actually happens | Check `home-assistant/rest_commands.yaml` — the URLs must point at `http://localhost:8080`, not the old Docker Compose service name `http://backend:8080` (that stopped resolving once both containers moved to `network_mode: host`). Changes to this file need a full `docker compose restart homeassistant`, not just `automation.reload` |
| Only 1 of 4 button entities shows up after pairing | Each button's `event.*_button_N` entity is only created once that specific button has been pressed at least once — press all 4 before checking |
| Button 1 doesn't tell single-tap from double-tap correctly | Its automations must trigger on the entity's `event_type` attribute specifically (`attribute: event_type`, `to: press` / `to: double_press`), not a plain state-change trigger — a plain state trigger fires identically for every click type |
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
