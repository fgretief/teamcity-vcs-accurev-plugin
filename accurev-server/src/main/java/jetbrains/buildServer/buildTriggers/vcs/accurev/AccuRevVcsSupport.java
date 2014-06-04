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
package jetbrains.buildServer.buildTriggers.vcs.accurev;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import jetbrains.buildServer.buildTriggers.vcs.accurev.command.*;
import jetbrains.buildServer.buildTriggers.vcs.AbstractVcsPropertiesProcessor;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.serverSide.InvalidProperty;
import jetbrains.buildServer.serverSide.PropertiesProcessor;
import jetbrains.buildServer.serverSide.BuildServerListener;
import jetbrains.buildServer.buildTriggers.vcs.accurev.command.AcSpecialLogin;
import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.*;
import jetbrains.buildServer.vcs.patches.PatchBuilder;
import jetbrains.buildServer.AgentSideCheckoutAbility;
import jetbrains.buildServer.Used;
import jetbrains.buildServer.serverSide.SBuildServer;
import jetbrains.buildServer.serverSide.ServerPaths;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.*;

import com.accurev.common.data.*;
import com.accurev.common.parsers.HistoryParser;
import com.accurev.common.process.*;
import com.accurev.common.utils.*;

import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;


// TODO 18SEP09 DCN setSessionToken and login lines are all commented out due to authentication failure

/**
 * @author Francois Retief
 */
