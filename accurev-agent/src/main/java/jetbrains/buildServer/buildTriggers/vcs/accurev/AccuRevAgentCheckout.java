package jetbrains.buildServer.buildTriggers.vcs.accurev;

import java.io.File;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.buildTriggers.vcs.accurev.command.AcRunProcess;
import jetbrains.buildServer.buildTriggers.vcs.accurev.command.AcSpecialLogin;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

import com.accurev.common.data.XMLTag;

/**
 * Class that implements a client side checkout.
 * 
 * @author skulczyc
 */

public class AccuRevAgentCheckout
{
	/**
	 * Ctor. Sets up some config stuff used elsewhere.
	 */
	public AccuRevAgentCheckout(BuildProgressLogger logger, File workingDir, VcsRoot vcsRoot, String version, AgentRunningBuild currentBuild)
		throws VcsException
	{
		mLogger = logger;
        mWorkingDir = workingDir;
        mVersion = version;
        
        mCurrentBuild = currentBuild;
		
		mSettings = new Settings(workingDir, vcsRoot);
		mSettings.setWorkingDir(mWorkingDir);
		
        mRunProcess = AcRunProcess.getInstance(mSettings, mWorkingDir);

        // Find some names for later use
        mDepotName = vcsRoot.getProperty(Constants.DEPOT);
        mParentStreamName = mSettings.getPopulateStream();            
        try
        {
            mStreamName =  InetAddress.getLocalHost().getHostName().toLowerCase();            
            mStreamName += "_" + mWorkingDir.getName();            
            mWorkspaceName = mStreamName;
            
            mWorkspaceName += "_" + mSettings.getUsername();
            mStreamName += "_timelock";
        }
        catch (UnknownHostException e)
        {
            throw new VcsException("The host name of agent computer could not be obtained", e);
        }        

        // Precalculate the checkout dir
        mCheckoutDir = mWorkingDir.toString();
        if (mSettings.getSubDirectory() != null)
        {
        	mCheckoutDir += File.separatorChar + mSettings.getSubDirectory();
        }
	}
	
	/**
	 * Logs into accurev.
	 */
	public void login()
		throws VcsException
	{
        AcSpecialLogin specialLogin = new AcSpecialLogin(mSettings);
        specialLogin.login();
	}
	
	/**
	 * Checks if there are overlaps and reports them. Skips if the parent stream is timlocked
	 * as accurev has an annoying bug that also reports overlaps for differences between a
	 * timelocked stream and its parent.
	 */
	public void detectOverlaps()
		throws VcsException
	{
        // Check if the parent stream is timelocked
        mLogger.progressStarted("Checking for overlaps");
        XMLTag streamInfo = mRunProcess.accurevShow(mDepotName, mParentStreamName);
        logLastCommand();
        if(streamInfo == null)
        {
            // Parent stream does not exist, shoot exception
            throw new VcsException("Parent stream does no exist");
        }
        if(streamInfo.getAttributeValue("time") != null)
        {
            // Report overlaps on the parent stream
            List<XMLTag> overlaps = mRunProcess.getStreamOverlaps(mParentStreamName).getTags("element"); 
            logLastCommand();
            if(!overlaps.isEmpty())
            {
                // Overlaps present, report them
                mLogger.error("Overlaps detected on selected stream:");
                for(XMLTag tag : overlaps)
                {
                    mLogger.error("Overlap on file: " + tag.getAttributeValue("location"));
                }
                
                // Check whether we should fail the whole build
                if(mSettings.getFailOnOverlap())
                {
                    throw new VcsException("Overlaps detected");
                }
            }
        }
        else
        {
        	mLogger.message("Stream is timelocked, skipping overlap check");
        }
        mLogger.progressFinished();
	}
	
