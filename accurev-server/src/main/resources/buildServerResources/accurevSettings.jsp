
<%@include file="/include.jsp"%>
<%@ taglib prefix="props" tagdir="/WEB-INF/tags/props" %>
<jsp:useBean id="propertiesBean" scope="request" type="jetbrains.buildServer.controllers.BasePropertiesBean"/>


<table class="runnerFormTable">

	<l:settingsGroup title="General Settings">

		<!-- DEPOT -->
		<tr>
			<th><label for="depot">Depot: <l:star/></label></th>
			<td>
				<props:textProperty name="depot" className="longField" />
				<span class="error" id="error_depot"></span>
			</td>
		</tr>
	  
		<!-- WATCH STREAM -->
		<tr>
			<th><label for="stream">Watch stream: <l:star/></label></th>
			<td>
				<props:textProperty name="stream" className="longField" />
				<span class="error" id="error_stream"></span>
				<div class="smallNote" style="margin: 0;">Watch Stream: The stream which is monitored for changes</div>
			</td>
		</tr>
	  
		<!-- POPULATE STREAM -->
		<tr>
			<th><label for="populatestream">Populate stream: </label></th>
			<td>
				<props:textProperty name="populatestream" className="longField" />
				<span class="error" id="error_populatestream"></span>
				<div class="smallNote" style="margin: 0;">Populate Stream: The stream that will be checked out when the build begins. If a populate stream is not specified then the Watch stream will be checked out instead</div>
			</td>
		</tr>

		<!-- SUBDIRECTORY -->
		<tr>
			<th><label for="subdirectory">Checkout subdirectory: </label></th>
			<td>
				<props:textProperty name="subdirectory" className="longField" />
				<div class="smallNote" style="margin: 0;">Checkout Subdirectory: a subdirectory that the stream will be populated to.</div>
			</td>
		</tr>

		<!-- HIDE CHANGES -->
		<tr>
			<th><label for="hidechanges">Hide Changes: </label></th>
			<td>
				<props:checkboxProperty name="hidechanges" />
				<div class="smallNote" style="margin: 0;">If selected all changes from this stream will appear as a single entry in the TeamCity change list</div>
			</td>
		</tr>
	
		<!-- WORKSPACE OR REFTREE -->
		<tr>
			<th><label for="usereftree">Use reference tree: </label></th>
			<td>
				<props:checkboxProperty name="usereftree" />
				<div class="smallNote" style="margin: 0;">If selected will create a reference tree to sync with the stream instead of a standard workspace</div>
			</td>
		</tr>
	
		<!-- FAIL ON OVERLAP -->
		<tr>
			<th><label for="hidechanges">Fail on overlap: </label></th>
			<td>
				<props:checkboxProperty name="failonoverlap" />
				<div class="smallNote" style="margin: 0;">If selected will cause the build to fail if an overlap on the stream is detected</div>
			</td>
		</tr>
	
		<!-- VERBOSITY -->
	    <tr>
	        <th><label for="verbosity">Verbosity: </label></th>
	        <td>
	            <div>
	             <props:radioButtonProperty name="verbosity"
	                                 value="0"
	                                 id="verbosity-none"
	                                 checked="${propertiesBean.properties[verbosity] == 0}"/>
	                 <label for="verbosity-none">None</label>
	            </div>
	
	            <div>
	             <props:radioButtonProperty name="verbosity"
	                                 value="1"
	                                 id="verbosity-some"
	                                 checked="${propertiesBean.properties[verbosity] == 1}"/>
	                 <label for="verbosity-some">Accurev commands</label>
	            </div>
	
	            <div>
	             <props:radioButtonProperty name="verbosity"
	                                 value="2"
	                                 id="verbosity-all"
	                                 checked="${propertiesBean.properties[verbosity] == 2}"/>
	                 <label for="verbosity-all">Accurev commands and output</label>
	            </div>
	            
	        </td>
	    </tr>

	</l:settingsGroup>
  
	<l:settingsGroup title="Post build events">
		<!-- SUBDIRECTORY -->
		<!-- ############################################################################################################# -->


		<tr>		
			<c:set var="onclick">
				$('radioButton_promoteSeparately').disabled = !this.checked;
				$('radioButton_promoteTogether').disabled   = !this.checked;
				
				var rad_promoteSeparately = $('radioButton_promoteSeparately').checked;
				var rad_promoteTogether	  = $('radioButton_promoteTogether').checked;
				
				
				if (!rad_promoteSeparately && !rad_promoteTogether)
				{
					$('radioButton_promoteSeparately').checked = true;
				}
			</c:set>		
			
			<th><label for="promoteOnSuccess">Promote on success:</label></th>
			<td>
				<props:checkboxProperty name="promoteOnSuccess" onclick="${onclick}"  />				
				<div class="smallNote" style="margin: 0;">If selected TeamCity will promote the watch stream up to the current transaction if build succeeds</div>
			</td>
		</tr>
		
		<!-- ############################################################################################################# -->
		<tr>
			<th><label for="promoteType">Promote Type:</label></th>
			<td>
				<props:radioButtonProperty id="radioButton_promoteSeparately"  name="radioButton_promoteType"  value="PromoteSeparately"  disabled="${propertiesBean.properties['promoteOnSuccess'] != 'true'}" />
				Promote Each Transaction Separately
				<div class="smallNote" style="margin: 0;">
					Promotes each transaction separately with a comment string of the form [userID:Comment].<br>
					Transactions are promoted in ascending order.
				</div>
			</td>
		</tr>
		
		<!-- ############################################################################################################# -->
		<tr>
			<th><label for="promoteType2"></label></th>
			<td>
				<props:radioButtonProperty id="radioButton_promoteTogether" name="radioButton_promoteType"  value="promoteTogether"  disabled="${propertiesBean.properties['promoteOnSuccess'] != 'true'}" />
				Promote all transactions together.
				
				<div class="smallNote" style="margin: 0;">
					Will promote all transactions as one promote. 
					The comment string for the promote will consist of an amalgamation of all the individual comment strings for each transaction.<br>
					The format of the comment string will be [userID:comment] followed by a new line.
				</div>
			</td>
		</tr>
	</l:settingsGroup>
  
  
	<l:settingsGroup title="Login settings">

		<tr>
			<th><label for="username">Username: <l:star/></label></th>
			<td>
				<props:textProperty name="username"/>
				<span class="error" id="error_username"></span>
			</td>
		</tr>

		<tr>
			<th><label for="secure:password">Password: </label></th>
			<td>
				<props:passwordProperty name="secure:password"/>
				<span class="error" id="error_secure:password"></span>
			</td>
		</tr>

		<tr>
			<th><label for="serverName">Server name: <l:star/></label></th>
			<td>
				<props:textProperty name="serverName" className="longField" />
				<span class="error" id="error_serverName"></span>
			</td>
		</tr>

		<tr>
			<th><label for="serverPort">Server port: <l:star/></label></th>
			<td>
				<props:textProperty name="serverPort" className="longField" />
				<span class="error" id="error_serverPort"></span>
			</td>
		</tr>

	</l:settingsGroup>
	
	<l:settingsGroup title="Miscellaneous settings">

		<tr>
			<th><label for="commandDir">AccuRev directory: <l:star/></label></th>
			<td>
				<props:textProperty name="commandDir" className="longField"/>
				<span class="error" id="error_commandDir"></span>
			</td>
		</tr>

	</l:settingsGroup>

</table>
