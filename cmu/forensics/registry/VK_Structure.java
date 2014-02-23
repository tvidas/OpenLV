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

package cmu.forensics.registry;

/**
 * VK_Structure
 * Represents a vk structure contained in a SAM Hive 
 * All offsets are relative to the beginning of the vk structure
 * 
 * VK Structures appear to be similar to similar to leaf nodes containing 
 * the actual data (or rather an offset pointing directly to its data)
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class VK_Structure
{
	private int 	id;					//0x0000
	private int 	lenName;			//0x0002
	private long 	lenData;			//0x0004
	private long 	offsetData;			//0x0008
	private long 	valType;			//0x000C
	private int[] 	keyName;			//0x0014 
	
	private byte[]  keyNameString;

	   /**
     *  VK Struct constructor
     *
     *  @param hiveBuf the hive to populate the struct with
     *  @param offsetInHive offset into the hive for the VK_structure
     */
	public VK_Structure(int[] hiveBuf, int offsetInHive)
	{
		id 				= RegistryParser.getWord(hiveBuf, offsetInHive + 0x0000);
		lenName			= RegistryParser.getWord(hiveBuf, offsetInHive + 0x0002);
		lenData 		= RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0004);
		offsetData 		= RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0008);
		valType	 		= RegistryParser.getDWord(hiveBuf, offsetInHive + 0x000C);
		
		keyName = new int[lenName];
		keyNameString = new byte[lenName];
		for(int i = 0; i < lenName; i++)
		{
			keyName[i] = hiveBuf[i + offsetInHive + 0x0014];
			keyNameString[i] += Character.toLowerCase(hiveBuf[i + offsetInHive + 0x0014]);
		}
	}

    /**
     * generic toString method that assembles datamembers
     * @return a formated string
     */
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		String newL = System.getProperty("line.separator");
		
		sb.append("id\t 0x" + Integer.toHexString(id) + newL);
		sb.append("len_name\t\t 0x" + Integer.toHexString(lenName) + newL);
		sb.append("len_data\t 0x" + Long.toHexString(lenData) + newL);
		sb.append("ofs_data\t 0x" + Long.toHexString(offsetData) + newL);
		sb.append("val_type\t 0x" + Long.toHexString(valType) + newL);
		
		sb.append("keyname\t ");
		for(int i = 0; i < keyName.length && i < 10; i++)
			sb.append(Integer.toHexString(keyName[i]) + " ");
		
		sb.append(newL + "String Keyname: " + new String(keyNameString).substring(0,8));
		
		return sb.toString();
	}

    /**
     * inspector for id
     * @return the id datamember
     */
	public int getId()
	{
		return id;
	}

    /**
     * inspector for keyName
     * @return the keyName datamember
     */
	public int[] getKeyname()
	{
		return keyName;
	}

    /**
     * inspector for lenData
     * @return the lenData datamember
     */
	public long getLen_data()
	{
		return lenData;
	}

    /**
     * inspector for lenName
     * @return the lenName datamember
     */
	public int getLen_name()
	{
		return lenName;
	}

    /**
     * inspector for offsetData
     * @return the offsetData datamember
     */
	public long getOfs_data()
	{
		return offsetData;
	}

    /**
     * inspector for valType
     * @return the valType datamember
     */
	public long getVal_type()
	{
		return valType;
	}

    /**
     * inspector for KeynameStr
     * @return the keyNameString datamember
     */
	public byte[] getKeynameStr()
	{
		return keyNameString;
	}
}
