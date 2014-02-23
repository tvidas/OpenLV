/*
   RI_Structure.java
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
 * RI_Structure
 * Represents an ri structure contained in a SAM Hive 
 * All offsets are relative to the beginning of the ri structure
 *
 * RI structures contain an offset to an array of LI structures.
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class RI_Structure
{
    private int     id;                 //0x0000
    private int     numLIs;             //0x0002
    private long[]  offsetLIArray;      //0x0004 

    /**
     *  RI Struct constructor
     *
     *  @param hiveBuf the hive to populate the struct with
     *  @param offsetInHive offset into the hive for the RI_structure
     */
    public RI_Structure(int[] hiveBuf, int offsetInHive)
    {
        id          = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0000);
        numLIs      = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0002);

        offsetLIArray = new long[1024];
        for(int i = 0; i < 1024; i++)
            offsetLIArray[i] = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x004);
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
        sb.append("no_keys\t\t 0x" + Integer.toHexString(numLIs) + newL);

        sb.append("ofs_li_array\t ");
        for(int i = 0; i < offsetLIArray.length && i < 10; i++)
            sb.append(Long.toHexString(offsetLIArray[i]) + " ");

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
     * inspector for numLIs
     * @return the numLIs datamember
     */
    public int getNo_lis()
    {
        return numLIs;
    }

    /**
     * inspector for offsetLIArray
     * @return the offsetLIArray datamember
     */
    public long[] getOfs_li_array()
    {
        return offsetLIArray;
    }
}
