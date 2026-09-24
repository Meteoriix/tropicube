export interface OperationLock {
  current: boolean;
}

/** Runs one asynchronous editor mutation at a time and always releases the lock. */
export async function runExclusive(lock: OperationLock, operation: () => Promise<void>): Promise<boolean> {
  if (lock.current) return false;
  lock.current = true;
  try {
    await operation();
    return true;
  } finally {
    lock.current = false;
  }
}

/** Tells whether the user changed a draft after the submitted snapshot was captured. */
export function hasNewerDraft(currentRevision: number, submittedRevision: number): boolean {
  return currentRevision !== submittedRevision;
}

type UiFileIdentity = { id: string; hash: string };

/** Builds an optimistic-lock payload containing only manifests edited in the current draft. */
export function uiApplicationPayload(files: UiFileIdentity[], documents: Record<string, string>,
                                     changedIds: string[]): { expectedHashes: Record<string, string>; documents: Record<string, string> } {
  const changed = new Set(changedIds);
  const selected = files.filter(file => changed.has(file.id));
  return {
    expectedHashes: Object.fromEntries(selected.map(file => [file.id, file.hash])),
    documents: Object.fromEntries(selected.map(file => [file.id, documents[file.id]])),
  };
}
