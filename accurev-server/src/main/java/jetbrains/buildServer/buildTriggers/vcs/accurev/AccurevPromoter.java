package jetbrains.buildServer.buildTriggers.vcs.accurev;

import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.ArrayList;
import java.util.UUID;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import jetbrains.buildServer.serverSide.BuildServerAdapter;
import jetbrains.buildServer.serverSide.SRunningBuild;
import jetbrains.buildServer.serverSide.BuildRevision;
import jetbrains.buildServer.buildTriggers.vcs.accurev.command.AcRunProcess;
import jetbrains.buildServer.messages.Status;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.messages.DefaultMessagesInfo;
import jetbrains.buildServer.messages.BuildMessage1;

import com.accurev.common.parsers.GenericXMLParser;
import com.accurev.common.data.XMLTag;

import jetbrains.buildServer.vcs.VcsRoot;


public class AccurevPromoter extends BuildServerAdapter 
{

	
	@SuppressWarnings("deprecation")
	public void beforeBuildFinish(SRunningBuild runningBuild, boolean buildFailed) 
	{
		if( buildFailed || runningBuild.getBuildStatus() != Status.NORMAL)
		{	
			return;
		}
		
		List<String> errMessages = new ArrayList<String>();
		HashMap<VcsRoot, Integer> highestRevisions = getHighestRevisionPerVcsNode(runningBuild.getRevisions());
		
		for(Map.Entry<VcsRoot, Integer> entry : highestRevisions.entrySet())
		{
			try
			{
				VcsRoot rootNode   = entry.getKey();
				Settings settings  = new Settings(null,rootNode);
				if(settings.getPromoteOnSuccess())
				{
					boolean promoteSeparately = settings.getPromoteSeparately();
					Integer revisionID = entry.getValue();
						 
					boolean promoteSuccessful = promoteStream(rootNode,revisionID, runningBuild,promoteSeparately, errMessages);
					if(promoteSuccessful)
					{
						BuildMessage1 message = DefaultMessagesInfo.createTextMessage("Successfuly promoted changes in stream " + settings.getWatchStream() + " up to transaction " + revisionID, Status.NORMAL);
						runningBuild.addBuildMessage(message);
					}
					else
					{
						BuildMessage1 message = DefaultMessagesInfo.createTextMessage("Unable to promote because there are no changes in stream " + settings.getWatchStream(), Status.NORMAL);
						runningBuild.addBuildMessage(message);
					}
				}				
			}
			catch(VcsException e)
			{
				runningBuild.setBuildStatus(Status.FAILURE);
				String errorMessages = "";
				for(String msg: errMessages)
				{
					errorMessages += "\n" + msg;
				}
				BuildMessage1 message = DefaultMessagesInfo.createError("Accurev Promote Failure:" + e.getMessage() + "\n" + errorMessages, "", Status.FAILURE);
				runningBuild.addBuildMessage(message);
			}			
		}	
	}

	
	
