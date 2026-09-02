package fr.tropicube.core.ui;

/** A Paper feature whose versioned UI definitions can be reloaded by the local editor. */
public interface UiReloadParticipant {
    /** Reads and validates definitions, returning a side-effect-free commit executed only if every participant succeeds. */
    Runnable prepareReload();

    /** Rebuilds visible UI after a successful definition swap; called on the main thread. */
    void refreshViewers();
}
