/** All mutations and signing share one origin-wide lock, including identity reset. */
export async function withWalletLock<T>(operation: () => Promise<T> | T): Promise<T> {
  if (!navigator.locks) throw new Error('This browser cannot safely coordinate wallet tabs. Use a secure browser with Web Locks support. Your existing data has been preserved.')
  return navigator.locks.request('trustweave-wallet', operation)
}