	/**
	 * Sets up the stream for the checkout. Since accurev does not have a simple way of
	 * syncing to a specific transaction, we create a new stream under the tested one and
	 * timelock it to the transaction. If a stream for this machine in this configuration
	 * already exists, it reuses that one.
	 */
	public void setupStreams()
		throws VcsException
	{
        // Setup the stream
        if (mRunProcess.doesStreamExist(mDepotName, mStreamName))
        {
            // Stream exists therefore update it
            logLastCommand();
            mLogger.progressStarted("Changing existing timelocked stream");
            mRunProcess.changeStream(mStreamName, mParentStreamName, mVersion);    
            logLastCommand();
            mLogger.progressFinished();
        }
        else
        {
            // The stream does not exist so we have to make it.
            logLastCommand();
        	mLogger.progressStarted("Creating new stream");
            mRunProcess.makeStream(mStreamName, mParentStreamName, mVersion); //throws exception on error
            logLastCommand();
            mLogger.progressFinished();
        }
	}
	
	/**
	 * Sets up the workspace to perform the checkout. If a workspace for this machine in this
	 * configuration for this user already exists, it reuses it. Also checks that the checkout
	 * directory exists and if a clean checkout is required.
	 */
	public void setupWorkspace()
		throws VcsException
	{
        // If we have a subdir specified for the workspace, make sure it exists. Do this before
        // cleaning as the clean assumes the subdir exists and does not delete it.
        if (mSettings.getSubDirectory() != null)
        {
            File f = new File(mCheckoutDir);
            if(!f.exists())
            {
                if (!f.mkdir())
                {
                    throw new VcsException("Unable to create Directory " + mCheckoutDir);
                }
                else
                {
                    mLogger.message("Subdirectory created");
                }
            }
        }
                
        // Check if a clean is required
        if(isSystemPropertySet(Constants.CLEAN))
        {
            // If we have a subdir, clean that instead
            String dirPathToClean = mWorkingDir.toString();
            if(mSettings.getSubDirectory() != null)
            {
                dirPathToClean += File.separatorChar + mSettings.getSubDirectory();
            }
            File dirToClean = new File(dirPathToClean);
            
            mLogger.progressStarted("Cleaning checkout directory");
            
            // Delete contents but keep the directory
            File[] files = dirToClean.listFiles();
            for(File f : files)
            {
                fileSystemDelete(f, mLogger);
            }            
            
            mLogger.progressFinished();
        }
        
        // Check whether to use a workspace of a ref tree (the checkout directory should change as
        // the property hash is different).
        if(!mSettings.getUseRefTree())
        {
	        // Setup the workspace
	        if (mRunProcess.doesWorkspaceExist(mDepotName, mWorkspaceName))
	        {
	            // The workspace exists, just make sure it is configured correctly
	            logLastCommand();
	            mLogger.progressStarted("Changing existing workspace");
	            mRunProcess.changeWorkspace(mWorkspaceName, mStreamName, mCheckoutDir);
	            logLastCommand();
	            mLogger.progressFinished();
	        }
	        else
	        {
	            // Workspace does not exist, create it
	            logLastCommand();
	            mLogger.progressStarted("Creating new workspace");
	            mRunProcess.makeWorkspace(mWorkspaceName, mStreamName, mCheckoutDir);
	            logLastCommand();
	            mLogger.progressFinished();
	        }
        }
        else
        {
	        // Setup the workspace
	        if (mRunProcess.doesReferenceTreeExist(mDepotName, mWorkspaceName))
	        {
	            // The workspace exists, just make sure it is configured correctly
	            logLastCommand();
	            mLogger.progressStarted("Changing existing reference tree");
	            mRunProcess.changeReferenceTree(mWorkspaceName, mStreamName, mCheckoutDir);
	            logLastCommand();
	            mLogger.progressFinished();
	        }
	        else
	        {
	            // Workspace does not exist, create it
	            logLastCommand();
	            mLogger.progressStarted("Creating new referecnce tree");
	            mRunProcess.makeReferenceTree(mWorkspaceName, mStreamName, mCheckoutDir);
	            logLastCommand();
	            mLogger.progressFinished();
	        }
        }
    }
	
