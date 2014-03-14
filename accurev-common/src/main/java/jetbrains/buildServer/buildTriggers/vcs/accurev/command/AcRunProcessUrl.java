package jetbrains.buildServer.buildTriggers.vcs.accurev.command;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.List;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import com.accurev.common.data.ElementStatusData;
import com.accurev.common.data.SessionToken;
import com.accurev.common.data.TransactionData;
import com.accurev.common.data.XMLTag;

import jetbrains.buildServer.buildTriggers.vcs.accurev.Settings;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.vcs.VcsException;

/**
 * @author Francois Retief <fgretief@gmail.com>
 */
public class AcRunProcessUrl implements AcRunProcess 
{

	public static AcRunProcess getInstance(@NotNull Settings settings, File workingDirectory) throws VcsException
	{
		AcRunProcessUrl result = new AcRunProcessUrl();
	
		return result;
	}
	
	public SessionToken getSessionToken() {
		throw new UnsupportedOperationException();
	}
	
	public void setSessionToken(SessionToken sessionToken) {
		/* do nothing */
	}
			
	public String getLastTransactionId(@NotNull String depot)
			throws VcsException
	{
    	try {
    		URL url = new URL("http://localhost:8055/accurev/lastTransactionId?depot="+depot);
    		HttpURLConnection connection = (HttpURLConnection) url.openConnection(); 

    		connection.setRequestMethod("GET"); 
            connection.setRequestProperty("Content-Type", "application/xml");
            
            Loggers.VCS.info("[XX] fetching data from URL: lastTransactionId");
            
            InputStream content = (InputStream) connection.getInputStream();
            BufferedReader in = new BufferedReader(new InputStreamReader(content));
    		
            StringBuffer buffer = new StringBuffer();
            
            String line;
            while ((line = in.readLine()) != null) {
            	buffer.append(line);
            }
            
            String data = buffer.toString();            
            Loggers.VCS.info(String.format("[XX] result '%s'", data));
            
            connection.disconnect();
            
            return data;

    	} catch (Exception e) {
    		Loggers.VCS.error(String.format("getLastTransactionId: exception '%s'", e.getMessage()), e);
    		return null;
    	}  		
	}
	
    public Document getListOfChangedElements(@NotNull String stream, String fromVer, String toVer)
            throws VcsException , ParserConfigurationException, SAXException, IOException
    {
    	throw new UnsupportedOperationException();
    }
    
    public Vector<TransactionData> getRevisionsBetween(@NotNull String depot, @NotNull String stream, String fromVer, String toVer) 
    		throws VcsException
    {
    	throw new UnsupportedOperationException();
    }
    
    public boolean doesStreamExist(String depot, String stream) throws VcsException
    {
    	throw new UnsupportedOperationException();
    }
    
    public boolean isPassThroughStream(String depot, String stream) 
    		throws VcsException
    {
    	throw new UnsupportedOperationException();
    }
    
	public void createSnapshot(String snapshotName, String backingStreamName, String timeSpec) 
			throws VcsException
	{
		throw new UnsupportedOperationException();
	}
	
	public List<XMLTag> getUpdateStreamInfo(String depot, String stream, String highTx, String lowTx)
			throws VcsException
	{
		throw new UnsupportedOperationException();
	}
	
    public void populateStream(@NotNull String depot, @NotNull String stream, @NotNull String location)
        	throws VcsException
	{
    	throw new UnsupportedOperationException();
	}

    public XMLTag accurevShow(String depot, String stream) 
    		throws VcsException
	{
    	throw new UnsupportedOperationException();
    }
    
	public ElementStatusData getElementInfo(String depot, String stream, String element) 
			throws VcsException
	{	
    	throw new UnsupportedOperationException();
	}
    
}
