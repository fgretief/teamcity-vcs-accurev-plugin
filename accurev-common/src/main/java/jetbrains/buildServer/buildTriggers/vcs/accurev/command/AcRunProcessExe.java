/*
 * Copyright 2009 Francois Retief
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package jetbrains.buildServer.buildTriggers.vcs.accurev.command;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.ByteArrayInputStream;
import java.util.Hashtable;
import java.util.Vector;
import java.util.List;

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;
//import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.buildTriggers.vcs.accurev.Settings;

import com.accurev.common.data.*;
import com.accurev.common.parsers.*;
import com.accurev.common.process.*;

import org.jetbrains.annotations.NotNull;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;

/**
 * @author Francois Retief
 */
public class AcRunProcessExe extends RunProcess implements AcRunProcess
{
	public class AcHistoryParser extends HistoryParser 
	{
		@SuppressWarnings("rawtypes")
		public Hashtable getStreamCollection()
		{
			return streamCollection;
		}
	}
	
	private File workingDir;
	private List<String> errMessages;
	final private  String commandPrefix = "RUN COMMAND:"; 

	public AcRunProcessExe(File workingDir)
	{
		this.workingDir = workingDir;
		enableDebug = true; // for testing
	}
	
	public void setErrMessagesList(List<String> errMessages) throws VcsException
	{
		this.errMessages = errMessages;
	}

	public static AcRunProcess getInstance(Settings settings, File workingDirectory) throws VcsException
	{
		RunProcess.setAccuRevExecutable(settings.getExecutablePath().getAbsolutePath());

		AcRunProcess cmd = new AcRunProcessExe(workingDirectory);
		
		AcSecurityProcess sec = new AcSecurityProcess();
		int[] ver = sec.getAccuRevServerVersion();
		
		if ((ver[0] == 4 && ver[1] >= 7) || (ver[0] > 4)) 
		{
			String token = "";
	    	if (sec.getSecurityInfo().startsWith(sec.NotAuthenticated))
	        	token = sec.Login(settings.getUsername(), settings.getPassword());
			
	    	cmd.setSessionToken(new SessionToken(settings.getServerName() + ":" + settings.getServerPort(), token, settings.getUsername()));
		}

		return cmd;
	}

	public SessionToken getSessionToken()
	{
		return sessionToken;
	}

	// TODO: this is the code to format an AccuRev date
    // /** Date format for AccuRev. */
    //private static final String ACCUREV_DATE_FORMAT = "yyyy/MM/dd HH:mm:ss";
	//String dateStr = new java.text.SimpleDateFormat(ACCUREV_DATE_FORMAT).format(sinceDate);

	private int ThrowIfError(String cmdName, int result, int[] acceptableReturnCodes) throws VcsException
	{
		
		boolean error = false;
		if(acceptableReturnCodes != null)
		{
			boolean returnCodeIsAcceptable = false;
			for(int code: acceptableReturnCodes)
			{
				if(code == result)
				{
					returnCodeIsAcceptable = true;
					break;
				}
			}
			if(!returnCodeIsAcceptable)
			{
				error = true;;
			}
		}
		
		if (error || (result != ERR_SUCCESS && result != 1)) // DCN 25SEP09 AccuRev for some reason is returning "1" under some circumstances. This behaviour doesn't seem to be documented.
		{
            String stderr = getErrorText().toString();
            String stdout = getResponseText().toString();

        	final String message = "'" + cmdName + "' command failed." +
        			" (" + CommandUtil.errorToString(result) + ")" +
                    (!StringUtil.isEmpty(stderr) ? "\nstderr: " + stderr : "") +
                    (!StringUtil.isEmpty(stdout) ? "\nstdout: " + stdout : "");

            Loggers.VCS.warn(message);
            throw new VcsException(message);
		}
		return result;
	}

	public int doExecute(String cmdName, String[] args)
		throws VcsException
	{
		mLastCommand = args;
		mLastResponse = null;
		return ThrowIfError(cmdName, exec(args, workingDir.getAbsolutePath()), null);
	}

