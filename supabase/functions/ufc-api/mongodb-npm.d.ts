declare module "npm:mongodb@6" {
  export class MongoClient {
    constructor(
      uri: string,
      options?: {
        maxPoolSize?: number;
        serverSelectionTimeoutMS?: number;
      },
    );

    connect(): Promise<MongoClient>;
    db(name: string): Db;
  }

  export interface Db {
    collection(name: string): Collection;
  }

  export interface Collection {
    find(filter?: Record<string, unknown>): FindCursor;
  }

  export interface FindCursor {
    toArray(): Promise<unknown[]>;
  }
}