@SuppressWarnings("deprecation")
public class AccuRevVcsSupport 
	extends
		ServerVcsSupport
	implements
		VcsSupportConfig,
		AgentSideCheckoutAbility,
		LabelingSupport,
		CurrentVersionIsExpensiveVcsSupport,
		CollectSingleStateChangesByCheckoutRules,
		BuildPatchByCheckoutRules,
		VcsFileContentProvider,
		TestConnectionSupport
{
	//private VcsManager acVcsManager;
	private File acDefaultWorkFolderParent;

	public AccuRevVcsSupport(
			@NotNull final VcsManager vcsManager,
			@NotNull ServerPaths paths,
			@NotNull SBuildServer server,
			EventDispatcher<BuildServerListener> eventDispatcher)
	{
	    //acVcsManager = vcsManager;
		acDefaultWorkFolderParent = new File(paths.getCachesDir());
		
		AccurevPromoter promoter = new AccurevPromoter();
		eventDispatcher.addListener(promoter);
	}
	
	@NotNull
	public VcsFileContentProvider getContentProvider() 
	{
		return this;
	}

	@NotNull
	public CollectChangesPolicy getCollectChangesPolicy() 
	{
		return this;
	}
	
	@NotNull
	public BuildPatchByCheckoutRules getBuildPatchPolicy() 
	{
		return this;
	}
	
	public TestConnectionSupport getTestConnectionSupport() {
		return ((isTestConnectionSupported()) ? this : null);
	}
	
	private Settings createSettings(final VcsRoot root) throws VcsException
	{
		return new Settings(acDefaultWorkFolderParent, root);
	}

    @NotNull
    public String getName()
    {
        return Constants.VCS_NAME;
    }

    @Used("jsp")
    public String getDisplayName()
    {
        return "AccuRev";
    }

    public String getVcsSettingsJspFilePath()
    {
        return "accurevSettings.jsp";
    }

    @Nullable
    public Map<String, String> getDefaultVcsProperties()
    {
    	Map<String, String> properties = new HashMap<String, String>();
    	properties.put(Constants.SERVER_PORT, "5050");
        properties.put(Constants.VERBOSITY, "1");
        properties.put(Constants.USE_REF_TREE, "true");

    	// Get the default server settings from the acclient file.
    	String currentServer = SecurityUtils.getCurrentServer();
    	if (currentServer != null)
    	{
    		String[] sp = currentServer.split(":");
    		if (sp.length > 0)
    			properties.put(Constants.SERVER_NAME, sp[0]);
    		if (sp.length > 1)
    			properties.put(Constants.SERVER_PORT, sp[1]);
    	}

        properties.put(Constants.COMMAND_DIR, Settings.getDefaultAccuRevDir());

    	return properties;
    }

    @Nullable
    public PropertiesProcessor getVcsPropertiesProcessor()
    {
        return new AbstractVcsPropertiesProcessor() {
            
        	public Collection<InvalidProperty> process(final Map<String, String> properties) 
            {
                List<InvalidProperty> result = new ArrayList<InvalidProperty>();
                
                if (isEmpty(properties.get(Constants.DEPOT))) {
                    result.add(new InvalidProperty(Constants.DEPOT, "Depot must be specified"));
                }

                if (isEmpty(properties.get(Constants.WATCHSTREAM))) {
                    result.add(new InvalidProperty(Constants.WATCHSTREAM, "Stream must be specified"));
                }

                //----------------------------------------------------------------------------------
                
                if (isEmpty(properties.get(Constants.USERNAME))) {
                    result.add(new InvalidProperty(Constants.USERNAME, "User name must be specified"));
                }
                
                //Password property is not checked because accurev allows for Null passwords
                
                if (isEmpty(properties.get(Constants.SERVER_NAME)))
                {
                	result.add(new InvalidProperty(Constants.USERNAME, "Server name must be specified"));
                }
                
                
                checkPortNumberProperty(Constants.SERVER_PORT, properties.get(Constants.SERVER_PORT), result);
                checkDirectoryProperty(Constants.COMMAND_DIR, properties.get(Constants.COMMAND_DIR), result);
                           
                
                if(result.size() > 0)
                {
                	return result;//something has already gone wrong and it is not worth continuing.
                }
                
                Settings settings  			= new Settings(acDefaultWorkFolderParent, properties);
                //-------------------------------------------------------------------------------------
                //check that the username and password are valid
                AcSpecialLogin specialLogin = null;
				try {
					specialLogin = new AcSpecialLogin(settings);
	                specialLogin.login();
				} catch (VcsException e) {
					
					String message = e.getMessage();
					if(message.indexOf("Failed authentication") > 0)
					{
						String errorMsg = "Username and/or password incorrect";
						result.add(new InvalidProperty(Constants.USERNAME, errorMsg));
						result.add(new InvalidProperty(Constants.PASSWORD, errorMsg));
					}
					else
					{
						String errorMsg = "An error occured whilst testing login credentials:";
						errorMsg += "stdErr:" + specialLogin.getStdErr() + " : ";
						errorMsg += "stdOut:" + specialLogin.getStdOut() + " : ";
						result.add(new InvalidProperty(Constants.USERNAME, errorMsg));
						result.add(new InvalidProperty(Constants.PASSWORD, errorMsg));
					}
				}

                if(result.size() > 0)
                {
                	return result;//we are unable to log in, there is no point in proceeding.
                }
                
                
				//-----------------------------------------------------------------------------------
				//check that WATCHSTREAM exists and is not a passthrough stream
				try
				{
					
					AcRunProcess cmd = AcRunProcessExe.getInstance(settings, acDefaultWorkFolderParent);
					
					boolean watchStreamExists = cmd.doesStreamExist(settings.getDepot(), settings.getWatchStream());
					if(!watchStreamExists)
					{
						result.add(new InvalidProperty(Constants.WATCHSTREAM, "The selected Stream does not exist "));
					}
					boolean isPassthroughStream = cmd.isPassThroughStream(settings.getDepot(), settings.getWatchStream());
					if(isPassthroughStream)
					{
						result.add(new InvalidProperty(Constants.WATCHSTREAM, "Passthrough streams cannot be used as watch stream"));
					}
				}
				catch (VcsException e) 
				{
					result.add(new InvalidProperty(Constants.WATCHSTREAM, "An Exception was encountered whilst validating the watchstream: " + e.getMessage()));
				}

                //----------------------------------------------------------------------------------
                //check that POPULATESTREAM exists
				try
				{
					AcRunProcess cmd = AcRunProcessExe.getInstance(settings, acDefaultWorkFolderParent);					
					boolean populateStreamExists = cmd.doesStreamExist(settings.getDepot(), settings.getPopulateStream());
					if(!populateStreamExists)
					{
						result.add(new InvalidProperty(Constants.POPULATESTREAM, "The selected Stream does not exist "));
					}

				}catch(VcsException e)
				{
					result.add(new InvalidProperty(Constants.POPULATESTREAM, "An Exception was encountered whilst validating the populatestream: " + e.getMessage()));
				}
                
                
                return result;
            }
        };
    }

    public String getVersionDisplayName(final String version, final VcsRoot root) throws VcsException
    {
        return version;
    }

    @NotNull
    public Comparator<String> getVersionComparator()
    {
        return new Comparator<String>() {
            public int compare(final String o1, final String o2) {
            	long tx1 = Long.parseLong(o1);
            	long tx2 = Long.parseLong(o2);
            	return Long.signum(tx1 - tx2);
            }
        };
    }

    @NotNull
    public String getCurrentVersion(final VcsRoot root) throws VcsException
    {
        // TODO DCN 18SEP09 Not exactly sure what we're supposed to return here, given that we're not supposed to implement this with "expensive" mode.
    	Settings settings = createSettings(root);
    	
    	AcRunProcess cmd = AcRunProcessExe.getInstance(settings, acDefaultWorkFolderParent);
    	
    	return cmd.getLastTransactionId(settings.getDepot());
    }

    public String describeVcsRoot(final VcsRoot vcsRoot)
    {
    	String depot = vcsRoot.getProperty(Constants.DEPOT);
    	String stream = vcsRoot.getProperty(Constants.WATCHSTREAM);
    	String describeRoot = "AccuRev:" + depot + "/" + stream;

    	return describeRoot;
    }

    private String fixSnapshotName(final String label)
    {
    	if (label.length() <= 0)
    		return label;

        // According to the AccuRev CLI user's guide (page 5):
    	// - the snapshot name has a maximum length of 79 characters
    	// - can ONLY contain A-Z,a-z,0-9,[-_.+@] and SPACE
    	// - cannot start with a digit or '.'
    	// - cannot contain forward-slash or back-slash characters

    	String symbols = "-_.+@ ";
    	StringBuilder sb = new StringBuilder();
    	int labelLength = (label.length() < 79) ? label.length() : 79;
    	for (int i = 0; i < labelLength; ++i)
    	{
    		char c = label.charAt(i);
    		if (Character.isLetterOrDigit(c) || symbols.indexOf((int)c) > -1)
			{
    			sb.append(c);
			}
    		else
    		{
    			// All other characters gets replaced with '_'
    			sb.append('_');
    		}
    	}
    	if (sb.charAt(0) == '.')
    		sb.setCharAt(0, '_');
    	if (Character.isDigit(sb.charAt(0)))
    		sb.insert(0, '_');
        return sb.toString();
    }

    public String label(@NotNull String label, @NotNull String version, @NotNull VcsRoot root, @NotNull CheckoutRules checkoutRules) throws VcsException
    {
    	Loggers.VCS.info("label stream: '" + label + "', " + version);
    	Settings settings = createSettings(root);
    	String snapshotName = fixSnapshotName(label);
    	AcRunProcess cmd = AcRunProcessExe.getInstance(settings, acDefaultWorkFolderParent);
    	cmd.createSnapshot(snapshotName, settings.getWatchStream(), version);
    	return snapshotName;
    }

    @Override
    public LabelingSupport getLabelingSupport() {
    	return this;
    }

    public boolean ignoreServerCachesFor(VcsRoot root)
    {
    	return false;
    }

    public boolean sourcesUpdatePossibleIfChangesNotFound(VcsRoot root)
    {
    	boolean base = true; //super.sourcesUpdatePossibleIfChangesNotFound(root);
    	Loggers.VCS.info("sourcesUpdatePossibleIfChangesNotFound: value = " + base);
    	return true;
    }

	public boolean isAgentSideCheckoutAvailable()
	{
		return true;
	}

    public boolean isTestConnectionSupported()
    {
        return true;
    }

    public boolean isCurrentVersionExpensive()
    {
        return true;
    }

    @Nullable
    public String testConnection(final VcsRoot vcsRoot) throws VcsException
    {
    	Settings settings = createSettings(vcsRoot);

    	String cmdPath = "";
    	cmdPath = settings.getExecutablePath().getAbsolutePath();
		
        int status = RunProcess.checkAccuRevExecutable(cmdPath);
        if (status != RunProcess.ERR_SUCCESS)
        {
        	final String message = "Unable to find the AccuRev executable. " +
        		"(" + CommandUtil.errorToString(status) + ")" +
        		"\n" + cmdPath;

        	Loggers.VCS.warn(message);
            throw new VcsException(message);
        }

        RunProcess.setAccuRevExecutable(cmdPath);
        Loggers.VCS.info("Executable: " + RunProcess.getAccuRevExecutable());
        
        AcSecurityProcess sec = new AcSecurityProcess();
        /*
        //DON'T REPLACE WITH AcRunProcess.getInstance(). This is a special method.
        sec.setSessionToken(new SessionToken(settings.getServerName() + ":" + settings.getServerPort(), "", settings.getUsername()));
      
        sec.Logout(); // Ensure we are logged out, to verify login details
        String token = sec.Login(settings.getUsername(), settings.getPassword());
        sec.setSessionToken(new SessionToken(settings.getServerName() + ":" + settings.getServerPort(), token, settings.getUsername()));
		*/
        int[] ver = sec.getAccuRevServerVersion();
        StringBuilder vb = new StringBuilder();
        for (int i = 0; i < ver.length; ++i)
        {
        	if (i > 0) vb.append(".");
        	vb.append(ver[i]);
        }
/*        
        if ((ver[0] == 4 && ver[1] < 7) || (ver[0] < 4))
        {
        	final String message = "AccuRev Server should be at least v4.7.0 or later, but found v" + vb.toString();
        	throw new VcsException(message);
        }
 */        
        Loggers.VCS.info("AccuRev Server version: " + vb.toString());

		AcRunProcess cmd = new AcRunProcessExe(acDefaultWorkFolderParent);
		//cmd.setSessionToken(new SessionToken(settings.getServerName() + ":" + settings.getServerPort(), token, settings.getUsername()));
		if (!cmd.doesStreamExist(settings.getDepot(), settings.getWatchStream()))
		{
			throw new VcsException("Stream cannot be found.");
		}
		else if(cmd.isPassThroughStream(settings.getDepot(), settings.getWatchStream()))
		{
			throw new VcsException("The specified watch stream is a passthrough stream. This is not allowed");
		}
		
        System.out.printf("Test Succes: %s\n", settings.getWatchStream());
		return null; // test connection success
    }

	public List<ModificationData> collectChanges(@NotNull VcsRoot root,
			@NotNull String fromVersion, 
			@Nullable String currentVersion,
			@NotNull CheckoutRules checkoutRules) throws VcsException {
		assert ((currentVersion != null) || (isCurrentVersionExpensive()));
		return ((currentVersion != null) 
				? collectBuildChanges(root, fromVersion, currentVersion, checkoutRules)
				: ((CurrentVersionIsExpensiveVcsSupport) this).collectBuildChanges(root, fromVersion, checkoutRules));
	}

    public List<ModificationData> collectBuildChanges(final VcsRoot root,
    		                                          @NotNull String fromVersion,
    		                                          final CheckoutRules checkoutRules) throws VcsException
    {
    	Settings settings = createSettings(root);

        String depot = settings.getDepot();
        String stream = settings.getWatchStream();
        Loggers.VCS.info(String.format("[XX] collectBuildChanges: depot '%s', stream '%s'", depot, stream));

        AcRunProcessExe run = (AcRunProcessExe)AcRunProcessExe.getInstance(settings, acDefaultWorkFolderParent);
        String currentVersion = run.getLastTransactionId(depot);
        
        return collectBuildChanges(root, fromVersion, currentVersion, checkoutRules);
    }
    
    public List<ModificationData> collectBuildChanges(final VcsRoot root,
            @NotNull final String fromVersion,
            @NotNull final String currentVersion,
            final CheckoutRules checkoutRules) throws VcsException
	{
        Loggers.VCS.info("[XX] collectBuildChanges: from " + fromVersion + " to " + currentVersion);
        
        Settings settings = createSettings(root);
        String depot = settings.getDepot();
        String stream = settings.getWatchStream();
        Loggers.VCS.info(String.format("[XX] collectBuildChanges: depot '%s', stream '%s'", depot, stream));

        List<ModificationData> result = new ArrayList<ModificationData>();
        if (Long.parseLong(fromVersion) == Long.parseLong(currentVersion))
        {
            return result;
        }

        try
        {
            XPathFactory factory = XPathFactory.newInstance();
            XPath xPath = factory.newXPath();

        	Document xmlChanges = null;

            AcRunProcessExe run = (AcRunProcessExe)AcRunProcessExe.getInstance(settings, acDefaultWorkFolderParent);
            //AcRunProcess url = AcRunProcessUrl.getInstance(settings, acDefaultWorkFolderParent);
            //Document xmlChanges = url.getListOfChangedElements(stream, fromVersion, currentVersion);
            //NodeList nodes = (NodeList) xPath.evaluate("/AcResponse/Element/Change", xmlChanges, XPathConstants.NODESET);
            //Loggers.VCS.info("Number of changes: " + nodes.getLength()); // Display the string.
            //if (nodes.getLength() == 0)
            //    return result;

            // ----------------------
            collectStreamChanges(root, depot, stream, result, xmlChanges, fromVersion, currentVersion, run, xPath);
            // ----------------------

        	if(settings.getHideChanges())
        	{
        		
        		ModificationData highestModData = null;
        		Integer highestTransId = 0;
        		for(ModificationData modData : result)
        		{
        			Integer transId = new Integer(modData.getVersion());
        			if(transId > highestTransId)
        			{
        				highestTransId = transId;
        				highestModData = modData;
        			}
        		}
        		
        		if( highestModData != null)
        		{
        			Date changeDate 		= highestModData.getVcsDate();
        			String userName 		= highestModData.getUserName();
        			String displayVersion 	= highestModData.getVersion();
        			String txVersion 		= currentVersion; // DCN 05APR2011 We use the depot-wide high watermark to ensure that the workspace is populated to the same Transaction number as the Collect changes 
        			List<VcsChange> changes = highestModData.getChanges();
        			
        			String description 		= "Changes from Ignored Stream (" + settings.getWatchStream() + ")" ;
        			
        			ModificationData mod = new ModificationData(changeDate, changes, description, userName, root, txVersion, displayVersion);
        			result.clear();
        			result.add(mod);
        			
        		}
        		return result;
        	}
            
            Loggers.VCS.info("Number of transaction mods: " + result.size()); // Display the string.
/* 
            NodeList inheritedVersionNodes = (NodeList) xPath.evaluate("/AcResponse/Element/Change[not(@VcsStatus) or @VcsStatus != 'Matched']", xmlChanges, XPathConstants.NODESET);

            if (inheritedVersionNodes.getLength() != 0)
            {
                List<VcsChange> changes = new ArrayList<VcsChange>(); // Transaction: version tags
                for (int i = 0; i<inheritedVersionNodes.getLength(); i++)
                {
                    try
                    {
                        Element inherVer = (Element)inheritedVersionNodes.item(i);

                        if (!inherVer.getAttribute("What").equals("eid"))
                            changes.add(createChangeItem(xPath, inherVer, null));
                    }
                    catch (Exception ex)
                    {
                           throw new VcsException("Could not collect other inherited changes: " + ex.getMessage());
                    }

                }
                if (changes.size()!= 0)
                {
                    Date changeDate = new Date(); // Transaction: time
                    String description = "(OTHER INHERITED CHANGES)";
                    String user = "AccuRev";
                    String txVersion = currentVersion;
                    String displayVersion = currentVersion;
                    ModificationData mod = new ModificationData(changeDate, changes, description, user, root, txVersion, displayVersion);
                    result.add(mod);
                }
                Loggers.VCS.info("Total number of mods: " + result.size()); // Display the string.
            }
 */
        }
        catch (Exception ex)
        {
            throw new VcsException("Collect build changes error: " + ex.getMessage(), ex);
        }
        return result;
    }
         
    public void buildPatch(final VcsRoot root,
    		@Nullable final String fromVersion,
    		@NotNull final String toVersion,
    		final PatchBuilder builder,
    		final CheckoutRules checkoutRules) throws VcsException, IOException
    {
    	Settings settings = createSettings(root);

    	// Testing: display input values
    	Loggers.VCS.info("buildPatch: from " + fromVersion + " to " + toVersion);
    	Loggers.VCS.info("buildPatch: checkoutRules = " + checkoutRules);
    	
    	System.out.println("[XX] buildPatch");

    	if (fromVersion == null) {
        	buildFullPatch(settings, toVersion, builder);
    	} else {
    		buildIncrementalPatch(settings, fromVersion, toVersion, builder);
    	}
    }

    // builds patch from version to version
    private void buildIncrementalPatch(final Settings settings, @NotNull final String fromVer, @NotNull final String toVer, final PatchBuilder builder)
    	throws VcsException, FileNotFoundException, IOException
    {
    	// Testing: display input values
    	Loggers.VCS.info("buildIncrementalPatch: fromVer = " + fromVer + ", toVer = " + toVer );

   		assert Long.parseLong(toVer) >= Long.parseLong(fromVer);

    	String stream = settings.getWatchStream();
        String depot = settings.getDepot();
    	
    	//IAcRunProcess cmd = AcRunProcess.getInstance(settings, acDefaultWorkFolderParent);
    	AcRunProcess cmd = AcRunProcessUrl.getInstance(settings, acDefaultWorkFolderParent);
    	
        AcCatProcess catCmd = new AcCatProcess();
    	catCmd.setSessionToken(cmd.getSessionToken());
    	
    	List<XMLTag> changes = cmd.getUpdateStreamInfo(depot, stream, toVer, fromVer);
	    for (XMLTag change : changes)
	    {
	    	String what = change.getAttributeValue("What");
	    	if (what.equals("version"))
	    	{
	    		XMLTag stream2 = change.getTag("Stream2");
	    		String depotName = settings.getDepot();
	    		String streamNameVersion = stream2.getAttributeValue("Version");
	    		String elementName = stream2.getAttributeValue("Name");

	    		byte[] fileContent = catCmd.getFileContentByFilePath(streamNameVersion, elementName, depotName);
	    		builder.changeOrCreateBinaryFile(new File(elementName), null, new ByteArrayInputStream(fileContent), fileContent.length );
	    	}
	    	else if (what.equals("now visible"))
	    	{
	    		XMLTag stream2 = change.getTag("Stream2");
	    		String elementName = stream2.getAttributeValue("Name");
	    		ElementStatusData elStat = cmd.getElementInfo(depot, stream, elementName);
	    		if (elStat.isDirectory())
	    		{
	    			builder.createDirectory(new File(elementName));
	    		}
	    		else
	    		{
		    		String depotName = settings.getDepot();
		    		String streamNameVersion = stream2.getAttributeValue("Version");

		    		byte[] fileContent = catCmd.getFileContentByFilePath(streamNameVersion, elementName, depotName);
		    		builder.createBinaryFile(new File(elementName), null, new ByteArrayInputStream(fileContent), fileContent.length );
	    		}
	    	}
	    	else if (what.equals("no longer visible"))
	    	{
	    		XMLTag stream2 = change.getTag("Stream2");
	    		String elementName = stream2.getAttributeValue("Name");
	    		ElementStatusData elStat = cmd.getElementInfo(depot, stream, elementName);
	    		if (elStat.isDirectory())
	    		{
	    			builder.deleteDirectory(new File(elementName), false);
	    		}
	    		else
	    		{
	    			builder.deleteFile(new File(elementName), false);
	    		}
	    	}
    		else if (what.equals("moved"))
    		{
	    		XMLTag stream1 = change.getTag("Stream1");
	    		String elementName1 = stream1.getAttributeValue("Name");
	    		XMLTag stream2 = change.getTag("Stream2");
	    		String elementName2 = stream2.getAttributeValue("Name");
	    		ElementStatusData elStat = cmd.getElementInfo(depot, stream, elementName2);
	    		if (elStat.isDirectory())
	    		{
	    			builder.renameDirectory(new File(elementName1), new File(elementName2), false);
	    		}
	    		else
	    		{
	    			builder.renameFile(new File(elementName1), new File(elementName2), false);
	    		}
	    	}
	    	else
	    	{
	    		Loggers.VCS.error("AccuRev: Unknown change type found: " + what);
    		}
	    }
    }

    // builds patch by exporting files using specified version
    public void buildFullPatch(final Settings settings, @NotNull final String toVersion, final PatchBuilder builder)
    	throws IOException, VcsException
    {
    	File tempDir = FileUtil.createTempDirectory("accurev", toVersion);
    	try 
    	{
            /* The following section is required for backward compatibility.
	         * The previous version of this plugin did not have a populateStream variable.
	         */
	        String workingStream;
	        if (settings.getPopulateStream() != null)
	        {
	        	workingStream = settings.getPopulateStream();
	        }
	        else
	        {
	        	workingStream = settings.getWatchStream();
	        }

			String checkoutDir = tempDir.getAbsolutePath();
			if (settings.getSubDirectory() != null)
			{
				checkoutDir += File.separatorChar + settings.getSubDirectory();
				File f = new File(checkoutDir);
				if(!f.exists())
				{
					if (!f.mkdir())
					{
						throw new VcsException("Unable to create Directory " + checkoutDir);
					}
				}
			}

			AcRunProcess cmd = AcRunProcessExe.getInstance(settings, acDefaultWorkFolderParent);
    		cmd.populateStream(settings.getDepot(), workingStream,checkoutDir);
    		buildPatchFromDirectory(builder, tempDir, null);
    	} finally {
    		FileUtil.delete(tempDir);
        }
    }

    protected void buildPatchFromDirectory(final PatchBuilder builder, final File repRoot, final FileFilter filter)
        throws IOException
    {
        buildPatchFromDirectory(repRoot, builder, repRoot, filter);
    }

    private void buildPatchFromDirectory(File curDir, final PatchBuilder builder, final File repRoot, final FileFilter filter)
    	throws IOException
	{
    	File[] files = curDir.listFiles(filter);
    	if (files == null)
    		return;

		for (File realFile: files) {
			String relPath = realFile.getAbsolutePath().substring(repRoot.getAbsolutePath().length());
			final File virtualFile = new File(relPath);
			if (realFile.isDirectory()) {
				builder.createDirectory(virtualFile);
				buildPatchFromDirectory(realFile, builder, repRoot, filter);
			} else {
				final FileInputStream is = new FileInputStream(realFile);
				try {
					builder.createBinaryFile(virtualFile, null, is, realFile.length());
				} finally {
					is.close();
				}
			}
		}
	}

    @NotNull
    public byte[] getContent(final VcsModification vcsModification,
                             final VcsChangeInfo change,
                             final VcsChangeInfo.ContentType contentType,
                             final VcsRoot vcsRoot) throws VcsException
    {
        String version = (contentType == VcsChangeInfo.ContentType.AFTER_CHANGE) 
                       ? change.getAfterChangeRevisionNumber() 
                       : change.getBeforeChangeRevisionNumber();
        return getContent(change.getRelativeFileName(), vcsRoot, version);
    }

    @NotNull
    public byte[] getContent(final String filePath, final VcsRoot vcsRoot, final String version)
        throws VcsException
    {
        Settings settings = createSettings(vcsRoot);
        AcCatProcess cmd = AcCatProcess.getInstance(settings);

        String depotName = settings.getDepot();
        String streamNameVersion = version;
        String elementName = filePath;
        
        return cmd.getFileContentByFilePath(streamNameVersion, elementName, depotName);
    }

    public VcsChange createChangeItem(XPath xPath, Element acChangeNode, String virtualVersion)
            throws XPathExpressionException
    {
        Element oldVer = (Element) xPath.evaluate("Stream1", acChangeNode, XPathConstants.NODE);
        Element newVer = (Element) xPath.evaluate("Stream2", acChangeNode, XPathConstants.NODE);

        String verFullPath = (String) ((newVer != null) ? newVer : oldVer).getAttribute("Name");
        
        if ( verFullPath.length() == 0 )
        {
            verFullPath = (String) ((newVer != null) ? newVer : oldVer).getAttribute("name");        	
        }

        String filePath = verFullPath.substring(1); // strip the /./ from the beginning
        Loggers.VCS.info("Version = " + filePath);

        String fileName = filePath;
        String relativeFileName = filePath;
        String afterNum;
        String beforeNum;
        String changeName = null;
        VcsChangeInfo.Type changeType;

        Boolean verIsDir = ((String) ((newVer != null) ? newVer : oldVer).getAttribute("IsDir")).equals("yes");

        if (newVer == null) {
            changeType = verIsDir ? VcsChangeInfo.Type.DIRECTORY_REMOVED : VcsChangeInfo.Type.REMOVED;
            beforeNum = (String) oldVer.getAttribute("Version");
            afterNum = null;
        } else if (oldVer != null) {
            changeType = verIsDir ? VcsChangeInfo.Type.DIRECTORY_CHANGED : VcsChangeInfo.Type.CHANGED;
            beforeNum = (String) oldVer.getAttribute("Version");
            afterNum = (String) newVer.getAttribute("Version");
            if (beforeNum.equals(afterNum))
                changeType = VcsChangeInfo.Type.NOT_CHANGED;

        } else {
            changeType = verIsDir ? VcsChangeInfo.Type.DIRECTORY_ADDED : VcsChangeInfo.Type.ADDED;
            beforeNum = null;
            afterNum = (String) newVer.getAttribute("Version");
        }

        VcsChange change = new VcsChange(changeType, changeName, fileName, relativeFileName, beforeNum, (virtualVersion != null) ? virtualVersion : afterNum);
        return change;
    }

    public void collectStreamChanges(VcsRoot root, String depot, String watchStream, 
    		List<ModificationData> result, Document xmlChanges, 
    		String fromVersion, String currentVersion, 
    		AcRunProcessExe run, XPath xPath)
        throws VcsException, XPathExpressionException
    {
    	Loggers.VCS.info("[XX] collectStreamChanges:  stream '" + watchStream + "', from " + fromVersion + ", to " + currentVersion);

        //Vector<TransactionData> vec = run.getRevisionsBetween(depot, watchStream, fromVersion, currentVersion);
    	// else
        AcRunProcessExe.AcHistoryParser hist = run.getHistoryBetween(depot, watchStream, fromVersion, currentVersion);    	
        @SuppressWarnings("unchecked")
        Vector<TransactionData> vec = hist.getHistoryDataCollection();
        
        Loggers.VCS.info("[XX] Number of transactions on '" + watchStream + "': " + vec.size());
        
        for (TransactionData tx : vec)
        {
            if (tx.getTranId().equals(fromVersion))
                continue;

            List<VcsChange> changes = new ArrayList<VcsChange>(); // Transaction: version tags
            @SuppressWarnings("unchecked")
			Vector<VersionData> versions = tx.getVersions();

            for (VersionData version : versions)
            {
                String fullRealNum = version.getRealStreamNum() + "/" + version.getRealVersionNum();
                //Loggers.VCS.debug("ElementID: " + version.getVerEID() + ", Real version num:" +  fullRealNum);

                //String expression = "/AcResponse/Element/Change[*/@eid='" + version.getVerEID() + "' and @What!='eid']";
                //NodeList nodeList = (NodeList)xPath.evaluate(expression, xmlChanges, XPathConstants.NODESET);  
/*                
                for ( int i = 0; i < nodeList.getLength(); ++i )
                {
                	Element node = ( Element )nodeList.item( i );
                                	
                	if (node == null) {
                		Loggers.VCS.info("[XX] node is null");
                		continue;
                	}
                	
                	Loggers.VCS.info("[XX] found a node, " + version.getVerVirtualName());
                	
                    Element oldVer = (Element) xPath.evaluate("Stream1", node, XPathConstants.NODE);
                    Element newVer = (Element) xPath.evaluate("Stream2", node, XPathConstants.NODE);

                    String verFullPath = (String) ((newVer != null) ? newVer : oldVer).getAttribute("Name");                	
                	
                    if ( verFullPath.length( ) > 0 )
                    {      
                    	node.setAttribute("VcsStatus", "Matched");
                        changes.add(createChangeItem(xPath, node, version.getVerVirtualName()));
                    
                    	break;
                    }
                }                               
  */
                
                String verVirtualNamed = StreamData.convertStreamVersion(version.getVerVirtual(), hist.getStreamCollection());
                String verRealNamed = StreamData.convertStreamVersion(version.getVerReal(), hist.getStreamCollection());
                                
                Loggers.VCS.info("|ElementID: "+ version.getVerEID()); 
                Loggers.VCS.info("|  Virtual: "+ version.getVerVirtual() +" \t'"+ verVirtualNamed +"'");
                Loggers.VCS.info("|     Real: "+ version.getVerReal() +" \t'"+ verRealNamed +"'");
                Loggers.VCS.info("|    IsDir: "+ version.getVerIsDir());
                Loggers.VCS.info("|     Path: "+ version.getVerFullPath());
                
                Boolean isDir = version.getVerIsDir();
                String filePath = version.getVerFullPath().substring(1); // strip the /./ from the beginning
                String fileName = filePath;
                String relativeFileName = filePath;
                String afterNum = version.getVerVirtual();
                
                String beforeNum = null;//version.getVerAncestor();
                if (beforeNum == null)
                	beforeNum = run.getDirectAncestor(verVirtualNamed, version.getVerFullPath());
                if (beforeNum != null && beforeNum.equals("0/0"))
                	beforeNum = null;

                VcsChangeInfo.Type changeType = isDir ? VcsChangeInfo.Type.DIRECTORY_CHANGED : VcsChangeInfo.Type.CHANGED;
                
                Loggers.VCS.info("| Ancestor: "+ beforeNum);
                
                Loggers.VCS.info("VcsChange: changeType="+changeType.toString()+" fileName="+fileName+" beforeNum="+(beforeNum != null ? beforeNum : "(null)")+" afterNum="+(afterNum != null ? afterNum : "(null)")+" isDir="+isDir);
                
                VcsChange change = new VcsChange(changeType, fileName, relativeFileName, beforeNum, afterNum);
                changes.add(change);
            }
            
            if (changes.size()!= 0)
            {
                Date changeDate = new Date(Long.parseLong(tx.getTranTime()) * 1000L); // Transaction: time
                String description = tx.getComment();
                String user = tx.getTranUser();
                String txVersion = currentVersion;  // DCN 05APR2011 We use the depot-wide high watermark to ensure that the workspace is populated to the same Transaction number as the Collect changes
                String displayVersion = tx.getTranId();
                
                Loggers.VCS.info("Modification: changeDate="+changeDate+" user="+user+" txVersion="+txVersion+" displayVersion="+displayVersion+" description='"+description+"'");
                
                ModificationData mod = new ModificationData(changeDate, changes, description, user, root, txVersion, displayVersion);
                result.add(mod);
            }
        }
        
        //NodeList inheritedVersionNodes = (NodeList) xPath.evaluate("/AcResponse/Element/Change[not(@VcsStatus) or @VcsStatus != 'Matched']", xmlChanges, XPathConstants.NODESET);
        //Loggers.VCS.info("Number of changes left: " + inheritedVersionNodes.getLength()); // Display the string.
       
        //if (inheritedVersionNodes.getLength() != 0)
        {
            String parentStream = run.getParentStreamName(depot, watchStream);
            
            Loggers.VCS.info("Parent stream: '" + parentStream + "'");
            if (parentStream != null)
            {
            	parentStream = parentStream.trim();
            	if (parentStream.length() > 0)
            	{
            		collectStreamChanges(root, depot, parentStream, result, xmlChanges, fromVersion, currentVersion, run, xPath);
            	}
            }
        }
    }
}
