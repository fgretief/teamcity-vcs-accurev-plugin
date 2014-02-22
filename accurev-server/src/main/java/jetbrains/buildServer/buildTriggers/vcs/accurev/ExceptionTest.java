package jetbrains.buildServer.buildTriggers.vcs.accurev;

import java.io.IOException;

import jetbrains.buildServer.vcs.VcsException;

public class ExceptionTest {

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		// TODO Auto-generated method stub
		ExceptionTest et = new ExceptionTest();
		
		try {
			et.runTest();
		} catch (VcsException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	public void runTest() throws VcsException
	{
		
		try {
			throwFakeException();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			throw new VcsException("Message 1", e);
		}
	}
	
	public void throwFakeException() throws IOException
	{
		throw new IOException();
	}

}
