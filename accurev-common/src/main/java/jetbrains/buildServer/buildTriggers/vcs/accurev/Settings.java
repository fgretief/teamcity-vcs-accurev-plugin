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

import jetbrains.buildServer.util.FileUtil;
import jetbrains.buildServer.util.Hash;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

import org.jetbrains.annotations.NotNull;

import com.accurev.common.process.RunProcess;

import java.io.File;
import java.util.Map;
/**
 * Represents AccuRev repository settings
 *
 * @author Francois Retief
 */
public class Settings {
    @NotNull private File acWorkFolderParentDir;

    public Settings() {
    }

    public Settings(@NotNull File workFolderParentDir, @NotNull VcsRoot vcsRoot) throws VcsException
    {
    	setup(workFolderParentDir, vcsRoot.getProperties()); 
    }

    
    public Settings(File workFolderParentDir, Map<String, String> properties )
    {
    	setup(workFolderParentDir, properties);
    }
    
    
    public void setup(File workFolderParentDir, Map<String, String> properties )
    {
    	acWorkFolderParentDir = workFolderParentDir;
    	
        setDepot(properties.get(Constants.DEPOT));
        setWatchStream(properties.get(Constants.WATCHSTREAM));
        setSubDirectory(properties.get(Constants.SUBDIRECTORY));
        setHideChanges(properties.get(Constants.HIDECHANGES));
        setFailOnOverlap(properties.get(Constants.FAIL_ON_OVERLAP));
        setVerbosity(properties.get(Constants.VERBOSITY));
        setUseRefTree(properties.get(Constants.USE_REF_TREE));
        
        // If the populate stream is empty use the watch stream
        String populateStream = properties.get(Constants.POPULATESTREAM);
        if(populateStream == null || populateStream.length() == 0)
        {
        	populateStream = properties.get(Constants.WATCHSTREAM);
        }
        setPopulateStream(populateStream);
        
        setPromoteOnSuccess(properties.get(Constants.PROMOTEONSUCCESS));
        //String temp = ;
        setPromoteSeparately(properties.get(Constants.PROMOTE_SEPARATELY));//if this is true then promote together must be false.
        
        
        setServerName(properties.get(Constants.SERVER_NAME));
        setServerPort(properties.get(Constants.SERVER_PORT));
        setUsername(properties.get(Constants.USERNAME));
        setPassword(properties.get(Constants.PASSWORD));

        setCommandDir(properties.get(Constants.COMMAND_DIR));
    }
    
    //#################################################################
    
    boolean promoteSeparately = false;
    public void setPromoteSeparately(String promoteType)
    {
    	if(promoteType == null)
    	{
    		return;
    	}
    	
    	if(promoteType.equals(Constants.Radio_Button_value)){
    		promoteSeparately = true;
    	}
    	
    }
    
    public boolean getPromoteSeparately()
    {
    	return promoteSeparately;
    }
    
    
    //#################################################################
    
    public String acWatchStream;
    public void setWatchStream(@NotNull final String watchStream){
    	
    	acWatchStream = watchStream;
    }
        
    public String getWatchStream(){
    	
    	return acWatchStream;
    }
    
    //#################################################################
    
    public String acSubDirectory;
    public void setSubDirectory(@NotNull final String subDirectory){
    	
    	acSubDirectory = subDirectory;//The subdirectory need not exist at this time
    }
        
    public String getSubDirectory(){
    	
    	return acSubDirectory;//The subdirectory need not exist at this time
    }
    
    //#################################################################
    
    public boolean acPromoteOnSuccess = false;
    public void setPromoteOnSuccess(@NotNull final String promoteOnSuccess){
    	if (promoteOnSuccess == null)
    	{
    		return;    		
    	}
    	
    	if(promoteOnSuccess.equals("true")){
        	acPromoteOnSuccess = true;
    	}
    	else
    	{
    		acPromoteOnSuccess = false;
    	}
    }
        
    public boolean getPromoteOnSuccess(){
    	
    	return acPromoteOnSuccess;
    }
    
    
    //#################################################################
    
    public boolean acHideChanges = false;
    public void setHideChanges(@NotNull final String ignoreChanges)
    {
    	if (ignoreChanges == null)
    	{
    		return;    		
    	}
    	
    	if(ignoreChanges.equals("true")){
    		acHideChanges = true;
    	}
    	else
    	{
    		acHideChanges = false;
    	}
    }
        
    public boolean getHideChanges()
    {
       	return acHideChanges;
    }
    
    
    //#################################################################
    
    public boolean acUseRefTree = false;
    public void setUseRefTree(@NotNull final String useRefTree)
    {
    	if (useRefTree == null || useRefTree.length() == 0)
    	{
    		return;    		
    	}
    	
    	if(useRefTree.equals("true")){
    		acUseRefTree = true;
    	}
    	else
    	{
    		acUseRefTree = false;
    	}
    }
        
    public boolean getUseRefTree()
    {
       	return acUseRefTree;
    }
    
    
    //#################################################################
    
    public boolean acFailOnOverlap = false;
    
    public void setFailOnOverlap(@NotNull final String failOnOverlap)
    {
    	if (failOnOverlap == null)
    	{
    		return;    		
    	}
    	
    	if(failOnOverlap.equals("true")){
    		acFailOnOverlap = true;
    	}
    	else
    	{
    		acFailOnOverlap = false;
    	}
    }
        
    public boolean getFailOnOverlap()
    {
       	return acFailOnOverlap;
    }
    
    
    //#################################################################
    
    
    private String acDepot;