	@SuppressWarnings("unchecked")
	public boolean promoteStream(VcsRoot vcsNode, Integer maxTransactionID,  SRunningBuild runningBuild, boolean promoteSeparately, List<String> errMessages) throws VcsException
	{

		Settings settings = new Settings(null, vcsNode);
		AcRunProcess cmd  = AcRunProcess.getInstance(settings, null); 
		cmd.setErrMessagesList(errMessages);
		
		String streamName = settings.getWatchStream();
		if(streamName == null)
		{
			throw new VcsException("The watch stream is empty");
		}
		
		/*
		 * Translist returns only those transactions which have not yet been promoted.
		 * We need it to find lowest transaction remaining.
		 */
    	GenericXMLParser translistParsar 	= cmd.getTranslist(streamName /*, runningBuild*/);
    	XMLTag translistRootElement 		= (XMLTag) translistParsar.getTagList().get(0);
    	List<XMLTag> pendingTransactions 	= translistRootElement.getTags();
    	
    	if(pendingTransactions != null && pendingTransactions.size() == 0)//TODO: check what happens if transactions == 0
    	{
    		printBuildMessage("WARNING:accurev TransList returned no transactions. No promotes can be made",runningBuild);
    		return false;//no more transactions in this stream.
    	}
        
    	//############################################################################################################	
    	int transactionCount  = 0;
    	Integer lowestTrans   = Integer.MAX_VALUE;
    	for(XMLTag transactionTag: pendingTransactions)
    	{
        	String tagName = transactionTag.getName();
        	if(tagName != "transaction")
        	{
        		continue;
        	}
        	
        	transactionCount++;
        	String transActionID = transactionTag.getAttributeValue("id");
        	Integer ID 			 = new Integer(transActionID);
        	
        	if(ID < lowestTrans)
        	{
        		lowestTrans = ID;
        	}	        	
    	}
    	
    	if(maxTransactionID < lowestTrans || transactionCount == 0)
    	{
    		printBuildMessage("WARNING: The lowest transaction returned by translist is " + lowestTrans + " But the highest transaction for this build is " + maxTransactionID + ". No promotes can be made",runningBuild);
    		return false;//Build has already been promoted.
    	}
    	
    	//############################################################################################################     	
        GenericXMLParser histParsar 		=  cmd.getAsXMLRevisionsBetween(settings.getDepot(),  streamName, lowestTrans.toString(), maxTransactionID.toString());
        List<XMLTag> histTransactionList 	= (List<XMLTag> )histParsar.getTagList();
        XMLTag histRootElement 				= histTransactionList.get(0);
        histTransactionList 				= histRootElement.getTags();	
    	
        if(promoteSeparately)
        {
        	String tempDirPath = System.getProperty("java.io.tmpdir");
        	Collections.reverse(histTransactionList);//the accurev hist command gives the transactions in ascening order but we need them in descending order.
        	for (XMLTag tag: histTransactionList)
	        {
	        	String tagName = tag.getName();
	        	if(tagName != "transaction")
	        	{
	        		continue;
	        	}
	        	String transActionID 	= tag.getAttributeValue("id");
	        	String userID 			= tag.getAttributeValue("user");
	        	XMLTag commentTag 		= tag.getTag("comment");
	        	String comment 			= userID;
	        	if(commentTag != null)
	        	{
	        		comment += ":" + commentTag.getContent();
	        	}
	        	
	        	try
	        	{
		        	String commentFilePath = tempDirPath + File.separator + "Comments_" + UUID.randomUUID().toString() + ".txt";
		        	commentFilePath	 = new File(commentFilePath).getAbsolutePath();
		        	
	        		FileWriter commentFileWriter = new FileWriter(commentFilePath);
	        		commentFileWriter.write(comment);
	        		commentFileWriter.close();
	        		File commentFile = new File(commentFilePath);
	        		        		
	        		String msgAttempt = "Attempting to promote transaction: " + transActionID;

	        		errMessages.add( msgAttempt);
	        		errMessages.add("Comment File: " + commentFilePath);
	        		errMessages.add("Comment: " + comment );        		
					
	        		printBuildMessage(msgAttempt,runningBuild);
		        	cmd.promoteSingleTransaction(streamName, commentFile, new Integer(transActionID));
		        	
	        		String msgSuccess = "Transaction " + transActionID + " Successfully promoted";
	        		printBuildMessage(msgSuccess,runningBuild);
	        		errMessages.add(msgSuccess);

	        	}
	            catch(IOException e)
	            {        	
	            	throw new VcsException("IO Exception " + e.getMessage(), e);
	            }

	        }       				
	        

	      //############################################################################################################
        }
        else
        {
        	//############################################################################################################
            String transactionsToPromote = getTransactionsToPromoteXML(pendingTransactions, maxTransactionID); 
            String tempDirPath			 = System.getProperty("java.io.tmpdir");
            String transactionFilePath 	 = tempDirPath + File.separator + "AccurevTransactions_" + streamName + "_" + maxTransactionID + "_" + UUID.randomUUID().toString() + ".txt";
            
            try
            {
                FileWriter transactionFileWriter = new FileWriter(transactionFilePath);
                transactionFileWriter.write(transactionsToPromote);
                transactionFileWriter.close();
                
                BuildMessage1 message = DefaultMessagesInfo.createProgressMessage("Promoting " + vcsNode.getName());        
        		runningBuild.addBuildMessage(message);   			
        		
        		//throws an exception in case of error.
        		String comments = getTransactionComments(histTransactionList, maxTransactionID);
        		
        		String commentFilePath = tempDirPath + File.separator +  "Comments_" + UUID.randomUUID().toString() + ".txt";
        		commentFilePath  = new File(commentFilePath).getAbsolutePath();
        		
        		//the comment might contain new-lines-characters so we have to write it to file. 
        		FileWriter commentFileWriter = new FileWriter(commentFilePath);
        		commentFileWriter.write(comments);
        		commentFileWriter.close();
        		File commentFile = new File(commentFilePath);
        		
        		
        		//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++     
        		String msgAttempt = "Attempting to promote transactions:\n" + transactionsToPromote ;


        		errMessages.add(msgAttempt 	  								);        		
        		errMessages.add("Transaction File : " + transactionFilePath );        		
        		errMessages.add("Comment File: " + commentFilePath 			);
        		errMessages.add("Comment:\n" + comments 					);	        		
				//++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++++
        		
        		printBuildMessage(msgAttempt , runningBuild);
                cmd.promoteStream(streamName , commentFile, transactionFilePath/*, runningBuild*/);
                
        		String msgSuccess = "Transactions promoted Successfully";
                printBuildMessage(msgSuccess , runningBuild);                
                errMessages.add(msgSuccess);
                
                commentFile.delete();
                File transactionsFile = new File(transactionFilePath);
                transactionsFile.delete();
                               
            }
            catch(IOException e)
            {        	
            	throw new VcsException("IO Exception " + e.getMessage(), e);
            }
          //############################################################################################################
        }      
        return true;        
	}
	
	
	
