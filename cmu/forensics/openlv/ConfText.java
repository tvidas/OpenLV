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
/*
import java.util.ResourceBundle;
import java.util.Properties;
import java.util.MissingResourceException;
import java.io.*;
import java.lang.System;
*/

/**
 * ConfText 
 *
 * @author Tim Vidas
 * @version 0.7, Jan 2009
 */

public class ConfText
{
	public static String theText = 
"#\r\n" +
"# This file contains some useful program text/parameters that can be modified without recompiling\r\n" +
"# To Make changes to program behavior, modify and save this file. Then rerun the program\r\n" +
"# and the changes should appear. \r\n" +
"#\r\n" +
"# - Do not modify any text appearing to the left of the equal sign\r\n" +
"# - Do not delete any of the lines (only text after the '=')\r\n" +
"# - Do not rename this file or move it as the program needs this file to load\r\n" +
"# - Lines begining with a # are comments and will be ignored\r\n" +
"# - Note that in general you should not have trailing spaces after the configuration items\r\n" +
"#\r\n" +
"############################################################################################\r\n" +
"\r\n" +
"##############################################\r\n" +
"#        Program Behavior Parameters\r\n" +
"##############################################\r\n" +
"\r\n" +
"#\r\n" +
"# \r\n" +
"#Enable or disable advanced features, must be true or false \r\n" +
"OpenLV.EnableAdvancedFeatures=false \r\n" +
"#\r\n" +
"#Default RAM size (must be a multiple of 4)\r\n" +
"#OpenLV.DefaultRamSize=1024\r\n" +
"\r\n" +
"#set the start time for the VM, <NOW> indicates the current time\r\n" +
"#setting to a specific time must follow the example format MMM dd yyyy HH:mm:ss z\r\n" +
"#Note, hour is in 24 hour format, z is for timezone: CST, EDT, GMT, -0600, etc\r\n" +
"#note: the virutal BIOS may not permit dates too far in the future (or past) and will adjust the date to be within a supported range\r\n" +
"#for further information on the format see http://java.sun.com/j2se/1.4.2/docs/api/java/text/SimpleDateFormat.html\r\n" +
"OpenLV.DefaultSystemTime=<NOW>\r\n" +
"#OpenLV.DefaultSystemTime=MMM dd yyyy HH:mm:ss z\r\n" +
"#OpenLV.DefaultSystemTime=Dec 08 1979 11:20:01 CST\r\n" +
"#OpenLV.DefaultSystemTime=Dec 08 1979 11:20:01 -600\r\n" +
"\r\n" +
"#default state of radio buttons (set to true or false)\r\n" +
"OpenLV.DefaultClearPassword=true\r\n" +
"OpenLV.DefaultClearDomainPassword=true\r\n" +
"OpenLV.DefaultDumpHives=true\r\n" +
"\r\n" +
"#default output directory, <HOME> indicates your home directory\r\n" +
"#use the full path to specify another directory, backslashes must be escaped: C:\\\\temp\\\\openlv\\\\VMs\r\n" +
"OpenLV.DefaultOutputDirectory=<HOME>\r\n" +
"#OpenLV.DefaultOutputDirectory=C:\\temp\r\n" +
"\r\n" +
"#string delemiter used to differentiate files when specify more than one in a single GUI box (eg split image disks)\r\n" +
"#note almost all users should be fine using the default value of \"; \" (a semicolon with one space after)\r\n" +
"#if you happen to use a directory or file naming conention that includes \"; \" then you may need to change this\r\n" +
"#OpenLV.FileSeperator=; \r\n" +
"#Note the space after the ; !!\r\n" +
"\r\n" +
"\r\n" +
"#the number of physical devices that should be enumerated when OpenLV looks for Physical Disk evidence\r\n" +
"#you likely only need to increase this if your exam machine has more than 30 physical devices (IDE/SATA/Fibrechannel/etc)\r\n" +
"#OpenLV.MaxPhysicalDrives=60\r\n" +
"\r\n" +
"\r\n" +
"\r\n" +
""
;

}
