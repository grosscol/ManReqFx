/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;

import java.net.URL;
import java.util.ResourceBundle;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.layout.AnchorPane;
import org.apache.log4j.Logger;

/**
 * FXML Controller class
 *
 * @author grossco
 */
public class IngressListViewController implements Initializable, SwapPanelController {

        //Logger for errors and debugging
    final Logger log = Logger.getLogger(this.getClass());
    //Text area for displaying information to the user.  Injectable as per the
    //MainInterface spec.
    TextArea infoTextOut;
    
    /* Simple property for tracking if the data has been isDataModified and needs to 
     * be persisted in the database.  The main user interface will be listening
     * on this value*/
    public BooleanProperty isDataModified = 
            new SimpleBooleanProperty(this, "isDataModified", false);
    
    public BooleanProperty isShowing = 
            new SimpleBooleanProperty(this, "isPanelShowing", false);
    
    @FXML
    ListView ingressListView;
    
    @FXML
    AnchorPane ingreesPane;
    
    
    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        ingreesPane.sceneProperty().addListener(new SceneMembershipListener() );
    }    

    @Override
    public void setInfoTextArea(TextArea ta) {
        infoTextOut = ta;
    }

    @Override
    public BooleanProperty getObservableIsModified() {
        return isDataModified;
    }

    @Override
    public BooleanProperty getObservableIsVisible() {
       return isShowing;
    }

    @Override
    public void commitEdits() {
        //Do some stuff.
        this.isDataModified.set(false);
    }
    
    @Override
    public void cancelEdits() {
        //Cancel doing stuff.
        this.isDataModified.set(false);
    }
    
        private class SceneMembershipListener implements ChangeListener<Scene>{

        @Override
        public void changed(ObservableValue<? extends Scene> ov, 
            Scene oldVal, Scene newVal) 
        {
            log.debug("Ingress panel scene membership value changed.");
            
            //was part of a scene, but now is not part of a scene
            if(oldVal != null && newVal == null){
                isShowing.set(false);
            }
            
            //was not part of a scene but not is part of a scene
            if(oldVal == null && newVal != null){
                isShowing.set(true);
            }
        }
        
    }
}
