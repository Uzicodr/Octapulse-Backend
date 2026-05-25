import { MongoClient } from "npm:mongodb@6";

declare const Deno: {
  env: {
    get(key: string): string | undefined;
  };
  serve(
    handler: (request: Request) => Response | Promise<Response>,
  ): void;
};

type JsonValue =
  | string
  | number
  | boolean
  | null
  | JsonValue[]
  | { [key: string]: JsonValue };

type DocumentRecord = Record<string, JsonValue>;

const MONGODB_URI = Deno.env.get("MONGODB_URI") ?? Deno.env.get("MONGODB_URL");
const DATABASE_NAME = Deno.env.get("MONGODB_DATABASE") ?? "MMADatabase";
const QUERY_TIMEOUT_MS = 30_000;

let clientPromise: Promise<MongoClient> | undefined;

class QueryTimeoutError extends Error {
  constructor() {
    super("Database query timeout");
    this.name = "QueryTimeoutError";
  }
}

function json(body: JsonValue, init?: ResponseInit): Response {
  return new Response(JSON.stringify(body), {
    ...init,
    headers: {
      "content-type": "application/json; charset=utf-8",
      ...init?.headers,
    },
  });
}

function toJsonValue(value: unknown): JsonValue {
  if (
    value === null ||
    typeof value === "string" ||
    typeof value === "number" ||
    typeof value === "boolean"
  ) {
    return value;
  }

  if (value instanceof Date) {
    return value.toISOString();
  }

  if (Array.isArray(value)) {
    return value.map(toJsonValue);
  }

  if (value && typeof value === "object") {
    if ("_bsontype" in value && value._bsontype === "ObjectId") {
      return String(value);
    }

    return Object.fromEntries(
      Object.entries(value).map(([key, entry]) => [key, toJsonValue(entry)]),
    );
  }

  return String(value);
}

function normalizeDocument(document: unknown): DocumentRecord {
  return toJsonValue(document) as DocumentRecord;
}

function requireMongoUri(): string {
  if (!MONGODB_URI) {
    throw new Error("MONGODB_URI or MONGODB_URL environment variable must be set");
  }

  return MONGODB_URI;
}

async function getClient(): Promise<MongoClient> {
  if (!clientPromise) {
    const client = new MongoClient(requireMongoUri(), {
      maxPoolSize: 5,
      serverSelectionTimeoutMS: QUERY_TIMEOUT_MS,
    });

    clientPromise = client.connect();
  }

  return clientPromise;
}

async function withTimeout<T>(promise: Promise<T>): Promise<T> {
  let timeout: ReturnType<typeof setTimeout> | undefined;

  try {
    return await Promise.race([
      promise,
      new Promise<never>((_, reject) => {
        timeout = setTimeout(
          () => reject(new QueryTimeoutError()),
          QUERY_TIMEOUT_MS,
        );
      }),
    ]);
  } finally {
    if (timeout) {
      clearTimeout(timeout);
    }
  }
}

async function findDocuments(
  collection: string,
  filter: DocumentRecord = {},
): Promise<DocumentRecord[]> {
  return withTimeout(
    (async () => {
      const client = await getClient();
      const documents = await client
        .db(DATABASE_NAME)
        .collection(collection)
        .find(filter)
        .toArray();

      return documents.map(normalizeDocument);
    })(),
  );
}

function fighterNameFilter(name: string | null): DocumentRecord {
  if (!name) {
    return {};
  }

  return {
    $or: [
      { first_name: { $regex: name, $options: "i" } },
      { last_name: { $regex: name, $options: "i" } },
    ],
  };
}

async function handleCollection(
  collection: string,
  filter: DocumentRecord = {},
): Promise<Response> {
  try {
    return json(await findDocuments(collection, filter));
  } catch (error) {
    if (error instanceof QueryTimeoutError) {
      return json({ detail: "Database query timeout" }, { status: 504 });
    }

    const message = error instanceof Error ? error.message : String(error);
    return json({ detail: `Database error: ${message}` }, { status: 500 });
  }
}

function apiPath(url: URL): string {
  const parts = url.pathname.split("/").filter(Boolean);
  const functionIndex = parts.lastIndexOf("ufc-api");
  const routeParts = functionIndex >= 0 ? parts.slice(functionIndex + 1) : parts;

  return `/${routeParts.join("/")}`.replace(/\/$/, "") || "/";
}

Deno.serve(async (request: Request) => {
  if (request.method !== "GET") {
    return json({ detail: "Method not allowed" }, { status: 405 });
  }

  const url = new URL(request.url);
  const pathname = apiPath(url);

  if (pathname === "/") {
    return json({ status: "API is running" });
  }

  if (pathname === "/fighterlogs") {
    return handleCollection(
      "fighterlogs",
      fighterNameFilter(url.searchParams.get("name")),
    );
  }

  if (pathname === "/upcomingevents") {
    return handleCollection("upcomingevents");
  }

  if (pathname === "/pastevents") {
    return handleCollection("pastevents");
  }

  if (pathname === "/rankings") {
    return handleCollection("rankings");
  }

  return json({ detail: "Not found" }, { status: 404 });
});
