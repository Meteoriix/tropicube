package fr.tropicube.core.ui;

/** Paper-thread state of one rendered inventory. A closed session can never become active again. */
public final class MenuSession {
    private boolean open = true;
    private boolean busy;

    /** Accepts at most one mutation until its completion. */
    public boolean beginAction() {
        if (!open || busy) return false;
        busy = true;
        return true;
    }
    /** Returns whether a completion still belongs to a visible session. */
    public boolean completeAction() {
        busy = false;
        return open;
    }
    public boolean canInteract() { return open && !busy; }
    public boolean isOpen() { return open; }
    public void close() { open = false; }
}
