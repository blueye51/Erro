export type Source = {
  id: string; documentId: string; chunkId: string; title: string; publisher: string
  url: string; section: string; standardNumber: string; documentVersion: string
  edition: string; amendment: string; jurisdiction: string; sourceType: string
  copyrightStatus: string; relevance: number; licenseNotes?: string
}
export type Calculation = {
  name: string; value: number; unit: string; formula: string
  inputs: Record<string, number>; assumptions: string[]
}
export type Check = { rule: string; status: 'PASS' | 'FAIL' | 'UNKNOWN' | 'WARNING'; explanation: string }
export type ChatReply = {
  reply: string; requestId?: string; sources: Source[]; calculations: Calculation[]
  assumptions: string[]; warnings: string[]; missingInformation: string[]
  validations: Check[]; products: { id: string; specifications: Record<string, unknown>; checks: Check[] }[]
  contextReset: boolean
  documentSources: { id: string; filename: string; location: string; method: string }[]
}
