import motor.motor_asyncio
from dotenv import load_dotenv
import os

load_dotenv()

MONGODB_URL = os.getenv("MONGODB_URL")
if not MONGODB_URL:
    raise ValueError("MONGODB_URL environment variable not set")

# Lazy-loaded MongoDB connection for Vercel serverless
_client = None
_database = None

def get_client():
    global _client
    if _client is None:
        _client = motor.motor_asyncio.AsyncIOMotorClient(
            MONGODB_URL,
            maxPoolSize=5,
            serverSelectionTimeoutMS=5000,
            connectTimeoutMS=5000,
        )
    return _client

def get_database_instance():
    global _database
    if _database is None:
        _database = get_client()["MMADatabase"]
    return _database

# Proxy object for backward compatibility
class DatabaseProxy:
    def __getitem__(self, key):
        return get_database_instance()[key]
    
    async def command(self, *args, **kwargs):
        return await get_database_instance().command(*args, **kwargs)

database = DatabaseProxy()
