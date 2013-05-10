/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javafx.beans.binding.BooleanExpression;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyStringProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.concurrent.Task;
import javafx.concurrent.Worker;
import javafx.concurrent.WorkerStateEvent;
import javafx.event.EventHandler;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.LockModeType;
import javax.persistence.Persistence;
import javax.persistence.Query;
import javax.persistence.TemporalType;
import javax.persistence.TypedQuery;
import org.jboss.logging.Logger;
import tilpersist.Ingress;
import tilpersist.Request;

/** The data model for interacting with the database and providing data to the
 * controller.  This basically has three arms: Requests, Ingress, and Mods.  
 * Each arm can be independently "busy"  Meaning that there is a thread running 
 * that is doing work with the database.
 *
 * @author grossco
 * 
 * 
 */
public class DataModel {
    //Make this a singleton class for the project.
    private final static DataModel dm = new DataModel();
    
    //Entity manager for application.
    private EntityManagerFactory emfactory;
    //class logger for the application
    private Logger log;
    
    //Boolean to track if the DataModel is currently waiting on a worker thread
    //This essentially serves as a global mutex.  In short, don't start doing 
    //anything while this is true.
    private BooleanProperty isBusyRequests;
    private BooleanProperty isBusyIngress;
    
    private Worker.State reqOpReturnStatus;
    
    /*Class variables specific to this program's operation */
    //List of requests pending approval
    private List<Request> pendingAppr;
    //List of requests pending pull
    private List<Request> pendingPull;
    //List of requests that have been recently pull;
    private List<Request> recentPull;
    //List of previous 10 completed requests.
    private List<Request> lastEn;
    //List of previous requests since some date (default: 30 days ago)
    private List<Request> lastBatch;
    //List of ingress awaiting merge
    private List<Ingress> pendingIng;
    
    //List of Requests backups.
    private Map<Long,Request> backedReqs;
    //List of Requests that have been modified
    private Map<Long,Request> moddedReqs;
    //List of Requests that you would like to delete
    private List<Long> deleteMeReqs;
    
    //Constructor
    private DataModel(){
        log = Logger.getLogger(this.getClass());
        emfactory = null;
        //initialize the backup list
        backedReqs = new LinkedHashMap<>();
        //initialize the modified list
        moddedReqs = new LinkedHashMap<>();
        //Set is Busy property of data model to false.
        isBusyRequests = new SimpleBooleanProperty(false);
        isBusyIngress = new SimpleBooleanProperty(false);
    }
    
    //Setup EntityManagerFactory
    public void initEntityManagerParameters(Map<String,String> params){
        emfactory = Persistence.createEntityManagerFactory("tillabPU",params);
    }
    
    //Check that EMF is fine
    public boolean emfactoryIsOkay(){
        if( emfactory != null && emfactory.isOpen() ){
            return true;
        }else{
            return false;
        }    
    }
    
    //the data model needs to have been initialized first
    public static DataModel getInstance(){
            return dm;
    }
    
    //Expose the entity manager factory in order to obtain enity managers.
    //Could instead make the EMF private and make a method for getting entity managers.
    private EntityManagerFactory getEMF(){
        return emfactory;
    }
       
    /* Backup a Request entity to map requests prior to making a change.
     * Use this list prior to making changes to the database to check if the
     * database has been changed from what this client considers the original.
    */
    public void backupRequest(Request req){
        //Don't do anything with null input
        if(req == null){ return;}
        
        //Check that the Request is NOT already in the Map
        if( backedReqs.containsKey(req.getReqIndex()) == false ){
            backedReqs.put(req.getReqIndex(), req);
        }
    }
    
    /* For an entry to be modified in the database it needs to be submitted to 
     * the data model.  This will be checked against the backup Request list 
     * prior to changes being made.
    */
    public void submitAlteredRequest(Request req){
        //Don't do anything with null input
        if(req == null){ return;}
        
        //If present, replace the pre-existing entry
        moddedReqs.put(req.getReqIndex(), req);
    }
    
