package jetbrains.buildServer.buildTriggers.vcs.accurev.command;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

import jetbrains.buildServer.vcs.VcsException;
import jetbrains.buildServer.buildTriggers.vcs.accurev.Settings;
import java.util.concurrent.CountDownLatch;
import java.io.File;

public class AcSpecialLogin {

	private String stdOut  = null;
	private String stdErr  = null;
	private int returnCode = 0;
	
	private String userId;
	private String password;
	private String accurevExePath;

	final CountDownLatch latch;

	public  AcSpecialLogin(Settings settings) throws VcsException{
		String userName = settings.getUsername();
		String password = settings.getPassword();
		String filePath = settings.getExecutablePath().getAbsolutePath();
		
		setup(userName, password, filePath);
		latch = new CountDownLatch(2);		
	}

	
	public  AcSpecialLogin(String userName, String userPassword, String accurevExePath) throws VcsException{
		setup(userName, userPassword, accurevExePath);
		latch = new CountDownLatch(2);
	}
	
	public void setup(String userName, String userPassword, String accurevExePath)
	{
		// TODO Auto-generated constructor stub
		this.userId         = userName;
		this.password       = userPassword;
	
		File accurev = new File(accurevExePath);
		if(accurev.isFile())
		{
			this.accurevExePath = accurev.getAbsolutePath();
		}
		else
		{
			this.accurevExePath = "accurev";
		}			
		
	}
	
	public int getReturnCode()
	{
		return returnCode;
	}
	
	public String getStdOut() {
		return stdOut;
	}

	public String getStdErr() {
		return stdErr;
	}

	public boolean login() throws VcsException {
		try {
			Runtime runtime = Runtime.getRuntime();

			String[] args = { accurevExePath, "login", "-A", // create session token
					"-n", // create everlasting session token
					userId, password };

			Process proc = runtime.exec(args);// IOException

			AcSpecialLogin.StreamGobbler stdOutGobbler = new AcSpecialLogin.StreamGobbler(
					proc.getInputStream());
			AcSpecialLogin.StreamGobbler stdErrGobbler = new AcSpecialLogin.StreamGobbler(
					proc.getErrorStream());

			Thread stdOutThread = new Thread(stdOutGobbler);
			Thread stdErrThread = new Thread(stdErrGobbler);

			stdOutThread.start();
			stdErrThread.start();

			int returnCode = proc.waitFor();// InterruptedException
			if (returnCode == 0) {
				latch.await();
				stdOut = stdOutGobbler.getResult();// will throw exception if
													// thread has not stopped
				stdErr = stdErrGobbler.getResult();// will throw exception if
													// thread has not stopped
				return true;

			}
			latch.await();
			
			stdOut = stdOutGobbler.getResult();
			stdErr = stdErrGobbler.getResult();
			
			String message = "Special Login Failed:\n";
			message += "returnCode: " + returnCode + "\n";
			message += "stdOut    : " + stdOut     + "\n";
			message += "stdErr    : " + stdErr     + "\n";
			throw new VcsException(message);
		} catch (InterruptedException e) {
			throw new VcsException(
					"Encountered InterruptedException whilst performing special login\n"
							+ e.getMessage());
		} catch (IOException e) {
			throw new VcsException(
					"Encountered IOException whilst performing special login\n"
							+ e.getMessage());
		}
	}

	private class StreamGobbler implements Runnable {
		InputStream inputStream;
		private String result = "";

		public StreamGobbler(InputStream inputStream) {
			this.inputStream = inputStream;
		}

		public String getResult() {
			return result;
		}

		public void run() {
			BufferedReader bufferedReader = new BufferedReader(
					new InputStreamReader(inputStream));
			StringBuilder strBuilder = new StringBuilder();
			try {
				String line = null;
				while ((line = bufferedReader.readLine()) != null) {
					strBuilder.append(line);
					strBuilder.append("\n");
				}

				result = strBuilder.toString();

			} catch (IOException e) {
				e.printStackTrace();// no one will see this but the
									// proc.exitCode should be none zero.
			} finally {
				latch.countDown();
			}

		}
	}

}
