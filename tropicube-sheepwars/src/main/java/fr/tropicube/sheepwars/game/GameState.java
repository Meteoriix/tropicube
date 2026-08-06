package fr.tropicube.sheepwars.game;

/** Phases successives du cycle de vie d'une instance SheepWars. */
public enum GameState {
    WAITING,  // En attente de joueurs
    STARTING, // Compte à rebours
    PLAYING,  // Partie en cours
    ENDING,   // Fin de partie (titre affiché)
    ENDED    // Partie terminée, joueurs renvoyés au lobby
}