    // Query methods
    public List<Request> doNamed_Request(String queryName){
        //The request list to return.  Default to null.
        List<Request> rl = null;
        
        //Obtain an Entity Manager from the Factory
        EntityManager em = emfactory.createEntityManager();
            
        try{
            //Start a transaction
            em.getTransaction().begin();
            log.debug(queryName+" transaction started. ");

            /*This query should be a named query */
            TypedQuery<Request> tr = em.createNamedQuery(queryName, Request.class);
            //entity manager is closed in execQuery function.
            rl = (List<Request>) execQuery(tr,em);    
        }
        catch(Exception myEx){
            log.error(queryName+" transaction error.");
            log.error(myEx);
        }
        log.debug(queryName+" transaction complete.");
        return rl;
    } 
    
    public Boolean queryPendingApproval(){
        //Do initial queries for request management
        //Get requests pending approval
        pendingAppr = doNamed_Request("Request.findJoinUnapproved");
        //if the result is null, the query failed.
        if(pendingAppr == null){
            return Boolean.FALSE;
        }else{
            return Boolean.TRUE;
        }
    }
    
    public Boolean queryPendingPull(){
        //Get requests pending pull
        pendingPull = doNamed_Request("Request.findJoinUnpulled");
        //if the result is null, the query failed.
        if(pendingPull == null){
            return Boolean.FALSE;
        }else{
            return Boolean.TRUE;
        }
    }
  
    public Boolean queryLastNCompleted(Integer n){
        Boolean retVal = Boolean.FALSE;
        //fail fast if the data model is busy.
        if(isBusyRequests.get() == true){ return retVal; }
        
        lastEn = null;
        //Obtain an Entity Manager from the Factory
        EntityManager em = emfactory.createEntityManager();
        
        try{
            //Start a transaction
            em.getTransaction().begin();
            log.debug("Recently_Completed requests transaction started. ");
            TypedQuery<Request> tq = em.createNamedQuery("Request.getCompleted", Request.class);
            tq.setMaxResults(n);

            lastEn = (List<Request>) execQuery(tq,em);
            //Query executed and value set.  set return value to true
            retVal = Boolean.TRUE;
        }
        catch(Exception myEx){
            log.error("Recently_Completed requests transaction problem.");
            log.error(myEx);
        }
        
        log.debug("Recently_Completed requests transaction complete.");
        return retVal;
    }
    
    public Boolean queryLastCompletedAfter(Date d){
        Boolean retVal = Boolean.FALSE;
        //fail fast if the data model is busy.
        if(isBusyRequests.get() == true){ return retVal; }
        
        lastBatch = null;
        
        //Obtain an Entity Manager from the Factory
        EntityManager em = emfactory.createEntityManager();
            
        try{
            //Start a transaction
            em.getTransaction().begin();
            log.debug("Completed_After transaction started.");

            /*This query should find the most recent patients frozen down
             from the Ingress table.  Query written in HQL*/
            TypedQuery<Request> tq = em.createNamedQuery("Request.getCompletedAfterDtPull", Request.class);
            tq.setParameter("dtpull", d, TemporalType.DATE);
            //call the function that executes the query
            lastBatch = (List<Request>) execQuery(tq,em);
            //set the return value to true. Query completed successfully.
            retVal = Boolean.TRUE;
        }
        catch(Exception myEx){
            log.error("Completed_After transaction problem.");
            log.error(myEx);
        }
        
        log.debug("Completed_After transaction ended.");
        return retVal;
    }
    
    public Boolean queryPendingIngress(){
        Boolean retVal = Boolean.FALSE;
        //fail fast if the data model is busy.
        if(isBusyRequests.get() == true){ return retVal; }
        
        //clear the existing values
        pendingIng = null;
        
        //Obtain an Entity Manager from the Factory
        EntityManager em = emfactory.createEntityManager();
            
        try{
            //Start a transaction
            em.getTransaction().begin();
            log.debug("Pending ingress transaction started. ");

            TypedQuery<Ingress> tq = em.createNamedQuery("Ingress.notVerified", Ingress.class);
            pendingIng = (List<Ingress>) execQuery(tq,em);
        }
        catch(Exception myEx){
            log.error(myEx);
        }
        
        log.debug("Pending ingress transaction ended. ");
        return retVal;
    }
    