	public int doExecute(String cmdName, String[] args, AccuRevHandler parseHandler)
		throws VcsException
	{
		mLastCommand = args;
		int ret = ThrowIfError(cmdName, exec(args, workingDir.getAbsolutePath(), parseHandler), null);
		mLastResponse = parseHandler.getMessageData();
		
		return ret;
	}

	public int doExecute(String cmdName, String[] args, int[] acceptableReturnCodes) throws VcsException
	{
		mLastCommand = args;
		mLastResponse = null;
		int returnCode  = ThrowIfError(cmdName, exec(args, workingDir.getAbsolutePath(), parseHandler), acceptableReturnCodes);
		return returnCode;
	}
	
	public void checkReturnCode(String cmdName, int returnCode )throws VcsException
	{
		if (returnCode != 0)
		{
            String stderr = getErrorText().toString();
            String stdout = getResponseText().toString();
            String msg = "\nFailed To execute command: " + cmdName;
            msg += "\nReturn Code : " + returnCode;
            msg += "\nStdout      : " + stdout;
            msg += "\nStderr      : " + stderr;
			throw new VcsException(msg);			
		}		
	}
		
	public void changeStream( @NotNull String stream, @NotNull String basisStream, @NotNull String timeSpec) 
	throws VcsException
	{
		String[] args = {
						RunProcess.getAccuRevExecutable(),
						"chstream",
						"-s",
						stream,
						"-b",
						basisStream,
						"-t", 
						timeSpec
					};
		String command = commandPrefix + "accurev chstream -s " + stream + " -b " + basisStream + " -t " + timeSpec;
		printBuildMessage(command);
		
		String cmdName = "accurev chstream";
		int returnCode = doExecute(cmdName, args);
		
		checkReturnCode(cmdName,returnCode);		
		printResultMessages(returnCode);
	}
		
	public void makeStream( @NotNull String newStream, @NotNull String parentStream, @NotNull String timeSpec) 
	throws VcsException
	{

		String[] args = {
						RunProcess.getAccuRevExecutable(),
						"mkstream",
						"-s",
						newStream,
						"-b",
						parentStream,
						"-t", 
						timeSpec
					};
		String command = commandPrefix + "accurev mkstream -s " + newStream + " -b " + parentStream + " -t " + timeSpec;
		printBuildMessage(command);
		
		String cmdName = "accurev mkstream";
		int returnCode = doExecute(cmdName, args);
				
		checkReturnCode(cmdName, returnCode);	
		printResultMessages(returnCode);
	}
	
    public void populateStream(@NotNull String depot, @NotNull String stream, @NotNull String location)
    	throws VcsException
    {
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"pop",
				"-fx",
				"-R",
				"-O",
				"-v", stream,
				"-L", location,
				"\\.\\",
		};
		
		String command = commandPrefix + "accurev pop -fx -R -O -v " + stream + " -L " + location + "\\.\\";
		printBuildMessage(command);
		
		String cmdName = "accurev pop";
		int returnCode = doExecute(cmdName, args);
		
