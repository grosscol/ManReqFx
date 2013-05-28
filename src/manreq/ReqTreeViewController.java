/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;

import java.awt.print.PageFormat;
import java.awt.print.PrinterJob;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.collections.ListChangeListener;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.SelectionMode;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeItem.TreeModificationEvent;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.AnchorPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
import javax.print.DocFlavor;
import javax.print.PrintService;
import javax.print.PrintServiceLookup;
import javax.print.attribute.HashPrintRequestAttributeSet;
import javax.print.attribute.PrintRequestAttributeSet;
import javax.print.attribute.PrintServiceAttributeSet;
import javax.print.attribute.standard.Copies;
import org.apache.log4j.Logger;
import tilpersist.Norminv;
import tilpersist.Request;

/**
 * FXML Controller class
 *
 * @author grossco
 */
public class ReqTreeViewController implements Initializable, SwapPanelController {

    //Logger for errors and debugging
    final Logger log = Logger.getLogger(this.getClass());
    
    //Text area for displaying information to the user.  Injectable as per the
    //MainInterface spec.
    TextArea infoTextOut;
    
    //Separate stage for displaying the confirm/cancel panel as a modal dialog.
    Stage confirmStage;
    
    //Controller for handling the confirm changes prompt.
    ConfirmationPanelController cpc;
    
    /* Simple property for tracking if the data has been isDataModified and needs to 
     * be persisted in the database.  The main user interface will be listening
     * on this value*/
    public BooleanProperty isDataModified = 
            new SimpleBooleanProperty(this, "isDataModified", false);
    
    public BooleanProperty isShowing =
            new SimpleBooleanProperty(this, "isPanelShowing", false);
    
    ReqTreeItem<Request> tiPendAppr;
    ReqTreeItem<Request> tiCompleted;
    ReqTreeItem<Request> tiPendPull;
    
    @FXML
    TreeView reqTreeView;

    @FXML
    AnchorPane requestsPane;
    
    @FXML
    Pane busyOverlay;
    
    @FXML
    VBox refreshOptionsBox;
     
    //Context Menu for the TreeCells representing Carts
    @FXML
    static ContextMenu reqCartConMen;
    
    //Context Menu for the TreeCells linked to Entry Items
    @FXML
    static ContextMenu reqEntryConMen;
    
    //Context Menu for the Organizing Categories
    @FXML
    static ContextMenu reqCategoryConMen;
  
    @FXML
    public void cartMarkApproved(ActionEvent aEvt){
        log.debug("Cart mark approved handler");
    }
    
    @FXML
    public void cartMarkPulled(ActionEvent aEvt){
        log.debug("Cart mark pulled handler");
    }
    
    @FXML
    public void printSelected(ActionEvent aEvt){
        //Get list of requests
        
        //Pass list of requests to Handle Print
        ArrayList<Request> al = new ArrayList<>();
        printRequests(al);
    }
    
    @FXML
    public void printCart(ActionEvent aEvt){
        //Get list of requests
        
        //Pass list of requests to Handle Print
        ArrayList<Request> al = new ArrayList<>();
        printRequests(al);
    }
    
    @FXML
    public void entryEdit(ActionEvent aEvt){
        log.debug("Entry: edit. action handler");
    }
    
    @FXML
    public void entryMoveToNewCart(ActionEvent aEvt ){
        log.debug("Entry: move to new cart. action handler");
        //Check that all selected items in tree are ENTRYs from same cart.
        if(checkSelectionHomogeneity() == false){
            return;
        }
        
        //Get the item that was last selected in the TreeView
        ReqTreeItem selItem = 
                (ReqTreeItem) reqTreeView.getSelectionModel().getSelectedItem();
        
        //Get the parent (the cart) of the last selected item 
        ReqTreeItem parentCart = (ReqTreeItem) selItem.getParent();
        
        //Get list of the indecies of the items that need to be moved
        //Make a snapshot of the selected indecies.
        List<Integer> indecies = reqTreeView.getSelectionModel().getSelectedIndices();
        //Build list of Tree items that are to be moved.
        List<ReqTreeItem> leafsToBeMoved = new ArrayList<>();
        for( Integer i : indecies){
            leafsToBeMoved.add( 
                    (ReqTreeItem) reqTreeView.getTreeItem(i) );
        }
        
        //Clear the selection from the TreeView.  It will no longer be used.
        reqTreeView.getSelectionModel().clearSelection();
            
        //Get the old cart number hash
        Long oldCartNum = ((Request) selItem.getValue()).getCartnum();
        Long newCartNum = getFreshCartNum(oldCartNum);
         
        //Create new cart item with the same text as the original cart parentCart.orgzText
        ReqTreeItem dupCart = 
                new ReqTreeItem(parentCart.orgzText, ReqTreeItem.ItemType.CART);
        
        //index of parent cart in category
        int j = parentCart.getParent().getChildren().indexOf(parentCart);
        
        //Add the duplicated cart to the children of the parent of the parentCart
        //Basically make a duplicated cart under the same node as the original.
        parentCart.getParent().getChildren().add(j, dupCart);
         
        /*Make a backup of the first Request item before it is isDataModified.
         * This is the only one that has to be isDataModified here since it is being 
         * moved into an empty cart.  The other entries will be altered to match 
         *  the entry when they are added. The acceptEntryFromOtherCart will 
         * handle the backups and modifications of the remainder. 
        */
        DataModel.getInstance().backupRequest(
                ((Request) leafsToBeMoved.get(0).getValue() ),
                leafsToBeMoved.get(0).getDataHasBeenDeleted()
                );

        //Modify the cart number on the first item after it has been backedup.
        ((Request) leafsToBeMoved.get(0).getValue() ).setCartnum(newCartNum);

        //Set the controller's isDataModified property to true
        this.isDataModified.set(true);
        
        //Move each item in the selection using acceptEntryFromOtherCart method.
        //This method will handle backups and changing cartNum information.
        for(int i=0; i < leafsToBeMoved.size(); i++){
            dupCart.acceptEntryFromOtherCart(leafsToBeMoved.get(i));
        }
        
        //After Everything is moved, expand the duplicate cart
        dupCart.setExpanded(true);

    }
        
    @FXML
    public void entryRemove(ActionEvent aEvt){
        log.debug("Entry: remove. action handler");
        
        //Get the item that was last selected in the TreeView
        ReqTreeItem selItem = 
                (ReqTreeItem) reqTreeView.getSelectionModel().getSelectedItem();
        
        Request r = (Request) selItem.getValue();
        
        //If there isn't a request, return immediately.
        if(r == null){return;}
        
        //Tell the data model to make a backup, and that this node is to be 
        //deleted.
        DataModel.getInstance().backupRequest(r, Boolean.TRUE);
        
        //Set data has been modified flag for the controller.
        this.setDataModified(true);
        
        //Mark the item display flag indicating that it will be deleted.
        selItem.setDataHasBeenDeleted(Boolean.TRUE);
        
        //update UI force redra. Kluge
        selItem.getParent().setExpanded(false);
        selItem.getParent().setExpanded(true);
    }
    
    @FXML
    public void categorySummary(ActionEvent aEvt){
        log.debug("Category: summary. action handler");
    }
    
