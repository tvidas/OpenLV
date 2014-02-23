/*
   Copyright (C) 2006-2008 Carnegie Mellon University

   Tim Vidas <tvidas at gmail d0t com>
   Brian Kaplan <bfkaplan at cmu d0t edu>


   This program is free software; you can redistribute it and/or modify it
   under the terms of the GNU General Public License as published by the Free
   Software Foundation; either version 2 of the License, or (at your option)
   any later version.

   This program is distributed in the hope that it will be useful, but WITHOUT
   ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
   FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
   more details.

   You should have received a copy of the GNU General Public License along with
   this program; if not, write to the Free Software Foundation, Inc., 59 Temple
   Place, Suite 330, Boston, MA 02111-1307 USA
   */

package cert.forensics.liveview;


import java.io.IOException;
import java.io.File;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


/**
 * LogWriter
 * A class used to privide an easy interface for making basic log entries for debugging
 * The log file is created in the program execution directory and stored in a file of the format
 * yyyy_MM_dd.log
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class LogWriter
{
    private static Logger tlogger;
    private static Logger logger;
    private static String recentFile;
    private static String logFile;

    /**
     * constructor requires two files and logs to both
     *
     * @param tFile the "temporary" file to log to - MostRecentRun.log
     * @param pFile the "permnent" file to log to - LiveView.log
     */
    public LogWriter(String tFile, String pFile){
	    recentFile = tFile;
	    logFile = pFile;
	

	    //Quick check on permanent files size, right now just deletes if over 5 MB
	    File permfile = new File(pFile);
	    if(permfile.exists()){
		    if(permfile.length() > LiveViewLauncher.BYTES_PER_MB * 5){
			    try{
				permfile.delete();
				permfile.createNewFile();
			    }
			    catch(Exception e){
				    System.err.println(e.getMessage());
			    }

		    }
	    }
	    
    }

    /**
     * simply a funtion that facilitates logging, attempts to create a new log if one doesn't exist
     * @param message a message indented to be logged
     */
    public static void log(String message)
    {
        if(logger == null)
        {
            try 
            {
                //Format formatter = new SimpleDateFormat("yyyy_MM_dd");
                // Create an appending file handler
                //FileHandler handler = new FileHandler(formatter.format(new Date()) + ".log", false);
                //FileHandler handler = new FileHandler("MostRecentRun.log", false);
                FileHandler handler = new FileHandler(recentFile, false);
                handler.setFormatter(new MinimalLogFormatter());

                // Add to the desired logger
                logger = Logger.getLogger("cert.forensics.liveview");
                logger.addHandler(handler);

                FileHandler phandler = new FileHandler(logFile, true);
                phandler.setFormatter(new MinimalLogFormatter());
                logger.addHandler(phandler);

                logger.info("Note: This is a log file for LiveView.  It can safely be deleted.");
                logger.info("============================START============================");
            } 
            catch (IOException e) 
            {
                System.out.println(e.getMessage());
            }
        }
        else
            logger.info(message);
    }

}
