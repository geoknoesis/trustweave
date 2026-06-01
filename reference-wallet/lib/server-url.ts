import 'server-only'
import os from 'os'

/** Best-effort LAN base URL for phones scanning issuer QRs (not localhost). */
export function guessMobileReachableUrl(port = 3000): string | null {
  const nets = os.networkInterfaces()
  for (const ifaces of Object.values(nets)) {
    if (!ifaces) continue
    for (const net of ifaces) {
      if (net.family !== 'IPv4' || net.internal) continue
      if (net.address.startsWith('169.254.')) continue
      return `http://${net.address}:${port}`
    }
  }
  return null
}
