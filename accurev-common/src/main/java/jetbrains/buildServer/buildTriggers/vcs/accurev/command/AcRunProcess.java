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
import com.accurev.common.parsers.HistoryParser;

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
    
    public String getParentStreamName(@NotNull String depot, @NotNull String stream) 
    		throws VcsException;
    
    public boolean doesStreamExist(@NotNull String depot, @NotNull String stream) 
    		throws VcsException;
    
    public boolean isPassThroughStream(@NotNull String depot, @NotNull String stream) 
    		throws VcsException;
    
	public void createSnapshot(String snapshotName, String backingStreamName, String timeSpec) 
			throws VcsException;
	
	public List<XMLTag> getUpdateStreamInfo(@NotNull String depot, @NotNull String stream, String highTx, String lowTx)
			throws VcsException;
	
    public void populateStream(@NotNull String depot, @NotNull String stream, @NotNull String location)
        	throws VcsException;
    
    public XMLTag accurevShow(@NotNull String depot, @NotNull String stream) 
    		throws VcsException;
    
	public ElementStatusData getElementInfo(@NotNull String depot, @NotNull String stream, String element) 
			throws VcsException;
	
    public String getDirectAncestor(String verId, String filePath)
        	throws VcsException;

	public HistoryParser getHistoryBetween(@NotNull String depot, @NotNull String stream, String fromVer, String toVer) 
			throws VcsException;
    
}
