/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package manreq;


import basefxpreloader.AppNotification;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javax.persistence.EntityManagerFactory;
import org.jasypt.encryption.pbe.StandardPBEStringEncryptor;
import org.jasypt.exceptions.EncryptionInitializationException;
import org.jasypt.hibernate4.encryptor.HibernatePBEEncryptorRegistry;
import org.jboss.logging.Logger;

/**
 *
 * @author grossco
 */
public class Manreq extends Application {
    
    /*Class variables for general initialization */
    //Name of environment variable key that contains the cypher value.
    private final static String osEnvKey = "TILLabManreqCode";
    //Name of file that contains the required configuration information.
    private final static String configFile= "connectionInfo.txt";
    //setup Entity Manager Factory in initialize function
    private EntityManagerFactory emfactory;
    //class logger for the application
    private Logger log;
    /* Boolean value indicating if an alternate launch should be done.
     * Use this in the case of an error during initialization.*/
    private boolean doAltLaunch_Error = false;
    //String to retain short information about initialization process
    private StringBuilder sbInit = new StringBuilder();
      
    
        
    /* Do initialization of database and logger resources.
     * Copy most of what the base authentication and connection example does
     * into this function.
     */
    @Override
    public void init(){
        /*LOGGER SETUP*/
        //Set the property that tells jboss logger which backend to use
        System.setProperty("org.jboss.logging.provider", "log4j");
        // create logger.
        log = Logger.getLogger(Manreq.class);
        log.debug("Logger created.  Initialization started.");
        
        //notify the Preloader of the current progress
        notifyPreloader(new AppNotification("Init Started.",0.05) );
        
        
        /* Setup configuration to use encrypted strings with Jasypt
         * If this returns false, you don't have the cypher key.  The user 
         * should be informed of this failure. 
         */
        if(doHibernateJaspytConfig() == false){
            //String for the error message
            String emsg = "Required data from connectionInfo.txt not found.";
            //send error message to the logger
            log.error(emsg);
            //send error message to the pre loader
            notifyPreloader(new AppNotification(emsg,0.10) );
            //store error message in the init description string
            sbInit.append(emsg); 
            sbInit.append('\n');
            //Set the flag to show an alternative stage instead of regular app.
            doAltLaunch_Error = true;
        }else{
            notifyPreloader(new AppNotification("Hibernate-Jasypt Configigured.",0.10) );
        }
        log.debug("Hibernate-Jaspyt config done. doAltLaunch_Error: "+doAltLaunch_Error);

        
        /* Look for connectionInfo overrides file. Read the relevant connection
         * parameters. If this returns null, there is a problem.  User should be informed
         * of the failure. 
         */
        Map<String,String> overrides = dbConnectInfoParse();
        if(overrides == null){
            String emsg = "Required data from connectionInfo.txt not found.";
            log.error(emsg);
            notifyPreloader(new AppNotification(emsg,0.15) );
            //store error message in the init description string
            sbInit.append(emsg); 
            sbInit.append('\n');
            doAltLaunch_Error = true;
        }else{
            notifyPreloader(new AppNotification("Connection Details Parsed.",0.15) );
        }
        log.debug("Parse connection info file done. doAltLaunch_Error: "+doAltLaunch_Error);
        
        
        /* Run directory check.  Check that this application is being run from 
         * one of the expected directories.  If not, then the user has probably 
         * copied the jar to their local machine.  Die gracefully in this case.         
         */
        if(checkWorkingDirectory() == false){
            String emsg = "Required data from connectionInfo.txt not found, or "
                    + "running application from incorrect directory.";
            log.error(emsg);
            notifyPreloader(new AppNotification(emsg,0.20) );
            //store error message in the init description string
            sbInit.append(emsg); 
            sbInit.append('\n');
            doAltLaunch_Error = true;
        }else{
            notifyPreloader(new AppNotification("Working Directory Checked.",0.20) );
        }
        log.debug("Check working directory done. doAltLaunch_Error: "+doAltLaunch_Error);
        
        
        /*Initialize Entity Manager
         * Let the entity manager deal with the Hibernate session and 
         * persisting data. This is where serious resource allocation starts. */
        DataModel.getInstance().initEntityManagerParameters(overrides);
        
        if(DataModel.getInstance().emfactoryIsOkay() == false){
            String emsg = "EntityManagerFactory could not be created.";
            notifyPreloader(new AppNotification(emsg,0.30d) );
            log.error(emsg);
            sbInit.append(emsg);
            sbInit.append('\n');
            doAltLaunch_Error = true;
        }else{
            notifyPreloader(new AppNotification("EntityManagerFactory Created.",0.30d) );
        }
        
        //Do simple validation query to check connectivity to the database
        if(DataModel.getInstance().simpleValidationQuery()==false){
            String emsg = "Simple validation query failed. Database connection problem.";
            notifyPreloader(new AppNotification(emsg,0.35d) );
            log.error(emsg);
            sbInit.append(emsg);
            sbInit.append('\n');
            doAltLaunch_Error = true;
        }
            
        /* Do initial database query if the startup state is still normal.
         * Check that none of the alternate launch values are true
         */
        if(doAltLaunch_Error==false){
            Boolean querySuccess = Boolean.FALSE;
            //Alternate Launch not triggered.
            sbInit.append("App startup successful.");
                    
            //Do initial queries for request management
            //Get requests pending approval
            notifyPreloader(new AppNotification("Getting approval pending requests.",0.45d) );
            querySuccess = DataModel.getInstance().queryPendingApproval();

            //Get requests pending pull
            notifyPreloader(new AppNotification("Getting pull pending requests.",0.60d) );
            querySuccess = DataModel.getInstance().queryPendingPull();
            
            //Get most recent N requests completed that have been completed.
            notifyPreloader(new AppNotification("Getting recent 10 requests completed.",0.70d) );
            querySuccess = DataModel.getInstance().queryLastNCompleted(10);
            
            //Get completed requests from past X days.
            notifyPreloader(new AppNotification("Getting recent completed within 30 days.",0.90d) );
            //get calendar for today and subtract 30 days.
            Calendar c = new GregorianCalendar();
            c.add(Calendar.DAY_OF_MONTH, -30);
            querySuccess = DataModel.getInstance().queryLastCompletedAfter(c.getTime());
            
            //Get the ingress entries that are pending merge
            notifyPreloader(new AppNotification("Getting Ingress sampes.",0.95d) );
            querySuccess = DataModel.getInstance().queryPendingIngress();
            log.debug("Pending ingress query complete.");
            
        }
        
        notifyPreloader(new AppNotification("Done.",1.0d) );
        
    }
       
    
    @Override
    public void start(Stage stage) throws Exception {
        //The root scence node depends on the startup status of the program
        Parent root;
        FXMLLoader loader;
        if(doAltLaunch_Error == true){
            loader = new FXMLLoader(getClass().getResource("tlbase.fxml"));
            root = (Parent) loader.load();
        }else{
            loader = new FXMLLoader(getClass().getResource("MainPanel.fxml"));
            root = (Parent) loader.load();
        }

        
        Scene scene = new Scene(root);
        
        stage.setScene(scene);
        stage.setTitle("Request Management Application");
        stage.show();
    }