	/**
	 * Performs accurev update, populate and optionally a "stat -n" to use the timestamp optimization
	 * and speed up future updates.
	 */
	public void syncWorkspace()
		throws VcsException
	{
        File checkoutDir = new File(mCheckoutDir);
		
		// If it was a clean build perfrom quicker update and populate
		if(mCurrentBuild.isCleanBuild() || isSystemPropertySet(Constants.CLEAN))
		{
			// Update
			mLogger.progressStarted("Performing accurev update for clean build");
	        mRunProcess.updateWorkspaceClean(checkoutDir);
	        logLastCommand();
	        mLogger.progressFinished();
	
	        // Populate
			mLogger.progressStarted("Performing accurev populate for clean build");
			mRunProcess.populateWorkspaceClean(checkoutDir);
	        logLastCommand();
	        mLogger.progressFinished();
		}
	    else
	    {
	        // Update
			mLogger.progressStarted("Performing accurev update");
	        mRunProcess.updateWorkspace(checkoutDir);
	        logLastCommand();
	        mLogger.progressFinished();
	
	        // Populate
			mLogger.progressStarted("Performing accurev populate");
			mRunProcess.populateWorkspace(checkoutDir);
	        logLastCommand();
	        mLogger.progressFinished();
	    }
        
        // Perform an accurev stat -n so that the timestamp optimization in accurev
        // triggers immediately after clean builds and not on the build after it
        mLogger.progressStarted("Forcing timestamp optimization");
        mRunProcess.forceTimestampOptimization(new File(mCheckoutDir));
        logLastCommand();
        mLogger.progressFinished();
	}
	
    /**
     * Deletes file and folders recursively. Warns if somethong cannot be deleted.
     */
    private boolean fileSystemDelete(File dir, BuildProgressLogger logger)
    {
        boolean canDeleteCurrent = true;
        
        // If directory need to process contents
        if (dir.isDirectory())
        {
            File[] contents = dir.listFiles();
            for (File item : contents)
            {
                // Recursive call
                boolean deleted = fileSystemDelete(item, logger);
                
                // Check if delete was successful
                if(!deleted)
                {
                    logger.warning("Could not delete " + item.getAbsolutePath());
                    canDeleteCurrent = false;
                }
            }
        }
    
        // If there was an error deleting contents the directory cannot be deleted
        if(canDeleteCurrent)
        {
            return dir.delete();
        }
        else
        {
            return false;
        }
    }
    
    /**
     * Check if an flag-type system property is set.
     */
    public boolean isSystemPropertySet(String name)
    {
        String value = mCurrentBuild.getBuildParameters().getSystemProperties().get(name);
        boolean isSet = value != null && (value.equals("1") || value.toLowerCase().equals("true") || value.toLowerCase().equals("on"));

        return isSet;
    }

	/**
	 * Logs the last accurev command.
	 */
    private void logLastCommand()
    {
        // Check if verbose enough to log command
    	if(mSettings.getVerbosity() >= 1 && mRunProcess.getLastCommand() != null)
    	{
    		mLogger.message("Command: " + mRunProcess.getLastCommand());
    	}

        // Check if verbose enough to log command output
    	if(mSettings.getVerbosity() >= 2 && mRunProcess.getResponseText().length() > 0)
    	{
    		mLogger.message("Output: \n" + mRunProcess.getResponseText());
    	}
    }
    
    
    private BuildProgressLogger mLogger;
	private File mWorkingDir;
	private String mVersion;

	private String mCheckoutDir;
	
	private Settings mSettings;
	private AcRunProcess mRunProcess;

    private String mDepotName;
    private String mParentStreamName;            
    private String mStreamName;
    private String mWorkspaceName;
    
    private AgentRunningBuild mCurrentBuild;
}
