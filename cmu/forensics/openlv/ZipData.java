/*
   Copyright (C) 2006-2008 Carnegie Mellon University

   Tim Vidas <tvidas at gmail d0t com>

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


import java.io.*;

import javax.swing.*;

import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.*;
import java.nio.*;
import java.text.*;

import cmu.forensics.mbr.MasterBootRecord;

/**
 * ZipData 
 *
 * @author Tim Vidas
 * @version 0.7, Jan 2009
 */

public class ZipData 
{
	 
	public  static final String      endL = System.getProperty("line.separator");



	private boolean isValid;
	private String filePath;
	private String contactName;
	private String contactEmail;
	private String helpMessage;

	public ZipData()
	{
		helpMessage = null;
		isValid = false;
		filePath = null;
	}

	public boolean isValid()
	{
		return this.isValid;
	}

	public String toString()
	{
		return "zip: " + filePath;
	}


	/**
	 * inspector for filePath datamember
	 * @return filePath string
	 */
	public String getFilePath()
	{
		return this.filePath;
	}

	/**
	 * mutator for helpMessage datamember
	 * @param bs the value to store in the private datamember
	 */
	public void setHelpMessage(String hm)
	{
		this.helpMessage = hm;
		if(this.helpMessage != null && this.filePath != null){
			isValid = true;
		}
	}
	/**
	 * mutator for filePath datamember
	 * @param bs the value to store in the private datamember
	 */
	public void setFilePath(String hm)
	{
		this.filePath = hm;
		if(this.helpMessage != null && this.filePath != null){
			isValid = true;
		}
	}
	/**
	 * mutator for contactName datamember
	 * @param bs the value to store in the private datamember
	 */
	public void setContactName(String s)
	{
		this.contactName = s;
	}
	/**
	 * mutator for contactEmail datamember
	 * @param bs the value to store in the private datamember
	 */
	public void setContactEmail(String s)
	{
		this.contactEmail = s;
	}
	/**
	 * inspector for contactName datamember
	 */
	public String getContactName()
	{
		return this.contactName;
	}
	/**
	 * inspector for contactEmail datamember
	 */
	public String getContactEmail()
	{
		return this.contactEmail;
	}
	/**
	 * inspector for helpMessage datamember
	 */
	public String getHelpMessage()
	{
		return this.helpMessage;
	}


}