    @FXML
    public void refreshPending(ActionEvent aEvt){     
        //Hide the refresh options Box.
        refreshOptionsBox.setVisible(false);

        infoTextOut.setText("Refreshing pending requests from database.");
        
        //First do a simple validation query. This will block the UI.
        if(DataModel.getInstance().simpleValidationQuery() == false){
            //In the event of failure, something is seriously f'ed with the db.
            infoTextOut.appendText("\nSimple Validation Query failed."
                    + "\nSomething is very wrong with the database/connection.");
        }else{

            infoTextOut.appendText("\nSimple validation query succeeded."
                    + "\nDatabase connection is okay."
                    + "\nDoing pending approval query...");
            
            //refresh Pending Approval data and Tree Items
            refreshPendAppr();
            
            //refresh Pending Pull data and Tree Items
            refreshPendPull();
            
            
            //Remove overlay and permit mouse events through
            busyOverlay.setVisible(false);
            busyOverlay.setMouseTransparent(true);

            setDataModified(false);

            //reset all the Tree Items is modified display flag to false.
            resetAllChildItemsIsModified(
                (ReqTreeItem) reqTreeView.getRoot()
                );
        }
    }
    
    @FXML
    public void refreshAll(ActionEvent aEvt){
        //Hide the refresh options Box.
        refreshOptionsBox.setVisible(false);

        infoTextOut.setText("Refreshing requests from database.");
        
        //First do a simple validation query. This will block the UI.
        if(DataModel.getInstance().simpleValidationQuery() == false){
            //In the event of failure, something is seriously f'ed with the db.
            infoTextOut.appendText("\nSimple Validation Query failed."
                    + "\nSomething is very wrong with the database/connection.");
        }else{

            infoTextOut.appendText("\nSimple validation query succeeded."
                    + "\nDatabase connection is okay."
                    + "\nDoing pending approval query...");
            
            //refresh Pending Approval data and Tree Items
            refreshPendAppr();
            
            infoTextOut.appendText("\nDoing pending pull query...");
            
            //refresh Pending Pull data and Tree Items
            refreshPendPull();
            
            infoTextOut.appendText("\nDoing completed requets query...");
            //refresh Completed Requests data and Tree Items
            refreshCompleted();
            
            //Remove overlay and permit mouse events through
            busyOverlay.setVisible(false);
            busyOverlay.setMouseTransparent(true);

            setDataModified(false);

            //reset all the Tree Items is modified display flag to false.
            resetAllChildItemsIsModified(
                (ReqTreeItem) reqTreeView.getRoot()
                );
        }
    }
    
    /**
     * Initializes the controller class.
     */
    @Override
    public void initialize(URL url, ResourceBundle rb) {
        //Populate the request tree view
        popRequestTreeView();
        
        //Setup the stage for confirm/cancel
        confirmStage = new Stage(StageStyle.TRANSPARENT);
        confirmStage.initModality(Modality.WINDOW_MODAL);
        confirmStage.setScene(null);
        confirmStage.setOnShown(
            new EventHandler<WindowEvent>(){
                @Override
                public void handle(WindowEvent t) {
                confirmStage.setX(
                    confirmStage.getOwner().getX() + 
                    confirmStage.getOwner().getWidth()/2 -
                    confirmStage.getWidth()/2
                );
                confirmStage.setY(
                    confirmStage.getOwner().getY() + 
                    confirmStage.getOwner().getHeight()/2 -
                    confirmStage.getHeight()/2
                ); 
                }   
            });
        
        try{
            log.debug("Loading the Request Vials Management pane.");
            
            //Load the FXML node heirarchy for the RequestsTreeView
            FXMLLoader confirmLoader = 
                    new FXMLLoader(this.getClass().getResource("ConfirmationPanel.fxml"));
            Scene s = new Scene( (Parent) confirmLoader.load() );
            confirmStage.setScene(s);
            cpc = confirmLoader.getController();
        }catch(Exception myEx){
            log.error(myEx);
            //confirm stage is bad at this point.  Won't be able to make changes.
            confirmStage = null;
            cpc = null;
        }

        //Add a listener for scene membership.  Allow the controller to track
        // when the scene is being shown.
        requestsPane.sceneProperty().addListener(new SceneMembershipListener() );
        
        //Add a listener to the Requests arm of the DataModel
        DataModel.getInstance().getIsRequestsBusy().addListener(new DataModelBusyListener());
    }    
    
    @Override
    public void setInfoTextArea(TextArea ta) {
        if(ta == null){
            ta = new TextArea();
            ta.setVisible(false);
        }else{
            infoTextOut = ta;
        }
        
    }
        
    @Override
    public BooleanProperty getObservableIsModified() {
        return(this.isDataModified);
    }

    @Override
    public BooleanProperty getObservableIsVisible(){
        return isShowing;
    }
    
    @Override
    public void commitEdits() {
        //Show confirm/cancel dialog or inform user why there is a problem.
        if(confirmStage == null){ return; }
        //need to set the owner of the stage
        if(confirmStage.getOwner() == null){
            //set the owner of the new stage (so that it blocks properly)
            confirmStage.initOwner(requestsPane.getScene().getWindow());
        }

        //Show confirmation dialog and wait for it to close.
        confirmStage.showAndWait();
        
        //get answer from the confirmController
        if(cpc.getWasConfirmed() == Boolean.TRUE){
            //This will return immediately.
            DataModel.getInstance().commitRequestChanges();
        }
    }
    
