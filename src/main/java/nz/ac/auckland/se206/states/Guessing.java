package nz.ac.auckland.se206.states;

import java.io.IOException;
import javafx.scene.input.MouseEvent;
import nz.ac.auckland.se206.GameStateContext;
import nz.ac.auckland.se206.controllers.RoomController;
import nz.ac.auckland.se206.speech.TextToSpeech;

/**
 * The Guessing state of the game. Handles the logic for when the player is making a guess about the
 * profession of the characters in the game.
 */
public class Guessing implements GameState {

  private final GameStateContext context;
  private RoomController roomController;

  /**
   * Constructs a new Guessing state with the given game state context.
   *
   * @param context the context of the game state
   */
  public Guessing(GameStateContext context, RoomController roomController) {
    this.context = context;
    this.roomController = roomController;
  }

  /**
   * Handles the event when a rectangle is clicked. Checks if the clicked rectangle is a customer
   * and updates the game state accordingly.
   *
   * @param event the mouse event triggered by clicking a rectangle
   * @param rectangleId the ID of the clicked rectangle
   * @throws IOException if there is an I/O error
   */
  @Override
  public void handleRectangleClick(MouseEvent event, String rectangleId) throws IOException {
    roomController.stopTimer();
    String clickedProfession = context.getProfession(rectangleId);
    if (rectangleId.equals("rectCashier")) {
      roomController.correctGuess();
      TextToSpeech.speak("Correct! You won! This is the " + clickedProfession);
    } else {
      roomController.incorrectGuess();
      TextToSpeech.speak("You lost! This is the " + clickedProfession);
    }
    context.setState(context.getGameOverState());
  }

  /**
   * Handles the event when the guess button is clicked. Since the player has already guessed, it
   * notifies the player.
   *
   * @throws IOException if there is an I/O error
   */
  @Override
  public void handleGuessClick() throws IOException {
    TextToSpeech.speak("You have already guessed!");
  }
}
