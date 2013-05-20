/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.animation.KeyFrame;
import javafx.animation.KeyValue;
import javafx.animation.Timeline;
import javafx.beans.property.BooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.WeakChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.ToolBar;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;
import org.jboss.logging.Logger;

/**
 * FXML Controller class
 *
 * @author grossco
 */
public class MainController implements Initializable {
    
    //Logger
    Logger log;
    
    /* Class variables for UI nodes for runtime generation and use.
     */
    
    AnchorPane vialRequestsPane;
    AnchorPane ingressManagementPane;
    AnchorPane modificationsPane;
    
    
    @FXML
    StackPane swapStack;

    /* Listener to handle when the requests panel indicates the data 
     * has been editted. Register the weak listener to avoid memory leaks from 
     * being too lazy to deal with unregistering the listners.
     */
    private final SwapPanelDataChangeListener reqDataModListener = new SwapPanelDataChangeListener();
    private final WeakChangeListener<Boolean> weakReqDataModListener =
            new WeakChangeListener<>(reqDataModListener);
    
    private final SwapPanelDataChangeListener ingDataModListener = new SwapPanelDataChangeListener();
    private final WeakChangeListener<Boolean> weakIngDataModListener =
            new WeakChangeListener<>(ingDataModListener);
    
    private final SwapPanelVisibilityListner swapVisibilityListener = new SwapPanelVisibilityListner();
    private final WeakChangeListener<Boolean> weakReqVisibilityListener =
            new WeakChangeListener<>(swapVisibilityListener);
    
    //Variables to store the sub panel controllers
    SwapPanelController currSwapPanelControl = null;
            
    /* Class variables that are also defined/used in corrosponding FXML.
     */
    
    //Pane to be swapped for different application uses: Ingress, Request, Mods.
    
    @FXML
    TextArea ta_info;

    @FXML
    Button btn_SwitchToRequests;
    
    @FXML
    Button btn_SwitchToIngress;
    
    @FXML 
    Button btn_commitEdits;
    
    @FXML
    Button btn_cancelEdits;
    
    @FXML
    ToolBar tbar_acts;
    
    //@FXML
    //TreeView<String> tview_prime;
    
    @FXML
    TextArea ta_welcome;
    
    
    
    
    @FXML
    private void handleDebugEvt(MouseEvent evt){

    }

    @FXML
    private void handleBtn_InitialAction(ActionEvent event) {
        ta_info.appendText("\nButton Clicked.");
    }
    
    @FXML
    private void handleBtn_SwithToRequests(ActionEvent event){
        log.debug("Handle switch to ingress event");
        //swapPaneChange(vialRequestsPane);
        swapPaneChangeTopStacked(vialRequestsPane);
    }
    
    @FXML
    private void handleBtn_SwitchToIngress(ActionEvent event){
        log.debug("Handle switch to ingress event");
        //swapPaneChange(ingressManagementPane);
        swapPaneChangeTopStacked(ingressManagementPane);
    }
    
    @FXML
    private void handleBtn_SwitchToModifications(ActionEvent event){
        log.debug("Handle switch to modifications event");
    }
    
    @FXML
    private void handleBtn_CommitEdits(ActionEvent event){
        log.debug("Handle commit edits.");
        //Call the commit edits of the current swap panel controller
        if(currSwapPanelControl != null){
            currSwapPanelControl.commitEdits();
        }
        log.debug("Handle Edits Complete.");
    }
    
    @FXML
    private void handleBtn_CancelEdits(ActionEvent event){
        log.debug("Handle cancel edits.");
        //Call the commit edits of the current swap panel controller
        if(currSwapPanelControl != null){
            currSwapPanelControl.cancelEdits();
        }
    }
    
    

    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        //setup logger
        log = org.jboss.logging.Logger.getLogger(Manreq.class);
        //populate the buttons of the actions toolbar
        
