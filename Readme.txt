GwGcalSync
==========

A simple Java application to push Groupwise calendar data to Google Calendar.

This application is a melding of two OpenSource applications.  Without the work of these stand-alone apps, this tool would not exist.

* Export from Groupwise to ICS format is accomplished using GroupwiseExporter by Ben Galbraith from 
  http://code.google.com/p/groupwise-exporter/.  Used under GPLv2 license.

* Push to Google Calender is via gCalDaemon from http://gcaldaemon.sourceforge.net/.  
  Used under Apache License Version 2 and various other third party open source licenses.

Any derived work in this project is made available under the respective original license (GPLv2 or APLv2).  Work created anew and
not derived for this application may be used under either GPLv2 or APLv2 at your option.

Usage
=====

GwGcalSync reads calendar data from the Groupwise web interface, saves it to a local file, and (if changed) pushed it to Google.  The
application is packaged as a simple launchable JAR file.  To use it, you must have a Java 6 JRE installed.  Before launching for the
first time, you must create a working directory and edit the configuration file to suit your needs.  You must create a directory named
".gwgcalsync" under your user's home directory (/home/yourname, /Users/yourname, C:\Users\yourname, C:\Documents and Settings\yourname)
depending on your operating system.  You may wish to install the JAR files from this project into that directory, though you're
not required to.  Copy the settings.properties file into that directory and edit it according to the comments in that file.

By default, GwGcalSync will operate in daemon mode which means once launched, the app will automatically wake every few minutes to
check for updates.  You may wish to launch the app with "nohup" on Unix-like operating systems à la:

  nohup java -jar gwgcalsync.jar &

All output will be written to log files in a "logs" directory under $HOME/.gwgcalsync/logs/.

It's also possible to run GwGcalSync in "one shot" mode where the app runs a single sync cycle then shuts down.  This is more suitable
for launching via cron, but it requires spawning a new JVM for each invocation which may be less efficient.

Known Issues
============

* Time zone is currently hard coded to America/New_York.  Editing the VcalendarExporter class is required to change this.  Future
versions will allow setting time zone as a parameter.

* There is currently no way to trigger syncing on demand.  Plans are to add a special URL to the HTTP server option which can trigger
this.

* The description of calendar events is not currently exported from Groupwise.

* Logging is mirrored to console & file which can needlessly fill up nohup.out.  Need a -quiet flag to squelch console logging in
favor of file logging only.

Contact / Support
=================

GwGcalSync is offered without any warranty or support.  If it breaks, you get to keep the pieces.  That said, I'll try to help if
possible, though the time I have to dedicate to this application is very limited.  You can contact the author Zachary Bedell
at zac at iphone bookshelf dot com.

