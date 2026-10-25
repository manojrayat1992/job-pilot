import { test } from 'node:test';
import assert from 'node:assert/strict';
import { refreshSession } from '../src/lib/sessionCoordinator.ts';
import type { SessionSnapshot } from '../src/lib/sessionCoordinator.ts';

function fixture() {
  let state: SessionSnapshot = { sessionKey: 'login-a', accessToken: 'access-a', refreshToken: 'refresh-a' };
  let calls = 0;
  let tail = Promise.resolve();
  const lock = <R>(action: () => Promise<R>): Promise<R> => {
    const result = tail.then(action);
    tail = result.then(() => {}, () => {});
    return result;
  };
  const dependencies = {
    lock,
    read: () => state,
    exchange: async (_token: string) => { calls++; return { ...state, accessToken: 'access-b', refreshToken: 'refresh-b' }; },
    write: (next: SessionSnapshot) => { state = next; },
    clear: () => { state = { sessionKey: null, accessToken: null, refreshToken: null }; },
  };
  return { dependencies, calls: () => calls };
}

test('parallel requests across adapters exchange the shared token only once', async () => {
  const f = fixture();
  const results = await Promise.all(Array.from({ length: 10 }, () => refreshSession('login-a', 'access-a', { ...f.dependencies })));
  assert.deepEqual(results, Array(10).fill('access-b')); assert.equal(f.calls(), 1);
  assert.equal(f.dependencies.read().refreshToken, 'refresh-b');
});
test('a delayed 401 reuses an already rotated access token', async () => {
  const f = fixture(); f.dependencies.write({ sessionKey: 'login-a', accessToken: 'new-access', refreshToken: 'new-refresh' });
  assert.equal(await refreshSession('login-a', 'access-a', f.dependencies), 'new-access'); assert.equal(f.calls(), 0);
});
test('an old request cannot refresh under a new login', async () => {
  const f = fixture(); f.dependencies.write({ sessionKey: 'login-b', accessToken: 'other', refreshToken: 'other' });
  await assert.rejects(refreshSession('login-a', 'access-a', f.dependencies), /login changed/);
  assert.equal(f.calls(), 0); assert.equal(f.dependencies.read().sessionKey, 'login-b');
});
test('logout while a response is pending cannot restore the session', async () => {
  const f = fixture(); f.dependencies.exchange = async () => {
    f.dependencies.clear(); return { sessionKey: 'login-a', accessToken: 'late', refreshToken: 'late' };
  };
  await assert.rejects(refreshSession('login-a', 'access-a', f.dependencies), /login changed/);
  assert.equal(f.dependencies.read().sessionKey, null);
});
test('a pending response cannot overwrite or clear a newer login', async () => {
  const f = fixture(); f.dependencies.exchange = async () => {
    f.dependencies.write({ sessionKey: 'login-b', accessToken: 'new-login', refreshToken: 'new-login' });
    return { sessionKey: 'login-a', accessToken: 'late', refreshToken: 'late' };
  };
  await assert.rejects(refreshSession('login-a', 'access-a', f.dependencies), /login changed/);
  assert.equal(f.dependencies.read().sessionKey, 'login-b');
});
test('an uncertain refresh failure clears credentials and queued requests do not replay the token', async () => {
  const f = fixture(); let attempts = 0;
  f.dependencies.exchange = async () => { attempts++; throw new Error('Network timeout'); };
  const results = await Promise.allSettled(Array.from({ length: 3 }, () => refreshSession('login-a', 'access-a', f.dependencies)));
  assert.ok(results.every(r => r.status === 'rejected')); assert.equal(attempts, 1);assert.equal(f.dependencies.read().refreshToken, null);
});
test('logout using the same lock revokes the latest refresh token and clears storage', async () => {
  const f = fixture(); const pending = refreshSession('login-a', 'access-a', f.dependencies); let revoked: string | null = null;
  const logout = f.dependencies.lock(async () => { revoked = f.dependencies.read().refreshToken; f.dependencies.clear(); });
  await Promise.all([pending, logout]);assert.equal(revoked, 'refresh-b');assert.equal(f.dependencies.read().sessionKey, null);
});
test('an incomplete refresh response never persists unusable credentials', async () => {
  const f = fixture(); f.dependencies.exchange = async () => ({ sessionKey: 'login-a', accessToken: 'new', refreshToken: null });
  await assert.rejects(refreshSession('login-a', 'access-a', f.dependencies), /Missing session tokens/);
  assert.equal(f.dependencies.read().sessionKey, null);
});
