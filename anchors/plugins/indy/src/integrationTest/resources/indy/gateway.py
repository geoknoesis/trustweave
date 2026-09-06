"""Test-only HTTP adapter around native Indy VDR and four real local validator processes."""
import asyncio
import subprocess
from aiohttp import web
from indy_vdr import open_pool, VdrError

nodes = []

async def start(app):
    subprocess.check_call(["generate_indy_pool_transactions", "--nodes", "4", "--clients", "0", "--nodeNum", "1", "2", "3", "4", "--ips", "127.0.0.1,127.0.0.1,127.0.0.1,127.0.0.1"])
    for i in range(4):
        nodes.append(subprocess.Popen(["start_indy_node", "Node" + str(i + 1), "0.0.0.0", str(9701 + 2*i), "0.0.0.0", str(9702 + 2*i)]))
    for attempt in range(12):
        try:
            app["pool"] = await asyncio.wait_for(open_pool(transactions_path="/home/indy/ledger/sandbox/pool_transactions_genesis"), 20)
            return
        except (VdrError, asyncio.TimeoutError):
            if attempt == 11:
                raise
            await asyncio.sleep(2)

async def stop(app):
    if "pool" in app:
        app["pool"].close()
    for node in nodes:
        node.terminate()

async def status(request):
    return web.json_response({"status": "READY"})

async def submit(request):
    try:
        result = await request.app["pool"].submit_request(await request.text())
        return web.json_response({"op": "REPLY", "result": result})
    except VdrError as error:
        return web.json_response({"op": "REJECT", "reason": str(error)}, status=400)

app = web.Application(client_max_size=1048576)
app.on_startup.append(start)
app.on_cleanup.append(stop)
app.router.add_get("/status", status)
app.router.add_post("/submit", submit)
web.run_app(app, host="0.0.0.0", port=8001)