    //Function to put all the UI elements back in place.
    @Override
    public void cancelEdits(){
        //Set the data isDataModified to false
        this.isDataModified.set(false);
             
        //Undo changes:
        /* Find the destination of the item.  
         * Make the UI match the destination.
         * Restore the old data.
         */
        
        //Get map of original values, modded (current) values
        Map modMap = DataModel.getInstance().getReadOnlyRequestChanges();
        
        
        //For each original request object in the modded list.
        for(Object orig : modMap.keySet()){
            //cast to correct type
            Request origReq = (Request) orig;
            Request currReq = (Request) modMap.get(origReq);
            
            //The tree item that will be moved and changed.
            ReqTreeItem source;
            //Destination that the request will be re-inserted into.
            ReqTreeItem destParent;
            
            //If the currReq is null, the entry was marked for deletion.
            if(currReq == null){
                //The original item is the current item, but the cartnum of the 
                // TreeItem value may have changed. Use the ReqIndex to find the entry.
                source = findTreeItemWithReqIndex(
                            (ReqTreeItem) reqTreeView.getRoot(), origReq.getReqIndex() );
            }else{
                //get the corresponding, current request tree item
                source =  findTreeItemWithValue(
                            (ReqTreeItem) reqTreeView.getRoot(), currReq );
            }
            
            //Search the appropriate branch
            if(origReq.getDatepull() != null){
                //Search Completed
                destParent = findParentOfCartNum(tiCompleted, origReq.getCartnum());
            }else if(origReq.getDateappr() != null){
                //Search Pending Pull
                destParent = findParentOfCartNum(tiPendPull, origReq.getCartnum());
            }else{
                //Search Pending Approval
                destParent = findParentOfCartNum(tiPendAppr, origReq.getCartnum());
            }
            
            //if the destination is null, the entire cart has been moved, or
            // the original cart no longer exists. Create new destination cart.
            if(destParent == null){
                //Create a new destination cart
                destParent = new ReqTreeItem(ReqTreeItem.ItemType.CART);   
                //Add the destination to the proper category based on the 
                // original Request
                if(origReq.getDatepull() != null){
                    tiCompleted.getChildren().add(destParent);
                }else if(origReq.getDateappr() != null){
                    tiPendPull.getChildren().add(destParent);
                }else{
                    tiPendAppr.getChildren().add(destParent);
                }
            }
            
            if(source.getParent() != null){
                //remove itself from the parent
                source.getParent().getChildren().remove(source);
            }
                        
            //add itself to the destination
            destParent.getChildren().add(source);
            
            //change the value to the original request object
            source.setValue(orig);
            
            //set the value of the parent (cart) to null to force recalc of the
            //text for the cart.  This will update it if neccissary.
            source.getParent().setValue(null);
        }
        
        //Remove carts that have no nodes and no value
        removeLeafNonValueItems((ReqTreeItem) reqTreeView.getRoot());
           
        //reset all the nodes to not modified flag.
        resetAllChildItemsIsModified(
            (ReqTreeItem) reqTreeView.getRoot()
            );

        //Notify the Data Model that all the modified elements are back in place
        DataModel.getInstance().clearBackupRequests();
        
        //force rerender. Kluge.
        reqTreeView.getRoot().setExpanded(false);
        reqTreeView.getRoot().setExpanded(true);
        
        
    }
    
////////////////////////// UTILITY FUNCTIONS ///////////////////////////////////
    //Call DataModel to re-obtain backing data. Retrieve backing data, and 
    //re-display.  This will block the user interface thread until it's done.
    private void refreshPendAppr(){
        //Update pending approval in data model.
        if(DataModel.getInstance().queryPendingApproval() == true){
            infoTextOut.appendText("\nPending approval query succeeded.");

            //Query succeeded. Update the UI.
            tiPendAppr.getChildren().clear();
            tiPendAppr.getChildren().addAll(
                groupRequestsByCart( DataModel.getInstance().getPendingAppr() ));
        }else{
            //Query failed. Update the UI.
            infoTextOut.appendText("\nPending approval query failed.");
            tiPendAppr.getChildren().clear();
        }
    }
    
    //Call DataModel to re-obtain backing data. Retrieve backing data, and 
    //re-display.  This will block the user interface thread until it's done.
    private void refreshPendPull(){
        //Update pending pull in data model.
        if(DataModel.getInstance().queryPendingPull() == true){
            infoTextOut.appendText("\nPending pull query succeeded.");

            //Query succeeded. Update the UI
            tiPendPull.getChildren().clear();
            tiPendPull.getChildren().addAll(
                groupRequestsByCart( DataModel.getInstance().getPendingPull() ));
        }else{
            //Query failed. Update the UI
            infoTextOut.appendText("\nPending pull query failed.");
            tiPendPull.getChildren().clear();
        }
    }
    
    //Call DataModel to re-obtain backing data. Retrieve backing data, and 
    //re-display.  This will block the user interface thread until it's done.
    private void refreshCompleted(){
        //Update completed requests in data model.
        
        if(DataModel.getInstance().queryLastCompletedAfter(new Date()) == true){
            infoTextOut.appendText("\nCompleted requests query succeeded.");

            //Query succeeded. Update the UI
            tiCompleted.getChildren().clear();
            tiCompleted.getChildren().addAll(
                groupRequestsByCart( DataModel.getInstance().getPendingPull() ));
        }else{
            //Query failed. Update the UI
            infoTextOut.appendText("\nCompleted requests query failed.");
            tiCompleted.getChildren().clear();
        }
    }
    
    /* Function to populate the tree view with information from Data Model.*/
    private void popRequestTreeView(){
        try{          
            //Set the TreeCell Factory callback for the tree view
            reqTreeView.setCellFactory( new ReqTreeCellFactory() );
            //Set the Selecion mode for the tree view
            reqTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            //Add a listener for selection changes
            reqTreeView.getSelectionModel().getSelectedItems().addListener(
                    new ReqTreeItemSelectionListener() );
            
            //Organizing Tree Items
            ReqTreeItem<Request> tiRoot 
                    = new ReqTreeItem("Inventory Withdrawl Requests", 
                        ReqTreeItem.ItemType.NOT_SPECIFIED);
            tiPendPull 
                    = new ReqTreeItem("Pending Pull", 
                        ReqTreeItem.ItemType.PENDING_PULL);
            tiPendAppr 
                    = new ReqTreeItem("Pending Approval", 
                        ReqTreeItem.ItemType.PENDING_APPROVAL);
            tiCompleted 
                    = new ReqTreeItem("Completed", 
                        ReqTreeItem.ItemType.COMPLETED);
           
            //Set a listener on the pending pull and pending approval items
            tiPendPull.addEventHandler(
                    TreeItem.childrenModificationEvent(), new childChangeListener() 
                    );
            
            tiPendAppr.addEventHandler(
                    TreeItem.childrenModificationEvent(), new childChangeListener() 
                    );
            
            /* Get the Lists of Requests from the Data Model, pass them to the 
             * function responsible for making ReqTreeItem instances of them.
             */
            tiPendAppr.getChildren().addAll(
                groupRequestsByCart( DataModel.getInstance().getPendingAppr() ));
            log.debug("Pending approval items: " + tiPendAppr.getChildren().size() );
            tiPendAppr.setExpanded(true);
            
            tiPendPull.getChildren().addAll(
                groupRequestsByCart( DataModel.getInstance().getPendingPull() ));
            tiPendPull.setExpanded(true);
            log.debug("Pending pull items: " + tiPendPull.getChildren().size() );
            
            tiCompleted.getChildren().addAll(
                groupRequestsByCart( DataModel.getInstance().getLastBatch()));
            log.debug("Last batch items: " + tiCompleted.getChildren().size() );
            
            //Add the child items to the root item
            tiRoot.getChildren().addAll(tiPendAppr,tiPendPull,tiCompleted);
            tiRoot.setExpanded(true);
            
            //set the root item in the treeView
            reqTreeView.setRoot(tiRoot);
            
            
            
        }catch(Exception myEx){
            log.error(myEx);
        }
        
    }

    // Set the property that is listened to by the parent control to indicate
    // that the data set has been changed.  Could move this to the data model.
    private void setDataModified(boolean b){
        isDataModified.set(b);
    }
    
    // Function to return a new cart number with the same requestor part of the
    // hash, but an updated date portion of the hash.
    private static Long getFreshCartNum(Long oldCartNum){
        if(oldCartNum == null){return null;}
        
        //Calculate a new half of the cart number for the date.
        //leading 32 bits are 0xffffffff mask to get 0x00000000
        Long dateHash = new Long (new Date().hashCode()) & 0x00000000ffffffffL; 
        //Mask off half the old cart number. Replace it with new date hash code.
        Long newCartNum = (oldCartNum & 0x7fffffff00000000L) | dateHash;

        //debug
        Logger.getLogger("manreq.ReqTreeViewController")
                .trace(String.format("\n oldHash:  %1$016x %1$d \n " +
                    "dateHash: %2$016x %2$d \n newHash:  %3$016x %3$d", 
                    oldCartNum, dateHash,newCartNum) 
                );

        return newCartNum;
    }
    
