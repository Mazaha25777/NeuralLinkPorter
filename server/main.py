import asyncio
import json
from typing import Dict, Any

import uvicorn
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel

# -------------------------------------------------
# FastAPI – optional REST API for health, config, etc.
# -------------------------------------------------
app = FastAPI(title="Neural Link Porter – PC Server")

class HealthResponse(BaseModel):
    status: str = "ok"
    gpu_available: bool
    uptime_seconds: float

@app.get("/health", response_model=HealthResponse)
async def health():
    import torch, time
    return HealthResponse(
        gpu_available=torch.cuda.is_available(),
        uptime_seconds=time.time() - start_time,
    )

# -------------------------------------------------
# UDP "offload" listener (runs in the same event loop)
# -------------------------------------------------
UDP_PORT = 9999          # choose an unused high‑port
MAX_PACKET = 65507       # UDP safe payload size

# Simple protobuf replacement – using JSON for brevity
async def udp_listener():
    loop = asyncio.get_running_loop()
    transport, protocol = await loop.create_datagram_endpoint(
        lambda: UDPServerProtocol(),
        local_addr=("0.0.0.0", UDP_PORT)
    )
    print(f"🔹 UDP listener bound to 0.0.0.0:{UDP_PORT}")
    # keep the task alive
    while True:
        await asyncio.sleep(3600)

class UDPServerProtocol(asyncio.DatagramProtocol):
    def datagram_received(self, data: bytes, addr):
        # Decode JSON payload (could be protobuf / msgpack in production)
        try:
            payload = json.loads(data.decode())
        except Exception as exc:
            print(f"[UDP] Bad packet from {addr}: {exc}")
            return

        asyncio.create_task(handle_task(payload, addr))

async def handle_task(payload: Dict[str, Any], addr):
    """
    Example: payload = {
        "task_id": "12345",
        "type": "pathfinding",
        "payload": {...}
    }
    """
    task_type = payload.get("type")
    result = {"task_id": payload.get("task_id")}

    if task_type == "pathfinding":
        result["result"] = await run_pathfinding(payload["payload"])
    elif task_type == "llm_inference":
        result["result"] = await run_llm(payload["payload"])
    else:
        result["error"] = f"unknown task {task_type}"
    
    # Send back via UDP (non‑blocking)
    resp_bytes = json.dumps(result).encode()
    loop = asyncio.get_running_loop()
    transport, _ = await loop.create_datagram_endpoint(
        lambda: asyncio.DatagramProtocol(),
        remote_addr=addr
    )
    transport.sendto(resp_bytes)
    transport.close()

# -------------------------------------------------
# Placeholder GPU‑accelerated workers
# -------------------------------------------------
async def run_pathfinding(data: Dict) -> Dict:
    import torch, time
    t0 = time.time()
    a = torch.randn((1024, 1024), device="cuda")
    b = torch.mm(a, a)
    torch.cuda.synchronize()
    latency = time.time() - t0
    return {"status": "ok", "latency_ms": latency * 1000, "debug": "tensor computed"}

async def run_llm(data: Dict) -> Dict:
    # In a real setup you would invoke a local LLM (e.g., llama.cpp with CUDA).
    # For the demo we simply echo the prompt.
    return {"output": f"Echo: {data.get('prompt', '')}"}

# -------------------------------------------------
# Startup
# -------------------------------------------------
start_time = asyncio.get_event_loop().time()

if __name__ == "__main__":
    loop = asyncio.get_event_loop()
    loop.create_task(udp_listener())
    uvicorn.run(app, host="0.0.0.0", port=8000)
