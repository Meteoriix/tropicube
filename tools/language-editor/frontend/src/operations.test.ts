import { describe, expect, it } from 'vitest';
import { hasNewerDraft, runExclusive, uiApplicationPayload } from './operations';

describe('runExclusive', () => {
  it('ignores a second application while the first one is pending', async () => {
    const lock = { current: false };
    let release!: () => void;
    const pending = new Promise<void>(resolve => { release = resolve; });
    let calls = 0;

    const first = runExclusive(lock, async () => { calls += 1; await pending; });
    const second = await runExclusive(lock, async () => { calls += 1; });
    release();

    expect(second).toBe(false);
    expect(await first).toBe(true);
    expect(calls).toBe(1);
  });

  it('releases the lock after a failed application', async () => {
    const lock = { current: false };

    await expect(runExclusive(lock, async () => { throw new Error('conflit de hash'); }))
      .rejects.toThrow('conflit de hash');

    expect(lock.current).toBe(false);
    expect(await runExclusive(lock, async () => {})).toBe(true);
  });

  it('detects edits made after an application captured its draft', () => {
    expect(hasNewerDraft(12, 12)).toBe(false);
    expect(hasNewerDraft(13, 12)).toBe(true);
  });

  it('submits only changed UI manifests for optimistic locking', () => {
    const payload = uiApplicationPayload([
      { id: 'lobby:menus.yml', hash: 'menu-old' },
      { id: 'tropicube-sheepwars:scoreboards.yml', hash: 'scoreboard-stale' },
    ], {
      'lobby:menus.yml': 'updated menu',
      'tropicube-sheepwars:scoreboards.yml': 'unchanged draft',
    }, ['lobby:menus.yml']);

    expect(payload).toEqual({
      expectedHashes: { 'lobby:menus.yml': 'menu-old' },
      documents: { 'lobby:menus.yml': 'updated menu' },
    });
  });
});
