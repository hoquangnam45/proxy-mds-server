export class SchemaService {
  private readonly url: string

  public constructor(url: string) {
    this.url = `${url}/schemas`
  }

  public async getAllSchemas(): Promise<Partial<Record<string, Partial<Record<string, any>>>>> {
    return fetch(this.url).then((resp) => resp.json())
  }

  public async getSchema(schemaName: string, subSchemaName: string): Promise<Partial<Record<string, any>>> {
    return fetch(`${this.url}/${schemaName}/${subSchemaName}`).then((resp) => resp.json())
  }
}
