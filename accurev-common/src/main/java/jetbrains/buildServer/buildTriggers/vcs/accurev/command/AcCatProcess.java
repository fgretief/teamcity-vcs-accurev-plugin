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

import com.accurev.common.process.CatProcess;
import com.accurev.common.process.RunProcess;

import com.intellij.execution.configurations.GeneralCommandLine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import jetbrains.buildServer.ExecResult;
import jetbrains.buildServer.vcs.VcsException;

/**
 * @author Francois Retief
 */
public class AcCatProcess extends CatProcess
{
    public AcCatProcess()
    {
        enableDebug = true; // for testing
    }

    private GeneralCommandLine createCommandLine(String depotName, String streamNameVersion, File workingDir)
    {
        GeneralCommandLine cli = new GeneralCommandLine();
        cli.setExePath(RunProcess.getAccuRevExecutable());
        cli.setWorkDirectory(workingDir.getAbsolutePath());
        cli.addParameter("cat");
        cli.addParameter("-v");
        cli.addParameter(streamNameVersion);
        cli.addParameter("-p");
        cli.addParameter(depotName);
        return cli;
    }

    public File catFileByName(String depotName, String streamNameVersion, String elementName, File workingDir)
        throws VcsException
    {
        GeneralCommandLine cli = createCommandLine(depotName, streamNameVersion, workingDir);
        cli.addParameter("\"" + elementName + "\"");
        return runCommand(cli, workingDir);
    }

    public File catFileById(String depotName, String streamNameVersion, String elementId, File workingDir)
        throws VcsException, IOException
    {
        GeneralCommandLine cli = createCommandLine(depotName, streamNameVersion, workingDir);
        cli.addParameter(elementId);
        return runCommand(cli, workingDir);
    }

    private File runCommand(GeneralCommandLine cli, File workingDir)
        throws VcsException
    {
        if (enableDebug)
        {
            StringBuilder sb = new StringBuilder("ACAPI:");
            for (String cmd : cli.getCommands())
            {
                sb.append(" '" + cmd + "'");
            }
            System.out.println(sb.toString());
        }

        ExecResult result = CommandUtil.runCommand(cli);
        try {
            // Save the contents to a temporary file.
            File tmpFile = File.createTempFile("accurev$", ".tmp", workingDir);
            try {
                FileWriter wr = new FileWriter(tmpFile);
                wr.write(result.getStdout());
                wr.close();
                return tmpFile;
            } catch (IOException ex) {
                throw new VcsException("Unable to write temporary file!", ex);
            }
        } catch (IOException ex) {
            throw new VcsException("Unable to create a temporary file!", ex);
        }
    }
}
