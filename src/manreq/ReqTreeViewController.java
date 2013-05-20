/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
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
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.WindowEvent;
import javafx.util.Callback;
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
    
    @FXML
    TreeView reqTreeView;
    
    ReqTreeItem<Request> tiPendAppr;
    ReqTreeItem<Request> tiCompleted;
    ReqTreeItem<Request> tiPendPull;
    
    @FXML
    AnchorPane requestsPane;
     
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
        //Calculate a new half of the cart number for the date.
        //leading 32 bits are 0xffffffff mask to get 0x00000000
        Long dateHash = new Long (new Date().hashCode()) & 0x00000000ffffffffL; 
        //Mask off half the old cart number. Replace it with new date hash code.
        Long newCartNum = (oldCartNum & 0x7fffffff00000000L) | dateHash;

        //debug
        Long s1 = oldCartNum & 0x7fffffff00000000L;
        log.debug("oldHash: "+String.format("%1$016x %1$d", oldCartNum));
        log.debug("masked : "+String.format("%1$016x %1$d", s1));
        log.debug("dateHash:"+String.format("%1$016x %1$d", dateHash));
        log.debug("newHash: "+String.format("%1$016x %1$d", newCartNum));

        //Create new cart item with the same text as the original cart parentCart.orgzText
        ReqTreeItem dupCart = 
                new ReqTreeItem(parentCart.orgzText, ReqTreeItem.ItemType.CART);
        
        //index of parent cart in category
        int j = parentCart.getParent().getChildren().indexOf(parentCart);
        //add the duplicated cart to the children of the parent of the parentCart
        parentCart.getParent().getChildren().add(j, dupCart);
         
        /*Make a backup of the first Request item before it is isDataModified.
         * This is the only one that has to be isDataModified here since it is being 
         * moved into an empty cart.  The other entries will be altered to match 
         *  the entry when they are added. The acceptEntryFromOtherCart will 
         * handle the backups and modifications of the remainder. 
        */
        DataModel.getInstance().backupRequest(
                ((Request) leafsToBeMoved.get(0).getValue() )
                );

        //Modify the cart number on the first item.
        ((Request) leafsToBeMoved.get(0).getValue() ).setCartnum(newCartNum);

        //Submit the request as isDataModified
        DataModel.getInstance()
                .submitAlteredRequest((Request) leafsToBeMoved.get(0).getValue());
        
        //Set the controller's isDataModified property to true
        this.isDataModified.set(true);
        
        //Move each item in the selection using acceptEntryFromOtherCart method.
        for(int i=0; i < leafsToBeMoved.size(); i++){
            dupCart.acceptEntryFromOtherCart(leafsToBeMoved.get(i));
        }
        
        //After Everything is moved, expand the duplicate cart
        dupCart.setExpanded(true);

    }
        
    @FXML
    public void entryRemove(ActionEvent aEvt){
        log.debug("Entry: remove. action handler");
    }
    
    @FXML
    public void categorySummary(ActionEvent aEvt){
        log.debug("Category: summary. action handler");
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
        
    }    
    
    /* Function to populate the tree view with information from Data Model.*/
    private void popRequestTreeView(){
        try{          
            //Set the TreeCell Factory callback for the tree view
            reqTreeView.setCellFactory( new ReqTreeCellFactory() );
            //Set the Selecion mode for the tree view
            reqTreeView.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            
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

    @Override
    public void setInfoTextArea(TextArea ta) {
        if(ta == null){
            ta = new TextArea();
            ta.setVisible(false);
        }else{
            infoTextOut = ta;
        }
        
    }
    
    public void setDataModified(boolean b){
        isDataModified.set(b);
    }
    
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
            log.debug("Selected List has more than zero items.");
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

        confirmStage.showAndWait();
        
        //get answer from the confirmController
        if(cpc.getWasConfirmed() == Boolean.TRUE){
                //Set the data isDataModified to false
            
            //Set tree view to disabled
            reqTreeView.setDisable(true);
            log.debug("setDisabled true");
            reqTreeView.setStyle("-fx-background-color: gray;");
            reqTreeView.requestLayout();

            //This will return immediately.
            DataModel.getInstance().commitRequestChanges();
            
            
            //TODO: Move all post transaction work to listeners
            
            //if(commitWored){ alter the UI
            //if(commitFailed) notify user, alter the UI call cancel operation
            this.isDataModified.set(false);
         
            //reset all the nodes to not modified flag.
            resetAllChildItemsIsModified(
                (ReqTreeItem) reqTreeView.getRoot()
                );
            //force rerender. Kluge.
            reqTreeView.getRoot().setExpanded(false);
            reqTreeView.getRoot().setExpanded(true);
            
            //set tree view to enabled
            reqTreeView.setDisable(false);
            log.debug("setDisabled false");
        }


        
    }
    
    @Override
    public void cancelEdits(){
        //Set the data isDataModified to false
        this.isDataModified.set(false);
        
        //Remove carts that have no nodes and no value
        removeLeafNonValueItems((ReqTreeItem) reqTreeView.getRoot());
        
        //Undo changes:
        /* Find the destination of the item.  
         * Make the UI match the destination.
         * Restore the old data.
         */
        
        //Get map of original values, modded (current) values
        Map modMap = DataModel.getInstance().getReadOnlyRequestChanges();
        //For each original request
        
        
        for(Object orig : modMap.keySet()){
            //cast to correct type
            Request origReq = (Request) orig;
            
            //get the corresponding, current request tree item
            ReqTreeItem source = 
                    findTreeItemWithValue(
                        (ReqTreeItem) reqTreeView.getRoot(), 
                        (Request) modMap.get(orig)
                    );
            
            //Destination that the request will be re-inserted into.
            ReqTreeItem destParent;
            
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
                
            }else{
                //Simply move the entry to the destination
                //remove itself from the parent
                source.getParent().getChildren().remove(source);
                //add itself to the destination
                destParent.getChildren().add(source);
                //change the value to the original request object
                source.setValue(orig);
            }
            
        }
        
        //reset all the nodes to not modified flag.
        resetAllChildItemsIsModified(
            (ReqTreeItem) reqTreeView.getRoot()
            );
        //force rerender. Kluge.
        reqTreeView.getRoot().setExpanded(false);
        reqTreeView.getRoot().setExpanded(true);
    }
    
    //Search children of start.
    //Designed to be used on the Completed, Pending Appr, or Pending Pull nodes.
    //Will return the PARENT of the first item with a matching cart num
    private ReqTreeItem findParentOfCartNum(ReqTreeItem start, Long findMe){
        Iterator itt = start.getChildren().iterator();
        ReqTreeItem retVal = null;
        
        //Iterate until the end or until you hae something to return.
        while(itt.hasNext() && retVal == null){
            ReqTreeItem rti = (ReqTreeItem) itt.next();
            //if this is it, return
            if( rti.getValue() != null &&
                ((Request) rti.getValue()).getCartnum() == findMe)
            { 
                retVal = (ReqTreeItem) rti.getParent(); 
            }else{
                //make a recursive call
                retVal =  findParentOfCartNum(rti, findMe) ;
            }
        }
        
        //Finally return the final over write of the retVal
        if(retVal == null){log.debug("Ret null");}
        else{ log.debug("Ret Something");}
        
        return retVal;

    }
    
    private ReqTreeItem findTreeItemWithValue(ReqTreeItem start, Request val){
        Iterator itt = start.getChildren().iterator();
        ReqTreeItem retVal = null;
        //Continue until there is nothing left, or the return value is not null
        while(itt.hasNext() && retVal == null){
            ReqTreeItem rti = (ReqTreeItem) itt.next();
            //if this is it, return
            if(rti.getValue() == val)
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
    
    private void removeLeafNonValueItems(ReqTreeItem start){
        Iterator itt = start.getChildren().iterator();
        while(itt.hasNext()){
            ReqTreeItem rti = (ReqTreeItem) itt.next();
            //if it is a leaf, has no data value, and is not the root
            if(rti.isLeaf() && 
                    rti.getValue() == null && 
                    rti.getParent() != null )
            {
                //Remove itself from the parent.
                rti.getParent().getChildren().remove(rti);
            }
        }
    }
    
    /* Go throught the reqTreeView, and reset the value of he property  
     * hasDataBeenModified of each of the ReqTreeItems.  The force a recalc
     * of all the ReqTreeCells in order to reflect the style changes.
     */
    private void resetAllChildItemsIsModified(ReqTreeItem rti){
        //Go through and remove class .tree-cell.dataChanged from all tree items.
       Iterator itt = rti.getChildren().iterator();
       rti.setDataHasBeenChanged(Boolean.FALSE);
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
            
            //NEED TO REWRITE THE CART TITLE IF THIS IS THE FIRST ENTRY ADDED.
            //If this is the first item added to the Cart, Rewrite the title
            if(this.getChildren().size() == 1){
                //setValue to null should trigger a value changed call.
                this.setValue(null);
            }
            
            //If the cart was not empty, modify the donor to match the exising
            //cart number.
            if(this.getChildren().size() > 1){
                //this donor is not the first in the cart
                //Get the request item from the first child of this cart item
                Request aReq = this.getChildren().get(0).getValue();
                
                //Backup the request before updating it.
                DataModel.getInstance()
                        .backupRequest( (Request) donor.getValue() );
                
                //Log the old and new cart values.
                Logger.getLogger(ReqTreeViewController.class)
                    .debug("Cart value update \nold: "
                        + ((Request) donor.getValue()).getCartnum() 
                        + "\nnew: " + aReq.getCartnum());
                
                //Set the cart number of the donor to match.
                ( (Request) donor.getValue() ).setCartnum(aReq.getCartnum());
                
                //Submit the Request entity to the list of modifications
                DataModel.getInstance()
                        .submitAlteredRequest( (Request) donor.getValue() );
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
                    //this.getStyleClass().add("tree-cell.dataChanged");
                    
                    //this.getStyleClass().add("tree-cell.dataChanged");
                    //this.getStyleClass().add(0, "tree-cell.dataChanged");
                    if(this.getReqTreeItem().dataHasBeenChanged){
                        this.setStyle("-fx-text-fill: red;");
                    }else{
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
                        //Get the MyTreeitem associated with the source of the drag
                        ReqTreeItem di = ((ReqTreeCell) dEvt.getGestureSource() ).getReqTreeItem();
                        //Do transfer from donor tree item to this one.
                        if( mti.acceptEntryFromOtherCart(di) == true){
                            //accept entry succeeded. 
                            //Set controller's isDataModified propert
                            isDataModified.set(true);
                        }
                        
                        //Set TreeView selection to none
                        c.getTreeView().getSelectionModel().clearSelection();
                    }
                    
                    dEvt.consume();
                }
            });
            
            //Remove item if drag and drop was successful
            c.setOnDragDone(new EventHandler<DragEvent>(){
                @Override
                public void handle(DragEvent dEvt){
                    
                    //check that caller c is the event source
                    if(dEvt.getGestureSource() != c){ return; }
                    //check that source is an Entry
                    if(getOrganizingType(c) != ReqTreeItem.ItemType.ENTRY ){
                        return;
                    }
                    //check that the DataFormat  of dragboard is tilpersist/Request
                    if(dEvt.getDragboard().hasContent(dFmt)){
                        //Do
                    }
                    dEvt.consume();
                }
            });
            
            return c;
        } 
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
    
    private class DataModelBusyListener implements ChangeListener<Boolean>{

        @Override
        public void changed(ObservableValue<? extends Boolean> obs, 
            Boolean oldVal, Boolean newVal) {
            if(oldVal == Boolean.FALSE && newVal == Boolean.TRUE){
                //The DataWorker is now busy.
                //TODO: Add Gray Overlay
            }else if(oldVal == Boolean.TRUE && newVal == Boolean.FALSE){
                //The Data Model Worker has returned.
                if(DataModel.getInstance().didRequestModSucceed()){
                    //Everything worked
                    //UI should be up
                }else{
                    //Transaction failed
                }
               
             }
            //To change body of generated methods, choose Tools | Templates.
            throw new UnsupportedOperationException("Not supported yet.");
        }
        
    }
}