    /* Execute query method.  This simply saves typing this section of code
    * over and over again.  The transaction is rolled back and the entity
    * manager is closed.  The calling function is going to have to define the
    * class of the objects in the returned list.
    */
    private List execQuery(Query q, EntityManager em){
        List lo = null;
        
        try{
                /*This query should find the most recent patients frozen down
                 from the Ingress table.  Query written in HQL*/
                lo = q.getResultList();
                
        } catch (Exception myEx) {
               //A problem with one of the queries
               log.error("Query failure\n" + myEx.getMessage()+"\n");
        } finally {
               //end transaction regardless of error or not.
               em.getTransaction().rollback();
               em.close();
               log.debug("Transaction complete and entity manager closed.");
        }
        
        return lo;
    }

    public boolean simpleValidationQuery(){
        try{
            //Obtain an Entity Manager from the Factory
            EntityManager em = emfactory.createEntityManager();
            
            //Start a transaction
            em.getTransaction().begin();
            log.debug("Simple validation transaction started. ");

            try{
                /*This query should find the most recent patients frozen down
                 from the Ingress table.  Query written in HQL*/
                //TypedQuery<Integer> tr = em.createQuery("SELECT 1 + 1 AS i", Integer.class);
                Query tr = em.createNativeQuery("SELECT 1 + 1");

                BigInteger checkInt = (BigInteger) tr.getSingleResult();
                //Check that 1+1 is equal to two according to the server.
                if( checkInt.intValue() != 2){
                    return false;
                }

             } catch (Exception myEx) {
                    //A problem with one of the queries
                    log.error("Simple validation query failure\n" + myEx.getMessage()+"\n");
                    return false;
             } finally {
                    //end transaction regardless of error or not.
                    em.getTransaction().rollback();
                    em.close();
                    log.debug("Simple validation transaction complete.");
             }
        }
        catch(Exception myEx){
            log.error(myEx);
        }
        
        return true;
    }
    
    ////////////////////////////////////////////////////////////////////////////
    
    
    /* Class of handler to take care of actions on success or failure of the 
     * Request Modification workers.  In the event of failur OR success, 
     * 1) Remove all pending changes
     * 2) Update the return status property
     * 3) Update the busy status property.
     */
    private class ReqReturnHandler implements EventHandler<WorkerStateEvent>
    {
            @Override
            public void handle(WorkerStateEvent wse) {
                //Worker succeeded.  Let there be rejoicing.

                //Clear the backups of the items that are in the requests to be modded
                for(Long l : moddedReqs.keySet()){
                    backedReqs.remove(l);
                }

                //Clear the requests to be modded.
                moddedReqs.clear();
                
                //Update the return status.
                reqOpReturnStatus = wse.getSource().getState();
                
                //Update the busy status of the DataModel. Controller should be
                //listening to this.
                isBusyRequests.set(false);
            }
        }
    
    public void commitRequestChanges(){
        //Don't run if isBusyRequests is already true
        if(isBusyRequests.get()){ return; }
        
        //Set busy to be true;
        isBusyRequests.set(true);
        
        //TODO: check that the backups are the same as in the db. (stale check)
        
        //After validation that backups aren't different than the db, 
        // get the list of requests to change, and create a Task to do the db
        // update in the background.
        List<Request> lr = new ArrayList<>(moddedReqs.values());
        //This worker is a Task that will be doing the updates.
        ReqModWorker rmw = new ReqModWorker(lr, emfactory.createEntityManager() );
        
        //Setup listeners to handle the result of the task.
        rmw.setOnSucceeded( new ReqReturnHandler() );
        rmw.setOnFailed(new ReqReturnHandler() );

        
        //Create a thread for the worker
        Thread thr = new Thread(rmw);
        //Set the thread status to daemon.  It's not a user thread.
        thr.setDaemon(true);
        //Set it running
        thr.start();

    }
    
    
    ////////////////////////////////////////////////////////////////////////////
    
