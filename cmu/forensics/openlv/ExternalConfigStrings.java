/*
   Copyright (C) 2006-2008 Carnegie Mellon University

   Tim Vidas <tvidas at gmail d0t com>


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

package cmu.forensics.openlv;

import java.util.ResourceBundle;
import java.util.Properties;
import java.util.MissingResourceException;
import java.io.*;
import java.lang.System;


/**
 * ExternalConfigStrings 
 * Resource Bundle accessor class for reading in text from the properties file dynamically
 * allows changes to program operation without re-compiling
 *
 * @author Tim Vidas
 * @version 0.7, Jan 2009
 */

public class ExternalConfigStrings 
{
	private static Properties myprops = null;
	//private final Properties myprops = getPropertiesFromHomeDir("OpenLV.properties");
	private static boolean haveWarned = false;
	private static String confFileLocation = null;

	public ExternalConfigStrings() 
	{
		try{
			myprops = getPropertiesFromHomeDir("OpenLV.properties");
		}catch(Exception ex){
			if(!haveWarned){
				System.err.println("No openlv.ini found at " + System.getProperty("user.home") + " using defaults\n");
				LogWriter.log("No openlv.ini found at " + System.getProperty("user.home") + " using defaults\n" );
			}
		}
	}
	public ExternalConfigStrings(String confFile) 
	{
		confFileLocation = confFile;
		try{
			myprops = getPropertiesFromHomeDir("OpenLV.properties");
		}catch(Exception ex){
			if(!haveWarned){
				System.err.println("No file found at " + confFile + " using defaults\n");
				LogWriter.log("No file found at " + confFile  + " using defaults\n" );
			}
		}
	}
	/**
	 * gets a config string from the properties file
	 * @param key the config item you're interested in
	 * @return the value of the config item
	 */
	public static String getString(String key) 
	{
		try 
		{
			/*
			if(myprops == null){
				try{
					myprops = getPropertiesFromHomeDir("OpenLV.properties");
				}catch(Exception ex){
					System.err.println("getString exit!!!)\n\n\nFOUNT!!!");
					System.exit(1);
				}
			}
			*/

			String s = myprops.getProperty(key);
			if(s.equals(null)){
				s = "notfound";
			}
			return s;
		} 
		catch (Exception e) 
		{
			//return '*' + key + '*';
			return "notfound";
		}
	}
	private Properties getPropertiesFromHomeDir(String propFileName) throws IOException {
		Properties props = new Properties();

		//props.load(new FileInputStream("c:/Temp/OpenLV.ini"));
		props.load(new FileInputStream(confFileLocation));

		return props;
	}

}
