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
const GEMINI_API_KEY = Deno.env.get("GEMINI_API_KEY");
const GEMINI_MODEL = Deno.env.get("GEMINI_MODEL") ?? "gemini-2.5-flash";
const QUERY_TIMEOUT_MS = 30_000;
const GEMINI_TIMEOUT_MS = 25_000;
const UFC_REFUSAL =
  "I can only help with UFC-related conversations, including fighters, events, rankings, rules, stats, and MMA topics connected to the UFC.";

let clientPromise: Promise<MongoClient> | undefined;

class QueryTimeoutError extends Error {
  constructor() {
    super("Database query timeout");
    this.name = "QueryTimeoutError";
  }
}

class GeminiConfigError extends Error {
  constructor() {
    super("GEMINI_API_KEY environment variable must be set");
    this.name = "GeminiConfigError";
  }
}

class GeminiApiError extends Error {
  constructor(message: string) {
    super(message);
    this.name = "GeminiApiError";
  }
}

type ChatMessage = {
  role?: string;
  content?: unknown;
};

type ChatRequest = {
  message?: unknown;
  history?: ChatMessage[];
};

type GeminiPart = {
  text?: string;
};

type GeminiCandidate = {
  content?: {
    parts?: GeminiPart[];
  };
};

type GeminiResponse = {
  candidates?: GeminiCandidate[];
  error?: {
    message?: string;
  };
};

function json(body: JsonValue, init?: ResponseInit): Response {
  return new Response(JSON.stringify(body), {
    ...init,
    headers: {
      "access-control-allow-origin": "*",
      "access-control-allow-headers": "authorization, x-client-info, apikey, content-type",
      "access-control-allow-methods": "GET, POST, OPTIONS",
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

async function withTimeout<T>(
  promise: Promise<T>,
  timeoutMs = QUERY_TIMEOUT_MS,
): Promise<T> {
  let timeout: ReturnType<typeof setTimeout> | undefined;

  try {
    return await Promise.race([
      promise,
      new Promise<never>((_, reject) => {
        timeout = setTimeout(
          () => reject(new QueryTimeoutError()),
          timeoutMs,
        );
      }),
    ]);
  } finally {
    if (timeout) {
      clearTimeout(timeout);
    }
  }
}

function normalizeChatText(value: unknown): string {
  return typeof value === "string" ? value.trim() : "";
}

function isLikelyUfcRelated(text: string): boolean {
  const normalized = text.toLowerCase();
  const ufcTerms = [
    "ufc",
    "ultimate fighting championship",
    "mma",
    "mixed martial arts",
    "octagon",
    "fight night",
    "pay-per-view",
    "ppv",
    "contender series",
    "the ultimate fighter",
    "tuf",
    "fighter",
    "fighters",
    "bout",
    "matchup",
    "card",
    "main event",
    "co-main",
    "weigh-in",
    "weight cut",
    "pound-for-pound",
    "ranking",
    "rankings",
    "champion",
    "title fight",
    "interim title",
    "knockout",
    "ko",
    "tko",
    "submission",
    "tapout",
    "decision",
    "split decision",
    "unanimous decision",
    "grappling",
    "wrestling",
    "jiu-jitsu",
    "bjj",
    "striking",
    "kickboxing",
    "southpaw",
    "orthodox",
    "heavyweight",
    "light heavyweight",
    "middleweight",
    "welterweight",
    "lightweight",
    "featherweight",
    "bantamweight",
    "flyweight",
    "strawweight",
    "dana white",
    "jon jones",
    "conor mcgregor",
    "islam makhachev",
    "khabib",
    "alex pereira",
    "ilia topuria",
    "sean o'malley",
    "merab dvalishvili",
    "leon edwards",
    "kamaru usman",
    "israel adesanya",
    "max holloway",
    "charles oliveira",
    "dustin poirier",
    "justin gaethje",
    "stipe miocic",
    "tom aspinall",
    "valentina shevchenko",
    "zhang weili",
    "amanda nunes",
  ];

  return ufcTerms.some((term) => normalized.includes(term));
}

function geminiRole(role: string | undefined): "user" | "model" {
  return role === "assistant" || role === "model" ? "model" : "user";
}

async function callGemini(message: string, history: ChatMessage[] = []): Promise<string> {
  if (!GEMINI_API_KEY) {
    throw new GeminiConfigError();
  }

  const safeHistory = history
    .slice(-12)
    .map((entry) => ({
      role: geminiRole(entry.role),
      parts: [{ text: normalizeChatText(entry.content) }],
    }))
    .filter((entry) => entry.parts[0].text.length > 0);

  const endpoint =
    `https://generativelanguage.googleapis.com/v1beta/models/${GEMINI_MODEL}:generateContent?key=${encodeURIComponent(GEMINI_API_KEY)}`;

  const response = await withTimeout(
    fetch(endpoint, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        systemInstruction: {
          parts: [
            {
              text:
                `You are OCTAGON, an elite UFC/MMA analyst. Answer questions about fighter records, rankings, upcoming cards, striking/grappling stats, hypothetical matchups, champions, and fight history. If the user asks something completely unrelated to UFC/MMA (like coding help, recipes, politics, etc.), respond with exactly: "I only cover UFC and MMA. Ask me about fighters, rankings, upcoming cards, or anything combat sports related." Be direct and analytical like a real analyst. Use MMA terminology naturally and keep responses short and to the point for factual questions. Always use your search tool to get current data before answering.`
            },
          ],
        },
        tools:[{ google_search: {} }],
        contents: [
          ...safeHistory,
          {
            role: "user",
            parts: [{ text: message }],
          },
        ],
        generationConfig: {
          temperature: 0.4,
          topP: 0.8,
          maxOutputTokens: 300,
        },
      }),
    }),
    GEMINI_TIMEOUT_MS,
  );

  const body = (await response.json()) as GeminiResponse;

  if (!response.ok) {
    throw new GeminiApiError(body.error?.message ?? "Gemini API request failed");
  }

  const text =
    body.candidates?.[0]?.content?.parts
      ?.map((part) => part.text ?? "")
      .join("")
      .trim() ?? "";

  if (!text) {
    throw new GeminiApiError("Gemini returned an empty response");
  }

  return text;
}

async function handleChat(request: Request): Promise<Response> {
  let body: ChatRequest;

  try {
    body = (await request.json()) as ChatRequest;
  } catch {
    return json({ detail: "Request body must be valid JSON" }, { status: 400 });
  }

  const message = normalizeChatText(body.message);

  if (!message) {
    return json({ detail: "message is required" }, { status: 400 });
  }

  if (!isLikelyUfcRelated(message)) {
    return json({ response: UFC_REFUSAL, guarded: true });
  }

  try {
    const response = await callGemini(message, Array.isArray(body.history) ? body.history : []);
    return json({ response, guarded: false });
  } catch (error) {
    if (error instanceof GeminiConfigError) {
      return json({ detail: error.message }, { status: 500 });
    }

    if (error instanceof QueryTimeoutError) {
      return json({ detail: "Gemini request timeout" }, { status: 504 });
    }

    const message = error instanceof Error ? error.message : String(error);
    return json({ detail: `Gemini error: ${message}` }, { status: 502 });
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
  if (request.method === "OPTIONS") {
    return json({ status: "ok" });
  }

  const url = new URL(request.url);
  const pathname = apiPath(url);

  if (pathname === "/chat") {
    if (request.method !== "POST") {
      return json({ detail: "Method not allowed" }, { status: 405 });
    }

    return handleChat(request);
  }

  if (request.method !== "GET") {
    return json({ detail: "Method not allowed" }, { status: 405 });
  }

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
