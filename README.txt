TeamCity plugin for AccuRev SCM
-------------------------------
http://www.jetbrains.net/confluence/display/TW/AccuRev


Installation
=================================

1.	Download accurev.zip and put it into the .BuildServer/plugins folder (no need to unzip).
2.	Restart the TeamCity server.



Contributors
=================================

Francois Retief
Derrick Rapp
Daniel Nolan (FlexiGroup Ltd)
Abraham Odamteng
Mark Baker



System Requirements
=================================

 - TeamCity 5.1 (or later)
 - Please note that your AccuRev server must be configured to use the "AccuRev login" 
   user-authentication scheme; the "Traditional" scheme is not compatible with the plugin.



Known Issues
=================================

 - Checkout on server-side only supports clean checkout; please agent-side checkout if you require incremental checkout (i.e. builds with “clean” option unchecked)
 - Checkout on server-side does not preserve permissions of files or symbolic/hard links
 - Remote run from IDE is not supported
 - Checkout rules are not fully supported

   


Release Notes
=================================

---------------------------------------------------------------------
Release 0.4 14-APR-2011 (Contributed by Abraham Odamteng, Mark Baker and Daniel Nolan)
---------------------------------------------------------------------
ENHANCEMENTS
 - AGENT-SIDE CHECKOUT SUPPORT ADDED
   This long-awaited feature allows for each TeamCity agent to populate the checkout area, directly 
   from the specified AccuRev server (i.e. rather than via the TeamCity Web Server). This will result
   in substantially reduced build times, as this allows for incremental checkouts (provided the 
   "clean build" option is unchecked). The plugin will automatically create workspaces or reference trees 
   and update them appropriately on subsequent changes using a time-locked basis stream.
 
 - PROMOTE ON SUCCCESS FEATURE
   If used TeamCity will promote the watch stream up to the current transaction if build succeeds.
   Two modes are available for this feature:
		* Promote Each Transaction Separately
			Promotes each transaction separately with a comment string of the form [userID:Comment].
			Transactions are promoted in ascending order.
		* Promote all transactions together.
			Will promote all transactions as one promote. The comment string for the promote will consist of 
			an amalgamation of all the individual comment strings for each transaction.
			The format of the comment string will be [userID:comment] followed by a new line.
 
 - WATCH STREAM vs POPULATE STREAM
   New VCS root setting allows you to watch one stream for changes, but actually populate from another.
   
 - CHECKOUT SUBDIRECTORY
   New VCS root setting allows you to populate the stream to a sub-directory. Useful if you have multiple
   VCS roots attached to your build configurations.
   
 - FAIL ON OVERLAP
   New VCS root setting causes the build to fail if an overlap on the stream is detected   
   
 - HIDE CHANGES SETTING
   New VCS root setting forces all changes from the stream to appear as a single entry in the TeamCity change list

BUG FIXES
 - 64 bit Windows issue: Previously, the plugin would fail to find the AccuRev bin directory if the 32 bit location 
   had been specified in VCS root settings. The plugin will now attempt to find the AccuRev executable in the default 
   location for the particular OS if the specified location cannot be found.
   
 - Transactional high-watermark issue. A bug in the way the latest version number was being determined
   resulted in doubling-up of change lists shown in TeamCity. This issue was particularly evident when building
   off AccuRev snapshots. The plugin now sets the version number for each change list as the transactional 
   high-watermark of the AccuRev depot (the display version however stays as the appropriate transaction
   number for that change list).
   
 - Collect changes issue resolved around particular scenario involving AccuRev elements with element ID (EID) changes.
   These types of changes are now being excluded from change lists).


---------------------------------------------------------------------
Release 0.3 (Contributed by Daniel Nolan)
---------------------------------------------------------------------

ENHANCEMENTS
 - INHERITED CHANGES SUPPORT ADDED
   Promoted changes inherited by the VCS root stream are now included in the TeamCity changes 
   list (previously only promotes directly affecting the VCS root stream were included). Any other 
   changes, such as reverts, stream basis time modifications, or changes resulting from re-parenting 
   of a stream are grouped into a generic change group with the comment "OTHER INHERITED CHANGES".

 - DEFUNCTED/REVERTED ELEMENTS SUPPORT ADDED
   Versions of files or directories that are removed from a stream will now show as removed or edited
  (depending on the outcome) in TeamCity.

BUG FIXES
 - AccuRev plugin error "Index: 0, Size: 0"
   http://youtrack.jetbrains.net/issue/TW-6883

 - AccuRev plugin error "Unable to find the last transaction ID!"
   http://youtrack.jetbrains.net/issue/TW-6884

 - (TBC) AccuRev plugin update problems
   http://youtrack.jetbrains.net/issue/TW-8017

 - Changes to existing elements in AccuRev were sometimes showing as "added" elements instead 
   of "edited" (occurred the first time an existing element was promoted to a stream).



---------------------------------------------------------------------
Release 0.2 (Contributed by Derrick Rapp)
---------------------------------------------------------------------

BUG FIXES
 - Plug-in did not work with configurations of multiple AccuRev servers


---------------------------------------------------------------------
Release 0.1 (Contributed by Francois Retief)
---------------------------------------------------------------------
Initial release.