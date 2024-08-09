package nz.ac.auckland.se206.controllers;

import java.io.IOException;
import java.net.URISyntaxException;
import java.net.URL;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import nz.ac.auckland.apiproxy.chat.openai.ChatCompletionRequest;
import nz.ac.auckland.apiproxy.chat.openai.ChatCompletionResult;
import nz.ac.auckland.apiproxy.chat.openai.ChatMessage;
import nz.ac.auckland.apiproxy.chat.openai.Choice;
import nz.ac.auckland.apiproxy.config.ApiProxyConfig;
import nz.ac.auckland.apiproxy.exceptions.ApiProxyException;
import nz.ac.auckland.se206.GameStateContext;
import nz.ac.auckland.se206.prompts.PromptEngineering;
import nz.ac.auckland.se206.speech.FreeTextToSpeech;

/**
 * Controller class for the room view. Handles user interactions within the room where the user can
 * chat with customers and guess their profession.
 */
public class RoomController {

  private static boolean isFirstTimeInit = true;

  @FXML private Pane headerPane;
  @FXML private Label lblHeader;

  @FXML private ImageView imageKid;
  @FXML private ImageView imageGrandma;
  @FXML private ImageView imageCashier;
  @FXML private ImageView imageBin;
  @FXML private ImageView imageTag;
  @FXML private ImageView imageTv;

  @FXML private Rectangle rectKid;
  @FXML private Rectangle rectGrandma;
  @FXML private Rectangle rectCashier;
  @FXML private Rectangle rectBin;
  @FXML private Rectangle rectTag;
  @FXML private Rectangle rectTv;

  @FXML private Button btnGuess;

  @FXML private AnchorPane chatPane;
  @FXML private ImageView talkerImage;
  @FXML private TextArea txtaChat;
  @FXML private TextField txtInput;
  @FXML private Button btnSend;
  @FXML private Button btnBack;

  @FXML private AnchorPane cluePane;
  @FXML private TextArea txtaClue;
  @FXML private Button btnBackClue;

  @FXML private TitledPane chatLogPane;
  @FXML private TextArea chatLogKid;
  @FXML private TextArea chatLogCashier;
  @FXML private TextArea chatLogGrandma;

  @FXML private Label timerLabel;

  private Timeline currentAnimation;
  private ChatCompletionRequest chatCompletionRequest;
  private String currentCharacter;
  private TextArea currentArea;
  private boolean animationFinished = false;
  private int timerValue;
  private boolean timerRanOut = false;
  private Thread timerThread;
  private boolean suspectTalkedTo = false;
  private boolean clueLookedAt = false;
  private boolean stateIsGameStarted = true;

  private boolean firstKid = true;
  private boolean firstGrandma = true;
  private boolean firstCashier = true;

  private ChatMessage openMsg;

  private GameStateContext context = new GameStateContext(this);
  private Media ranOutAudio =
      new Media(getClass().getResource("/sounds/ranOutAudio.mp3").toString());
  private MediaPlayer ranOutPlayer = new MediaPlayer(ranOutAudio);

  private Media talkToSuspectAudio =
      new Media(getClass().getResource("/sounds/talkToSuspectAudio.mp3").toString());
  private MediaPlayer talkToSuspectPlayer = new MediaPlayer(talkToSuspectAudio);

  private Media tenSecondsAudio =
      new Media(getClass().getResource("/sounds/tenSecondsAudio.mp3").toString());
  private MediaPlayer tenSecondsPlayer = new MediaPlayer(tenSecondsAudio);

  private Media interactWithObjectAudio =
      new Media(getClass().getResource("/sounds/interactWithObjectAudio.mp3").toString());
  private MediaPlayer interactWithObjectPlayer = new MediaPlayer(interactWithObjectAudio);

  private Media alreadyGuessedAudio =
      new Media(getClass().getResource("/sounds/alreadyGuessedAudio.mp3").toString());
  public MediaPlayer alreadyGuessedPlayer = new MediaPlayer(alreadyGuessedAudio);

  private Media correctAudio =
      new Media(getClass().getResource("/sounds/correctAudio.mp3").toString());
  public MediaPlayer correctPlayer = new MediaPlayer(correctAudio);

