import motor.motor_asyncio
from dotenv import load_dotenv
import os

load_dotenv()

MONGODB_URL = os.getenv("MONGODB_URL")
if not MONGODB_URL:
    raise ValueError("MONGODB_URL environment variable not set. Please add it to your .env file.")

client = motor.motor_asyncio.AsyncIOMotorClient(MONGODB_URL)
database = client["MMADatabase"]

async def get_database():
    return database