		printResultMessages(returnCode);
		checkReturnCode(cmdName,returnCode);
    }

	@SuppressWarnings("unchecked")
	public TransactionData getTransaction(@NotNull String depot, @NotNull String stream, String transactionId)
		throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"hist",
				"-fx",
				"-t", transactionId,
				"-k", "promote",
				"-p", depot,
				"-s", stream,
		};
		
		String command = commandPrefix + "accurev hist -fx -t " + transactionId + " -k  promote" + " -p " + depot + " -s " + stream;
		printBuildMessage(command);
		
		HistoryParser hist = new HistoryParser();		
		int returnCode = doExecute("accurev hist", args, hist);
		
		printResultMessages(returnCode);
		
		Vector<TransactionData> vec = hist.getHistoryDataCollection();
		if (vec.size() > 0)
		{
			return vec.get(0);
		}
		return null;
	}

	public Vector<TransactionData> getRevisionsSince(@NotNull String depot, @NotNull String stream, String lastVer)
		throws VcsException
	{
		return getRevisionsBetween(depot, stream, lastVer, "highest");
	}

	@SuppressWarnings("unchecked")
	public Vector<TransactionData> getRevisionsBetween(@NotNull String depot, @NotNull String stream, String fromVer, String toVer) throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"hist",
				"-fvx",
				"-t", toVer + "-" + fromVer,
				"-k", "promote",
				"-p", depot,
				"-s", stream,
		};
		String command  = commandPrefix + "accurev hist -fvx -t" + toVer + "-" + fromVer + " -k promote -p " + depot + " -s " + stream;
		printBuildMessage(command);
		
		HistoryParser hist = new HistoryParser();
		int returnCode = doExecute("accurev hist", args, hist);
		
		printResultMessages(returnCode);		
		return hist.getHistoryDataCollection();
	}
	
	public AcHistoryParser getHistoryBetween(@NotNull String depot, @NotNull String stream, String fromVer, String toVer) 
			throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"hist",
				"-fvx",
				"-t", toVer + "-" + fromVer,
				"-k", "promote",
				"-p", depot,
				"-s", stream,
		};
		String command  = commandPrefix + "accurev hist -fvx -t" + toVer + "-" + fromVer + " -k promote -p " + depot + " -s " + stream;
		printBuildMessage(command);
		
		AcHistoryParser hist = new AcHistoryParser();
		int returnCode = doExecute("accurev hist", args, hist);
		
		printResultMessages(returnCode);		
		return hist;
	}
	
	
	public GenericXMLParser getAsXMLRevisionsBetween(@NotNull String depot, @NotNull String stream, String fromVer, String toVer) throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"hist",
				"-fvx",
				"-t", toVer + "-" + fromVer,
				"-k", "promote",
				"-p", depot,
				"-s", stream,
		};
		
		String command = commandPrefix + "accurev hist -fvx -t" + toVer + "-" + fromVer + " -k promote -p " + depot + " -s " + stream;
		printBuildMessage(command);
		
		GenericXMLParser parser = new GenericXMLParser();
		int returnCode = doExecute("accurev hist", args, parser);
		
		printResultMessages(returnCode);		
		return parser;
}

	public String getLastTransactionId(@NotNull String depot)
		throws VcsException 
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"hist",
				"-fx",
				"-t", "now.1",
				"-p", depot,
		};
