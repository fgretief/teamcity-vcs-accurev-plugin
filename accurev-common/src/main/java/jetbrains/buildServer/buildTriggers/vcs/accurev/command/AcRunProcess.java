package jetbrains.buildServer.buildTriggers.vcs.accurev.command;

import java.io.IOException;
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

import jetbrains.buildServer.vcs.VcsException;

public interface AcRunProcess {
	
	public SessionToken getSessionToken();
	public void setSessionToken(SessionToken sessionToken);
	
	public String getLastTransactionId(@NotNull String depot) 
			throws VcsException;
	
	public Document getListOfChangedElements(@NotNull String stream, String fromVer, String toVer)
            throws VcsException , ParserConfigurationException, SAXException, IOException;

    public Vector<TransactionData> getRevisionsBetween(@NotNull String depot, @NotNull String stream, String fromVer, String toVer) 
    		throws VcsException;
    
    public boolean doesStreamExist(String depot, String stream) 
    		throws VcsException;
    
    public boolean isPassThroughStream(String depot, String stream) 
    		throws VcsException;
    
	public void createSnapshot(String snapshotName, String backingStreamName, String timeSpec) 
			throws VcsException;
	
	public List<XMLTag> getUpdateStreamInfo(String depot, String stream, String highTx, String lowTx)
			throws VcsException;
	
    public void populateStream(@NotNull String depot, @NotNull String stream, @NotNull String location)
        	throws VcsException;
    
    public XMLTag accurevShow(String depot, String stream) 
    		throws VcsException;
    
	public ElementStatusData getElementInfo(String depot, String stream, String element) 
			throws VcsException;
    
}