  private Media incorrectAudio =
      new Media(getClass().getResource("/sounds/incorrectAudio.mp3").toString());
  public MediaPlayer incorrectPlayer = new MediaPlayer(incorrectAudio);

  private Media initialAudio =
      new Media(getClass().getResource("/sounds/initialAudio.mp3").toString());
  public MediaPlayer initialPlayer = new MediaPlayer(initialAudio);

  /**
   * Initializes the room view. If it's the first time initialization, it will provide instructions
   * via text-to-speech.
   */
  @FXML
  public void initialize() {
    startCountdownTimer(121);
    if (isFirstTimeInit) {
      initialPlayer.play();
      isFirstTimeInit = false;
    }
    txtInput.setOnKeyPressed(
        event -> {
          if (event.getCode() == KeyCode.ENTER) {
            try {
              onSendMessage();
            } catch (IOException | ApiProxyException e) {
            }
          }
        });
  }

  /**
   * Handles the key pressed event.
   *
   * @param event the key event
   */
  @FXML
  public void onKeyPressed(KeyEvent event) {
    System.out.println("Key " + event.getCode() + " pressed");
  }

  /**
   * Handles the key released event.
   *
   * @param event the key event
   */
  @FXML
  public void onKeyReleased(KeyEvent event) {
    System.out.println("Key " + event.getCode() + " released");
  }

  /**
   * Handles mouse clicks on rectangles representing people in the room.
   *
   * @param event the mouse event triggered by clicking a rectangle
   * @throws IOException if there is an I/O error
   */
  @FXML
  private void handleRectangleClick(MouseEvent event) throws IOException {
    stopAnimation();
    suspectTalkedTo = true;
    Rectangle clickedRectangle = (Rectangle) event.getSource();
    context.handleRectangleClick(event, clickedRectangle.getId());
  }

  @FXML
  private void handleClueClick(MouseEvent event) throws IOException {
    // Make all this happen only if the state is in 'gameStarted'
    if (stateIsGameStarted) {
      onCloseChat();
      clueLookedAt = true;
      Rectangle clickedRectangle = (Rectangle) event.getSource();
      String rectId = clickedRectangle.getId();
      String clueText = "";
      // Show the correct clue text for each item depending on what it is
      switch (rectId) {
        case "rectBin":
          clueText =
              "You see a bin by the shelves. Inside it, you find a crumpled list of groceries. One"
                  + " of the items reads 'Headphones for Billy'.";
          break;
        case "rectTag":
          clueText =
              "You spot a plastic black object near the register. It looks like one of the security"
                  + " tags on the tech products here.";
          break;
        case "rectTv":
          clueText =
              "You go over to check the screen that shows the security footage. Looks like it's"
                  + " off.";
          break;
        default:
          break;
      }
      cluePane.setVisible(true);
      txtaClue.appendText(
          clueText); // Don't play the animation, as nobody is talking, so it wouldn't make sense
      clickedRectangle.setVisible(false);
    }
  }

  /**
   * Handles the guess button click event.
   *
   * @param event the action event triggered by clicking the guess button
   * @throws IOException if there is an I/O error
   */
  @FXML
  private void handleGuessClick() throws IOException {
    if (!suspectTalkedTo) {
      talkToSuspectPlayer.play();
      return;
    } else if (!clueLookedAt) {
      interactWithObjectPlayer.play();
      return;
    }
    if (stateIsGameStarted) {
      onCloseChat();
      onCloseClue();
      stopTimer();
      timerRanOut = true;
      tenSecondsPlayer.play();
      lblHeader.setText("You have 10 seconds remaining! Click on the thief.");
      lblHeader.setAlignment(Pos.CENTER);
      headerPane.setStyle("-fx-background-color: #f2c46f;");
      startCountdownTimer(11);
      stateIsGameStarted = false;
    }
    context.handleGuessClick();
  }