/* for Debugging only
        Loggers.VCS.info("[XX] getLastTransactionId: depot: '"+depot);
        File f = new File("W:/TeamCity/aclogs/ac_last_trx_" + depot + ".txt");
        if (f.exists())
        { 
        	try {
            	BufferedReader bufferedReader = new BufferedReader(new FileReader(f));
        		try {
	        		// read only the first line of the file
					String result = bufferedReader.readLine();
	            	Loggers.VCS.info("[XX] getLastTransactionId:  version "+ result);
	            	return result;
            	} finally {
            		bufferedReader.close();
        		}
        	} catch (FileNotFoundException ex) {
        	} catch (IOException e) {
			}
        }
 */
        GenericXMLParser parser = new GenericXMLParser();
        String command = commandPrefix + "accurev hist -fx -t now.1 -p " + depot;
        printBuildMessage(command);
        
        int returnCode = doExecute("accurev hist", args, parser);
        printResultMessages(returnCode);
        
        XMLTag xTag = (XMLTag)parser.getTagList().get(0);
        String sInput = xTag.toXML();
        sInput = sInput.replaceAll("&", "&amp;");
        ByteArrayInputStream ssInput = new ByteArrayInputStream(sInput.getBytes());

        try
        {
            DocumentBuilderFactory DocFactory = DocumentBuilderFactory.newInstance();
            DocumentBuilder DocBuilder = DocFactory.newDocumentBuilder();
            Document DocToParse = DocBuilder.parse(ssInput);
            DocToParse.getElementsByTagName("transaction").item(0);
            Element iTxn = (Element) DocToParse.getElementsByTagName("transaction").item(0);
            return iTxn.getAttribute("id");
        }
        catch (Exception ex)
        {
            throw new VcsException("Unable to parse response document whilst obtaining latest TXNID: " + ex.getMessage() + "\nResponse Data: " + ssInput);
        }
    }

    public String getDirectAncestor(String verId, String filePath)
    	throws VcsException
    {
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"anc",
				"-fx",
				"-v", verId,
				filePath,
		};

		String command = commandPrefix + "accurev anc -fx -v "+ verId +" "+ filePath;
		printBuildMessage(command);

        GenericXMLParser parser = new GenericXMLParser();
        int returnCode = doExecute("accurev anc", args, parser);
		printResultMessages(returnCode);
		
		XMLTag acResponse = (XMLTag)parser.getTagList().get(0);
		assert acResponse.getName().equals("acResponse");
		
		XMLTag element = acResponse.getTag("element");
		String streamId = element.getAttributeValue("stream");
		String ancVerId = element.getAttributeValue("version");
		return streamId +"/"+ ancVerId;		 
    }	

	@SuppressWarnings("unchecked")
	public List<XMLTag> getUpdateStreamInfo(String depot, String stream, String highTx, String lowTx)
		throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"update",
				"-fx",
				"-t", highTx,
				"-s", stream,
				"-i",
		};

		String command = commandPrefix + "accurev update -fx -t" + highTx + "-" + lowTx + " -s " + stream + " -i";
		printBuildMessage(command);
		
		GenericXMLParser parser = new GenericXMLParser();
		int returnCode = doExecute("accurev update -i", args, parser);
		
		printResultMessages(returnCode);

		XMLTag acResponse = (XMLTag)parser.getTagList().get(0);
		assert acResponse.getName().equals("acResponse");
		return acResponse.getTags("Change");
	}

	class AcStatusParser extends StatusParser
	{
		public AcStatusParser()
		{
			super();
		}
	}

	@SuppressWarnings("unchecked")
	public ElementStatusData getElementInfo(String depot, String stream, String element) throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"stat",
				"-fx",
				"-s", stream,
				element,
		};
		StatusParser parser = new AcStatusParser();
		
		String command = commandPrefix + "accurev stat -fx -s" + stream + " " + element;
		printBuildMessage(command);
		
		int returnCode = doExecute("accurev update -i", args, parser);		
		printResultMessages(returnCode);
		
		System.out.println(this.getResponseText().toString());

		Vector<ElementStatusData> vec = parser.getElementStatusCollection();

		for (ElementStatusData stat : vec)
		{
			System.out.println("isDirectory = " + stat.isDirectory());
		}

		if (vec.size() > 0)
		{
			return vec.get(0);
		}
		return null;
	}

	public void createSnapshot(String snapshotName, String backingStreamName, String timeSpec) throws VcsException
	{
		String[] args = {
			RunProcess.getAccuRevExecutable(),
			"mksnap",
			"-s", snapshotName,
			"-b", backingStreamName,
			"-t", timeSpec,
		};
		
		String command = commandPrefix + "accurev mksnap -s " + snapshotName + " -b " + backingStreamName + " -t " + timeSpec;
		printBuildMessage(command);
		
		int returnCode = doExecute("accurev mksnap", args);		
		printResultMessages(returnCode);
	}

    public Document getListOfChangedElements(String stream, String fromVer, String toVer)
            throws VcsException , ParserConfigurationException, SAXException, IOException
    {
        String[] args = {
				RunProcess.getAccuRevExecutable(),
				"diff",
                "-a",
				"-fx",
                "-i",
				"-v", stream,
                "-V", stream,
                "-t", toVer + "-" + fromVer,
		};
        
        String command = commandPrefix + "accurev diff -a -fx -i -v " + stream + " -V " + stream + " -t" + toVer + "-" + fromVer;
        printBuildMessage(command);
        
        GenericXMLParser parser = new GenericXMLParser();
        int returnCode = doExecute("accurev diff", args, parser);
        
        printResultMessages(returnCode);
        
        XMLTag xTag = (XMLTag)parser.getTagList().get(0);
        String sInput = xTag.toXML();
        
        /* TODO 25SEP09 DCN - AccuRev CLI is returning unescaped characters in XML response. There could be others so need to investigate this further */
        sInput = sInput.replaceAll("&", "&amp;");

        ByteArrayInputStream ssInput = new ByteArrayInputStream(sInput.getBytes());
        DocumentBuilderFactory DocFactory = DocumentBuilderFactory.newInstance();
        DocumentBuilder DocBuilder = DocFactory.newDocumentBuilder();
        Document DocToParse = DocBuilder.parse(ssInput);
        return DocToParse;
    }
    
    public XMLTag accurevShow(String depot, String stream) throws VcsException
    {
        String[] args = {
				RunProcess.getAccuRevExecutable(),
				"show",
				"-fx",
				"-p", depot,
				"-s", stream,
                "streams"
		};
        
        String command = commandPrefix + "accurev -fx -p " + depot + " -s " + stream + " streams";
        printBuildMessage(command);
        
        GenericXMLParser parser = new GenericXMLParser(); 
        int returnCode = doExecute("accurev show", args, parser);
        
        printResultMessages(returnCode);
        
        XMLTag acResponse     = (XMLTag)parser.getTagList().get(0);
        String responseAsText = acResponse.toXML();
        boolean isCorrectType =  acResponse.getName().equals("streams");

        int numberOfStreams = acResponse.getTags("stream").size();
        if(!isCorrectType )
        {
        	throw new VcsException("Accurev show command returned returned an element which was not of type \'stream\' :" + responseAsText);
        }
        else if(numberOfStreams > 1)
        {
        	throw new VcsException("Accurev show command returned more than one element of type \'stream\'");
        }
        else if(numberOfStreams == 0)
        {
        	return null;//the requested stream does not exist
        }
        
        return acResponse;
    }
      
    public XMLTag getStreamOverlaps(String stream) throws VcsException
    {
        // Build the command
    	String[] args = {
				RunProcess.getAccuRevExecutable(),
				"stat",
				"-fx",
				"-s", stream,
                "-o"
		};
        
        String command = commandPrefix + "stat -fx -s " + stream + " -o";
        printBuildMessage(command);
        
        // Execute the command and parse the response
        GenericXMLParser parser = new GenericXMLParser(); 
        int returnCode = doExecute("accurev stat", args, parser);        
        printResultMessages(returnCode);
        
        // Get the root of the response
        XMLTag acResponse = (XMLTag)parser.getTagList().get(0);

        // Check if the response is in a correct format
        if(!acResponse.getName().equals("AcResponse"))
        {
            String responseAsText = acResponse.toXML();
        	throw new VcsException("Accurev stat command returned an invalid response' :" + responseAsText);
        }
        
        return acResponse;
    }
      
    public boolean isPassThroughStream(String depot, String stream) throws VcsException
    {
    	XMLTag acResponse = accurevShow(depot, stream);
    	if(acResponse == null)
    	{
    		return false;//it can only be a passthrough stream if it exists.
    	}
        XMLTag acStream   = (XMLTag)acResponse.getTags("stream").get(0);
        
        String streamType = acStream.getAttributeValue("type");
        if(streamType.equals("passthrough"))
        {
        	return true;
        }
        return false;    	
    }
        
    public String getParentStreamName(String depot, String stream) throws VcsException
    {
    	XMLTag acResponse = accurevShow(depot, stream); 
    	if(acResponse == null)
    	{
    		return null; //Stream does not exist
    	}
        XMLTag acStream   = (XMLTag)acResponse.getTags("stream").get(0);
        
        return acStream.getAttributeValue("basis");
    }
        
    public boolean doesStreamExist(String depot, String stream) throws VcsException
    {
    	XMLTag acResponse  = accurevShow(depot, stream);
    	if(acResponse == null)
    	{
    		return false;
    	}
    	//if an acception has not been thrown then the stream exists
        return true;
    }
        
    @SuppressWarnings("unchecked")
    public Vector<WorkspaceData> accurevShowWorkspaces(String depot) throws VcsException
    {
        String[] args = {
				RunProcess.getAccuRevExecutable(),
				"show",
				"-fx",
				"-p", depot,
                "wspaces"
		};
        
        String command = commandPrefix + "accurev show -fx -p " + depot + " wspaces";
        printBuildMessage(command);
        
        // Parse accurev response
        ShowWorkspaceParser parser = new ShowWorkspaceParser(); 
        int returnCode = doExecute("accurev show wspaces", args, parser);        
        printResultMessages(returnCode);
        
        // Get the list of workspaces
        Vector<WorkspaceData> wspaces = parser.getWorkspaceShowCollection();

        if (wspaces.isEmpty())
        {
        	// No workspace
        	return null;
        }
        else
        {
        	return wspaces;
        }
    }
      
    public Boolean doesWorkspaceExist(String depot, String workspace) throws VcsException
    {
    	Vector<WorkspaceData> wspaces = accurevShowWorkspaces(depot);
    	if (wspaces == null)
    	{
    		// No workspaces
    		return false;
    	}
    	
        // Search for the required workspace
    	for (WorkspaceData ws : wspaces)
    	{
    		if(ws.getName().equalsIgnoreCase(workspace))
    		{
    			return true;
    		}
    	}
    	
        return false;
    }
    
	public void changeWorkspace(String workspace, String stream, String path) throws VcsException
	{
		String[] args = {
						RunProcess.getAccuRevExecutable(),
						"chws",
						"-w", workspace,
						"-b", stream,
						"-l", path
					};
		String command = commandPrefix + "accurev chws -w " + workspace + " -b " + stream + " -l " + path;
		printBuildMessage(command);
		
		String cmdName = "accurev chws";
		int returnCode = doExecute(cmdName, args);
		
		checkReturnCode(cmdName,returnCode);		
		printResultMessages(returnCode);
	}
		
	public void makeWorkspace(String workspace, String stream, String path) throws VcsException
	{

		String[] args = {
						RunProcess.getAccuRevExecutable(),
						"mkws",
						"-w", workspace,
						"-b", stream,
						"-l", path
					};
		String command = commandPrefix + "accurev mkws -w " + workspace + " -b " + stream + " -l " + path;
		printBuildMessage(command);
		
		String cmdName = "accurev mkws";
		int returnCode = doExecute(cmdName, args);
				
		checkReturnCode(cmdName,returnCode);	
		printResultMessages(returnCode);
	}

    public void populateWorkspace(File checkoutDir)
	throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"pop",
				"-R",
				"\\.\\",
		};
		
		String command = commandPrefix + "accurev pop -R \\.\\";
		printBuildMessage(command);
		
		String cmdName = "accurev pop";
		mLastCommand = args; // Need to do this manually as we are calling ThrowIfError directly
		int returnCode = ThrowIfError(cmdName, exec(args, checkoutDir.getAbsolutePath()), null);		
		
		printResultMessages(returnCode);
		checkReturnCode(cmdName, returnCode);
	}

    public void updateWorkspace(File checkoutDir)
	throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"update",
		};
		
		String command = commandPrefix + "accurev update";
		printBuildMessage(command);
		
		String cmdName = "accurev update";
		mLastCommand = args; // Need to do this manually as we are calling ThrowIfError directly
		int returnCode = ThrowIfError(cmdName, exec(args, checkoutDir.getAbsolutePath()), null);		
		
		printResultMessages(returnCode);
		checkReturnCode(cmdName, returnCode);
	}
	
    public GenericXMLParser getTranslist(String stream/*, SRunningBuild runningBuild*/) throws VcsException
    {
        String[] args = {
				RunProcess.getAccuRevExecutable(),
				"translist",
				"-fx",
				"-s", stream,
                "streams"
		};
        
        String command = commandPrefix + "accurev translist -fx -s " + stream + " streams";
        printBuildMessage(command);
        
        GenericXMLParser parser = new GenericXMLParser();
        int returnCode = doExecute("accurev translist", args, parser);
        
        printResultMessages(returnCode);        
        
        if (returnCode == 0)
        {
        	return parser;     	
        }        
        throw new VcsException(getErrorText().toString());
    }
    
    public int promoteStream(String stream, File commentFile , String transactionFileName /*,SRunningBuild runningBuild*/ ) throws VcsException
    {
    	
       // String cmd =  "promote -Fx -Z -c \" [comment] \" -s  [stream]  -l \"[fname]\"" ;
    	String commentArg = "";
    	if(commentFile != null)
    	{
        	commentArg = "-c@" + commentFile.getAbsolutePath();
    	}
    	else
    	{
    		commentArg = "";
    	}

        String[] args = {
				RunProcess.getAccuRevExecutable(),
				"promote",
				"-Fx",
				"-Z",
				commentArg ,
				"-s",
				stream,
				"-l",
				"\"" + transactionFileName + "\""
        };
        String command = commandPrefix + "accurev promote -Fx -Z " + commentArg + " -s " + stream + " -l " + "\"" + transactionFileName + "\"";
        printBuildMessage(command);
        
        int[] acceptableReturnCodes = {0};
        int returnCode = doExecute("accurev promote", args, acceptableReturnCodes);
        
        printResultMessages(returnCode);
        return returnCode;
    }
        
    public int promoteSingleTransaction(String stream, File commentFile , Integer transactionID) throws VcsException
    {
    	
        //String cmd =  "promote -Fx -Z -c \" [comment] \" -s  [stream]  -l \"[fname]\"" ;
    	String commentArg = "";
    	if(commentFile != null)
    	{
        	commentArg = "-c@" + commentFile.getAbsolutePath();
    	}
    	else
    	{
    		commentArg = "";
    	}
    	
        String[] args = {
				RunProcess.getAccuRevExecutable(),
				"promote",
				commentArg,
				"-s",
				stream,
				"-t", transactionID.toString()
				
        };
       
        String command = commandPrefix + "accurev promote " + commentArg + " -s " + stream + " -t" + transactionID.toString();
		printBuildMessage(command);
		
		int[] acceptableReturnCodes = {0};
		int returnCode = doExecute("accurev promote", args, acceptableReturnCodes );
       
		printResultMessages(returnCode);		
		return returnCode;
    }
    
    public void printResultMessages(int returnCode)
    {

		String stderr = getErrorText().toString();
        String stdout = getResponseText().toString();
		
		printBuildMessage("Return Code: " + returnCode);
		printBuildMessage("StdOut     : " + stdout);
		printBuildMessage("StdErr     : " + stderr);
    }
    
	public void printBuildMessage(String message) 
	{
		if (errMessages != null)
		{
			errMessages.add(message);
		}
	}
	
    public void forceTimestampOptimization(File workspaceDir) throws VcsException
    {
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"stat",
                "-n"
		};
		
        String command = commandPrefix + "stat -n";
        printBuildMessage(command);
		
		String cmdName = "accurev stat";		
		mLastCommand = args; // Need to do this manually as we are calling ThrowIfError directly
		int returnCode = ThrowIfError(cmdName, exec(args, workspaceDir.getAbsolutePath()), null);
		
		printResultMessages(returnCode);
		checkReturnCode(cmdName, returnCode);
    }
    
    public String getLastCommand()
    {
    	if(mLastCommand == null)
    	{
    		return "";
    	}
    	
    	String command = "";
    	String separator = "";
    	for(String item : mLastCommand)
    	{
    		command += separator + item;
    		separator = " ";		
    	}
    	
    	return command;
    }
    
    public String getLastResponse()
    {
    	return mLastResponse;
    }
    
    public void populateWorkspaceClean(File checkoutDir)
    		throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"pop",
				"-R",
				"-O",
				"\\.\\",
		};
		
		String command = commandPrefix + "accurev pop -R -O \\.\\";
		printBuildMessage(command);
		
		String cmdName = "accurev pop";
		mLastCommand = args; // Need to do this manually as we are calling ThrowIfError directly
		int returnCode = ThrowIfError(cmdName, exec(args, checkoutDir.getAbsolutePath()), null);		
		
		printResultMessages(returnCode);
		checkReturnCode(cmdName, returnCode);
	}

    public void updateWorkspaceClean(File checkoutDir)
	throws VcsException
	{
		String[] args = {
				RunProcess.getAccuRevExecutable(),
				"update",
				"-9"
		};
		
		String command = commandPrefix + "accurev update";
		printBuildMessage(command);
		
		String cmdName = "accurev update";
		mLastCommand = args; // Need to do this manually as we are calling ThrowIfError directly
		int returnCode = ThrowIfError(cmdName, exec(args, checkoutDir.getAbsolutePath()), null);		
		
		printResultMessages(returnCode);
		checkReturnCode(cmdName, returnCode);
	}
	
    @SuppressWarnings("unchecked")
    public Vector<WorkspaceData> accurevShowReferenceTrees(String depot) throws VcsException
    {
        String[] args = {
				RunProcess.getAccuRevExecutable(),
				"show",
				"-fx",
				"-p", depot,
                "refs"
		};
        
        String command = commandPrefix + "accurev show -fx -p " + depot + " refs";
        printBuildMessage(command);
        
        // Parse accurev response. Use Workspace parser as "show -fx wspaces" and "show -fx refs" have the
        // same structure.
        ShowWorkspaceParser parser = new ShowWorkspaceParser(); 
        int returnCode = doExecute("accurev show refs", args, parser);        
        printResultMessages(returnCode);
        
        // Get the list of ref trees
        Vector<WorkspaceData> wspaces = parser.getWorkspaceShowCollection();

        if (wspaces.isEmpty())
        {
        	// No ref tree
        	return null;
        }
        else
        {
        	return wspaces;
        }
    }
      
    public Boolean doesReferenceTreeExist(String depot, String reftree) throws VcsException
    {
    	Vector<WorkspaceData> reftrees = accurevShowReferenceTrees(depot);
    	if (reftrees == null)
    	{
    		// No ref trees
    		return false;
    	}
    	
        // Search for the required workspace
    	for (WorkspaceData ref : reftrees)
    	{
    		if(ref.getName().equalsIgnoreCase(reftree))
    		{
    			return true;
    		}
    	}
    	
        return false;
    }
    
	public void changeReferenceTree(String reftree, String stream, String path) throws VcsException
	{
		String[] args = {
						RunProcess.getAccuRevExecutable(),
						"chref",
						"-r", reftree,
						"-b", stream,
						"-l", path
					};
		String command = commandPrefix + "accurev chref -r " + reftree + " -b " + stream + " -l " + path;
		printBuildMessage(command);
		
		String cmdName = "accurev chref";
		int returnCode = doExecute(cmdName, args);
		
		checkReturnCode(cmdName,returnCode);		
		printResultMessages(returnCode);
	}
		
	public void makeReferenceTree(String workspace, String stream, String path) throws VcsException
	{

		String[] args = {
						RunProcess.getAccuRevExecutable(),
						"mkref",
						"-r", workspace,
						"-b", stream,
						"-l", path
					};
		String command = commandPrefix + "accurev mkref -r " + workspace + " -b " + stream + " -l " + path;
		printBuildMessage(command);
		
		String cmdName = "accurev mkref";
		int returnCode = doExecute(cmdName, args);
				
		checkReturnCode(cmdName,returnCode);	
		printResultMessages(returnCode);
	}

    private String[] mLastCommand = null;
    private String mLastResponse = null;
}
