const CLICK_WINDOW_MS = 350;
const LONG_PRESS_MS = 600;
const SECRET_STORAGE_KEY = "smartapartment.webhookSecret";

const ACTION_LABELS = {
  single: "Single press → Spotify playlist on Sonos",
  double: "Double press → Hue relax scene",
  triple: "Triple press → Hue party scene",
  long: "Long press → Welcome home greeting",
};

const button = document.getElementById("smart-button");
const statusEl = document.getElementById("status");
const logList = document.getElementById("log-list");
const secretInput = document.getElementById("webhook-secret");
const resetButton = document.getElementById("reset-presence");

let clickCount = 0;
let clickTimer = null;
let longPressTimer = null;
let longPressFired = false;

secretInput.value = localStorage.getItem(SECRET_STORAGE_KEY) || "";
secretInput.addEventListener("change", () => {
  localStorage.setItem(SECRET_STORAGE_KEY, secretInput.value.trim());
});

function setStatus(message, kind) {
  statusEl.textContent = message;
  statusEl.className = "status" + (kind ? ` ${kind}` : "");
}

function addLogEntry(label, detail, ok) {
  const item = document.createElement("li");
  item.className = ok ? "" : "error";

  const time = document.createElement("span");
  time.className = "time";
  time.textContent = new Date().toLocaleTimeString();

  const text = document.createElement("span");
  text.textContent = detail ? `${label} — ${detail}` : label;

  item.append(text, time);
  logList.prepend(item);
}

async function callAction(url) {
  const secret = localStorage.getItem(SECRET_STORAGE_KEY);
  const headers = {};
  if (secret) {
    headers["X-Webhook-Secret"] = secret;
  }

  const response = await fetch(url, { method: "POST", headers });
  const raw = await response.text();
  let body = {};
  if (raw) {
    try {
      body = JSON.parse(raw);
    } catch {
      body = { detail: raw };
    }
  }

  if (!response.ok) {
    throw new Error(body.detail || body.message || `HTTP ${response.status}`);
  }
  return body;
}

async function triggerButtonAction(type) {
  setStatus(`Sending ${type} press…`, "pending");
  try {
    const body = await callAction(`/api/actions/button/${type}`);
    setStatus(ACTION_LABELS[type] || type, "ok");
    addLogEntry(type, body.message || body.action, true);
  } catch (err) {
    setStatus(`${type} press failed: ${err.message}`, "error");
    addLogEntry(type, err.message, false);
  }
}

function resolvePressType() {
  if (clickCount >= 3) return "triple";
  if (clickCount === 2) return "double";
  return "single";
}

function pressStart(event) {
  event.preventDefault();
  longPressFired = false;
  button.classList.add("pressed");

  longPressTimer = setTimeout(() => {
    longPressFired = true;
    clearTimeout(clickTimer);
    clickCount = 0;
    button.classList.add("active-long");
    triggerButtonAction("long");
  }, LONG_PRESS_MS);
}

function pressEnd(event) {
  event.preventDefault();
  button.classList.remove("pressed");
  clearTimeout(longPressTimer);

  if (longPressFired) {
    button.classList.remove("active-long");
    return;
  }

  clickCount += 1;
  clearTimeout(clickTimer);
  clickTimer = setTimeout(() => {
    const type = resolvePressType();
    clickCount = 0;
    triggerButtonAction(type);
  }, CLICK_WINDOW_MS);
}

function pressCancel() {
  clearTimeout(longPressTimer);
  button.classList.remove("pressed", "active-long");
}

button.addEventListener("pointerdown", pressStart);
button.addEventListener("pointerup", pressEnd);
button.addEventListener("pointerleave", pressCancel);
button.addEventListener("pointercancel", pressCancel);
button.addEventListener("contextmenu", (e) => e.preventDefault());

resetButton.addEventListener("click", async () => {
  try {
    const body = await callAction("/api/actions/presence/away");
    addLogEntry("reset", body.message || body.action, true);
    setStatus("Welcome-home state reset.", "ok");
  } catch (err) {
    addLogEntry("reset", err.message, false);
    setStatus(`Reset failed: ${err.message}`, "error");
  }
});