        //Comparator for use in ordering the requests before groupbing by cart.
    private static class RequestComtor implements Comparator<Request>{

        @Override
        public int compare(Request o1, Request o2) {
            //EQUALITY COMPARISON
            if( o1.getReqIndex() == o2.getReqIndex() ){return 0;}

            //0.DATE SUBMITTED
            if( o1.getDatesub().compareTo(o2.getDatesub()) != 0 ){
                return o1.getDatesub().compareTo(o2.getDatesub());
            }

            //1.CART NUMBER COMPARISON
            if( o1.getCartnum() < o2.getCartnum()){
                return -1;
            }else if(o1.getCartnum() > o2.getCartnum()){
                return 1;
            }

            

            //2.LOCATION COMPARISON
            int locCompare;
            //make sure locations are not null
            if (o1.getInvRecord().getLocation() != null && 
                    o2.getInvRecord().getLocation() != null) {
                //Attached Norminv and Patient exist Check Location.
                locCompare = o1.getInvRecord().getLocation().compareTo(
                            o2.getInvRecord().getLocation() );

                if(locCompare < 0){ return -1;}   //o1 Location is less.
                else if(locCompare > 0){return 1;} //o1 Location is greater
            }

            //3.NAME COMPARISON
            int nameComp;
            if(o1.getInvRecord().getOwner() != null &&
                        o2.getInvRecord().getOwner() != null){
                //Attached Patients are not null.  Compare Names
                nameComp = o1.getInvRecord().getOwner().getLname().compareTo(
                        o2.getInvRecord().getOwner().getLname());

                if(nameComp < 0){return -1;}      //o1 Lname is less
                else if(nameComp > 0){return 1;}    //o1 Lname is greater
                else{
                    //Last names are the same.  onto Fname.
                    nameComp = o1.getInvRecord().getOwner().getFname().compareTo(
                        o2.getInvRecord().getOwner().getFname());

                    if(nameComp < 0){return -1;}  //o1 Fname is less
                    else if(nameComp > 0 ){return 1;}//o1 Fname is greater
                }
            }

            //Finally compare req indexes
            return ( ( o1.getReqIndex() < o2.getReqIndex() ) ?  -1 :  1  );
        }

    }
   
 
////////////////////////// TREE VIEW FUNCTIONS /////////////////////////////////    
    /* Functions and Inner classes to handle the TreeView
     * specifically for Request entities. */
    
    /* Given a list of requests, return a list of MyTreeItems grouped by cart.
     * This method comes from dfernandez from bendingthejavasppon.com 2010-04.
     * It was originally in reference to the op4j package.
    */
    public static List<ReqTreeItem<Request>> groupRequestsByCart(List<Request> lr){
        //List of All Carts
        List<ReqTreeItem<Request>> allCarts = new ArrayList<>();
        
        //Make mapping of Long to list of treeItems
        Map<Long, List<ReqTreeItem<Request>>> requestsByCart = new LinkedHashMap<>();
        
        /* Sort the list of requests.
         * This way the LinkedHashMap and Lists come out sorted. */
        Collections.sort(lr, Collections.reverseOrder(new RequestComtor()));
        
        //For each request in the list of requests passed in:
        for(Request r : lr){
            //Get the list that corresponds to the request cart number
            List requestsForCart = requestsByCart.get(r.getCartnum());
            //If that list doesn't exist yet, create it.
            if(requestsForCart == null){
                requestsForCart = new ArrayList<>();
                requestsByCart.put(r.getCartnum(), requestsForCart);
            }
            
            //Add the request ReqTreeItem<Request> into the list.
            requestsForCart.add(new ReqTreeItem<>(r));
        }
        
        /*At the end of the previous loop, we should have a 
         * map of lists of MyTreeItems, where the key to each list 
         * is the cartnumber.  Now make the set of request tree items that will
         * represent tha carts from the cart numbers and lists of requests.
        */
        for(Long cartNumber : requestsByCart.keySet() ){         
            //Make a new cart for the cartNumber. title.toString(),
            ReqTreeItem<Request> cart = 
                    new ReqTreeItem(
                        ReqTreeItem.ItemType.CART);
            
            //Add the matching list of elements from requestsByCart to this
            cart.getChildren().addAll(requestsByCart.get(cartNumber));
            
            //Sort out the name of the cart.
            cart.setOrgzText( ReqTreeItem.getDefaultOrgText(cart) );
            
            //Add this cart to the list of all carts
            allCarts.add(cart);
        }
        
        return allCarts;
    }
    
    /* Check that the selected cells are all linked to Entry items and that all
     * of those items are in the same cart.
     */
    private boolean checkSelectionHomogeneity(){
        
        List<ReqTreeItem> selected = reqTreeView.getSelectionModel().getSelectedItems();
        
        if(selected.size() > 0){
            //Get parent of the first item to check against the rest
            TreeItem parent = selected.get(0).getParent();
            
            //Check each item for type and parent
            for( ReqTreeItem rtc : selected){
                //Check that the tree cell is linked to an ENTRY.
                if(rtc.orgzType != ReqTreeItem.ItemType.ENTRY){
                    log.debug("Non-ENTRY ItemType in multiple selection.");
                    return false;
                }
                
                //Check that each is from the same cart as the first Item.
                if(rtc.getParent() != parent){
                    log.debug("Different parents in multiple selection.");
                    return false;
                }
            }
            //The selected items are all ENTRYs from the same cart.
            return true;
        }
        
        return false;
    }


    /* Search children of start for a specific cartnumber, findMe.
     * Designed to be used on the Completed, Pending Appr, or Pending Pull nodes.
     * Will return the PARENT of the first item with a matching cart num */
    private ReqTreeItem findParentOfCartNum(ReqTreeItem start, Long findMe){
        Iterator itt = start.getChildren().iterator();
        ReqTreeItem retVal = null;
        
        //Iterate until the end or until you hae something to return.
        while(itt.hasNext() && retVal == null){
            ReqTreeItem rti = (ReqTreeItem) itt.next();
            Request rVal = (Request) rti.getValue();
            
            //if this is it, return
            if( rVal != null &&
                rVal.getCartnum().compareTo(findMe) == 0)
            { 
                retVal = (ReqTreeItem) rti.getParent();
                log.debug("Tree Item match found");
            }else{
                //make a recursive call
                retVal =  findParentOfCartNum(rti, findMe) ;
            }
        }
        
        //Finally return the final over write of the retVal
        return retVal;

    }
    
    private ReqTreeItem findTreeItemWithValue(ReqTreeItem start, Request val){
        Iterator itt = start.getChildren().iterator();
        ReqTreeItem retVal = null;
        //Continue until there is nothing left, or the return value is not null
        while(itt.hasNext() && retVal == null){
            ReqTreeItem rti = (ReqTreeItem) itt.next();
            Request rVal = (Request) rti.getValue();
            
            //if this is it, return
            if(rVal != null && rVal.getReqIndex() == val.getReqIndex())
            { 
                retVal = rti; 
            }else{
                //make a recursive call
                retVal =  findTreeItemWithValue(rti, val) ;
            }
        }
        
        //Finally return the final over write of the retVal
        return retVal;
    }
    
