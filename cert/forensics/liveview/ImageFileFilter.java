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

import java.io.File;

/**
 * ImageFileFilter
 * File extension filter for forensic image files
 * Used with the "Browse Dialog" to limit the view of files to those
 * which are known forensic image files
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class ImageFileFilter extends javax.swing.filechooser.FileFilter
{
    /**
     * tests extensions for accepts file types.
     * @param file a java File to test for acceptance (for display in the GUI)
     * @return true if the file is an accepted type
     */
    public boolean accept(File file)
    {
        String s = file.getName();
        int i = s.lastIndexOf('.');

        if(file.isDirectory())  //allow directories
            return true;

        if (i > 0 && i < s.length() - 1)
        {
            String extension = s.substring(i + 1).toLowerCase();
            int extValDec = -1, extValHex = -1;
            try
            {
                extValDec = Integer.parseInt(extension);    //check if extension is an integer (for split files)
            }
            catch(NumberFormatException nfe)
            {}          

            //          try
            //          {
            //              extValHex = Integer.parseInt(extension, 16);    //check if extension is a hex value
            //          }
            //          catch(NumberFormatException nfe)
            //          {}

            //allow: .img, .dd, .raw, .{integer}, .{2 chars}
            if (extension.equals("img") || extension.equals("dd") || extension.equals("raw") || extValDec != -1 || extension.length() == 2)
                return true;
            else
                return false;
        }
        return false;
    }

    /**
     * for use in the lower "type of file" box in GUI, mainly for the user
     * @return a string that displays the acceptable file types
     */
    public String getDescription()
    {
        return "Forensic Images (.img, .dd, .raw, .{split})";
    }
}