    /**
     * @return the pendingAppr
     */
    public List<Request> getPendingAppr() {
        return pendingAppr;
    }

    /**
     * @param pendingAppr the pendingAppr to set
     */
    private void setPendingAppr(List<Request> pendingAppr) {
        this.pendingAppr = pendingAppr;
    }

    /**
     * @return the pendingPull
     */
    public List<Request> getPendingPull() {
        return pendingPull;
    }

    /**
     * @param pendingPull the pendingPull to set
     */
    private void setPendingPull(List<Request> pendingPull) {
        this.pendingPull = pendingPull;
    }

    /**
     * @return the recentPull
     */
    public List<Request> getRecentPull() {
        return recentPull;
    }

    /**
     * @param recentPull the recentPull to set
     */
    private void setRecentPull(List<Request> recentPull) {
        this.recentPull = recentPull;
    }

    /**
     * @return the lastEn
     */
    public List<Request> getLastEn() {
        return lastEn;
    }

    /**
     * @param lastEn the lastEn to set
     */
    private void setLastEn(List<Request> lastEn) {
        this.lastEn = lastEn;
    }

    /**
     * @return the lastBatch
     */
    public List<Request> getLastBatch() {
        return lastBatch;
    }

    /**
     * @param lastBatch the lastBatch to set
     */
    private void setLastBatch(List<Request> lastBatch) {
        this.lastBatch = lastBatch;
    }

    /**
     * @return the pendingIng
     */
    public List<Ingress> getPendingIng() {
        return pendingIng;
    }

    /**
     * @param pendingIng the pendingIng to set
     */
    private void setPendingIng(List<Ingress> pendingIng) {
        this.pendingIng = pendingIng;
    }
    
    //This getter will allow the controller to listen to the Request arm of the DM.
    public BooleanProperty getIsRequestsBusy(){
        return isBusyRequests;
    }
    
    //This getter will allow the controller to listen to the Ingress arm of the DM.
    public BooleanProperty getIsIngressBusy(){
        return isBusyIngress;
    }
    
}

////////////////////////////////////////////////////////////////////////////////

/* Background worker to run on separate thread and execute updates to the database.
 */

    class ReqModWorker extends Task<Boolean>{
        
        List<Request> reqsToBeMerged;
        EntityManager entManager;
        
        //Constructor that takes a list of requests to merge
        ReqModWorker(List<Request> lr, EntityManager em){
            reqsToBeMerged = lr;
            entManager = em;
        }
        
        @Override
        protected Boolean call() throws Exception {
            //assume the call does not succeed. Set initila return value to false
            Boolean retVal = Boolean.FALSE;
            //Immediately return false if the prerequisite inputs are false.
            if(reqsToBeMerged == null){
                return(retVal);
            }
            if(entManager == null || entManager.isOpen() == false){
                return(retVal);
            }
            
            try{

                entManager.getTransaction().begin();
                for (Request r : reqsToBeMerged) {
                    //check that the backup copy is same as db copy.
                    
                    //Lock the entity and merge it (update the db).
                    entManager.lock(r, LockModeType.WRITE);
                    entManager.merge(r);
                }

                //commit the transaction (all the updates)
                entManager.getTransaction().commit();
                retVal=Boolean.TRUE;

            }catch(Exception myEx){
                //rollback the transaction if there was a problem
                entManager.getTransaction().rollback();
                retVal=Boolean.FALSE;
            }finally{
                //Regardless of success or failure, close the entity manager.
                entManager.close();
            }

            this.updateMessage("Task call completed.");
            return retVal;            
        }
        
    }