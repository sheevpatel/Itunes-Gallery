package cs1302.gallery;

import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.util.Duration;
import java.util.Random;
import javafx.animation.Timeline;
import javafx.animation.KeyFrame;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.layout.HBox;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.layout.TilePane;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.control.ComboBox;
import javafx.scene.control.TextField;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.Priority;
import java.net.URLEncoder;
import java.io.IOException;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

/**
 * Represents an iTunes Gallery App.
 */
public class GalleryApp extends Application {

    /** HTTP client. */
    public static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_2)           // uses HTTP protocol version 2 where possible
        .followRedirects(HttpClient.Redirect.NORMAL)  // always redirects, except from HTTPS to HTTP
        .build();                                     // builds and returns a HttpClient object

    /** Google {@code Gson} object for parsing JSON-formatted strings. */
    public static Gson GSON = new GsonBuilder()
        .setPrettyPrinting()                          // enable nice output when printing
        .create();                                    // builds and returns a Gson object

    public static final String ITUNES_API = "https://itunes.apple.com/search";

    private static final String DEFAULT_IMAGE = "file:resources/default.png";

    private Stage stage;
    private Scene scene;
    private VBox root; //changed Hbox to Vbox

    //inside root container
    private HBox layerOne;
    private TilePane imageGrid;
    private HBox bottom;
    private Label instructions;

    private ProgressBar progressBar;

    private Image replace;

    private Label cite;

    //inside layer1
    private Button play;
    private Label label;
    private TextField search;
    private ComboBox<String> cmb;
    private Button getImages;

    //tilepane image
    private Image defImg;
    private ImageView imgVw;
    private ImageView imgVw2;

    private Image uniqueImage;
    private Image freshImage;

    private List<Image> downloadedImages = new ArrayList<>();
    private Timeline timeline;
    private String uri;
    private TilePane defaultPane;
    private double progress;
    private int successSearches;

    /**
     * Constructs a {@code GalleryApp} object}.
     */
    public GalleryApp() {
        this.stage = null;
        this.scene = null;
        this.root = new VBox(5); //changed HBox to VBox
        this.layerOne = new HBox(5);
        this.play = new Button("Play");
        play.setDisable(true);
        this.label = new Label("Search:");
        this.search = new TextField("daft punk");
        this.cmb = new ComboBox();
        this.getImages = new Button("Get Images");
        this.instructions = new Label("Type in a term, select a media, then click the button.");
        this.imageGrid = new TilePane();
        this.defImg = new Image(DEFAULT_IMAGE);
        this.bottom = new HBox();
        this.progressBar = new ProgressBar();
        this.progressBar.setProgress(0.00);
        this.progressBar.setPrefWidth(200);
        this.cite = new Label("Images provided by Itunes Search API");
    } // GalleryApp

    /** {@inheritDoc} */
    @Override
    public void init() {
        // feel free to modify this method
        System.out.println("init() called");
        root.getChildren().addAll(layerOne, instructions, imageGrid, bottom);
        layerOne.getChildren().addAll(play, label, search, cmb, getImages);
        cmb.getItems().addAll("movie",
            "podcast",
            "music", "musicVideo", "audiobook", "shortFilm", "tvShow", "software", "ebook", "all");
        cmb.setValue("music");
        HBox.setHgrow(layerOne, Priority.ALWAYS);
        HBox.setHgrow(play, Priority.ALWAYS);
        HBox.setHgrow(label, Priority.ALWAYS);
        HBox.setHgrow(search, Priority.ALWAYS);
        HBox.setHgrow(cmb, Priority.ALWAYS);
        HBox.setHgrow(getImages, Priority.ALWAYS);
        bottom.getChildren().addAll(progressBar, cite);
        imageGrid.setPrefColumns(5);
        imageGrid.setPrefRows(4);
        for (int i = 0; i < 20; i++) {
            Image defImg = new Image(DEFAULT_IMAGE);
            imgVw = new ImageView(defImg);
            imageGrid.getChildren().add(imgVw);
            //TilePane
            defaultPane = imageGrid;
        } //for

        EventHandler<ActionEvent> getImageHandler = (e) -> {
            //clear previous images
            if (downloadedImages.isEmpty() == false) {
                downloadedImages.clear();
            } //if

            //set progress to 0
            progressBar.setProgress(0.0);
            Runnable loadTask = () -> {
                this.loadImage(e);
            };
            runInNewThread(loadTask);
        };
        getImages.setOnAction(getImageHandler);

        //play button
        EventHandler<ActionEvent> getPlayHandler = (e) -> {
            this.shuffleImages(e);
        };
        play.setOnAction(getPlayHandler);

    } // init

    /** {@inheritDoc} */
    @Override
    public void start(Stage stage) {
        System.out.println("start called");
        this.stage = stage;
        this.scene = new Scene(this.root);
        this.stage.setOnCloseRequest(event -> Platform.exit());
        this.stage.setTitle("GalleryApp!");
        this.stage.setScene(this.scene);
        this.stage.sizeToScene();
        this.stage.show();
        Platform.runLater(() -> this.stage.setResizable(false));
    } // start


    /**
     * Loads images.
     *
     * Request building and Response along with string construction
     * used thanks to reference to Http reading example 3.
     * @param event event
     */
    public void loadImage(ActionEvent event) {
        try {
            String term = URLEncoder.encode(search.getText(), StandardCharsets.UTF_8);
            String media = URLEncoder.encode(cmb.getValue(), StandardCharsets.UTF_8);
            String limit = URLEncoder.encode("200", StandardCharsets.UTF_8);
            String query = String.format("?term=%s&media=%s&limit=%s", term, media, limit);
            uri = ITUNES_API + query;
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(uri))
                .build(); // build request
            // send request / receive response in the form of a String
            HttpResponse<String> response = HTTP_CLIENT
                .send(request, BodyHandlers.ofString());
            // ensure the request is okay
            if (response.statusCode() != 200) {
                throw new IOException(response.toString());
            } // if
             // get request body
            String jsonString = response.body();
            ItunesResponse itunesResponse = GSON
                .fromJson(jsonString, ItunesResponse.class); // parse the JSON string using GSON
            int count = 0;
            for (int i = 0; i < itunesResponse.resultCount; i++) {
                ItunesResult res = itunesResponse.results[i];
                String art = res.artworkUrl100;
                boolean checkUnique = false;
                for (int j = 0; j < downloadedImages.size(); j++) {
                    if (art.equals(downloadedImages.get(j).getUrl())) {
                        checkUnique = true;
                        break;
                    } //if
                } //for
                //if not in then download image
                if (checkUnique == false) {
                    uniqueImage = new Image(art);
                    downloadedImages.add(uniqueImage);
                    count++;
                } //if
                progress = (i + 1) / itunesResponse.resultCount;
                updateBar();
            } //for
            //check if enough downloads
            if (count <= 20) {
                throw new IllegalArgumentException(count
                + " distinct results found, but 21 or more are needed.");
            } //if
            updateGrid();
        } catch (IllegalArgumentException | InterruptedException | IOException e) {
            errorMessage(e);
        } // try
    } //loadImage

    /**
     * Update grid.
     */
    public void updateGrid() {
        Platform.runLater(() -> {
            imageGrid.getChildren().clear();
            imageGrid.setPrefColumns(5);
            imageGrid.setPrefRows(4);
            imageGrid.setPrefTileWidth(100.0);
            imageGrid.setPrefTileHeight(100.0);
            successSearches++;
            for (int i = 0; i < 20; i++) {
                replace = downloadedImages.get(i);
                imgVw2 = new ImageView(replace);
                imageGrid.getChildren().add(imgVw2);
            } //for
            stage.sizeToScene();
        });
    } //updateGrid

    /**
     * update bar.
     */
    public void updateBar() {
        Platform.runLater(() -> {
            progressBar.setProgress(progress);

            if (progressBar.getProgress() != 1) {
                Platform.runLater(() -> {
                    instructions.setText("Getting images...");
                    getImages.setDisable(true);
                });
            } //if
            if (progressBar.getProgress() == 1) {
                Platform.runLater(() -> {
                    instructions.setText(uri);
                    getImages.setDisable(false);
                    play.setDisable(false);
                });
            } //if
        });


    } //updateBar

    /**
     * Shuffle images if play clicked.
     * @param event event
     */
    public void shuffleImages(ActionEvent event) {
        //used timeline to shuffle images
        timeline = new Timeline();
        KeyFrame keyFrame = new KeyFrame(Duration.seconds(2), event2 -> {
            Random random = new Random();
            int randomTile = random.nextInt(20); //random image from tilepane
            int randomStorage = random.nextInt(downloadedImages.size()); //random image in storage
            Image replacerImage = downloadedImages.get(randomStorage);
            ImageView replacerView = new ImageView(replacerImage);
            imageGrid.getChildren().set(randomTile, replacerView);
        });
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.getKeyFrames().add(keyFrame);
        timeline.play();
        play.setText("Pause");
        if (play.getText().equals("Pause")) {
            EventHandler<ActionEvent> stopPlay = (e) -> {
                this.stopShuffle(e);
            };
            play.setOnAction(stopPlay);
        } //if
    } //shuffleImages

    /**
     * Stop shuffling the images if pause clicked.
     * @param event event
     */
    public void stopShuffle(ActionEvent event) {
        timeline.stop();
        play.setText("Play");
        EventHandler<ActionEvent> resumePlay = (e) -> {
            this.shuffleImages(e);
        };
        play.setOnAction(resumePlay);
    } //stopShuffle

    /**
     * If an error occurs, a popup window will display for the user.
     * @param reason reason
     */
    public void errorMessage(Throwable reason) {
        Platform.runLater(() -> {
            Alert alert = new Alert(AlertType.ERROR);
            alert.getDialogPane().setContentText("URI " + uri + "\n" + "Exception: "
                + reason.toString());
            alert.setResizable(true);
            instructions.setText("Last attempt to get images failed...");
            progressBar.setProgress(1.);
            alert.showAndWait();
            if (successSearches >= 1) {
                play.setDisable(false);
            } else if (successSearches == 0) {
                play.setDisable(true);
            } //if
        });
    } //errorMessage

    /**
     * Thread method which was created in lecture.
     * @param task task
     */
    private static void runInNewThread(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
    } //runInNewThread


/** {@inheritDoc} */
    @Override
    public void stop() {
        // feel free to modify this method
        System.out.println("stop() called");
    } // stop

} // GalleryApp
