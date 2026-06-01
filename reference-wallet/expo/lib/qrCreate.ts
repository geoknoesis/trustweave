/**
 * Pure-JS QR matrix generation for React Native.
 * Do NOT import from 'qrcode' (main entry resolves to Node server/canvas code).
 */
// eslint-disable-next-line @typescript-eslint/no-require-imports
const QRCore = require('qrcode/lib/core/qrcode') as {
  create: (
    data: string,
    options?: { errorCorrectionLevel?: 'L' | 'M' | 'Q' | 'H' },
  ) => { modules: QrModules }
}

export interface QrModules {
  size: number
  get: (row: number, col: number) => boolean
}

export function createQrModules(
  value: string,
  errorCorrectionLevel: 'L' | 'M' | 'Q' | 'H' = 'M',
): QrModules | null {
  try {
    return QRCore.create(value, { errorCorrectionLevel }).modules
  } catch {
    return null
  }
}
