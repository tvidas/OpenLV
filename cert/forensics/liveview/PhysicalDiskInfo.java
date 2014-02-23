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

/**
 * PhysicalDiskInfo
 * Represents a Physical Disk Device on a Windows System. 
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class PhysicalDiskInfo
{
    private String 	index;			// index 'X' in \\.\PhysicalDriveX
    private String 	interfaceType;	// interface (IDE, 1394, usb, etc)
    private String 	model;			// model name for the drive
    private double 	size;			// size in bytes

    public PhysicalDiskInfo(String index, String interfaceType, String model, double size)
    {
        this.index = index;
        this.interfaceType = interfaceType;
        this.model = model;
        this.size = size;
    }

    /**
     * tests for a removable device
     * @return true if the device is a usb or firewire device
     */
    public boolean isRemovable()
    {
        if(interfaceType.equalsIgnoreCase("usb") || interfaceType.equalsIgnoreCase("1394"))
            return true;
        return false;
    }

    /**
     * gets the physical device name
     * @return the device name for this device: \\.\PhysicalDevice{index}
     */
    public String getDeviceName()
    {
        return "\\\\.\\PhysicalDrive" + index;
    }

    /**
     * gets just the index of the physical device
     * @return the device index 'X' in \\.\PhysicalDeviceX
     */
    public String getIndex()
    {
        return index;
    }

    /**
     * gets the interface type for this device
     * @return the interface on which the physical disk is connected (usb, 1394, ide, etc)
     */
    public String getInterfaceType()
    {
        return interfaceType;
    }

    /**
     * gets the model name of the device (Maxtor, Seagate, etc)
     * @return the model name for the device
     */
    public String getModel()
    {
        return model;
    }

    /**
     * gets the size of the device
     * @return the size of the device (in bytes)
     */
    public double getSize()
    {
        return size;
    }

    /**
     * generic toString function that returns many datamembers of the class
     * @return a combined string representing this instance
     */
    public String toString()
    {
        return "Index: " + index + " Interface: " + interfaceType + " Model: " + model + " Size: " + size;
    }

}
