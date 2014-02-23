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

package cmu.forensics.openlv;

import java.io.File;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;

/**
 * OperatingSystem 
 * Represents Operating System Attributes from the disk image.  
 *
 * Caution: OS object can mount its corresponding snapshot but does not unmount it automatically
 * Caller is responsible for unmount
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class OperatingSystem
{

    private String vmGuestOS;
    private String publicOSName;
    private String systemRoot;
    private static HashMap  prodNameToGuestOSMap = new HashMap();  //maps windows product name (from registry) to its corresponding vmware guestos name
    private int partitionID;

    /**
     * class constructor, automatically mounts if needed, DOES NOT UNMOUNT - the caller is responsible for that
     * many of the parameters are expected to come from the PhysicalDiskInfo
     * @param mountDriveLetter the drive letter on the host OS that the vmdk is (or will be mounted to)
     * @param snapshotVMDKLoc the location of the snapshot VMDK to mount (typically ending in -00001)
     * @param partition which partition on the disk hold the OS
     * @param fsType the type of filesystem on the partition
     * @param guestOSName the name of the guest OS as specified in the GUI (so it can be "auto")
     */
    public OperatingSystem(String mountDriveLetter, String snapshotVMDKLoc, int partition, String fsType, String guestOSName) throws OpenLVException
    {
	publicOSName = guestOSName;	

        //map the user displayed OS choice vals to the vmware guest os values 
        String[] guestOSVals = InternalConfigStrings.getString("OpenLVLauncher.GuestOSVals").split(","); //get the vmware guest OS values from properties file
        String[] osVals = InternalConfigStrings.getString("OpenLVLauncher.OSChoices").split(","); //get the corresponding plain text os choices from properties file

        //TODO virtual machine add types 
        
	partitionID = partition;

        for(int i = 0; i < guestOSVals.length && i < osVals.length; i++)
        {
            //build the hash map of osname to vmware guestos val pairs
            prodNameToGuestOSMap.put(osVals[i], guestOSVals[i]);
        }

        boolean autoDetect = guestOSName.equalsIgnoreCase("auto");
        boolean mountableFS = false;

        if(fsType != null && (fsType.equals("FAT") || fsType.equals("NTFS")))  //fat and ntfs are mountable
            mountableFS = true;
        else if(isNTKernel(guestOSName) || getBaseOS(guestOSName).equalsIgnoreCase("nt"))    //any nt based system including original nt is mountable
            mountableFS = true;

        boolean userSelected9xOrNT4 = false;  //did the user say it is a 9x OS?          //(WORKAROUND* skip nt also (bluescreens on boot when it is mounted for some reason) 
        if(getBaseOS(guestOSName).equals("me") || getBaseOS(guestOSName).equals("98") || getBaseOS(guestOSName).equals("nt"))
            userSelected9xOrNT4 = true;

	if(OpenLVLauncher.isVMWare() && OpenLVLauncher.isWindows()){  //TODO remove stupid patch for Virtualbox and linux development
		//mountable non 9x (XP,2k,2003,nt,fat32-linux) and either autodetection is enabled or linux not chosen
		if(mountableFS && !userSelected9xOrNT4 && ((autoDetect || !getBaseOS(guestOSName).equals("unknown"))))  //mountable filesystem
		{
	System.out.println("m " + mountableFS + " " + userSelected9xOrNT4 + " " + autoDetect + " " + getBaseOS(guestOSName));
		    //mount snapshot
		    if(OpenLVLauncher.mountSnapshot(mountDriveLetter, snapshotVMDKLoc, partition))  
		    {
			OpenLVLauncher.postOutput("Snapshot Mounted" + OpenLVLauncher.endL);
		    }
		    else
		    {
			OpenLVLauncher.postOutput("Snap mount failed.  Tried: " + mountDriveLetter + "," + snapshotVMDKLoc + "," + partition + OpenLVLauncher.endL);
			throw new OpenLVException("Snapshot Mount Failed. Could Not Auto Detect OS For Partition.");
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
			    OpenLVLauncher.postOutput("Could not locate software hive on partition" + OpenLVLauncher.endL);
			    OpenLVLauncher.postOutput("Assuming Windows 9x or Linux OS" + OpenLVLauncher.endL);
			    detectedWin9xOrLinux = true;
			}
		    }

		    if(!userSelected9xOrNT4 && detectedWin9xOrLinux && !getBaseOS(guestOSName).equals("unknown"))  //user says OS is not 9x based, but it appears to be 9x
		    {
			throw new OpenLVException("Your image appears to be a Windows 9x based OS, but you have selected an NT based OS. Please select the correct OS and try again. This could also be caused by the image having a non-standard windows directory.");
		    }

		    if(!detectedWin9xOrLinux)  //xp,2k,2k3,nt, etc
		    {
			//load software hive
			if(OpenLVLauncher.loadSoftwareHive(softHiveLoc))  //load the image software hive into local system registry
			{
			    OpenLVLauncher.postOutput("Software Hive Loaded" + OpenLVLauncher.endL);
			}
			else
			{
			    throw new OpenLVException("Software Hive Load Failed");
			}

			if(autoDetect)  //if autodetect os is enabled, query registry for it
			{
			    this.publicOSName = queryRegistryForOSName(softHiveLoc);
			    if(this.publicOSName == null)  //software hive was found, but couldnt find ProductName key (Windows NT has this issue)
				this.publicOSName = "Microsoft Windows NT";  
			    //get guest os name
			    this.vmGuestOS = getClosestGuestOSMapping();  //using public name, get closest vmware guest osname
			}
			else  //user provided os
			{
			    this.vmGuestOS = guestOSName;      //set vmware guest os value that user set
			    this.publicOSName = getFullOSNameForGuestOS(guestOSName);  //set corresponding full os name
			}
		    }
		    else  //detected win 9x
		    {
			this.publicOSName = "Win9xOrLinux";
			this.vmGuestOS = "other";
			return;  //no further processing needed
		    }


		    //get the system root directory
		    StringBuffer regPath = new StringBuffer("HKLM\\NEWSOFTWARE\\Microsoft\\Windows");

		    //for xp, 2k, 2k3, or orginial nt, check HKLM\SOFTWARE\Microsoft\WindowsNT\\CurrentVersion (otherwise just Windows)
		    if(isNTKernel(getVmGuestOS()) || getBaseOS(getVmGuestOS()).equalsIgnoreCase("nt"))  
			regPath.append(" NT");
		    regPath.append("\\CurrentVersion");


		    String tempSystemRoot = OpenLVLauncher.queryRegistry(regPath.toString(), "SystemRoot", "REG_SZ");

		    //change SystemRoot value to start with 'mountDriveLetter' rather than C: or whatever it is on the actual image
		    tempSystemRoot = tempSystemRoot.trim().substring(1,tempSystemRoot.trim().length());  //cut off drive letter (ie c)
		    this.systemRoot = mountDriveLetter + tempSystemRoot;  //add mount drive letter to rest of path

		    //unload software hive
		    if(OpenLVLauncher.unloadHive("SOFTWARE"))  //unload the image software hive from local machine' registry
		    {
			OpenLVLauncher.postOutput("Software Hive Unloaded" + OpenLVLauncher.endL);
		    }
		    else
		    {
			throw new OpenLVException("Software Hive Unload Failed");
		    }

		}
	}//REMOVE isVMWare()  TODO FIXME
        else  //not mountable filesystem type or win9x
        {
            if(!userSelected9xOrNT4)  //assume linux (non mountable - not win9x)
            {
                this.vmGuestOS = "linux";
                this.publicOSName = getFullOSNameForGuestOS(this.vmGuestOS);  //set corresponding full os name
            }
            else  //win 9x
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
            currKey = (String)keyIterator.next();  //full OS Name
            currVal = (String)prodNameToGuestOSMap.get(currKey);  //vm guest os val
            if(currVal.equals(guestOSName))
                return currKey;
        }
        return null;
    }

    /**
     * for use in auto detection of OS, simple string match attempting to determine Windows based OSes based on name (eg 2000, XP, etc)
     * @returns the guessed OS or "unknown"
     */
    private String getClosestGuestOSMapping()
    {
        String guestOSVal = (String)prodNameToGuestOSMap.get(publicOSName);

        if(guestOSVal == null) //unknown OS string detected
        {
            //see if we can find one that closely matches it
            String searchStr = null;
            if(publicOSName.toUpperCase().contains(" XP"))
                searchStr = " XP";
            else if(publicOSName.toUpperCase().contains("WINDOWS7"))
                searchStr = "WINDOWS7";
            else if(publicOSName.toUpperCase().contains("WINDOWS 7"))
                searchStr = "WINDOWS 7";
            else if(publicOSName.toUpperCase().contains(" VISTA"))
                searchStr = " VISTA";
            else if(publicOSName.toUpperCase().contains(" 2000"))
                searchStr = " 2000";
            else if(publicOSName.toUpperCase().contains(" 2008"))
                searchStr = " 2008";
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
                if(osLongName.toUpperCase().contains(searchStr))  //found closest match
                {
                    //              OpenLVLauncher.postOutput("Unknown OS " + publicOSName + " using closest match: " + osLongName + System.getProperty("line.separator"));
                    guestOSVal = osVMGuestVal;  //set guest os actual val to the closest match
                }
            } 
        }
        //    else  //win 9x or Linux -- TODO rather than default to "other" try to figure out actual guest os val
        //    {
        //      guestOSVal = "other";
        //      publicOSName = "Windows 9x or Linux";
        //    }

        if(guestOSVal == null){  //no close match could be found above
            OpenLVLauncher.postError("Unknown Operating System: " + publicOSName + " Please manually choose the most similar OS from the dropdown and try again");
	}else{
            OpenLVLauncher.postOutput("AutoDetected Operating System: " + publicOSName + " " + OpenLVLauncher.endL);
	}

        return guestOSVal;
    }

    /**
     * Checks the registry for particular values
     * 
     * HKLM\SOFTWARE\Microsoft\Windows NT\CurrentVersion\ProductName
     *  or
     * HKLM\SOFTWARE\Microsoft\Windows\CurrentVersion\ProductName
     *  
     *  (the former is typically present on nt based systems whereas the latter
     *  is typically present on pre NT systems)
     *  
     * @param softwareHiveLoc location on the mounted image of the software hive 
     * @return the ProductName value in the registry, or null on failure
     */
    private String queryRegistryForOSName(String softwareHiveLoc)
    {
        String regData = OpenLVLauncher.queryRegistry("HKLM\\NEWSOFTWARE\\Microsoft\\Windows NT\\CurrentVersion", "ProductName", "REG_SZ");

        if(regData == null)  //key could not be found -- probably a win9x image, so check that path for prod name
        {
            regData = OpenLVLauncher.queryRegistry("HKLM\\NEWSOFTWARE\\Microsoft\\Windows\\CurrentVersion", "ProductName", "REG_SZ");
        }
        return regData;
    }

    /**
     * inspector for partitionID 
     * @return partitionID datamember
     */
    public int getPartitionID()
    {
        return partitionID;
    }

    /**
     * inspector for publicOSName
     * @return publicOSName datamember
     */
    public String getPublicOSName()
    {
        return publicOSName;
    }

    /**
     * Returns the base name for the the OS choices. This makes it easier
     * to handle groups of OS', that for the purposes of OpenLV, are the same.
     * @param vmGuestOS the detected or specified OS
     * @return the 'converted' OS, for use with VMWare
     */
    public static String getBaseOS(String vmGuestOS)
    {
        //TODO include other supported os' 
        if(vmGuestOS.compareTo("winXPHome") == 0 || 
		//vmware
                vmGuestOS.compareTo("winXPPro") == 0 || 
                vmGuestOS.compareTo("winXPPro-64") == 0 ||
                vmGuestOS.compareTo("winNetStandard") == 0 ||      //win2k3
                vmGuestOS.compareTo("winNetEnterprise") == 0 ||    //win2k3
                vmGuestOS.compareTo("winNetWeb") == 0 ||           //win2k3
                vmGuestOS.compareTo("winNetBusiness") == 0 ||      //win2k3
                vmGuestOS.compareTo("winNetStandard-64") == 0 ||   //win2k3
                vmGuestOS.compareTo("winNetEnterprise-64") == 0 || //win2k3
                vmGuestOS.compareTo("whistler") == 0 || 
                vmGuestOS.compareTo("win2k3") == 0||

                vmGuestOS.compareTo("WindowsXP") == 0 ||
                vmGuestOS.compareTo("WindowsXP_64") == 0 ||
                vmGuestOS.compareTo("Windows2003") == 0 ||
                vmGuestOS.compareTo("Windows2003_64") == 0)
        {
            return "xp";
        }
        else if (vmGuestOS.compareTo("winvista") == 0 || 
                vmGuestOS.compareTo("winvista-64") == 0 ||    //vista

                vmGuestOS.compareTo("WindowsVista") == 0 ||    //vista
                vmGuestOS.compareTo("WindowsVista_64") == 0 )    //vista

        {
            return "vista";
        }
        else if (vmGuestOS.compareTo("longhorn") == 0 ||  //server 2008
                vmGuestOS.compareTo("longhorn-64") == 0  ||   //server 2008
		vmGuestOS.compareTo("winServer2008Web-32") == 0 ||
		vmGuestOS.compareTo("winServer2008Web-64") == 0 ||
		vmGuestOS.compareTo("winServer2008StandardCore-32") == 0 ||
		vmGuestOS.compareTo("winServer2008StandardCore-64") == 0 ||
		vmGuestOS.compareTo("winServer2008Standard-32") == 0 ||
		vmGuestOS.compareTo("winServer2008Standard-64") == 0 ||
		vmGuestOS.compareTo("winServer2008SmallBusinessPremium-32") == 0 ||
		vmGuestOS.compareTo("winServer2008SmallBusinessPremium-64") == 0 ||
		vmGuestOS.compareTo("winServer2008SmallBusiness-32") == 0 ||
		vmGuestOS.compareTo("winServer2008SmallBusiness-64") == 0 ||
		vmGuestOS.compareTo("winServer2008EnterpriseCore-32") == 0 ||
		vmGuestOS.compareTo("winServer2008EnterpriseCore-64") == 0 ||
		vmGuestOS.compareTo("winServer2008Enterprise-32") == 0 ||
		vmGuestOS.compareTo("winServer2008Enterprise-64") == 0 ||
		vmGuestOS.compareTo("winServer2008DatacenterCore-32") == 0 ||
		vmGuestOS.compareTo("winServer2008DatacenterCore-64") == 0 ||
		vmGuestOS.compareTo("winServer2008Datacenter-32") == 0 ||
		vmGuestOS.compareTo("winServer2008Datacenter-64") == 0 ||
		vmGuestOS.compareTo("winServer2008Cluster-32") == 0 ||
		vmGuestOS.compareTo("winServer2008Cluster-64") == 0 ||

                vmGuestOS.compareTo("Windows2008") == 0||
                vmGuestOS.compareTo("Windows2008_64") == 0)
        {
            return "2008";
        }
        else if (vmGuestOS.compareTo("windows7") == 0 || 
                vmGuestOS.compareTo("windows7srv-64") == 0 || 
                vmGuestOS.compareTo("windows7-64") == 0||

                vmGuestOS.compareTo("Windows7") == 0||
                vmGuestOS.compareTo("Windows7_64") == 0||
                vmGuestOS.compareTo("Windows8") == 0||
                vmGuestOS.compareTo("Windows8_64") == 0||
                vmGuestOS.compareTo("Windows2012_64") == 0)
        {
            return "win7";
        }
        else if (vmGuestOS.compareTo("win2000") == 0 || 
                vmGuestOS.compareTo("win2000AdvServ") == 0 || 
                vmGuestOS.compareTo("win2000Pro") == 0 || 
                vmGuestOS.compareTo("win2000Serv") == 0||

                vmGuestOS.compareTo("Windows2000") == 0 )
        {
            return "2k";
        }
        else if(vmGuestOS.compareTo("winNT") == 0||
                vmGuestOS.compareTo("WindowsNT4") == 0 )
        {
            return "nt";
        }
        else if(vmGuestOS.compareTo("win98") == 0||
                vmGuestOS.compareTo("Windows98") == 0 )
        {
            return "98";
        }
        else if(vmGuestOS.compareTo("winMe") == 0||
                vmGuestOS.compareTo("WindowsMe") == 0 )
        {
            return "me";
        }
        else if(vmGuestOS.compareTo("win95") == 0||
                vmGuestOS.compareTo("Windows95") == 0 )
        {
            return "95";
        }
        else if(vmGuestOS.compareTo("win31") == 0||
                vmGuestOS.compareTo("Windows31") == 0 )
        {
            return "31";
        }
        else
            return "unknown";
    }

    /**
     * used to determine if the OS uses an NT based kernel (based solely on name)
     * @return true for any os based on NT 
     */
    public static boolean isNTKernel(String vmGuestOS)
    {
        if( (getBaseOS(vmGuestOS).compareTo("xp") == 0) || 
                (getBaseOS(vmGuestOS).compareTo("2k") == 0) ||
                (getBaseOS(vmGuestOS).compareTo("2003") == 0) ||
                (getBaseOS(vmGuestOS).compareTo("2008") == 0) ||
                (getBaseOS(vmGuestOS).compareTo("win7") == 0) ||
                (getBaseOS(vmGuestOS).compareTo("vista") == 0))
            return true;
        else
            return false;
    }

    /**
     * inspector for systemRoot 
     * @return the value of the %SystemRoot% env variable (for windows images); null on failuer
     */
    public String getSystemRoot()
    {
        return this.systemRoot;
    }

    /**
     * mutator for publicOSName
     * @param publicOSName to set the private datamember
     */
    public void setPublicOSName(String publicOSName)
    {
        this.publicOSName = publicOSName;
    }

    /**
     * inspector for vmGuestOS
     * @return vmGuestOS data member
     */
    public String getVmGuestOS()
    {
        return vmGuestOS;
    }
}
