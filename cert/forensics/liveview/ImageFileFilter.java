package cert.forensics.liveview;
/*
 * File extension filter for forensic image files
 * Used with the "Browse Dialog" to limit the view of files to those
 * which are known forensic image files
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

import java.io.File;

public class ImageFileFilter extends javax.swing.filechooser.FileFilter
{
	public boolean accept(File file)
	{
		String s = file.getName();
		int i = s.lastIndexOf('.');

		if(file.isDirectory())	//allow directories
			return true;
		
		if (i > 0 && i < s.length() - 1)
		{
			String extension = s.substring(i + 1).toLowerCase();
			int extValDec = -1, extValHex = -1;
			try
			{
				extValDec = Integer.parseInt(extension);	//check if extension is an integer (for split files)
			}
			catch(NumberFormatException nfe)
			{}			
			
//			try
//			{
//				extValHex = Integer.parseInt(extension, 16);	//check if extension is a hex value
//			}
//			catch(NumberFormatException nfe)
//			{}
			
			//allow: .img, .dd, .raw, .{integer}, .{2 chars}
			if (extension.equals("img") || extension.equals("dd") || extension.equals("raw") || extValDec != -1 || extension.length() == 2)
				return true;
			else
				return false;
		}
		return false;
	}

	public String getDescription()
	{
		return "Forensic Images (.img, .dd, .raw, .{split})";
	}
}
