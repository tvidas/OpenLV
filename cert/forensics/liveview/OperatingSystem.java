package cert.forensics.liveview;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/*
 * Represents Operating System Attributes on a disk image. 
 * 
 * Caution: OS object can mount its corresponding snapshot but does not unmount it automatically
 * Caller is responsible for unmount
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

public class OperatingSystem
{

	private String vmGuestOS;
	private String publicOSName;
	private String systemRoot;
	private static HashMap	prodNameToGuestOSMap = new HashMap();	//maps windows product name (from registry) to its corresponding vmware guestos name
	
	public OperatingSystem(String mountDriveLetter, String snapshotVMDKLoc, int partition, String fsType, String guestOSName) throws LiveViewException
	{
		//map the user displayed OS choice vals to the vmware guest os values 
		String[] guestOSVals = InternalConfigStrings.getString("LiveViewLauncher.GuestOSVals").split(","); //get the vmware guest OS values from properties file
		String[] osVals = InternalConfigStrings.getString("LiveViewLauncher.OSChoices").split(","); //get the corresponding plain text os choices from properties file

    	for(int i = 0; i < guestOSVals.length && i < osVals.length; i++)
    	{
    		//build the hash map of osname to vmware guestos val pairs
    		prodNameToGuestOSMap.put(osVals[i], guestOSVals[i]);
    	}
    	
    	boolean autoDetect = guestOSName.equalsIgnoreCase("auto");
		boolean mountableFS = false;
		
		if(fsType != null && (fsType.equals("FAT") || fsType.equals("NTFS")))	//fat and ntfs are mountable
			mountableFS = true;
		else if(isNTKernel(guestOSName) || getBaseOS(guestOSName).equalsIgnoreCase("nt"))		//any nt based system including original nt is mountable
			mountableFS = true;
		
		boolean userSelected9xOrNT4 = false;	//did the user say it is a 9x OS?					//(WORKAROUND* skip nt also (bluescreens on boot when it is mounted for some reason) 
		if(getBaseOS(guestOSName).equals("me") || getBaseOS(guestOSName).equals("98") || getBaseOS(guestOSName).equals("nt"))
			userSelected9xOrNT4 = true;
		
		//mountable non 9x (XP,2k,2003,nt,fat32-linux) and either autodetection is enabled or linux not chosen
		if(mountableFS && !userSelected9xOrNT4 && ((autoDetect || !getBaseOS(guestOSName).equals("unknown"))))	//mountable filesystem
		{
			//mount snapshot
	    	if(LiveViewLauncher.mountSnapshot(mountDriveLetter, snapshotVMDKLoc, partition))	
	    	{
	    		LiveViewLauncher.postOutput("Snapshot Mounted" + LiveViewLauncher.endL);
	    	}
	    	else
	    	{
	    		throw new LiveViewException("Snapshot Mount Failed. Could Not Auto Detect OS For Your Image.");
	    	}
	    	
        	//check the two default windows directory locations for software hive
	    	//TODO what about non standard directory locations?
        	String softHiveLoc;
        	File softwareHive;
        	boolean detectedWin9xOrLinux = false;
        	softHiveLoc = mountDriveLetter + ":\\WINNT\\system32\\config\\software";
        	softwareHive = new File(softHiveLoc);
        	if(!softwareHive.exists())
        	{
        		softHiveLoc = mountDriveLetter + ":\\WINDOWS\\system32\\config\\software";
        		softwareHive = new File(softHiveLoc);
        		if(!softwareHive.exists())
        		{
        			LiveViewLauncher.postOutput("Could not locate software hive on system" + LiveViewLauncher.endL);
        			LiveViewLauncher.postOutput("Assuming Windows 9x or Linux OS" + LiveViewLauncher.endL);
        			detectedWin9xOrLinux = true;
        		}
        	}
        	       
        	if(!userSelected9xOrNT4 && detectedWin9xOrLinux && !getBaseOS(guestOSName).equals("unknown"))	//user says OS is not 9x based, but it appears to be 9x
        	{
        		throw new LiveViewException("Your image appears to be a Windows 9x based OS, but you have selected an NT based OS. Please select the correct OS and try again. This could also be caused by the image having a non-standard windows directory.");
        	}
        	
        	if(!detectedWin9xOrLinux)	//xp,2k,2k3,nt, etc
        	{
        		//load software hive
            	if(LiveViewLauncher.loadSoftwareHive(softHiveLoc))	//load the image software hive into local system registry
            	{
            		LiveViewLauncher.postOutput("Software Hive Loaded" + LiveViewLauncher.endL);
            	}
            	else
            	{
            		throw new LiveViewException("Software Hive Load Failed");
            	}
        		
        		if(autoDetect)	//if autodetect os is enabled, query registry for it
        		{
        			this.publicOSName = queryRegistryForOSName(softHiveLoc);
        			if(this.publicOSName == null)	//software hive was found, but couldnt find ProductName key (Windows NT has this issue)
        				this.publicOSName = "Microsoft Windows NT";	
        			//get guest os name
            		this.vmGuestOS = getClosestGuestOSMapping();	//using public name, get closest vmware guest osname
        		}
        		else	//user provided os
        		{
        			this.vmGuestOS = guestOSName;			//set vmware guest os value that user set
        			this.publicOSName = getFullOSNameForGuestOS(guestOSName);	//set corresponding full os name
        		}
        	}
        	else	//detected win 9x
        	{
        		this.publicOSName = "Win9xOrLinux";
        		this.vmGuestOS = "other";
        		return;	//no further processing needed
        	}


    		//get the system root directory
    		StringBuffer regPath = new StringBuffer("HKLM\\NEWSOFTWARE\\Microsoft\\Windows");
    		
    		//for xp, 2k, 2k3, or orginial nt, check HKLM\SOFTWARE\Microsoft\WindowsNT\\CurrentVersion (otherwise just Windows)
    		if(isNTKernel(getVmGuestOS()) || getBaseOS(getVmGuestOS()).equalsIgnoreCase("nt"))	
    			regPath.append(" NT");
    		regPath.append("\\CurrentVersion");
    		

    		String tempSystemRoot = LiveViewLauncher.queryRegistry(regPath.toString(), "SystemRoot", "REG_SZ");
    		
    		//change SystemRoot value to start with 'mountDriveLetter' rather than C: or whatever it is on the actual image
    		tempSystemRoot = tempSystemRoot.trim().substring(1,tempSystemRoot.trim().length());	//cut off drive letter (ie c)
    		this.systemRoot = mountDriveLetter + tempSystemRoot;	//add mount drive letter to rest of path
    		
    		//unload software hive
        	if(LiveViewLauncher.unloadHive("SOFTWARE"))	//unload the image software hive from local machine' registry
        	{
        		LiveViewLauncher.postOutput("Software Hive Unloaded" + LiveViewLauncher.endL);
        	}
        	else
        	{
        		throw new LiveViewException("Software Hive Unload Failed");
        	}
    		
		}
		else	//not mountable filesystem type or win9x
		{
			if(!userSelected9xOrNT4)	//assume linux (non mountable - not win9x)
			{
				this.vmGuestOS = "linux";
				this.publicOSName = getFullOSNameForGuestOS(this.vmGuestOS);	//set corresponding full os name
			}
			else	//win 9x
			{
				this.vmGuestOS = guestOSName;
				this.publicOSName = getFullOSNameForGuestOS(guestOSName);
			}
		}
	}

	/*
	 * Returns the full OS name corresponding to the vm guest os value: guestOSName
	 */
	private String getFullOSNameForGuestOS(String guestOSName)
	{
		Iterator keyIterator = prodNameToGuestOSMap.keySet().iterator();
		String currKey, currVal;
		while(keyIterator.hasNext())
		{
			currKey = (String)keyIterator.next();	//full OS Name
			currVal = (String)prodNameToGuestOSMap.get(currKey);	//vm guest os val
			if(currVal.equals(guestOSName))
				return currKey;
		}
		return null;
	}
	
	private String getClosestGuestOSMapping()
	{
		String guestOSVal = (String)prodNameToGuestOSMap.get(publicOSName);
		
    	if(guestOSVal == null) //unknown OS string detected
    	{
    		//see if we can find one that closely matches it
    		String searchStr = null;
    		if(publicOSName.toUpperCase().contains(" XP"))
    			searchStr = " XP";
    		else if(publicOSName.toUpperCase().contains(" 2000"))
    			searchStr = " 2000";
    		else if(publicOSName.toUpperCase().contains(" 2003"))
    			searchStr = " 2003";
    		else if(publicOSName.toUpperCase().contains(" ME"))
    			searchStr = " ME";
    		else if(publicOSName.toUpperCase().contains(" 98"))
    			searchStr = " 98";
    		
    	    Set keys = prodNameToGuestOSMap.keySet();
    	    Iterator osKeys = keys.iterator();
    	     
    	    String osLongName, osVMGuestVal;
    	    while(osKeys.hasNext())
    	    {
    	    	osLongName = (String)osKeys.next();
    	    	osVMGuestVal = (String)prodNameToGuestOSMap.get(osLongName);
    	    	if(osLongName.toUpperCase().contains(searchStr))	//found closest match
    	    	{
//    	    		LiveViewLauncher.postOutput("Unknown OS " + publicOSName + " using closest match: " + osLongName + System.getProperty("line.separator"));
    	    		guestOSVal = osVMGuestVal;	//set guest os actual val to the closest match
    	    	}
    	    } 
		}
//		else	//win 9x or Linux -- TODO rather than default to "other" try to figure out actual guest os val
//		{
//			guestOSVal = "other";
//			publicOSName = "Windows 9x or Linux";
//		}
    	
		if(guestOSVal == null)	//no close match could be found above
			LiveViewLauncher.postError("Unknown Operating System: " + publicOSName + " Please manually choose the most similar OS from the dropdown and try again");
		
		return guestOSVal;
	}

	/*
	 * Check 
	 * 
	 * HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProductName
	 *  or
	 * HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\ProductName
	 *  
	 *  (the former is typically present on nt based systems whereas the latter
	 *  is typically present on pre NT systems)
	 *  
	 *  Input: location on the mounted image of the software hive 
	 *  Return: the ProductName value in the registry
	 *  		null indicates failure
	 */
	private String queryRegistryForOSName(String softwareHiveLoc)
	{
    	String regData = LiveViewLauncher.queryRegistry("HKLM\\NEWSOFTWARE\\Microsoft\\Windows NT\\CurrentVersion",
    									"ProductName",
    									"REG_SZ");
    	
    	if(regData == null)	//key could not be found -- probably a win9x image, so check that path for prod name
    	{
    		regData = LiveViewLauncher.queryRegistry("HKLM\\NEWSOFTWARE\\Microsoft\\Windows\\CurrentVersion",
    								"ProductName",
    								"REG_SZ");
    	}
	    return regData;
	}


	public String getPublicOSName()
	{
		return publicOSName;
	}

	/*
	 * Returns the base name for the the OS choices. This makes it easier
	 * to handle groups of OS', that for the purposes of Live View, are the same.
	 */
	public static String getBaseOS(String vmGuestOS)
	{
    	//TODO include other supported os' 
    	if(vmGuestOS.compareTo("winXPHome") == 0 || 
    		vmGuestOS.compareTo("winXPPro") == 0 || 
    		vmGuestOS.compareTo("winXPPro-64") == 0 ||
    		vmGuestOS.compareTo("winNetStandard") == 0 ||		//win2k3
    		vmGuestOS.compareTo("win2k3") == 0)
    	{
    		return "xp";
    	}
    	else if (vmGuestOS.compareTo("win2000") == 0 || 
    			 vmGuestOS.compareTo("win2000AdvServ") == 0 || 
    			 vmGuestOS.compareTo("win2000Pro") == 0 || 
    			 vmGuestOS.compareTo("win2000Serv") == 0)
    	{
    		return "2k";
    	}
    	else if(vmGuestOS.compareTo("winNT") == 0)
    	{
    		return "nt";
    	}
    	else if(vmGuestOS.compareTo("win98") == 0)
    	{
    		return "98";
    	}
    	else if(vmGuestOS.compareTo("winMe") == 0)
    	{
    		return "me";
    	}
    	else
    		return "unknown";
	}
	
	/* 
	 * returns true for any os based on NT 
	 */
	public static boolean isNTKernel(String vmGuestOS)
	{
		if( getBaseOS(vmGuestOS).compareTo("xp") == 0 || 
			getBaseOS(vmGuestOS).compareTo("2k") == 0)
			return true;
		else
			return false;
	}
	
	/*
	 * Returns the value of the %SystemRoot% environment variable for the image being launched
	 *  
	 *  Return: the value of the %SystemRoot% env variable (for windows images)
	 *  		otherwise null
	 */
	public String getSystemRoot()
	{
		return this.systemRoot;
	}
	
	
	public void setPublicOSName(String publicOSName)
	{
		this.publicOSName = publicOSName;
	}

	public String getVmGuestOS()
	{
		return vmGuestOS;
	}
}