    private ReqTreeItem findTreeItemWithReqIndex( ReqTreeItem start, Long findIndex){
        Iterator itt = start.getChildren().iterator();
        ReqTreeItem retVal = null;
        
        //Iterate until the end or until you hae something to return.
        while(itt.hasNext() && retVal == null){
            ReqTreeItem rti = (ReqTreeItem) itt.next();
            Request rVal = (Request) rti.getValue();
            
            //if this is it, return
            if( rVal != null &&
                rVal.getReqIndex().compareTo(findIndex) == 0)
            { 
                retVal = rti;
                log.debug("Tree Item match found by ReqIndex.");
            }else{
                //make a recursive call
                retVal =  findTreeItemWithReqIndex(rti, findIndex) ;
            }
        }
        
        return retVal;
    }
    
    //Function to go through and remove empty carts from tree view
    //Will not remove the item from which the call initially begins.
    private void removeLeafNonValueItems(ReqTreeItem parent){
        Iterator itt = parent.getChildren().iterator();
        while(itt.hasNext()){
            ReqTreeItem rti = (ReqTreeItem) itt.next();
            //if it is a leaf, has no data value, and is not the root
            if(rti.isLeaf() && 
                    rti.getValue() == null && 
                    rti.getParent() != null )
            {
                //Remove itself from the collection of children using iterator.
                itt.remove();
            }else{
                //Do recursive call
                removeLeafNonValueItems(rti);
            }
        }
    }
    
    //Function to remove items marked for deletion from tree view.
    //Will not remove the parent node that the call initially starts from.
    private void removeMarkedForDeletionItems(ReqTreeItem parent){
        
        //Do recursive call through all child nodes
        Iterator itt = parent.getChildren().iterator();
        while(itt.hasNext()){
            ReqTreeItem rti = (ReqTreeItem) itt.next();
            
            //If the item is a leaf and has been marked for deletion
            if(rti.isLeaf() && rti.getDataHasBeenDeleted()){
                itt.remove();
            }else{
                removeMarkedForDeletionItems( rti );
            }
            
        }
        

    }
    
    /* Go throught the reqTreeView, and reset the value of he property  
     * hasDataBeenModified of each of the ReqTreeItems.  Then force a recalc
     * of all the ReqTreeCells in order to reflect the style changes.
     */
    private void resetAllChildItemsIsModified(ReqTreeItem rti){
        //Go through and remove class .tree-cell.dataChanged from all tree items.
       Iterator itt = rti.getChildren().iterator();
       rti.setDataHasBeenChanged(Boolean.FALSE);
       rti.setDataHasBeenDeleted(Boolean.FALSE);
       //A recursive call... meh.
       while(itt.hasNext()){
           ReqTreeItem r = (ReqTreeItem) itt.next();
           resetAllChildItemsIsModified(r);
       } 
    }

    /* TreeItem for Request types.  In the absence of a Request backing, use
     * a category string for the display.
     */
    public static class ReqTreeItem<T> extends TreeItem<Request>{

        public static enum ItemType {
            ENTRY, CART, PENDING_PULL, 
            PENDING_APPROVAL, COMPLETED, NOT_SPECIFIED
        }
                
        private String orgzText = "";
        private ItemType orgzType;
        private Boolean dataHasBeenChanged = false;
        private Boolean dataHasBeenDeleted = false;
        
        //Blank item and default constructor
        ReqTreeItem(){
            super();
            setValue(null);
        }
        
        //Basic constructor for a tree item with request entity data.
        ReqTreeItem( Request r){
            super();
            if( r != null){
                this.setValue(r);
            }
            this.orgzType = ItemType.ENTRY;
        }
        
        //String constructor for organizing items such as categories and carts.
        ReqTreeItem( String s){
            this(s, ItemType.NOT_SPECIFIED);
        }
        
        //Request tree item type constructor
        ReqTreeItem( ItemType it ){
            super();
            this.orgzType = it;
        }
        
        //String constructor for specifying the type assigned to this TreeItem
        ReqTreeItem( String s, ItemType t ){
            super();
            setValue(null);
            this.orgzText=s;
            if(t == ItemType.ENTRY){
                /* handle the event of someone tryng to set a cart entry 
                   using the string constructor */
                this.orgzType = ItemType.NOT_SPECIFIED;
            }
            this.orgzType = t;
        }
                  
        /* Method for accepting an Entry from another Cart to this Cart. 
         * Return true if modifications where made.
         */
        boolean acceptEntryFromOtherCart(ReqTreeItem donor){
            //Get the parent of the item that will be moved.
            ReqTreeItem dParent = (ReqTreeItem) donor.getParent();
            
            //Check that the donor is an entry
            if(donor.orgzType != ItemType.ENTRY){ 
                Logger.getLogger(ReqTreeViewController.class)
                            .debug(":-: Donor item is not an Entry");
                return false; 
            }
            
            //Check if parent is null and is also a cart.
            if(dParent == null || dParent.orgzType != ItemType.CART){ 
                Logger.getLogger(ReqTreeViewController.class)
                            .debug(":-: Donor Parent is null or is not a Cart");
                return false; 
            }
            
            //Check that this is a cart and not the starting cart. 
            if(this.orgzType != ItemType.CART || this == dParent ){ 
                Logger.getLogger(ReqTreeViewController.class)
                            .debug(":-: Target is not cart or is the same cart as donor parent.");
                return false; 
            }
            
            /* Check that this and the donor parent are both children
             *  of the same Parent. Can only pass Entries between siblings */
            if(this.getParent() != dParent.getParent()){ 
                Logger.getLogger(ReqTreeViewController.class)
                            .debug(":-: This and donor parent are not siblings");
                return false; 
            }
            
            //Check that this is empty, or if not, that requestors match.
            if(this.getChildren().size() > 0){
                //Get the request item from the first child of this cart item
                Request aReq = this.getChildren().get(0).getValue();
                //Get the request item from the donor item
                Request dReq = ((Request) donor.getValue());
                
                //Valid matches are same SB user.
                Boolean userMatch = aReq.getSbuser().equals(dReq.getSbuser());
                //Valid matches are the same Requestor
                Boolean requestorMatch = aReq.getRname().equalsIgnoreCase(dReq.getRname());
                
                //If neither of these match, return.
                if(userMatch == false && requestorMatch == false){
                    Logger.getLogger(ReqTreeViewController.class)
                            .debug(":-: userMatch: " + userMatch +
                            " requestorMatch: " + requestorMatch );
                    return false; 
                }
            }
            
            //ALL CHECKS PASS.
            Logger.getLogger(ReqTreeViewController.class)
                    .debug("Donor tree item will be moved.");
            
            //Remove the donor from the Parent Children List.
            dParent.getChildren().remove(donor);
            
            //Add the donor Item as a child of this Item
            this.getChildren().add(donor);
            
            //get the request that is associated with the donor tree item.
            Request donorReq = (Request) donor.getValue();
            
            //NEED TO REWRITE THE CART TITLE IF THIS IS THE FIRST ENTRY ADDED.
            //If this is the first item added to the Cart, Rewrite the title
            //Also need to assign fresh cart number.
            if(this.getChildren().size() == 1){
                //Backup the request before updating it. Include intent to delete flag.
                DataModel.getInstance().backupRequest( donorReq, donor.getDataHasBeenDeleted() );
                
                //Get fresh cart number
                donorReq.setCartnum(getFreshCartNum(donorReq.getCartnum()));
                
                //setValue to null should trigger a value changed call.
                //The TreeItem should sort out its text based on it's value
                //and children.
                this.setValue(null);
            }
            else if(this.getChildren().size() > 1){
                //this donor is not the first in the cart. Need to update cartnum
                //Get the request item from the first child of this cart item
                Request aReq = this.getChildren().get(0).getValue();
                
                //Backup the request before updating it. Include intent to delete flag.
                DataModel.getInstance().backupRequest( donorReq, donor.dataHasBeenDeleted );
                                
                //Set the cart number of the donor to match.
                ( (Request) donor.getValue() ).setCartnum(aReq.getCartnum());
            }
            
            //Mark the donor as data changed.
            donor.setDataHasBeenChanged(Boolean.TRUE);
            
            //Modifications were made.
            return true;
            
        }
        