        //Load the panels that will be swapped out to handle the different task
        //views of the program.
        loadManagedPanels();

    }   
    
    /* Load the panels that will be swapped to accomodate the different types
     * of requests that are going to be managed by this applicition: 
     * Ingress, VialRequests, Modifications
      */
    private void loadManagedPanels(){

        //Load Vial Request Management Pane (vialRequestsPane)
        try{
            log.debug("Loading the Request Vials Management pane.");
            
            //Load the FXML node heirarchy for the RequestsTreeView
            FXMLLoader reqLoader = 
                    new FXMLLoader(this.getClass().getResource("ReqTreeView.fxml"));
            vialRequestsPane = (AnchorPane) reqLoader.load();
            
            //Inject the infoTextArea information into the child controller.
            ((SwapPanelController) reqLoader.getController()).setInfoTextArea(ta_info);

            //Set up a weak listener for data changed events
            ((SwapPanelController) reqLoader.getController())
                    .getObservableIsModified().addListener(weakReqDataModListener);
            
            //Set up a weak listener for the controller's visibility property
            ((SwapPanelController) reqLoader.getController())
                    .getObservableIsVisible().addListener(weakReqVisibilityListener);
            
        }catch (Exception Ex){
            log.error("Error loading Request Vials Management pane.");
            log.error(Ex);
            vialRequestsPane = null;
        }
        
        //Load the Ingress Panel
        try{
            log.debug("Loading the Ingress Management pane.");
            
            //Load the FXML node heirarchy for the RequestsTreeView
            FXMLLoader ingLoader = 
                    new FXMLLoader(this.getClass().getResource("IngressListView.fxml"));
            ingressManagementPane = (AnchorPane) ingLoader.load();
            
            //Inject the infoTextArea information into the child controller.
            ((SwapPanelController) ingLoader.getController()).setInfoTextArea(ta_info);

            //Set up a weak listener for data changed events
            ((SwapPanelController) ingLoader.getController())
                    .getObservableIsModified().addListener(weakIngDataModListener);
            
            //Set up a weak listener for the controller's visibility property
            ((SwapPanelController) ingLoader.getController())
                    .getObservableIsVisible().addListener(swapVisibilityListener);
            
        }catch (Exception Ex){
            log.error("Error loading Ingress Management pane.");
            log.error(Ex);
            vialRequestsPane = null;
        }
    }

    /*Given a node, add it to the stacked pane just below the top node, and 
     * call the left swipe off stack on the top node.  The result will be the 
     * replacement node showing.
    */
    private void swapPaneChangeTopStacked(Node replacement){
        //check that the passed node is not null
        if(replacement == null){
            //inform user
            ta_info.setText("The node you want to display failed to initialize "
                    + "at startup.  Either the FXML file is missing from the "
                    + "jar, or the components of the pane could not be "
                    + "initialized.");
            log.debug("Replacement node is null.");
            //return with no changes being made to stack pane
            return;
        }
        
        //check that the stack doesn't already contain the replacement
        if(swapStack.getChildren().contains(replacement) == true){
            log.debug("The replacement node to be switched to is already  in the stacked pane.");
            return;
        }
        
        if(swapStack.getChildren().size() > 0){
            //Add the replacement just behind the existing top node
            int topnum = swapStack.getChildren().size() - 1;
            //This will shift the current top of the stack up on index.
            swapStack.getChildren().add(topnum, replacement);
            //Now do an animation that will end with the removal of the top node.
            swapPaneLeftSwipeOffStack( swapStack.getChildren().get(topnum+1) );
        }else{
            //just add the new node
            swapStack.getChildren().add(replacement);
        }
        
        
    }

    //Do animation of swipe to the left and remove the node from it's parent panel.
    private void swapPaneLeftSwipeOffStack(final Node swipee){
        //Get the bounds of the panel to be swiped off (swipee)
        double x = swipee.getBoundsInParent().getMinX();
        double y = swipee.getBoundsInParent().getMinY();
        double h = swipee.getBoundsInParent().getHeight();
        double w = swipee.getBoundsInParent().getWidth();
        //get the initial TranslateX property it can be reset after animation
        final double initialX = swipee.getTranslateX();
        
        //Use the bounds to get a rectangle
        Rectangle clipRect = new Rectangle(x,y,w,h);
        
        //Set this rectancle as the clipping node for the swipee.
        swipee.setClip(clipRect);
        
        /* Setup Keyvalue for value transions for a swipe to the left.
         * The clipping rectangle will be have the width shrunk (from right to
         * left).  The node itself will be moved to the left the same amount.
         */
        KeyValue kvLeftClip = new KeyValue(clipRect.widthProperty(), 0.0);
        KeyValue kvLeftPane = new KeyValue(swipee.translateXProperty(), -w);
        //Setup keyframe in which to do the value transition
        KeyFrame kfLeftSwipe = new KeyFrame(Duration.millis(250), kvLeftClip, kvLeftPane);
        
        //Setup timeline to do the animation
        Timeline tLeftSwipe = new Timeline();
        tLeftSwipe.setCycleCount(1);
        tLeftSwipe.setAutoReverse(true);
        
        tLeftSwipe.getKeyFrames().add(kfLeftSwipe);
        
        //Use the callback to complete the action of removing the child panels
        // and adding a new one.
        tLeftSwipe.setOnFinished(new EventHandler<ActionEvent>(){
            @Override
            public void handle(ActionEvent t) {
                //Remove the swipee from it's parent.
                Pane parent = (Pane) swipee.getParent();
                parent.getChildren().removeAll(swipee);
                
                //Reset the clipping and translateXProperty.
                swipee.setClip(null);
                swipee.setTranslateX(initialX);
            }
        } );
        
        //play the animation.
        tLeftSwipe.play();
        
        //The animation will still be playing by the time this function returns
        log.debug("Left swipe off function done.");
        
    }
    
    
    private class SwapPanelDataChangeListener implements ChangeListener<Boolean>
    {
        @Override
        public void changed(ObservableValue<? extends Boolean> observable, 
            Boolean oldVal, Boolean newVal) 
        {
            log.debug("Swap panel bool listener called.");
            if(oldVal.booleanValue() == false && newVal.booleanValue() == true){
                //Turn buttons on by turning disable to false
                btn_commitEdits.disableProperty().set(false);
                btn_cancelEdits.disableProperty().set(false);
            }
            if(oldVal.booleanValue() == true && newVal.booleanValue() == false){
                //Turn buttons off
                btn_commitEdits.disableProperty().set(true);
                btn_cancelEdits.disableProperty().set(true);
            }
        }
    }
    
    
    /* Listener for making changes when a swap panel goes from visible=false
     * to visible = true.  The listened property belongs to the controller,
     * so the returned bean should be the controler and it should be an
     * implementation of SwapPanelController.
     */
    private class SwapPanelVisibilityListner implements ChangeListener<Boolean>
    {
        @Override
        public void changed(ObservableValue<? extends Boolean> ov, 
            Boolean oldVal, Boolean newVal) 
        {
            //if the panel was not showing, but now is showing
            if(oldVal.booleanValue() == false && newVal.booleanValue() == true){
                log.debug("Swap pane visibility listener: panel now visible.");
                //Get the swap panel controller, which should be the owner of 
                // the property, and set this as the new current controller
                currSwapPanelControl = 
                        (SwapPanelController) ((BooleanProperty) ov).getBean();
                
                //Check the state of the data changed property and adjust the
                // Cancel/Commit Edit buttons accordingly.
                if(currSwapPanelControl.getObservableIsModified()
                        .getValue().booleanValue() == true){
                    //Turn buttons on by turning disable to false
                    btn_commitEdits.disableProperty().set(false);
                    btn_cancelEdits.disableProperty().set(false);
                }else{
                    //Turn buttons off
                    btn_commitEdits.disableProperty().set(true);
                    btn_cancelEdits.disableProperty().set(true);
                }
            }
        }
    }
   
}
