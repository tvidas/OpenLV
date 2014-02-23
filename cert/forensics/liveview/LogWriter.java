package cert.forensics.liveview;

/*
 * A class used to privide an easy interface for making basic log entries for debugging
 * The log file is created in the program execution directory and stored in a file of the format
 * yyyy_MM_dd.log
 * 
 * Author: 	Brian Kaplan
 * 			bfkaplan@cmu.edu
 * 
 * Copyright (C) 2006  Carnegie Mellon University
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 * 
 */

import java.io.IOException;
import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;


public class LogWriter
{
	private static Logger logger;

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