        /**
         * @return the orgzText
         */
        public String getOrgzText() {
            return orgzText;
        }

        /**
         * @param orgzText the orgzText to set
         */
        public void setOrgzText(String orgzText) {
            this.orgzText = orgzText;
        }
        
        /* Method to get what the base text of a request tree item should be 
         * depending on it's organizing type. 
        */      
        public static String getDefaultOrgText(ReqTreeItem rti){
            if(rti == null){ return null;}
            switch(rti.orgzType){
                case PENDING_PULL: return "Pending Pull";
                case PENDING_APPROVAL: return "Pending Approval";
                case COMPLETED: return "Completed";
                case CART: //clear tht title
                    StringBuilder title = new StringBuilder();
                    StringBuilder cname = new StringBuilder();

                    //get data from the first Request item in the cart.
                    //Request r = (Request) requestsByCart.get(cartNumber).get(0).getValue();
                    if(rti.getChildren().size() == 0){
                        return "Empty Cart";
                    } 
                    ReqTreeItem c = (ReqTreeItem) rti.getChildren().get(0);
                    
                    //if there are no children, return the same text.
                    //if(c == null){
                    //    return "Empty Cart";
                    //}
                    
                    //Get the requiest item of the child
                    Request r = (Request) c.getValue();                    
                    
                    //Make special name string from requestor name and sb user
                    cname.append(r.getRname())
                         .append(" (").append(r.getSbuser()).append(")");
                    //Make the cart title
                    title.append( String.format("%1$-40.40s",cname.toString() ) );
                    title.append( String.format("%1$-8.8s %2$-4tY-%2$-2tm-%2$-2td",
                            r.getRmdest(),r.getDatesub() ));
                    
                    return title.toString();
                case ENTRY: 
                case NOT_SPECIFIED: 
                default: return rti.orgzText;
            }
        }
        
        /**
         * @return the dataHasBeenChanged
         */
        public Boolean getDataHasBeenChanged() {
            return dataHasBeenChanged;
        }
        
        /**
         * @return the dataHasBeenDeleted
         */
        public Boolean getDataHasBeenDeleted() {
            return dataHasBeenDeleted;
        }
        
        /**
         * @param dataHasBeenDeleted( Boolean dataHasBeenDeleted )
         * Function to set the flag indicating that this item is flagged for 
         * deletion
         */
        public void setDataHasBeenDeleted(Boolean dataHasBeenDeleted){
            this.dataHasBeenDeleted = dataHasBeenDeleted;
        }
        
        /**
         * @param dataHasBeenChanged the dataHasBeenChanged to set
         */
        public void setDataHasBeenChanged(Boolean dataHasBeenChanged) {
            this.dataHasBeenChanged = dataHasBeenChanged;
        }
    }
    
    /* Listner for the pending approval and pending pull organizing nodes that 
     * will recalculate the number of vials in the children of a node when it's
     * children have been changed, and add that text into the description.
     */
    private class childChangeListener implements EventHandler<TreeModificationEvent<Object>>  {
        @Override
        public void handle(TreeModificationEvent<Object> t) {
            log.debug("Child nodes changed.");
            Integer total = 0;
            
            ReqTreeItem source = (ReqTreeItem) t.getSource();
            
            for( ReqTreeItem rti : (List<ReqTreeItem>) source.getChildren() ){
                //Get sum for this cart
                Integer i = sumVialsRequestedOfChildren(rti);
                //Add sum to organizing text
                //rti.orgzText = rti.orgzText + " vials: " + i.toString();
                rti.setOrgzText(
                    ReqTreeItem.getDefaultOrgText(rti) + " vials: " + i 
                    );
                //Add sum to running total
                total = total + i;
            }
            
            //Re-write organizing text with updated vials
            source.setOrgzText(
                    ReqTreeItem.getDefaultOrgText(source) + " vials: " + total 
                    );
            
            //source.orgzText = source.orgzText + " vials: " + total;
            log.debug("New sum of vials in child items is: "+total);
        }
        
    }
    
    //Get the sum of the vial numbers of the children of the item.
    private Integer sumVialsRequestedOfChildren(ReqTreeItem rti){
        if(rti == null){ return null; } //nothing from nothing
        
        Integer i = 0;
        
        //If rti is an Entry, simply return the vial number
        if(rti.orgzType == ReqTreeItem.ItemType.ENTRY){
            Integer n = ((Request) rti.getValue()).getNumrequested();
            if(n == null){
                return 0;
            }else{
                return n;
            }
        }else{
            //Get the sum of all the children recursively
            Iterator itt = rti.getChildren().iterator();
            while(itt.hasNext()){
                i = i + sumVialsRequestedOfChildren( (ReqTreeItem) itt.next() );
            }
        }
       
        return i;
    }
    
    //TreeCell<Request> re-implement a bunch of TextFieldTreeCell 
    //but without the CellUtils abstraction.
    public class ReqTreeCell extends TreeCell<Request>{
       
       //Icon for Request Entry leaf nodes.
       final Image vialIcon = new Image(
               ReqTreeCell.class
               .getResourceAsStream("resources/BloodVial_16x16_b.png")
               );
       
       final Image cartIcon = new Image(
               ReqTreeCell.class
               .getResourceAsStream("resources/Cart_16x16_b.png")
               );
       
       final Image blankIcon = new Image(
               ReqTreeCell.class
               .getResourceAsStream("resources/Blank_16x16.png")
               );
       
        ReqTreeCell(){
            this.setText("Blank");
            this.setItem(null);
        }
        
        ReqTreeCell(Request r){
            this.setItem(r);
        }
        
        //Remove this constructor if it does not end up being used explicitly.
        ReqTreeCell(String s){
            this.setItem(null);
            this.setText(s);
        }
        
        
        /* This method is only kosher so long as it is assure that the only class
        * that extends TreeItem<> and is associated with this classs is
        * ReqTreeItem
        */
        ReqTreeItem getReqTreeItem(){
            return ((ReqTreeItem) this.getTreeItem());
        }
        
        @Override 
        public void cancelEdit(){
            //Do nothing. Don't bother with isEditable
        }
        
        @Override
        public void startEdit(){
            //Do nothing. Don't bother with isEditable
        }
        