    /* Override the stop function to release resources obtained in init().
     * Specifically, close the EntityManagerFactory.
     */
    @Override
    public void stop(){
        log.debug("Program Shutdown.");
        if(emfactory!= null){
            if(emfactory.isOpen()){
                /* The Exception in the logging output is there simply to show
                 * the stack trace. This is from the C3P0 logging code.
                 * Not an actual error. */
                emfactory.close();
            }
        }
    }
    
    
     /* Setup registerPBEStringEncryptor with Hibernate registry.
     * This gets the cypher key from an environment variable which is then used
     * to decrypt the connection and directory details required to run the 
     * application
     */
    public boolean doHibernateJaspytConfig(){

        String keyphrase=getJasptKeyphrase();
        if(keyphrase == null){
            log.error("Unable to obtain keyphrase");
            return false;
        }
        
        StandardPBEStringEncryptor strongEncryptor = new StandardPBEStringEncryptor();
        strongEncryptor.setAlgorithm("PBEWITHMD5ANDDES");
        strongEncryptor.setPassword(dc(keyphrase.toCharArray()));
        HibernatePBEEncryptorRegistry registry =  HibernatePBEEncryptorRegistry.getInstance();
        registry.registerPBEStringEncryptor("hibernateEncryptor", strongEncryptor);
        strongEncryptor.initialize();
              
        return true;
    }
    
    /* Get the keyphrase from the user or server's environment variables.
     * This prevents us from having to store the keyphrase in plain text in 
     * the code or in an easily accesible file.
    */
    private String getJasptKeyphrase(){
        //Get environment variables as a String key, String value Map.
        Map<String,String> envVars = System.getenv();
        String keyphrase;
        
        /* Get the TIL Lab inventory applications code from the OS Environment.
         * Environment variable name should be "TILLabInvAppsCode".  If it's 
         * not set as an environment variable for the calling user, then the
         * password based encryption isn't going to work.  The digest algorithm
         * was Jasypt's PBEWITHMD5ANDDES.
         */
        try{
            //Check that the System Environment contains the required Key.
            if( envVars.containsKey(osEnvKey) ){
                log.debug(osEnvKey+" = "+envVars.get(osEnvKey));
                keyphrase= envVars.get(osEnvKey);
            }else{
                log.debug(osEnvKey+" not found in environment variables.");
                keyphrase=null;
            }
        }catch(  ClassCastException | NullPointerException myMex){
            log.error(myMex);
            keyphrase=null;
        }
        
        return keyphrase;
    }
    
