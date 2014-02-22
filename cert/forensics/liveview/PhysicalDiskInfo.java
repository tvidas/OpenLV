package cert.forensics.liveview;

/*
 * Represents a Physical Disk Device on a Windows System. 
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
	
	/*
	 * Return true if the device is a usb or firewire device
	 */
	public boolean isRemovable()
	{
		if(interfaceType.equalsIgnoreCase("usb") || interfaceType.equalsIgnoreCase("1394"))
			return true;
		return false;
	}
	
	/*
	 * Return the device name for this device: \\.\PhysicalDevice{index}
	 */
	public String getDeviceName()
	{
		return "\\\\.\\PhysicalDrive" + index;
	}

	/*
	 * Return the device index 'X' in \\.\PhysicalDeviceX
	 */
	public String getIndex()
	{
		return index;
	}

	/*
	 * Return the interface on which the physical disk is connected (usb, 1394, ide, etc)
	 */
	public String getInterfaceType()
	{
		return interfaceType;
	}

	/*
	 * Return the model name for the device
	 */
	public String getModel()
	{
		return model;
	}

	/*
	 * Return the size of the device (in bytes)
	 */
	public double getSize()
	{
		return size;
	}

}
