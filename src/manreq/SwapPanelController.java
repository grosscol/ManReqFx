/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;

import javafx.beans.property.BooleanProperty;
import javafx.scene.control.TextArea;

/**
 *
 * @author grossco
 */
public interface SwapPanelController {
    
    /* Method to inject the Text Area that the controller should use to report
     * information to the user 
     */
    public void setInfoTextArea(TextArea ta);
    
    /* Method that the containing controller can use to set a listener to take
     * action when the data state changes from modified to unmodified.
     */
    public BooleanProperty getObservableIsModified();
    
    /* Method that the containing controller can use to set a listener to take
     * action when the swap panels controller registers that it's main anchor
     * pane is visible.
     */
    public BooleanProperty getObservableIsVisible();
    
    public void commitEdits();
    
    public void cancelEdits();
    
}