	public HashMap<VcsRoot,Integer> getHighestRevisionPerVcsNode( List<BuildRevision> allRevisions)
	{
		HashMap<VcsRoot,Integer> result = new HashMap<VcsRoot, Integer>();
		for(BuildRevision rev : allRevisions)
		{
			VcsRoot vcsRootNode =  rev.getRoot();
			String revisionIDString = rev.getRevision();
			Integer revisionID = new Integer(revisionIDString);
			if(result.containsKey(vcsRootNode))
			{
				Integer value = result.get(vcsRootNode);
				if(revisionID > value)
				{
					result.put(vcsRootNode, revisionID);
				}			
			}
			else
			{
				result.put(vcsRootNode, revisionID);
			}
		}
		return result;
	}
	
	
	
	public String getTransactionsToPromoteXML(List<XMLTag> pendingTransactions, int maxTransactionID ) throws VcsException
	{
     
        String outputText = "<transactions>\n";
        for(XMLTag tag :pendingTransactions)
        {
        	String elementName 		= tag.getName();
        	String id 				= tag.getAttributeValue("id");        	
        	Integer transactionId 	= new Integer(id);
        	
        	
        	if(elementName.equals("transaction") && transactionId <= maxTransactionID)
        	{
        		outputText += "\t<id>" + id + "</id>\n";
        	}
        }
        outputText += "</transactions>";
        return outputText;
	}
	

	
	public String getTransactionComments(List<XMLTag> histTransactions,int maxTransactionID)
	{
      
        String comments = "";
        for(XMLTag tag : histTransactions)
        {
        	if(!tag.getName().equals("transaction"))
        	{
        		continue;
        	}
        	
        	String transID 			= tag.getAttributeValue("id");
        	Integer transactionID 	= new Integer(transID);
        	if(transactionID <= maxTransactionID)
        	{
            	String userID 		= tag.getAttributeValue("user");
            	XMLTag commentTag 	= tag.getTag("comment");
            	String transactionComment = "";
            	if(commentTag != null)
            	{
            		transactionComment = commentTag.getContent();
            	}
            	comments += userID + ":" + transactionComment + "\n";
        	}
        }
        return comments;
	}
	
	
	

	
	public void printBuildMessage(String message, SRunningBuild runningBuild)
	{
		BuildMessage1 buildMessage = DefaultMessagesInfo.createTextMessage( message , Status.NORMAL);
		runningBuild.addBuildMessage(buildMessage);
	}
	
	public void println(String str)
	{
		System.out.println(str);
	}
}