    public void setDepot(@NotNull final String depot)
    {
        acDepot = depot;
    }

    public String getDepot()
    {
        return acDepot;
    }

    //#################################################################    
    
    private int acVerbosity = 0;

    protected void setVerbosity(@NotNull final String Verbosity)
    {
    	if (Verbosity == null || Verbosity.length() == 0)
    	{
    		return;
    	}

        setVerbosity(Integer.parseInt(Verbosity));
    }

    public void setVerbosity(final int Verbosity)
    {
        acVerbosity = Verbosity;
    }

    public int getVerbosity()
    {
        return acVerbosity;
    }

    //#################################################################

    private String acPopulateStream;

    public void setPopulateStream(@NotNull final String stream)
    {
        acPopulateStream = stream;
    }

    public String getPopulateStream()
    {
        return acPopulateStream;
    }

    //#################################################################

    private String acServerName;

    public void setServerName(@NotNull final String serverName)
    {
        acServerName = serverName;
    }

    public String getServerName()
    {
        return acServerName;
    }

    //#################################################################

    private int acServerPort;

    protected void setServerPort(@NotNull final String serverPort)
    {
        int port = 5050; // default value
        if (serverPort != null)
            port = Integer.parseInt(serverPort);
        setServerPort(port);
    }

    public void setServerPort(final int serverPort)
    {
        acServerPort = serverPort;
    }

    public int getServerPort()
    {
        return acServerPort;
    }

    // --------------------------------------------------

    private String acUsername;

    public void setUsername(@NotNull final String username)
    {
        acUsername = username;
    }

    public String getUsername()
    {
        if (acUsername == null)
            return "";

        return acUsername;
    }

    // --------------------------------------------------

    private String acPassword;

    public void setPassword(@NotNull final String password)
    {
        acPassword = password;
    }

    public String getPassword()
    {
        if (acPassword == null)
            return "";

        return acPassword;
    }

    // --------------------------------------------------

    private File acCommandDir;

    public void setCommandDir(@NotNull final String cmdDir) 
    {
        setCommandDir(new File(cmdDir));
    }

    public void setCommandDir(@NotNull final File cmdDir)
    {
        acCommandDir = cmdDir;
    }

    public File getCommandDir()
    {
        return acCommandDir;
    }

    public File getExecutablePath()
    {
        // DCN 13APR2011 - Because the location of the AccuRev executable may vary from server to server (or agent to agent)
        //                 fall back on the default for the OS of the host if we can't locate it at the configured path.
        String fileName = RunProcess.getDefaultAccuRevExecutable();
        File accurevPath = (acCommandDir.exists()) ?  acCommandDir : new File(getDefaultAccuRevDir());
        if (!accurevPath.getName().equalsIgnoreCase("bin"))
        {
            fileName = "bin/" + fileName;
        }
        return new File(accurevPath, fileName);
    }

    // --------------------------------------------------

    private File acWorkingDir;

    public void setWorkingDir(@NotNull final File workingDir) {
        acWorkingDir = FileUtil.getCanonicalFile(workingDir);
    }

    @NotNull
    public File getWorkingDir() {
        if (acWorkingDir != null) {
            return acWorkingDir;
        }
        return getDefaultWorkDir(acWorkFolderParentDir, acPopulateStream);
    }

    public static String DEFAULT_WORK_DIR_PREFIX = "ac_";

    private static File getDefaultWorkDir(@NotNull File workFolderParentDir, @NotNull String repPath) {
        String workingDirname = DEFAULT_WORK_DIR_PREFIX + String.valueOf(Hash.calc(normalize(repPath)));
        return FileUtil.getCanonicalFile(new File(workFolderParentDir, workingDirname));
    }

    public static String getDefaultAccuRevDir()
    {
        String path;
    	if (File.separatorChar == '\\')
    	{
    		// TODO: How do we detect the difference between x86 and x64 Windows?
    		path = "C:\\Program Files\\AccuRev\\";
    		//properties.put(Constants.COMMAND_DIR, path);
    		File f = new File(path);
    		if (!f.exists())
    		{
                path = "C:\\Program Files (x86)\\AccuRev\\";
    			//properties.put(Constants.COMMAND_DIR, "C:\\Program Files (x86)\\AccuRev\\");
    		}
    	}
    	else
    	{   // TODO: What is the default path for AccuRev on Linux/Unix systems?

    		path = "/usr/bin/accurev";
    		//properties.put(Constants.COMMAND_DIR, path);

    		File f = new File(path);

    		if (f.exists() && f.isFile())
    		{
                path = "/usr/bin";
    			//properties.put(Constants.COMMAND_DIR, "/usr/bin");
    		}
    		else if(!f.exists())
    		{
                path = "/opt/accurev";
        		//properties.put(Constants.COMMAND_DIR, "/opt/accurev");
    		}

    	}
        return path;
    }

    private static String normalize(@NotNull final String path) {
        String normalized = normalizeSeparator(path);
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length()-1);
        }
        return normalized;
    }

    @NotNull
    public static String normalizeSeparator(@NotNull final String repPath) {
        return repPath.replace('\\', '/');
    }

    // --------------------------------------------------

    public String toString()
    {
        StringBuilder sb = new StringBuilder();
        sb.append("Principal:      " + acUsername + "\n");
        sb.append("Host:           " + acServerName + "\n");
        sb.append("Port:           " + acServerPort + "\n");
        sb.append("Depot:          " + acDepot + "\n");
        sb.append("Stream:         " + acPopulateStream + "\n");
        return sb.toString();
    }

    // -----------------------------------
}
