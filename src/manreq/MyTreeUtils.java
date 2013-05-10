/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.event.EventHandler;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.DataFormat;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.paint.Color;
import javafx.util.Callback;
import tilpersist.Norminv;
import tilpersist.Request;

/**
 *
 * @author grossco
 */
public class MyTreeUtils {
    
        /* Given a list of requests, return a list of MyTreeItems grouped by cart.
     * This method comes from dfernandez from bendingthejavasppon.com 2010-04.
     * It was originally in reference to the op4j package.
    */
    private List<MyTreeItem<Request>> groupRequestsByCart(List<Request> lr){
        //List of All Carts
        List<MyTreeItem<Request>> allCarts = new ArrayList<>();
        
        //Make mapping of Long to list of treeItems
        Map<Long, List<MyTreeItem<Request>>> requestsByCart = new LinkedHashMap<>();
        
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
            
            //Add the request MyTreeItem<Request> into the list.
            requestsForCart.add(new MyTreeItem<>(r));
        }
        
        /*At the end of the previous loop, we should have a 
         * map of lists of MyTreeItems, where the key to each list 
         * is the cartnumber.
        */
        for(Long cartNumber : requestsByCart.keySet() ){
            //clear tht title
            StringBuilder title = new StringBuilder();
            StringBuilder cname = new StringBuilder();
            
            //get data from the first Request item in the cart.
            Request r = (Request) requestsByCart.get(cartNumber).get(0).getValue();
            //Make special name string from requestor name and sb user
            cname.append(r.getRname())
                 .append(" (").append(r.getSbuser()).append(")");
            //Make the cart title
            title.append( String.format("%1$-40.40s",cname.toString() ) );
            title.append( String.format("%1$-8.8s %2$-4tY-%2$-2tm-%2$-2td",
                    r.getRmdest(),r.getDatesub() ));

            //Make a new cart for the cartNumber.
            MyTreeItem<Request> cart = 
                    new MyTreeItem(title.toString(),
                            MyTreeItem.ItemType.CART);
            //Add the matching list of elements from requestsByCart to this
            cart.getChildren().addAll(requestsByCart.get(cartNumber));
            //Add this cart to the list of all carts
            allCarts.add(cart);
        }
        
