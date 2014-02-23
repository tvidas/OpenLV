/*
    LI_Structure
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
 * LI_Structure
 * Represents an li structure contained in a registry Hive 
 * All offsets are relative to the beginning of the li structure
 * 
 * LI structures point to an array of nk structure offsets
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class LI_Structure
{
	private int 	id;			//0x0000
	private int 	numKeys;		//0x0002
	private long[] 	offsetNKArray;		//0x0004 
	
	public LI_Structure(int[] hiveBuf, int offsetInHive)
	{
		id 		= RegistryParser.getWord(hiveBuf, offsetInHive + 0x0000);
		numKeys		= RegistryParser.getWord(hiveBuf, offsetInHive + 0x0002);
		
		offsetNKArray = new long[1024];
		for(int i = 0; i < 1024; i++)
			offsetNKArray[i] = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x004);
	}

    /**
     * simple toString implementation that assembles class datamembers
     * @return the formatted string
     */
	public String toString()
	{
		StringBuffer sb = new StringBuffer();
		String newL = System.getProperty("line.separator");
		
		sb.append("id\t 0x" + Integer.toHexString(id) + newL);
		sb.append("no_keys\t\t 0x" + Integer.toHexString(numKeys) + newL);
		
		sb.append("ofs_nk_array\t ");
		for(int i = 0; i < offsetNKArray.length && i < 10; i++)
			sb.append(Long.toHexString(offsetNKArray[i]) + " ");
		
		return sb.toString();
	}

    /**
     * inspector for id
     * @return id datamember
     */
	public int getId()
	{
		return id;
	}
   
    /**
     * inspector for numKeys
     * @return numKeys datamember
     */
	public int getNo_keys()
	{
		return numKeys;
	}

    /**
     * inspector for offsetNKArray
     * @return offsetNKArray
     */
	public long[] getOfs_nk_array()
	{
		return offsetNKArray;
	}
}
