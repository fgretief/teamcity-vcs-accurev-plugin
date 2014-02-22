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

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.SimpleCommandLineProcessRunner;
import jetbrains.buildServer.log.Loggers;
import jetbrains.buildServer.util.StringUtil;
import jetbrains.buildServer.vcs.VcsException;

import org.jetbrains.annotations.NotNull;

import com.accurev.common.process.RunProcess;
import com.intellij.execution.configurations.GeneralCommandLine;

/**
 * @author Francois Retief
 */
public class CommandUtil
{
    public static void checkCommandFailed(@NotNull String cmdName, @NotNull ExecResult res)
        throws VcsException
    {
        if (res.getExitCode() > 0 || res.getException() != null) {
            commandFailed(cmdName, res);
        }
        if (res.getStderr().length() > 0) {
          Loggers.VCS.warn("Error output produced by: " + cmdName);
          Loggers.VCS.warn(res.getStderr());
        }
    }

    public static void commandFailed(final String cmdName, final ExecResult res)
        throws VcsException
    {
        Throwable exception = res.getException();
        String stderr = res.getStderr();
        String stdout = res.getStdout();
        final String message = "'" + cmdName + "' command failed.\n" +
            (!StringUtil.isEmpty(stderr) ? "stderr: " + stderr + "\n" : "") +
            (!StringUtil.isEmpty(stdout) ? "stdout: " + stdout + "\n" : "") +
            (exception != null ? "exception: " + exception.getLocalizedMessage() : "");
        Loggers.VCS.warn(message);
        throw new VcsException(message);
    }

    public static ExecResult runCommand(@NotNull GeneralCommandLine cli)
        throws VcsException
    {
        String cmdStr = cli.getCommandLineString();
        Loggers.VCS.debug("Run command: " + cmdStr);
        ExecResult res = SimpleCommandLineProcessRunner.runCommand(cli, null);
        CommandUtil.checkCommandFailed(cmdStr, res);
        Loggers.VCS.debug(res.getStdout());
        return res;
    }

    public static String errorToString(int error)
    {
        switch (error)
        {
        case RunProcess.ERR_SUCCESS:
            return "ERR_SUCCESS";
        case 1:
        case RunProcess.ERR_FAILURE:
            return "ERR_FAILURE";
        case RunProcess.ERR_COMMAND_NOT_EXECUTED:
            return "ERR_COMMAND_NOT_EXECUTED";
        case RunProcess.ERR_CONNECTING_SERVER:
            return "ERR_CONNECTING_SERVER";
        case RunProcess.ERR_NOT_AUTHENTICATED:
            return "ERR_NOT_AUTHENTICATED";
        case RunProcess.ERR_PROCESS_HAS_NO_OUTPUT:
            return "ERR_PROCESS_HAS_NO_OUTPUT";
        case RunProcess.ERR_PROCESS_NON_SPECIFIC:
            return "ERR_PROCESS_NON_SPECIFIC";
        case RunProcess.ERR_PROCESS_RUN:
            return "ERR_PROCESS_RUN";
        case RunProcess.ERR_PROCESS_WAIT_TERMINATED:
            return "ERR_PROCESS_WAIT_TERMINATED";
        default:
            return String.format("<unknown: %d>", error);
        }
    }
}