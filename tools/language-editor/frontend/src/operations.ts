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