        return allCarts;
    }
    

    /* TreeItem for Request types.  In the absence of a Request backing, use
     * a category string for the display.
     */
    private static class MyTreeItem<T> extends TreeItem<Request>{

        public static enum ItemType {
            ENTRY, CART, PENDING_PULL, 
            PENDING_APPROVAL, COMPLETED, NOT_SPECIFIED
        }
                
        private String orgzText = "";
        private MyTreeItem.ItemType orgzType;
        
        //Blank item and default constructor
        MyTreeItem(){
            super();
            setValue(null);
        }
        
        //Basic constructor for a tree item with request entity data.
        MyTreeItem( Request r){
            super();
            if( r != null){
                this.setValue(r);
            }
            this.orgzType = MyTreeItem.ItemType.ENTRY;
        }
        
        //String constructor for organizing items such as categories and carts.
        MyTreeItem( String s){
            this(s, MyTreeItem.ItemType.NOT_SPECIFIED);
        }
        
        //String constructor for specifying the type assigned to this TreeItem
        MyTreeItem( String s, MyTreeItem.ItemType t ){
            super();
            setValue(null);
            this.orgzText=s;
            if(t == MyTreeItem.ItemType.ENTRY){
                /* handle the event of someone tryng to set a cart entry 
                   using the string constructor */
                this.orgzType = MyTreeItem.ItemType.NOT_SPECIFIED;
            }
            this.orgzType = t;
        }
                  
        //Method for accepting an Entry from another Cart to this Cart.
        void acceptEntryFromOtherCart(MyTreeItem donor){
            //Get the parent of the item that will be moved.
            MyTreeItem dParent = (MyTreeItem) donor.getParent();
            
            //Check that the donor is an entry
            if(donor.orgzType != MyTreeItem.ItemType.ENTRY){ 
                System.out.println(":-: Donor item is not an Entry");
                return; 
            }
            
            //Check if parent is null and is also a cart.
            if(dParent == null || dParent.orgzType != MyTreeItem.ItemType.CART){ 
                System.out.println(":-: Donor Parent is null or is not a Cart");
                return; 
            }
            
            //Check that this is a cart and not the starting cart.
            if(this.orgzType != MyTreeItem.ItemType.CART || this == dParent){ 
                System.out.println(":-: Target is not cart or is the same cart as donor parent.");
                return; 
            }
            
            /* Check that this and the donor parent are both children
             *  of the same Parent. Can only pass Entries between siblings */
            if(this.getParent() != dParent.getParent()){ 
                System.out.println(":-: This and donor parent are not siblings");
                return; 
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
                    System.out.println(":-: userMatch: " + userMatch +
                            " requestorMatch: " + requestorMatch );
                    return; 
                }
            }
            
            //ALL CHECKS PASS.
            
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
            
            int i;
            //dParent.getChildren().
            
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
        
        
    }
    
    //TreeCell<Request> re-implement a bunch of TextFieldTreeCell 
    //but without the CellUtils abstracted away.
    private static class MyTreeCell extends TreeCell<Request>{
       
       //Icon for Request Entry leaf nodes.
       static final Image vialIcon = new Image(
               MyTreeCell.class
               .getResourceAsStream("resources/BloodVial_16x16_b.png")
               );
       
       static final Image cartIcon = new Image(
               MyTreeCell.class
               .getResourceAsStream("resources/Cart_16x16_b.png")
               );
       
       static final Image blankIcon = new Image(
               MyTreeCell.class
               .getResourceAsStream("resources/Blank_16x16.png")
               );

       /* This method is only kosher so long as it is assure that the only class
        * that extends TreeItem<> and is associated with this classs is
        * MyTreeItem
        */
        MyTreeItem getMyTreeItem(){
            return ((MyTreeItem) this.getTreeItem());
        }
        
        MyTreeCell(){
            this.getStyleClass().add("my-tree-cell");
            this.setText("Blank");
            this.setItem(null);

        }
        
        MyTreeCell(Request r){
            this.getStyleClass().add("my-tree-cell");
            this.setItem(r);
        }
        
        //Remove this constructor if it does not end up being used explicitly.
        MyTreeCell(String s){
            this.getStyleClass().add("my-tree-cell-organizing");
            this.setItem(null);
            this.setText(s);
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
            
            //Empty cells have no text nor graphic            
            if(empty==true){
                this.setText(null);
                this.setGraphic(null);
            }else{
                if(item != null){
                    //Derive the label text from the request information
                    this.setText(requestToText(item));
                    //Set the graphic to the request entry image.
                    this.setGraphic( new ImageView(vialIcon));
                    
                }else{
                    //If the item backing is null, use the organizing text.
                    MyTreeItem it = (MyTreeItem) this.getTreeItem();
                    //Get the type item
                    this.setText(it.getOrgzText());
                    //Check to see if the organizing type is a Cart.
                    if(it.orgzType == MyTreeItem.ItemType.CART){
                        this.setGraphic(new ImageView(cartIcon));
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
    private class MyTreeCellFactory 
    implements Callback<TreeView<Request>, TreeCell<Request> > {
        
        //Custom DataFormat for Request object in the drag board
        private DataFormat dFmt = new DataFormat("tilpersist/Request");
        
        //Convenience method for checking the associated TreeItem
        private MyTreeItem.ItemType getOrganizingType(TreeCell<Request> c){
            return ((MyTreeItem) c.getTreeItem() ).orgzType;
        }
        
        @Override
        public MyTreeCell call(TreeView<Request> p) {
            final MyTreeCell c = new MyTreeCell();
            
            //Define handlers for drag and drop of requests
            c.setOnDragDetected(new EventHandler<MouseEvent>() {
                @Override
                public void handle(MouseEvent mEvt) {
                    //Only allow dragging of Request Entries
                    if(getOrganizingType(c) != MyTreeItem.ItemType.ENTRY){
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
                    
                    if ( getOrganizingType(c) == MyTreeItem.ItemType.CART) {
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
                    //check that target is MyTreeCell cart
                    if(getOrganizingType(c) != MyTreeItem.ItemType.CART ){
                        return;
                    }
                    //check that the DataFormat  of dragboard is tilpersist/Request
                    if(dEvt.getDragboard().hasContent(dFmt)){
                        //Get the MyTreeItem associated with this Cell
                        MyTreeItem mti = (MyTreeItem) c.getTreeItem() ;
                        //Get the MyTreeitem associated with the source of the drag
                        MyTreeItem di = ((MyTreeCell) dEvt.getGestureSource() ).getMyTreeItem();
                        //Do transfer from donor tree item to this one.
                        mti.acceptEntryFromOtherCart(di);
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
                    if(getOrganizingType(c) != MyTreeItem.ItemType.ENTRY ){
                        return;
                    }
                    //check that the DataFormat  of dragboard is tilpersist/Request
                    if(dEvt.getDragboard().hasContent(dFmt)){
                        //Do the 
                    }
                    dEvt.consume();
                }
            });
            
            return c;
        } 
    }
    
    //Comparator for use in ordering the requests before groupbing by cart.
    private class RequestComtor implements Comparator<Request>{

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
   
}
