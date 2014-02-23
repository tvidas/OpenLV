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
    private static Logger logger;

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
                FileHandler handler = new FileHandler("MostRecentRun.log", false);
                handler.setFormatter(new MinimalLogFormatter());

                // Add to the desired logger
                logger = Logger.getLogger("cert.forensics.liveview");
                logger.addHandler(handler);

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
