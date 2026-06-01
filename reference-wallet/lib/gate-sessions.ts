/**
 * In-memory airspace gate sessions — binds nonce to requested activity and location.
 */
import 'server-only'

export interface GateSession {
  nonce: string
  activityType: string
  lat: number
  lon: number
  createdAt: string
}

const sessions = new Map<string, GateSession>()
const MAX = 50

export function createGateSession(activityType: string, lat: number, lon: number): GateSession {
  const session: GateSession = {
    nonce: crypto.randomUUID(),
    activityType,
    lat,
    lon,
    createdAt: new Date().toISOString(),
  }
  sessions.set(session.nonce, session)
  if (sessions.size > MAX) {
    const oldest = [...sessions.keys()][0]
    if (oldest) sessions.delete(oldest)
  }
  return session
}

export function getGateSession(nonce: string): GateSession | null {
  return sessions.get(nonce) ?? null
}
