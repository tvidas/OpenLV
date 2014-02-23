/*
   NK_Structure.java
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
 * NK_Structure
 * Represents an NK Registry Hive Structure in a registry hive
 * All offsets are relative to the beginning of the NK structure
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class NK_Structure
{
    private int id;                 //0x0000
    private int type;               //0x0002
    private long ofs_parent;        //0x0010
    private long no_subkeys;        //0x0014
    private long ofs_lf;            //0x001C
    private long no_values;         //0x0024
    private long ofs_vallist;       //0x0028
    private long ofs_sk;            //0x002C
    private long ofs_classnam;      //0x0030
    private int len_name;           //0x0048
    private int len_classnam;       //0x004A
    private int[] keyname;          //0x004C 

    private byte[]  keynameStr;

    /**
     *  NK Struct constructor
     *
     *  @param hiveBuf the hive to populate the struct with
     *  @param offsetInHive offset into the hive for the NK_structure
     */
    public NK_Structure(int[] hiveBuf, int offsetInHive)
    {
        id              = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0000);
        type            = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0002);
        ofs_parent      = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0010);
        no_subkeys      = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0014);
        ofs_lf          = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x001C);
        no_values       = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0024);
        ofs_vallist     = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0028);
        ofs_sk          = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x002C);
        ofs_classnam    = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0030);
        len_name        = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0048);
        len_classnam    = RegistryParser.getWord(hiveBuf, offsetInHive + 0x004A);   

        keyname = new int[len_name];
        keynameStr = new byte[len_name];
        for(int i = 0; i < len_name; i++)
        {
            keyname[i] = hiveBuf[i + offsetInHive + 0x004C];
            keynameStr[i] += Character.toLowerCase(hiveBuf[i + offsetInHive + 0x004C]);
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
        sb.append("type\t\t 0x" + Integer.toHexString(type) + newL);
        sb.append("ofs_parent\t 0x" + Long.toHexString(ofs_parent) + newL);
        sb.append("no_subkeys\t 0x" + Long.toHexString(no_subkeys) + newL);
        sb.append("ofs_lf\t\t 0x" + Long.toHexString(ofs_lf) + newL);
        sb.append("no_values\t 0x" + Long.toHexString(no_values) + newL);
        sb.append("ofs_vallist\t 0x" + Long.toHexString(ofs_vallist) + newL);
        sb.append("ofs_sk\t\t 0x" + Long.toHexString(ofs_sk) + newL);
        sb.append("ofs_classnam\t 0x" + Long.toHexString(ofs_classnam) + newL);
        sb.append("len_name\t 0x" + Integer.toHexString(len_name) + newL);
        sb.append("len_classnam\t 0x" + Integer.toHexString(len_classnam) + newL);

        sb.append("keyname\t ");
        for(int i = 0; i < keyname.length && i < 10; i++)
            sb.append(Integer.toHexString(keyname[i]) + " ");

        sb.append(newL + "String Keyname: " + keynameStr);

        return sb.toString();
    }

    /**
     * inspector function for id
     * @return the id datamember
     */
    public int getId()
    {
        return id;
    }

    /**
     * inspector function for keyname
     * @return the keyname datamember
     */
    public int[] getKeyname()
    {
        return keyname;
    }

    /**
     * inspector function for len_classnam
     * @return the len_classnam datamember
     */
    public int getLen_classnam()
    {
        return len_classnam;
    }

    /**
     * inspector function for getLen_name
     * @return the getLen_name datamember
     */
    public int getLen_name()
    {
        return len_name;
    }

    /**
     * inspector function for no_subkeys
     * @return the no_subkeys datamember
     */
    public long getNo_subkeys()
    {
        return no_subkeys;
    }

    /**
     * inspector function for no_values
     * @return the no_values datamember
     */
    public long getNo_values()
    {
        return no_values;
    }

    /**
     * inspector function for getOfs_classnam
     * @return the ofs_classnam datamember
     */
    public long getOfs_classnam()
    {
        return ofs_classnam;
    }

    /**
     * inspector function for ofs_lf
     * @return the ofs_lf datamember
     */
    public long getOfs_lf()
    {
        return ofs_lf;
    }

    /**
     * inspector function for ofs_parent
     * @return the ofs_parent datamember
     */
    public long getOfs_parent()
    {
        return ofs_parent;
    }

    /**
     * inspector function for ofs_sk
     * @return the ofs_sk datamember
     */
    public long getOfs_sk()
    {
        return ofs_sk;
    }

    /**
     * inspector function for ofs_vallist
     * @return the ofs_vallist datamember
     */
    public long getOfs_vallist()
    {
        return ofs_vallist;
    }

    /**
     * inspector function for type
     * @return the type datamember
     */
    public int getType()
    {
        return type;
    }

    /**
     * inspector function for getKeynameStr
     * @return the getKeynameStr datamember
     */
    public String getKeynameStr()
    {
        return new String(keynameStr);
    }
}