  /**
   * Sends a message to the GPT model.
   *
   * @param event the action event triggered by the send button
   * @throws ApiProxyException if there is an error communicating with the API proxy
   * @throws IOException if there is an I/O error
   */
  @FXML
  private void onSendMessage() throws ApiProxyException, IOException {
    clearChat();
    stopAnimation();
    String message = txtInput.getText().trim();
    if (message.isEmpty()) {
      return;
    }
    txtInput.clear();
    ChatMessage msg = new ChatMessage("user", message);

    // Starts running an animation to make the text appear letter by letter
    Task<ChatMessage> gptTask = // creates a background task to generate the response of the suspect
        new Task<ChatMessage>() {
          @Override
          protected ChatMessage call() throws Exception {
            return runGpt(msg);
          }
        };

    gptTask.setOnSucceeded( // once it's done and the animation is finished, put the box in the chat
        event -> {
          ChatMessage gptMsg = gptTask.getValue();

          new Thread(
                  () -> {
                    while (!animationFinished) {
                      try {
                        Thread.sleep(50);
                      } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                      }
                    }
                    Platform.runLater(() -> speakGpt(gptMsg));
                  })
              .start();
        });

    new Thread(gptTask).start(); // start the background thread

    // after starting the background thread, set animationFinished back to false and start playing
    // the animation for user text
    Platform.runLater(
        () -> {
          animationFinished = false;
          setChatLog();
          currentArea.appendText("User: " + msg.getContent() + "\n\n");
          appendChatMessage(msg, "User");
        });
  }

  /**
   * Sets the profession for the chat context and initializes the ChatCompletionRequest.
   *
   * @param profession the profession to set
   */
  public void setPrompt() {

    // Generate a welcome message only if it's first time talking
    if (!firstTimeCheck(currentCharacter)) {
      return;
    }

    // After this part of the code runs, make sure it doesn't again the next time
    switch (currentCharacter) {
      case "Kid":
        firstKid = false;
        break;
      case "Grandma":
        firstGrandma = false;
        break;
      case "Cashier":
        firstCashier = false;
        break;
    }

    // Generate opening message
    try {
      ApiProxyConfig config = ApiProxyConfig.readConfig();
      chatCompletionRequest =
          new ChatCompletionRequest(config)
              .setN(1)
              .setTemperature(0.2)
              .setTopP(0.5)
              .setMaxTokens(50);
      ChatMessage openMsg = runGpt(new ChatMessage("system", getSystemPrompt(currentCharacter)));
      this.openMsg = openMsg;
    } catch (ApiProxyException e) {
      e.printStackTrace();
    }
  }

  /**
   * Generates the system prompt based on the profession.
   *
   * @return the system prompt string
   */
  private String getSystemPrompt(String characterName) {

    return PromptEngineering.getPrompt("chat" + characterName + ".txt", characterName);
  }

  /**
   * Appends a chat message to the chat text area.
   *
   * @param msg the chat message to append
   */
  private void appendChatMessage(ChatMessage msg, String sender) {
    TextArea currentArea = txtaChat;

    // If nobody is talking (like narration), don't say who the speaker is
    if (!sender.isEmpty()) {
      currentArea.appendText(sender + ": ");
    }

    // If there is an animation running while trying to start a new one, stop it
    if (currentAnimation != null && currentAnimation.getStatus() == Timeline.Status.RUNNING) {
      currentAnimation.stop();
      animationFinished = true;
    }

    // Play an animation to append text letter by letter to the text area
    final int[] index = {0};
    Timeline animation =
        new Timeline(
            new KeyFrame(
                Duration.millis(50),
                event -> {
                  if (index[0] < msg.getContent().length()) {
                    currentArea.appendText(String.valueOf(msg.getContent().charAt(index[0])));
                    index[0]++;
                  }
                }));

    animation.setCycleCount(msg.getContent().length());

    // Once it's done, append new lines and set animationFinished to 'true'
    animation.setOnFinished(
        event -> {
          currentArea.appendText("\n\n");
          animationFinished = true;
        });

    currentAnimation = animation;
    currentAnimation.play();
  }

  /**
   * Runs the GPT model with a given chat message.
   *
   * @param msg the chat message to process
   * @return the response chat message
   * @throws ApiProxyException if there is an error communicating with the API proxy
   */
  private ChatMessage runGpt(ChatMessage msg) throws ApiProxyException {

    String conversationHistory = currentArea.getText();
    String template = "";

    URL resourceUrl =
        PromptEngineering.class
            .getClassLoader()
            .getResource("prompts/chat" + currentCharacter + ".txt");
    try {
      template = PromptEngineering.loadTemplate(resourceUrl.toURI());
    } catch (IOException | URISyntaxException e) {
    }

    String[] templateLines = template.split("\n");

    for (String line : templateLines) {
      chatCompletionRequest.addMessage(new ChatMessage("user", line));
    }

    String[] lines = conversationHistory.split("\n\n");
    for (String line : lines) {
      if (line.startsWith("User: ")) {
        chatCompletionRequest.addMessage(new ChatMessage("user", line.replace("User: ", "")));
      } else if (line.startsWith(currentCharacter + ": ")) {
        chatCompletionRequest.addMessage(
            new ChatMessage("assistant", line.replace(currentCharacter + ": ", "")));
      }
    }

    chatCompletionRequest.addMessage(msg);
    try {
      ChatCompletionResult chatCompletionResult = chatCompletionRequest.execute();
      Choice result = chatCompletionResult.getChoices().iterator().next();
      chatCompletionRequest.addMessage(result.getChatMessage());
      return result.getChatMessage();
    } catch (ApiProxyException e) {
      e.printStackTrace();
      return null;
    }
  }

  private void speakGpt(ChatMessage msg) {
    setChatLog();
    currentArea.appendText(currentCharacter + ": " + msg.getContent() + "\n\n");
    appendChatMessage(msg, currentCharacter);
    FreeTextToSpeech.speak(msg.getContent());
  }

  /**
   * Opens the chat view and sets the profession in the chat controller.
   *
   * @param event the mouse event that triggered the method
   * @param profession the profession to set in the chat controller
   * @throws IOException if the FXML file is not found
   */
  public void openChat(MouseEvent event, String profession) throws IOException {
    setHeadImage(event);
    setChatLog();

    // For the first time chatting with a suspect, use concurrency to play opening text while
    // generating response
    if (firstTimeCheck(currentCharacter)) {
      Task<Void> professionTask =
          new Task<Void>() {
            @Override
            protected Void call() throws Exception {
              setPrompt();
              return null;
            }
          };

      professionTask.setOnSucceeded(
          e -> {
            new Thread(
                    () -> {
                      while (!animationFinished) {
                        try {
                          Thread.sleep(50);
                        } catch (InterruptedException ex) {
                          Thread.currentThread().interrupt();
                        }
                      }
                      Platform.runLater(() -> speakGpt(openMsg));
                    })
                .start();
          });

      new Thread(professionTask).start();

      // Show different opening message for each suspect
      Platform.runLater(
          () -> {
            animationFinished = false;
            chatPane.setVisible(true);
            switch (currentCharacter) {
              case "Kid":
                appendChatMessage(
                    new ChatMessage("assistant", "You approach the kid. He seems full of energy."),
                    "");
                break;
              case "Grandma":
                appendChatMessage(
                    new ChatMessage("assistant", "You approach the grandma. She seems down."), "");
                break;
              case "Cashier":
                appendChatMessage(
                    new ChatMessage("assistant", "You approach the Cashier. He looks bored."), "");
              default:
                break;
            }
          });
    } else { // if is not first time chatting, don't play animation
      chatPane.setVisible(true);
      setPrompt();
    }
  }

  public void setHeadImage(MouseEvent event) {
    Rectangle rect = (Rectangle) event.getSource();
    String id = rect.getId();
    String characterName = id.replace("rect", "");
    this.currentCharacter = characterName;
    Image image =
        new Image(getClass().getResourceAsStream("/images/head" + characterName + ".png"));
    talkerImage.setImage(image);
  }

  @FXML
  private void onCloseChat() {
    chatPane.setVisible(false);
    clearChat();
  }

  public void clearChat() {
    txtaChat.clear();
  }

  private void setChatLog() {
    switch (currentCharacter) {
      case "Kid":
        currentArea = chatLogKid;
        break;
      case "Cashier":
        currentArea = chatLogCashier;
        break;
      case "Grandma":
        currentArea = chatLogGrandma;
      default:
        break;
    }
  }

  private void startCountdownTimer(int timerValue) {
    this.timerValue = timerValue;

    // Makes a new thread that runs in the background of everything and keeps the timer going at all
    // times during the game when it's supposed to
    timerThread =
        new Thread(
            () -> {
              try {
                while (this.timerValue
                    > 0) { // every second, decrease the tiner value by 1 and update the label
                  Thread.sleep(1000);
                  this.timerValue--;
                  Platform.runLater(() -> timerLabel.setText(Integer.toString(this.timerValue)));
                }
                if (!timerRanOut) { // if it ends for the first time, give extra 10 seconds to guess
                  // if eligible
                  Platform.runLater(
                      () -> {
                        if (!suspectTalkedTo || !clueLookedAt) {
                          timeOut();
                        } else {
                          try {
                            handleGuessClick();
                          } catch (IOException e) {
                            e.printStackTrace();
                          }
                        }
                      });
                } else {
                  timeOut();
                }
              } catch (InterruptedException e) {
                // Handle thread interruption
                Thread.currentThread().interrupt();
              }
            });
    timerThread.setDaemon(true);
    timerThread.start();
  }

  private void timeOut() {
    ranOutPlayer.play();
    incorrectGuess();
    context.setState(context.getGameOverState());
  }

  public void stopTimer() {
    if (timerThread != null && timerThread.isAlive()) {
      timerThread.interrupt();
    }
  }

  public void stopAnimation() {
    if (currentAnimation != null) {
      currentAnimation.stop();
    }
  }

  @FXML
  private void suspectGlow(MouseEvent event) {
    Rectangle rect = (Rectangle) event.getSource();
    String id = rect.getId();
    String characterName = id.replace("rect", "");
    // Fetches the appropriate glowing image of the suspect/clue
    Image image =
        new Image(getClass().getResourceAsStream("/images/glow" + characterName + ".png"));

    // Sets the image to the glowing one when the mouse enters the rectangle
    switch (characterName) {
      case "Kid":
        imageKid.setImage(image);
        break;
      case "Grandma":
        imageGrandma.setImage(image);
        break;
      case "Cashier":
        imageCashier.setImage(image);
        break;
      case "Bin":
        imageBin.setImage(image);
        break;
      case "Tag":
        imageTag.setImage(image);
        break;
      case "Tv":
        imageTv.setImage(image);
        break;
      default:
        break;
    }
  }

  @FXML
  private void suspectUnglow(MouseEvent event) {
    Rectangle rect = (Rectangle) event.getSource();
    String id = rect.getId();
    String characterName = id.replace("rect", "");
    // Get the right normal image of the current item/suspect
    Image image =
        new Image(
            getClass().getResourceAsStream("/images/" + characterName.toLowerCase() + ".png"));

    // When mouse leaves the rectangle, set back to not glowing image
    switch (characterName) {
      case "Kid":
        imageKid.setImage(image);
        break;
      case "Grandma":
        imageGrandma.setImage(image);
        break;
      case "Cashier":
        imageCashier.setImage(image);
        break;
      case "Bin":
        imageBin.setImage(image);
        break;
      case "Tag":
        imageTag.setImage(image);
        break;
      case "Tv":
        imageTv.setImage(image);
        break;
      default:
        break;
    }
  }

  private boolean firstTimeCheck(String currentCharacter) {
    switch (currentCharacter) {
      case "Kid":
        return firstKid;
      case "Grandma":
        return firstGrandma;
      case "Cashier":
        return firstCashier;
      default:
        return false;
    }
  }

  @FXML
  private void onCloseClue() {
    cluePane.setVisible(false);
    txtaClue.clear();
  }

  public void correctGuess() {
    lblHeader.setText("Game over! You correctly guessed the thief!");
    lblHeader.setAlignment(Pos.CENTER);
    headerPane.setStyle("-fx-background-color: #5bf08a;");
  }

  public void incorrectGuess() {
    lblHeader.setText("Game over! You did not guess the thief correctly.");
    lblHeader.setAlignment(Pos.CENTER);
    headerPane.setStyle("-fx-background-color: #f0795b;");
  }
}
