package nz.ac.auckland.se206.controllers;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
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
import nz.ac.auckland.se206.speech.TextToSpeech;

/**
 * Controller class for the room view. Handles user interactions within the room where the user can
 * chat with customers and guess their profession.
 */
public class RoomController {

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

  @FXML private Label lblProfession;
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

  private ChatCompletionRequest chatCompletionRequest;
  private String profession;
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

  private static boolean isFirstTimeInit = true;
  private GameStateContext context = new GameStateContext(this);
  private Media ranOutAudio =
      new Media(getClass().getResource("/sounds/ranOutAudio.mp3").toString());
  private MediaPlayer ranOutPlayer = new MediaPlayer(ranOutAudio);

  private Media talkToSuspectAudio =
      new Media(getClass().getResource("/sounds/talkToSuspectAudio.mp3").toString());
  private MediaPlayer talkToSuspectPlayer = new MediaPlayer(talkToSuspectAudio);

  private Media tenSecondsAudio =
      new Media(getClass().getResource("/sounds/tenSeconds.mp3").toString());
  private MediaPlayer tenSecondsPlayer = new MediaPlayer(tenSecondsAudio);

  /**
   * Initializes the room view. If it's the first time initialization, it will provide instructions
   * via text-to-speech.
   */
  @FXML
  public void initialize() {
    startCountdownTimer(121);
    if (isFirstTimeInit) {
      TextToSpeech.speak("You have 10 seconds to make a guess on who the thief is.");
      isFirstTimeInit = false;
    }
    lblProfession.setText(context.getProfessionToGuess());
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
    suspectTalkedTo = true;
    Rectangle clickedRectangle = (Rectangle) event.getSource();
    context.handleRectangleClick(event, clickedRectangle.getId());
  }

  @FXML
  private void handleClueClick(MouseEvent event) throws IOException {
    if (stateIsGameStarted) {
      closeChat();
      clueLookedAt = true;
      Rectangle clickedRectangle = (Rectangle) event.getSource();
      String rectId = clickedRectangle.getId();
      String clueText = "";
      switch (rectId) {
        case "rectBin":
          clueText = "looks like a bin lol";
          break;
        case "rectTag":
          clueText = "lmao now it's a tag";
          break;
        case "rectTv":
          clueText = "wow tv so cool";
          break;
        default:
          break;
      }
      cluePane.setVisible(true);
      appendChatMessage(new ChatMessage("user", clueText), "", "clue");
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
    }
    if (stateIsGameStarted) {
      closeChat();
      stopTimer();
      timerRanOut = true;
      tenSecondsPlayer.play();
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
    String message = txtInput.getText().trim();
    if (message.isEmpty()) {
      return;
    }
    txtInput.clear();
    ChatMessage msg = new ChatMessage("user", message);
    Task<ChatMessage> gptTask =
        new Task<ChatMessage>() {
          @Override
          protected ChatMessage call() throws Exception {
            return runGpt(msg);
          }
        };

    gptTask.setOnSucceeded(
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

    new Thread(gptTask).start();

    Platform.runLater(
        () -> {
          animationFinished = false;
          setChatLog();
          currentArea.appendText("User: " + msg.getContent() + "\n\n");
          appendChatMessage(msg, "User", "chat");
        });
  }

  /**
   * Sets the profession for the chat context and initializes the ChatCompletionRequest.
   *
   * @param profession the profession to set
   */
  public void setProfession(String profession) {
    this.profession = profession;

    if (!firstTimeCheck(currentCharacter)) {
      return;
    }

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

    try {
      ApiProxyConfig config = ApiProxyConfig.readConfig();
      chatCompletionRequest =
          new ChatCompletionRequest(config)
              .setN(1)
              .setTemperature(0.2)
              .setTopP(0.5)
              .setMaxTokens(2);
      ChatMessage openMsg = runGpt(new ChatMessage("system", getSystemPrompt()));
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
  private String getSystemPrompt() {
    Map<String, String> map = new HashMap<>();
    map.put("profession", profession);
    return PromptEngineering.getPrompt("chat.txt", map);
  }

  /**
   * Appends a chat message to the chat text area.
   *
   * @param msg the chat message to append
   */
  private void appendChatMessage(ChatMessage msg, String sender, String area) {
    TextArea currentArea;
    switch (area) {
      case "clue":
        currentArea = txtaClue;
        break;
      default:
        currentArea = txtaChat;
        break;
    }
    if (sender != "") {
      currentArea.appendText(sender + ": ");
    }
    final int[] index = {0};
    Timeline timeline =
        new Timeline(
            new KeyFrame(
                Duration.millis(100),
                event -> {
                  if (index[0] < msg.getContent().length()) {
                    currentArea.appendText(String.valueOf(msg.getContent().charAt(index[0])));
                    index[0]++;
                  }
                }));
    timeline.setCycleCount(msg.getContent().length());
    timeline.setOnFinished(
        event -> {
          currentArea.appendText("\n\n");
          animationFinished = true;
        });
    timeline.play();
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
    appendChatMessage(msg, currentCharacter, "chat");
    TextToSpeech.speak(msg.getContent());
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

    if (firstTimeCheck(currentCharacter)) {
      Task<Void> professionTask =
          new Task<Void>() {
            @Override
            protected Void call() throws Exception {
              setProfession(profession);
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

      Platform.runLater(
          () -> {
            animationFinished = false;
            chatPane.setVisible(true);
            switch (currentCharacter) {
              case "Kid":
                appendChatMessage(new ChatMessage("assistant", "kid filler text lol"), "", "chat");
                break;
              case "Grandma":
                appendChatMessage(
                    new ChatMessage("assistant", "grandma filler text lol"), "", "chat");
                break;
              case "Cashier":
                appendChatMessage(
                    new ChatMessage("assistant", "cashier filler text lol"), "", "chat");
              default:
                break;
            }
          });
    } else {
      chatPane.setVisible(true);
      setProfession(profession);
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
  public void closeChat() {
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

    timerThread =
        new Thread(
            () -> {
              try {
                while (this.timerValue > 0) {
                  Thread.sleep(1000);
                  this.timerValue--;
                  Platform.runLater(() -> timerLabel.setText(Integer.toString(this.timerValue)));
                }
                if (!timerRanOut) {
                  Platform.runLater(
                      () -> {
                        if (!suspectTalkedTo) {
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
    context.setState(context.getGameOverState());
  }

  public void stopTimer() {
    if (timerThread != null && timerThread.isAlive()) {
      timerThread.interrupt();
    }
  }

  @FXML
  private void suspectGlow(MouseEvent event) {
    Rectangle rect = (Rectangle) event.getSource();
    String id = rect.getId();
    String characterName = id.replace("rect", "");
    Image image =
        new Image(getClass().getResourceAsStream("/images/glow" + characterName + ".png"));
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
    Image image =
        new Image(
            getClass().getResourceAsStream("/images/" + characterName.toLowerCase() + ".png"));
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
  public void closeClue() {
    cluePane.setVisible(false);
    txtaClue.clear();
  }
}
