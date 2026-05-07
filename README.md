# NeuralLinkPorter

A cross‑platform system that lets Android devices off‑load heavy GPU/CPU workloads (e.g., path‑finding, local LLM inference) to a nearby PC equipped with an NVIDIA RTX GPU.

## Overview
- **Android client** (Kotlin) runs a foreground service that monitors thermal state. When temperature or throttling crosses a threshold it streams a task payload over a low‑latency UDP socket.
- **PC server** (Python FastAPI) receives the UDP packet, runs the heavy computation on the RTX GPU (via PyTorch/CUDA), and replies via UDP.
- Optional HTTP endpoints expose health‑checks and a simple dashboard.

## Directory Layout
```
NeuralLinkPorter/
├─ README.md                # ← this file
├─ server/                  # Python server
│   └─ main.py
└─ android/                 # Android Studio project (Gradle)
    └─ app/
        ├─ src/main/AndroidManifest.xml
        ├─ src/main/java/com/example/neuralinkporter/
        │   ├─ MainActivity.kt
        │   └─ ThermalService.kt
        └─ src/main/res/layout/activity_main.xml
```

## Getting Started
### PC Server
1. Install **Python 3.11+** and create a virtual environment:
   ```bash
   python -m venv venv
   source venv/bin/activate   # on Windows use venv\Scripts\activate
   ```
2. Install dependencies:
   ```bash
   pip install fastapi uvicorn[standard] torch  # torch with CUDA for RTX
   ```
3. Run the server:
   ```bash
   python server/main.py
   ```
   The UDP listener binds to `0.0.0.0:9999`; adjust `UDP_PORT` in `main.py` if needed.

### Android Client
1. Install **Android Studio** (Electric Eel or later) with SDK ≥ 33.
2. Open the `android` folder as an existing project.
3. Add the `INTERNET` and `FOREGROUND_SERVICE` permissions in `AndroidManifest.xml` (already present).
4. Update the `serverIp` constant in `ThermalService.kt` to point to your PC's LAN IP.
5. Build and run the app on a tablet/phone (8th‑gen or newer for best performance).
6. The app will start the `ThermalService` automatically (you can also start it from `MainActivity`). When the device overheats, the service will offload the defined task to the PC and display the result.

## License
MIT – feel free to adapt, improve, and extend.
