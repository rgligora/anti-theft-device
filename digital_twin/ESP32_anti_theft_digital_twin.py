#!/usr/bin/env python3
import time
import math
import random
import json
import paho.mqtt.client as mqtt

# ─── CONFIG ────────────────────────────────────────────────────────────────────
TB_SERVER        = "161.53.133.253"
TB_PORT          = 1883
TB_DEVICE_TOKEN  = "xgRHGG2KZk18tGJqRm3l"
CLIENT_ID        = f"dtwin_{random.randint(0,9999)}"

TELEMETRY_TOPIC       = "v1/devices/me/telemetry"
RPC_REQUEST_TOPIC     = "v1/devices/me/rpc/request/+"
RPC_RESPONSE_PREFIX   = "v1/devices/me/rpc/response/"

TELEMETRY_INTERVAL    = 5.0    # seconds
MOTION_PROBABILITY    = 0.20   # ~20% chance per check once armed

# Mocked GPS path (Zagreb → northeast)
MOCK_PATH = [
    (45.8050, 15.9419),
    (45.8180, 15.9570),
    (45.8300, 15.9600),
    (45.8420, 15.9750),
    (45.7950, 15.9880),
    (45.8200, 16.0020),
]
DIST_PER_STEP_KM = 0.042  # ~30 km/h → 0.042 km / 5s
# ────────────────────────────────────────────────────────────────────────────────

# State
armed          = False
in_alert       = False
currentLat     = 0.0
currentLon     = 0.0
last_direction = None   # None or one of "N","E","S","W"

# MQTT client
client = mqtt.Client(client_id=CLIENT_ID)

def on_connect(client, userdata, flags, rc, properties=None):
    print(f"[CONNECT] rc={rc}")
    client.subscribe(RPC_REQUEST_TOPIC)
    print(f"[MQTT] Subscribed to {RPC_REQUEST_TOPIC}")

def on_message(client, userdata, msg):
    global armed, in_alert, currentLat, currentLon, last_direction

    topic = msg.topic
    payload = msg.payload.decode()
    print(f"[RPC RECEIVED] {topic} → {payload}")
    request_id = topic.split("/")[-1]

    try:
        j = json.loads(payload)
        method = j.get("method")
    except Exception:
        print("[RPC] invalid JSON")
        return

    if method == "armDevice":
        armed      = True
        in_alert   = False
        last_direction = None

        # pick initial spot
        currentLat, currentLon = random.choice(MOCK_PATH)

        # publish initial ARM telemetry (exactly as firmware)
        arm_payload = {
            "armed":    True,
            "latitude":  round(currentLat, 6),
            "longitude": round(currentLon, 6)
        }
        client.publish(TELEMETRY_TOPIC, json.dumps(arm_payload))
        print("[TEL]", arm_payload)

        # RPC response
        resp = {"armed": True}
        client.publish(RPC_RESPONSE_PREFIX + request_id, json.dumps(resp))
        print(f"[RPC RESP] {resp}")

    elif method == "disarmDevice":
        armed      = False
        in_alert   = False

        # publish initial DISARM telemetry (exactly as firmware)
        disarm_payload = {
            "armed":    False,
            "latitude":  round(currentLat, 6),
            "longitude": round(currentLon, 6)
        }
        client.publish(TELEMETRY_TOPIC, json.dumps(disarm_payload))
        print("[TEL]", disarm_payload)

        # RPC response
        resp = {"armed": False}
        client.publish(RPC_RESPONSE_PREFIX + request_id, json.dumps(resp))
        print(f"[RPC RESP] {resp}")

    else:
        print("[RPC] unknown method")

def updateGPSMock():
    global last_direction, currentLat, currentLon
    dirs = ["N","E","S","W"]
    opp  = {"N":"S","S":"N","E":"W","W":"E"}
    # pick random cardinal not opposite of last
    while True:
        d = random.choice(dirs)
        if last_direction is None or d != opp[last_direction]:
            break
    last_direction = d

    step_lat = DIST_PER_STEP_KM / 111.0
    step_lon = DIST_PER_STEP_KM / (111.0 * math.cos(math.radians(currentLat)))

    if d == "N":
        currentLat += step_lat
    elif d == "S":
        currentLat -= step_lat
    elif d == "E":
        currentLon += step_lon
    elif d == "W":
        currentLon -= step_lon

def check_motion_and_alert():
    global in_alert
    if not armed or in_alert:
        return
    if random.random() < MOTION_PROBABILITY:
        in_alert = True
        print("[ALERT] motion detected!")
        # immediate alert → full telemetry
        publish_telemetry()
        print("Alert telemetry sent")

def publish_telemetry():
    global currentLat, currentLon
    # simulate gyro readings
    gx = random.uniform(-5, 5)
    gy = random.uniform(-5, 5)
    gz = random.uniform(-5, 5)

    data = {
        "motion_detected": in_alert,
        "gyro_x":          round(gx, 2),
        "gyro_y":          round(gy, 2),
        "gyro_z":          round(gz, 2),
        "armed":           armed
    }
    if in_alert:
        updateGPSMock()
        data["latitude"]  = round(currentLat, 6)
        data["longitude"] = round(currentLon, 6)

    client.publish(TELEMETRY_TOPIC, json.dumps(data))
    print("[TEL]", data)

def main_loop():
    next_time = time.time()
    while True:
        now = time.time()
        if now >= next_time:
            check_motion_and_alert()
            publish_telemetry()
            next_time += TELEMETRY_INTERVAL
        client.loop(timeout=1.0)

if __name__ == "__main__":
    client.username_pw_set(TB_DEVICE_TOKEN)
    client.on_connect = on_connect
    client.on_message = on_message

    print(f"[MQTT] Connecting to {TB_SERVER}:{TB_PORT} …")
    client.connect(TB_SERVER, TB_PORT, keepalive=60)

    main_loop()
