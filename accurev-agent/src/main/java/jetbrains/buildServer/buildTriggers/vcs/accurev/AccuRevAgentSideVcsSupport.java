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

import java.io.File;

import jetbrains.buildServer.agent.AgentLifeCycleAdapter;
import jetbrains.buildServer.agent.AgentRunningBuild;
import jetbrains.buildServer.agent.BuildProgressLogger;
import jetbrains.buildServer.agent.vcs.CheckoutOnAgentVcsSupport;
import jetbrains.buildServer.util.EventDispatcher;
import jetbrains.buildServer.vcs.IncludeRule;
import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.vcs.VcsRoot;

import org.jetbrains.annotations.NotNull;

/**
 * Implementation of the client side checkout for the Accurevplugin. currently uses
 * CheckoutOnAgentVcsSupport which is deprecated.
 * 
 * @author skulczyc
 */
public class AccuRevAgentSideVcsSupport implements CheckoutOnAgentVcsSupport
{
    /**
     * Ctor. Attaches a listener to detect build starts and save a reference to the
     * AgentRunningBuild which stores build parameters.
     */
    public AccuRevAgentSideVcsSupport(EventDispatcher<AgentLifeCycleAdapter> dispatcher)
    {
        mCurrentBuild = null;
        
        // Register the listener to look for when the build starts
        AgentLifeCycleAdapter listener = new AgentLifeCycleAdapter()
        {
            public void buildStarted(AgentRunningBuild runningBuild)
            {
                mCurrentBuild = runningBuild;
            }
        };
        dispatcher.addListener(listener);
    }
    
    /**
     * Called when we should update the workspace.
     */
    public void updateSources(@NotNull final BuildProgressLogger logger, @NotNull final File workingDir, @NotNull final VcsRoot vcsRoot, @NotNull final String version, final IncludeRule includeRule)
        throws VcsException
    {
    	AccuRevAgentCheckout checkout = new AccuRevAgentCheckout(logger, workingDir, vcsRoot, version, mCurrentBuild);        
        checkout.login();
        checkout.detectOverlaps();
        checkout.setupStreams();
        checkout.setupWorkspace();
        checkout.syncWorkspace();
    }
    
    /**
     * Called when we are suppsed to supply a name.
     */
    public String getName()
    {
        return Constants.VCS_NAME;
    }

    private AgentRunningBuild mCurrentBuild;
}
