/** Shared by the Axios adapter and tests; the lock must span every tab using the token store. */
export interface SessionSnapshot {
  sessionKey: string | null;
  accessToken: string | null;
  refreshToken: string | null;
}

export async function refreshSession<T extends SessionSnapshot>(
  expectedKey: string,
  failedAccessToken: string,
  dependencies: {
    lock: <R>(action: () => Promise<R>) => Promise<R>;
    read: () => T;
    exchange: (refreshToken: string) => Promise<T>;
    write: (session: T) => void;
    clear: () => void;
  },
): Promise<string> {
  return dependencies.lock(async () => {
    const before = dependencies.read();
    if (before.sessionKey !== expectedKey || !before.refreshToken || !before.accessToken) {
      throw new Error('Your login changed. Please try again.');
    }
    // A different request or tab already refreshed while this request was in flight.
    if (before.accessToken !== failedAccessToken) return before.accessToken;
    try {
      const replacement = await dependencies.exchange(before.refreshToken);
      const current = dependencies.read();
      if (current.sessionKey !== expectedKey || current.refreshToken !== before.refreshToken) {
        throw new Error('Your login changed. Please try again.');
      }
      if (!replacement.accessToken || !replacement.refreshToken) throw new Error('Missing session tokens');
      dependencies.write({ ...replacement, sessionKey: expectedKey });
      return replacement.accessToken;
    } catch (error) {
      // An uncertain network result may have consumed the token. Never retry it.
      if (dependencies.read().sessionKey === expectedKey) dependencies.clear();
      throw error;
    }
  });
}
