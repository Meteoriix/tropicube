package fr.tropicube.lobby.cosmetic;

import fr.tropicube.core.cosmetic.CosmeticCatalog;

/** A private preview stops at its deadline and is independent of persistent equipment. */
record PreviewWindow(CosmeticCatalog.Entry entry, long expiresAtTick) {
    boolean active(long currentTick) { return currentTick < expiresAtTick; }
}
