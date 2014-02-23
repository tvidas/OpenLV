/*
   Hive.java
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

package cert.forensics.registry;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;


/**
 * Hive
 * Represents a Windows Registry Hive
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class Hive
{
    private static final int ROOTKEY_OFFSET = 0x24;
    private int[] buffer;
    private int rootOffset;

    public Hive(File file)
    {
        try
        {
            InputStream fileStream = new FileInputStream(file);
            buffer = new int[(int)file.length()];

            /* read registry file into buffer */
            byte[] tmpb = new byte[(int)file.length()];

            int bytesRead = fileStream.read(tmpb, 0, tmpb.length);

            fileStream.close();

            if (bytesRead != tmpb.length)
            {
                System.out.println("Only read " + bytesRead + " bytes out of " + tmpb.length + " bytes!");
            }

            // perform byte to int conversion in memory
            for(int i = 0; i < tmpb.length; i++)
            {
                buffer[i] = (int) tmpb[i];
            }

            rootOffset = RegistryParser.getWord(buffer, ROOTKEY_OFFSET) + 0x1000;
        }
        catch(IOException ioe)
        {
            System.out.println("Problem: " + ioe);
        }
    }

    /**
     * inspector for rootOffset datamember
     * @return the rootOffset datamember
     */
    public int getRootOffset()
    {
        return rootOffset;
    }

    /**
     * inspector for buffer datamember
     * @return the buffer datamember (int [])
     */
    public int[] getBuffer()
    {
        return buffer;
    }
}
