package fr.tropicube.sheepwars.game;

/** Successive phases in the lifecycle of a SheepWars instance. */
public enum GameState {
    WAITING,  // Waiting for players
    STARTING, // Countdown
    PLAYING,  // Match in progress
    ENDING,   // End screen is being displayed
    ENDED     // Match ended and players returned to the lobby
}