    /* Parse database connection information out of the config file.
     * The config file should be connectionInfo.properties: user, password, url.
     * It's plain text that uses # as comment marks and key=value syntax.  No 
     * duplicate keys permitted.
     */
    public Map<String,String> dbConnectInfoParse(){
        HashMap<String,String> params = null;
        //If the directory is not specified in the string, will check working dir
        File cfile = new File(configFile);

        //Check if the file exists
        if(cfile.exists()){
            try{
                params = new HashMap<>();
                //load data from the text file in the fashion of properties file
                Properties prop = new Properties();
                prop.load(new FileInputStream(cfile));

                //if the specific keys exist, add them to params hashmap
                if(prop.getProperty("password") != null){
                    params.put("hibernate.connection.password", prop.getProperty("password"));
                }
                if(prop.getProperty("user") != null){
                    params.put("hibernate.connection.username", prop.getProperty("user"));
                }
                if(prop.getProperty("url") != null){
                    params.put("hibernate.connection.url", prop.getProperty("url"));
                }
            }
            catch(Exception myEx){
                log.error(myEx.getMessage());
            }
        }
        else{
            log.error("File not found: "+cfile.toString());
        }
        
        return params;
    }
    
    /* Check the running location of the machine.
     * If the working directory is not one of the approved locations specified
     * in the connectionInfo file, then there is problem.  It's a rather hackish
     * method of access control, but at least it prevents the ludites from 
     * inadvertently copying the entire jar to thier local drive. 
     */
    private boolean checkWorkingDirectory(){

        log.debug(System.getProperty("user.dir"));
  
        //return value
        boolean retVal = false;
        
        //Get the user directory as a Path.
        //Compare to those specified in external file.
        File ufile = new File(System.getProperty("user.dir"));
        
        //Get the PBE keyphrase
        String keyphrase=getJasptKeyphrase();
        if(keyphrase == null){
            log.error("Unable to obtain keyphrase");
            return false;
        }
        
        //Setup PBE for decypherment of connections info
        StandardPBEStringEncryptor encryptor = new StandardPBEStringEncryptor();
        encryptor.setAlgorithm("PBEWithMD5AndDES");
        encryptor.setPassword(dc(keyphrase.toCharArray()));
        
        //Initialize the encryptor, and die if it fails
        try{
        encryptor.initialize();
        }catch(EncryptionInitializationException myEIEx){
            log.error(myEIEx);
            return false;
        }
        
        //Will check for the configFile in the user.dir without path specified.
        Path f = Paths.get(configFile);
        //Check if the file exists or die
        if(Files.exists(f) == false){
            //die
            log.error("File not found: "+f.toString());
            return false;
        }
        
        //load data from the text file in the fashion of properties file
        Properties prop = new Properties();
        try {
            prop.load(Files.newInputStream(f));
        } catch (IOException myIOEx) {
            log.error(myIOEx);
            return false;
        }

        //get list of property keys
        Set<String> propNames = prop.stringPropertyNames();

        //check property keys against regex for validLocations
        Pattern patt= Pattern.compile("^validLocation\\d$");
        Matcher matt = patt.matcher("default");
        //variable for hodling file paths (directory) read from external file
        File pfile;
        for (String inStr : propNames) {
            //reset the matcher with the next name in the property names
            matt.reset ( inStr );
            
            //check the new input against the pattern
            if(matt.matches() == true){
                // if there is a name match, get the property value String                
                String pStr = prop.getProperty(inStr);
                log.debug("Read propValue="+pStr);
                
                //Check if the property value string is encoded
                if(pStr.startsWith("ENC(\"") & pStr.endsWith("\")") ){
                    //Decode String and convert to file
                    log.debug("decrypting" );
                    try{
                    pfile = new File(encryptor.decrypt( pStr.substring(5, pStr.length()-2)) );
                    //Check the propertyValue asgainst the user.dir string
                    if( ufile.compareTo(pfile)==0){
                        /*set the return value to true if the user directory
                          matches one of the specified locations */
                        retVal = true;
                        log.debug("Valid location property value matches user.dir.");
                    }//fi
                    }catch(Exception myEx){
                        log.warn(myEx);
                    }
                }//fi
            }//fi
        }//rof

        return retVal;
    }
    
    ///Simple function
    private static String dc(char[] c){
        for(int i=0;i<c.length;i++){
            c[i] =  (char)((int) c[i] + 8);}
        return(String.valueOf(c));
    }
    
}