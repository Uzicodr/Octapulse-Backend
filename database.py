import motor.motor_asyncio
from dotenv import load_dotenv
import os
import ssl

load_dotenv()

MONGODB_URL = os.getenv("MONGODB_URL")
if not MONGODB_URL:
    raise ValueError("MONGODB_URL environment variable not set")

# Create custom SSL context for Vercel environment
ssl_context = ssl.create_default_context()
ssl_context.check_hostname = False
ssl_context.verify_mode = ssl.CERT_NONE

# MongoDB connection with Vercel-compatible settings
client = motor.motor_asyncio.AsyncIOMotorClient(
    MONGODB_URL,
    tlsCAFile=None,
    appName="ufc-backend",
    maxPoolSize=10,
    minPoolSize=1,
    serverSelectionTimeoutMS=10000,
    connectTimeoutMS=20000,
    retryWrites=True
)

database = client["MMADatabase"]

async def get_database():
    return database
