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

import jetbrains.buildServer.vcs.VcsRoot;

/**
 * @author Francois Retief
 */
public interface Constants
{
    String VCS_NAME 			= "accurev";
    String DEPOT 				= "depot";
    String WATCHSTREAM 			= "stream";//the html name is unchanged for backwards compatibility.
    String POPULATESTREAM 		= "populatestream";
    String SUBDIRECTORY   		= "subdirectory";
    
    String HIDECHANGES 			= "hidechanges";
    String VERBOSITY			= "verbosity";
    String FAIL_ON_OVERLAP		= "failonoverlap";
    String USE_REF_TREE			= "usereftree";
        
    String PROMOTEONSUCCESS 	= "promoteOnSuccess";
    String PROMOTE_SEPARATELY 	= "radioButton_promoteType";
    String Radio_Button_value	= "PromoteSeparately";
    
    String SERVER_NAME 			= "serverName";
    String SERVER_PORT 			= "serverPort";
    String USERNAME 			= "username";
    String PASSWORD 			= VcsRoot.SECURE_PROPERTY_PREFIX + "password";
    
    String CLEAN				= "vcs.accurev.clean";

    String COMMAND_DIR 			= "commandDir";
    
  
}