        @Override
        public void updateItem(Request item, boolean empty){
            super.updateItem(item, empty);
            //No matter what, get rid of the disclosureIcon
            this.setDisclosureNode(new ImageView(blankIcon));
            
            //Set the text, graphic, and context menu based on the new item.          
            if(empty==true){
                this.setText(null);
                this.setGraphic(null);
                this.setContextMenu(null);
            }else{
                //set the style string (override) to nothing.
                this.setStyle(null);
                
                if(item == null){
                    //Organizing nodes will have a null item.
                    //If the item backing is null, use the organizing text.
                    ReqTreeItem it = this.getReqTreeItem();
                    //Get the type item
                    this.setText(it.getOrgzText());
                    //Check to see if the organizing type is a Cart.
                    if(it.orgzType == ReqTreeItem.ItemType.CART){
                        this.setGraphic(new ImageView(cartIcon));
                        this.setContextMenu(reqCartConMen);
                    }else{
                        this.setContextMenu(reqCategoryConMen);
                    }
                }else{
                    //Request entries will have non-null item.
                    //Derive the label text from the request information
                    this.setText(requestToText(item));
                    //Set the graphic to the request entry image.
                    this.setGraphic( new ImageView(vialIcon));
                    //Set the contextMenu
                    this.setContextMenu(reqEntryConMen);
                    
                    //Set the style if the based on if the data has been modified
                    if(this.getReqTreeItem().getDataHasBeenDeleted() == Boolean.TRUE){
                        //do style for has data been deleted
                        this.setStyle("-fx-font-style: italic; "
                                + "-fx-font-weight: bold; "
                                + "-fx-text-fill: red;");
                    }else if(this.getReqTreeItem().dataHasBeenChanged){
                        this.setStyle("-fx-text-fill: blue;");
                    }else{
                        //default back to the externally CSS defined style.
                        this.setStyle(null);
                    }
                }
            }
        }
        
        //Function to convert request to text spcifically for this UI element.
        private String requestToText(Request rq){
            StringBuilder sb = new StringBuilder();
            
            if(rq.getInvRecord() == null){
                sb.append(String.format("%1$-22.22 p:%2$02d ",
                        "No Inventory Record",
                        rq.getNumrequested()
                     ));
                sb.append(rq.getFpni());
            }else{
                Norminv inv = rq.getInvRecord();
                
                // pull of vials remaining, location
                sb.append(String.format("%1$03d of %2$03d %3$-11.11s ",
                        rq.getNumrequested(),
                        inv.getVialnum(),
                        inv.getLocation()
                     ));
                // last name, mrn, cap color
                sb.append(String.format("%1$-12.12s %2$-9.9s %3$-6.6s ",
                        inv.getOwner().getLname(),
                        inv.getOwner().getMrn(),
                        inv.getCapcolor()
                        ));
                // cellid, date frozen, 
                sb.append(String.format("%1$-20.20s (%2$-4tY-%2$-2tm-%2$-2td) ",
                        inv.getCellid(),
                        inv.getDatefrzn()
                     ));
                // amount, comments
                sb.append(String.format("%1$-7.2g %2$-20s  ",
                        inv.getAmount(),
                        inv.getDatefrzn()
                     ));
            }
            return sb.toString();
        }
      
    }
    
    //Cell factory callback function for use by the TreeView to create the 
    // nodes (cells) that will each display one TreeItem.
    public class ReqTreeCellFactory 
        implements Callback<TreeView<Request>, TreeCell<Request> > {
        
        //Custom DataFormat for Request object in the drag board
        private DataFormat dFmt = new DataFormat("tilpersist/Request");
        
        //Convenience method for checking the associated TreeItem
        private ReqTreeItem.ItemType getOrganizingType(TreeCell<Request> c){
            return ((ReqTreeItem) c.getTreeItem() ).orgzType;
        }
        
        @Override
        public ReqTreeCell call(TreeView<Request> p) {
            final ReqTreeCell c = new ReqTreeCell();
            
            //Define handlers for drag and drop of requests
            c.setOnDragDetected(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent mEvt) {
                    //Return immediately if the cell is empty
                    if(c.isEmpty()){return;}
                    //Only allow dragging of Request Entries
                    if(getOrganizingType(c) != ReqTreeItem.ItemType.ENTRY){
                        return;
                    }
                    
                    //If the selection is not homogeneous, don't start.
                    if(checkSelectionHomogeneity() == false){
                        return;
                    }
                    
                    //Begin drag-and-drop gesture()
                    Dragboard db = c.startDragAndDrop(TransferMode.MOVE);

                    //Create some clipboard content (Request) for the dragboard
                    ClipboardContent ct = new ClipboardContent();
                    ct.put(dFmt, c.getItem());
                    //Set the dragboard content to this backing request.
                    db.setContent(ct);

                    mEvt.consume();
                }
            });
            
            //Determine accept transfer mode.
            c.setOnDragOver(new EventHandler<DragEvent>() {
                @Override
                public void handle(DragEvent dEvt) {
                    /* data is dragged over the target */
                    /* accept it only if it is not dragged from the same node 
                     * and if it the target is a cart. */
                    if(dEvt.getGestureSource() == c){
                        return;
                    }
                    
                    if ( getOrganizingType(c) == ReqTreeItem.ItemType.CART) {
                        dEvt.acceptTransferModes(TransferMode.MOVE);
                    }

                    dEvt.consume();
                }
            });
            
            //Change text color to green for underlying node.
            c.setOnDragEntered(new EventHandler<DragEvent>(){
                @Override
                public void handle(DragEvent dEvt){
                    if (dEvt.getGestureSource() != c) {
                            //event.acceptTransferModes(TransferMode.COPY_OR_MOVE);
                            c.setTextFill(Color.GREEN);
                        } else {

                        }
                    
                    dEvt.consume();
                }
            });
            
            //Force repaint to restore text color
            c.setOnDragExited(new EventHandler<DragEvent>(){
                @Override
                public void handle(DragEvent dEvt){
                    if (dEvt.getGestureSource() != c) {
                            //event.acceptTransferModes(TransferMode.COPY_OR_MOVE);

                            //Force repaint
                            c.requestLayout();                           
                        }
                    dEvt.consume();
                }
            });
            
