import os
import sys

import pymongo
from dotenv import load_dotenv

load_dotenv
def main() -> int:
    load_dotenv()
    load_dotenv("supabase/.env")

    uri = os.getenv("MONGODB_URI") or os.getenv("MONGODB_URL")
    database_name = os.getenv("MONGODB_DATABASE", "MMADatabase")

    if not uri:
        print("Missing MONGODB_URI or MONGODB_URL in .env / supabase/.env")
        return 1

    try:
        client = pymongo.MongoClient(uri, serverSelectionTimeoutMS=10000)
        client.admin.command("ping")
        collections = client[database_name].list_collection_names()
    except Exception as exc:
        print(f"MongoDB connection failed: {exc}")
        return 1

    print("MongoDB connection OK")
    print(f"Database: {database_name}")
    print("Collections:")
    for collection in collections:
        print(f"- {collection}")

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
