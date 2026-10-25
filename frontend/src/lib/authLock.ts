/** Web Locks coordinate localStorage credentials across tabs on HTTPS (or localhost). */
export function withAuthLock<T>(action: () => Promise<T>): Promise<T> {
  return navigator.locks ? navigator.locks.request('jobpilot-auth', action) : action();
}
