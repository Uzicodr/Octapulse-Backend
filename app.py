from fastapi import FastAPI
from database import database
from fastapi import Query
app = FastAPI(title="UFC Backend API")

@app.get("/")
async def health_check():
    return {"status": "API is running"}

@app.get("/fighterlogs")
async def getfighter(name: str | None = Query(default=None)):
    collection = database["fighterlogs"]
    logs = []

    query = {}
    if name:
        query = {
            "$or": [
                {"first_name": {"$regex": name, "$options": "i"}},
                {"last_name": {"$regex": name, "$options": "i"}},
            ]
        }

    async for log in collection.find(query):
        log["_id"] = str(log["_id"])
        logs.append(log)

    return logs


@app.get("/upcomingevents")
async def get_upcomingevents():
    collection = database["upcomingevents"]
    events = []
    async for event in collection.find():
        event["_id"] = str(event["_id"])
        events.append(event)
    return events

# Get all past events
@app.get("/pastevents")
async def get_pastevents():
    collection = database["pastevents"]
    events = []
    async for event in collection.find():
        event["_id"] = str(event["_id"])
        events.append(event)
    return events

# Get all rankings
@app.get("/rankings")
async def get_rankings():
    collection = database["rankings"]
    rankings = []
    async for ranking in collection.find():
        ranking["_id"] = str(ranking["_id"])
        rankings.append(ranking)
    return rankings

