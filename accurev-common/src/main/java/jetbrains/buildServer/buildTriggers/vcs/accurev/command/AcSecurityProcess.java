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

import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;

import com.accurev.common.process.RunProcess;
import com.accurev.common.process.SecurityProcess;

/**
 * @author Francois Retief
 */
public class AcSecurityProcess extends SecurityProcess
{
    public String IsAuthenticated  = "authuser";
    public String NotAuthenticated = "notauth";
    // Warning: The actual result has line-feeds at the end.
    // Don't use equal() for comparison, use startsWith()

    public AcSecurityProcess()
    {
        enableDebug = true; // for testing
    }

    private int ThrowIfError(String cmdName, int result) throws VcsException
    {
        if (result != ERR_SUCCESS)
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

    public String Login(String userName, String password) throws VcsException
    {
        int result = login(userName, password);
        ThrowIfError("accurev login", result);
        System.out.println("Accurev Login Command PASSED !!!!" + result);
        String token = getResponseText().toString();
        if (token.length() > 32)
        {
            token = token.substring(0, 32);
        }
        return token;
    }

    public void Logout() throws VcsException
    {
        String[] args = {
            RunProcess.getAccuRevExecutable(),
            "logout",
        };
        ThrowIfError("accurev logout", exec(args, null));
    }

    public String getSecurityInfo() throws VcsException
    {
        String[] args = {
            RunProcess.getAccuRevExecutable(),
            "secinfo",
        };
        exec(args, null);
        return getResponseText().toString();
    }
}
