/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;

/**
 * FXML Controller class
 *
 * @author grossco
 */
public class ConfirmationPanelController implements Initializable {

    //default completely false return state. Might just want to track confirmed.
    private Boolean wasConfirmed = Boolean.FALSE;
    private Boolean wasCancelled = Boolean.FALSE;
    
    //node to display in the center of the dialog to provide info to the user
    Node infoNode;
    
    @FXML
    AnchorPane descriptionPane;
    
    @FXML
    AnchorPane rootPane;
    
    @FXML
    Button btn_Confirm;
    @FXML
    Button btn_Cancel;
    
    @FXML
    TextArea defaultTextArea;
    
    
    @FXML
    private void handleConfirmClick(ActionEvent aEvt){
        wasCancelled = Boolean.FALSE;
        wasConfirmed = Boolean.TRUE;
        rootPane.getScene().getWindow().hide();
    }
    
    @FXML
    private void handleCancelClick(ActionEvent aEvt){
        wasCancelled = Boolean.TRUE;
        wasConfirmed = Boolean.FALSE;
        rootPane.getScene().getWindow().hide();
    }
        
    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        //Initially the info Node to default text area
        setInfoNode(defaultTextArea);
        
        //setListener for on show. Do reset on showing
        rootPane.visibleProperty().addListener( new VisibilityChangeListener() );
        
        //setListener for scene ownership changes
        //rootPane.sceneProperty().addListener(rb);
    }    
    
    public void setInfoNode(Node n){
        infoNode = n;
    }
    
    public void setInfoText(String s){
        defaultTextArea.setText(s);
        setInfoNode(defaultTextArea);
    }
    
    public Boolean getWasConfirmed(){
        return wasConfirmed;
    }
    
    public Boolean getWasCancelled(){
        return wasCancelled;
    }
    
    /* Use the visibility property to determine when to reset the boolean values
     *  of the message box.
     */
    private class VisibilityChangeListener implements ChangeListener<Boolean>{

        @Override
        public void changed(ObservableValue<? extends Boolean> ov, 
            Boolean oldVal, Boolean newVal) 
        {
            if(oldVal == false & newVal == true){
                //Scene is now showing: reset values
                wasConfirmed = Boolean.FALSE;
                wasCancelled = Boolean.FALSE;
            }
        }
        
    }
    

}