            //Call function to create new Item as Child of target Cart.
            //The target in this case is the caller c.
            c.setOnDragDropped(new EventHandler<DragEvent>(){
                @Override
                public void handle(DragEvent dEvt){
                    //check that target is not source
                    if(dEvt.getGestureSource() == c){ return; }
                    //check that target is ReqTreeCell cart
                    if(getOrganizingType(c) != ReqTreeItem.ItemType.CART ){
                        return;
                    }
                    //check that the DataFormat  of dragboard is tilpersist/Request
                    if(dEvt.getDragboard().hasContent(dFmt)){
                        //Get the ReqTreeItem associated with this Cell
                        ReqTreeItem mti = (ReqTreeItem) c.getTreeItem() ;
                        
                        //make a snapshot of the selected items. The selected items list will
                        //change with each move.  The snapshot should preserve the list of items
                        //that need to be moved.
                        List<ReqTreeItem> lri = reqTreeView.getSelectionModel().getSelectedItems();
                        List<ReqTreeItem> snapshot = new LinkedList<>();
                        for(ReqTreeItem rti : lri){
                            snapshot.add(rti);
                        }
                        
                        Boolean didAnyWork = false;
                        Boolean didItWork;
                        for( ReqTreeItem di : snapshot){
                            //Keep track of any true result from acceptEntry function
                            didItWork = mti.acceptEntryFromOtherCart(di);
                            didAnyWork = didAnyWork || didItWork;
                        }
                        //If any of the moves worked, set data modified flag to true
                        if(didAnyWork == true){
                            isDataModified.set(true);
                        }
                    }
                    
                    dEvt.consume();
                }
            });
            
            
            return c;
        } 
    }
    
    
    //Listener class to react to selection changes.
    private class ReqTreeItemSelectionListener implements ListChangeListener<ReqTreeItem> {

        @Override
        public void onChanged(ListChangeListener.Change<? extends ReqTreeItem> change) {
            //Get the selected items
            List<ReqTreeItem> si = reqTreeView.getSelectionModel().getSelectedItems();
            //return immediately if none are selected.
            if(si == null || si.size()<1){return;}

            //Construct the text to add to the infoTextArea.
            StringBuilder sb = new StringBuilder();
            for(ReqTreeItem r : si){
                //for whatever reason the selected items can include null
                if(r == null){
                    sb.append("No Selection");
                }
                else if(r.getValue() == null){
                    sb.append("Cart: ").append(r.getOrgzText());

                }else{
                    sb.append("Entry CartNum: ");
                    sb.append( ((Request) r.getValue()).getCartnum() );
                }
                sb.append('\n');
            }
            //Set info text for the user
            infoTextOut.setText(sb.toString());
        }

    }
   
//////////////////////////////////////////////////////////////////////////////// 
    
    
    /* Listener to track if the panel is currently showing, and record that 
     * state in a place that the main controller can observe.  The main panel 
     * will make decisions about which controller to call based on which panel
     * is showing.
     */
    private class SceneMembershipListener implements ChangeListener<Scene>{

        @Override
        public void changed(ObservableValue<? extends Scene> ov, 
            Scene oldVal, Scene newVal) 
        {
            log.debug("Request panel scene membership value changed.");
            
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
    
    
    /* Listener to track the state of the boolean property of the data model
     * that is true when the data model is executing a request query/modification
     */
    private class DataModelBusyListener implements ChangeListener<Boolean>{

        @Override
        public void changed(ObservableValue<? extends Boolean> obs, 
            Boolean oldVal, Boolean newVal) {
            if(oldVal == Boolean.FALSE && newVal == Boolean.TRUE){
                //The DataWorker is now busy.
                log.debug("DataModel Requests is busy.");
                //Overlay with grey and block mouse events.
                busyOverlay.setVisible(true);
                busyOverlay.setMouseTransparent(false);
                
            }else if(oldVal == Boolean.TRUE && newVal == Boolean.FALSE){
                log.debug("DataModel Requests is no longer busy.");
                //The Data Model Worker has returned.
                if(DataModel.getInstance().didRequestModSucceed()){
                    log.debug("The last database operation succeeded.");
                    //Everything worked. Clear out any empty carts.
                    removeLeafNonValueItems((ReqTreeItem) reqTreeView.getRoot());
                    
                    //Change property informing main controller about data state.
                    setDataModified(false);

                    //remove all the nodes that were marked for deletion
                    removeMarkedForDeletionItems(
                            (ReqTreeItem) reqTreeView.getRoot()
                            );
                    
                    //reset all the nodes to not modified flag.
                    resetAllChildItemsIsModified(
                        (ReqTreeItem) reqTreeView.getRoot()
                        );
                    
                    //force rerender of tree view. Kluge.
                    reqTreeView.getRoot().setExpanded(false);
                    reqTreeView.getRoot().setExpanded(true);
                    
                    //Remove overlay and permit mouse events through
                    busyOverlay.setVisible(false);
                    busyOverlay.setMouseTransparent(true);

                    
                    
                    
                }else{
                    log.debug("The last database operation failed.");
                    //Transaction failed
                    //UI is not in sync with the database.
                    //Show the refresh options Box.
                    refreshOptionsBox.setVisible(true);
                }     
             }
        }
        
    }
    
    ////////////////////////////////////////////////////////////////////////////////


    //Handle printing function.  Given a list of Requests, Do ALL the printing setup
    //and display required.

    private void printRequests(List<Request> reqs){
        //Get the printer dimensions
        DocFlavor docFlavor = DocFlavor.INPUT_STREAM.PNG;

        PrintRequestAttributeSet attributes = new HashPrintRequestAttributeSet();
        attributes.add(new Copies(1));

        PrintService printServices[] = PrintServiceLookup.lookupPrintServices(
                docFlavor, attributes);
        
        if (printServices.length == 0) {
            log.error("No java PrinterService found.");
            return;
        }
        
        PrintServiceAttributeSet psa = printServices[0].getAttributes();
        if(psa.isEmpty()){
            return;
        }
        
        
        
        // Show PrintDialog
        boolean confirm = PrinterJob.getPrinterJob().printDialog(attributes);
        
        //Cancelled printing.
        if(confirm == false){ return; }
        
        //AnchorPane that is the printing template
        AnchorPane template;
        //Load the FXML anchor pane template
        try{
            log.debug("Loading the Request print template.");
            
            //Load the FXML node heirarchy for the RequestsTreeView
            FXMLLoader printTemplateLoader = 
                    new FXMLLoader(this.getClass().getResource("ReqPrintingNode.fxml"));
            template = (AnchorPane) printTemplateLoader.load();
        }catch(Exception myEx){
            log.error("Request print template failed to load.");
            log.error(myEx);
            //Template did not load.  Depart from this function post haste.
            return;
        }
        
        
        
        //The attributes should be updated at this point
        attributes.size();
        
        PageFormat pf = PrinterJob.getPrinterJob().getPageFormat(attributes);
        
        pf.getImageableHeight();
        pf.getHeight();
        pf.getImageableWidth();
        pf.getWidth();
        
        
        //Figure out how many of our printing nodes are going to fit per page.
        Double numNodesInWidth;
        Double numNodesInHeight;
        
        Long numXnodes;
        Long numYnodes;
        
        //Do calculations for how many nodes are going to fit.
        numNodesInWidth = pf.getImageableWidth() / template.getPrefWidth();
        numXnodes = Math.round( Math.floor(numNodesInWidth) );

        numNodesInHeight = pf.getImageableHeight() / template.getPrefHeight();
        numYnodes = Math.round( Math.floor(numNodesInHeight) );
        
        if(pf.getOrientation() == PageFormat.LANDSCAPE){
            //Reverse number of nodes for X and Y
            Long temp = numXnodes;
            numXnodes = numYnodes;
            numYnodes = temp;
        }
        
        //Do calculations as to much how much padding needs to be put in.
        Long paddingX;
        Long paddingY;
        
    
        
        template.getPrefHeight();
        template.getPrefWidth();
        
        //Build me a node for printing.
        //start with an anchor pane.
        AnchorPane ap = new AnchorPane();
        //ap.setPrefWidth(pf.getImageableWidth());
        //ap.set
    }

}


