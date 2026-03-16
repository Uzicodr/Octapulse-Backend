from fastapi import FastAPI, HTTPException
from database import database
from fastapi import Query
import asyncio

app = FastAPI(title="UFC Backend API")

@app.get("/")
async def health_check():
    return {"status": "API is running"}

@app.get("/fighterlogs")
async def getfighter(name: str | None = Query(default=None)):
    try:
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

        async def fetch_logs():
            async for log in collection.find(query):
                log["_id"] = str(log["_id"])
                logs.append(log)
        
        await asyncio.wait_for(fetch_logs(), timeout=30)
        return logs
    except asyncio.TimeoutError:
        raise HTTPException(status_code=504, detail="Database query timeout")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


@app.get("/upcomingevents")
async def get_upcomingevents():
    try:
        collection = database["upcomingevents"]
        events = []
        
        async def fetch_events():
            async for event in collection.find():
                event["_id"] = str(event["_id"])
                events.append(event)
        
        await asyncio.wait_for(fetch_events(), timeout=30)
        return events
    except asyncio.TimeoutError:
        raise HTTPException(status_code=504, detail="Database query timeout")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


@app.get("/pastevents")
async def get_pastevents():
    try:
        collection = database["pastevents"]
        events = []
        
        async def fetch_events():
            async for event in collection.find():
                event["_id"] = str(event["_id"])
                events.append(event)
        
        await asyncio.wait_for(fetch_events(), timeout=30)
        return events
    except asyncio.TimeoutError:
        raise HTTPException(status_code=504, detail="Database query timeout")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")


@app.get("/rankings")
async def get_rankings():
    try:
        collection = database["rankings"]
        rankings = []
        
        async def fetch_rankings():
            async for ranking in collection.find():
                ranking["_id"] = str(ranking["_id"])
                rankings.append(ranking)
        
        await asyncio.wait_for(fetch_rankings(), timeout=30)
        return rankings
    except asyncio.TimeoutError:
        raise HTTPException(status_code=504, detail="Database query timeout")
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"Database error: {str(e)}")
