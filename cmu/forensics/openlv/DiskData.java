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
 * DiskData 
 *
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class DiskData 
{
	 
	public  static final String      endL = System.getProperty("line.separator");
        private static final String      PARENT_DISK_SIZE_GB = "950";   //parent disk size for partitions (arbitrary large size -- was previously a user supplied value)
        public static final long         BYTES_PER_GIG       = 1073741824;   //2^30 bytes per gig
        public static final long         BYTES_PER_MB        = 1048576;



	private MasterBootRecord mbr;
	private boolean isPhysicalDisk;
	private String[] files;
	private String[] pathForInputFiles;
	private File[] imgFiles;
	private String physicalDiskInfo;
	private String physicalDiskName;
	private String physicalDiskModel;
	private String bootSource;
	private boolean isValid;
	public StringBuffer vmdkBuffer; 
	private boolean isChunked;
	private boolean isFullDisk;
	private String filename;

	public DiskData()
	{
		isPhysicalDisk = false;
		isValid = false;
		vmdkBuffer = new StringBuffer();
	}

	public boolean isValid()
	{
		return this.isValid;
	}

	public String toString()
	{
		return bootSource + ": " + Arrays.toString(files);
	}

	public void setFiles(String[] f,JFrame frame,File outDir) throws OpenLVException
	{
		//XXXX TODO needs to throw error or sanity check so we don't add crap disks
		  if(!(f.equals("") || f.equals("ImageFile: [Select Your Image File(s)]"))){
			this.files = new String[f.length];
			System.arraycopy(f,0,this.files,0,f.length);

                        sortChunkFileNamesByExtension(files);   //sort the file extensions so they can be concatenated in order
                        
                        if(files.length == 1)
                            isChunked = false;   //single file
                        else
                            isChunked = true;   //multiple chunked files   

			if(checkDisk(frame,outDir)){
				isValid = true;
			}
		  }
	}
        /**
         * Sorts the file extensions of chunked images so that they can be 
         * put in order before attempting to boot the image
         * 
         * Sorts purely numeric extensions numerically, and purely alphabetic and alphanumeric
         * extensions alphabetically
         *
         * @param chunkFiles the image file chunk array
         */
        private static void sortChunkFileNamesByExtension(String[] chunkFiles)
        {
            String[] extensions = new String[chunkFiles.length];      //string file extensions for chunkFiles
            int[]    numericExtensions = new int[chunkFiles.length];   //numeric file extensions
            boolean isNumber = false;
            boolean inconsistent = false; //keeps track of whether all extensions are numerical or if they are mixed

            for(int i = 0; i < chunkFiles.length; i++)
            {
                String curFile = chunkFiles[i];
                int offset = curFile.lastIndexOf('.');

                if (i >= 0 && i < chunkFiles.length)
                {
                    extensions[i] = curFile.substring(offset + 1).toLowerCase();

                    int extValDec = -1;

                    try
                    {
                        extValDec = Integer.parseInt(extensions[i]);   //check if extension is an integer
                        numericExtensions[i] = extValDec;
                        isNumber = true; 
                    }
                    catch(NumberFormatException nfe)
                    {
                        if(isNumber == true)
                        {
                            inconsistent = true;
                            // System.err.println("Found Inconsistent Image File Extension: " + extensions[i]);
                        }
                    }         
                }
            }

            if(isNumber && !inconsistent)   //if we have all numeric extensions
            {
                // System.err.println("All numeric extensions");
                //basic bubble sort of file extensions and file names at the same time
                for(int i = 0; i < extensions.length - 1; i++)
                {
                    for(int j = 0; j < extensions.length - i - 1; j++)
                    {
                        int tempInt;
                        String tempStr;
                        if(numericExtensions[j+1] < numericExtensions[j])
                        {
                            tempInt = numericExtensions[j];
                            tempStr = chunkFiles[j];

                            numericExtensions[j] = numericExtensions[j+1];
                            chunkFiles[j] = chunkFiles[j+1];

                            numericExtensions[j+1] = tempInt;
                            chunkFiles[j+1] = tempStr;
                        }
                    }
                }
                // System.err.println("Sorted extensions numerically");
                //         System.out.println("Sorted Numerically: " + Arrays.toString(chunkFiles));
            }
            else   //not numeric extensions (or mixed extensions)
            {
                // System.err.println("non-numeric or mixed extensions detected");
                Arrays.sort(chunkFiles);   //sort alphabetically
                //         System.out.println("sorted alphabetically: " + Arrays.toString(chunkFiles));
            }
        }

	public String getImageName()
	{
		String imageNamePre;
		if(isPhysicalDisk)
		    imageNamePre = physicalDiskModel;//physicalDiskName.substring(4,physicalDiskName.length());
		else
		    imageNamePre = imgFiles[0].getName();

		/* some physical devices may have a directory "/" in the name of the device (like write blocker combo dev)*/
		/* we don't want that interpreted as a path */
		imageNamePre = imageNamePre.replaceAll("\\\\", "_"); //just because
		imageNamePre = imageNamePre.replaceAll("/", "_"); 
		return imageNamePre;
	}
 
	private boolean checkDisk(JFrame frame, File outDir) throws OpenLVException
	{
		boolean retVal = true;
		MasterBootRecord tmp512;
		File genericMBR = null; 
		File customMBR = null;
		String[] pathForInputFiles = files;

		if(!isPhysicalDisk)   //user chose to boot a disk image, not physical disk
		{
		    //create array of files (chunks) for regular image it is just an array of length 1
		    imgFiles = new File[pathForInputFiles.length];
		    for(int i = 0; i < imgFiles.length; i++)
			imgFiles[i] = new File(pathForInputFiles[i].trim());

		    for(int i = 0; i < imgFiles.length; i++)   //check if all input files exist
		    {
			if(!imgFiles[i].exists())
			{
			    throw new OpenLVException("The image file: " + imgFiles[i].getName() + " could not be found");
			}
		    }

		    //load first 512 bytes of image file supplied as an MBR to test its validity
		  
		    tmp512 = new MasterBootRecord(imgFiles[0]);   //first img file should contain mbr
		}
		else   //physical disk
		{
		
		    String deviceName = physicalDiskName;
		    int[] unsignedMBRBuffer = null;
		    try
		    {
			RandomAccessFile raFile = new RandomAccessFile(deviceName, "r");
			byte[] mbrBuffer = new byte[512];   //signed mbr bytes
			raFile.readFully(mbrBuffer);   
			raFile.close();
			unsignedMBRBuffer = new int[512];   //unsigned bytes from mbr
			for(int i = 0; i < 512; i++)   //convert signed bytes to unsigned
			{
			    unsignedMBRBuffer[i] = mbrBuffer[i];
			    if(mbrBuffer[i] < 0)
				unsignedMBRBuffer[i] = 256 + mbrBuffer[i];
			}
		    }
		    catch(FileNotFoundException fnf)
		    {
			throw new OpenLVException("Could not open physical device: " + deviceName + " " + fnf.getMessage());
		    }
		    catch(IOException ioe)
		    {
			//postError("I/O problem reading physical device: " + deviceName + " " + ioe.getMessage());
			System.err.println("I/O problem reading physical device: " + deviceName + " " + ioe.getMessage());
		    }

		    tmp512 = new MasterBootRecord(unsignedMBRBuffer);
		}

		String imageName = this.getImageName();

		/* check for the MBR identifying sequence 55AA in bytes 510-511 of MBR as a check that it is valid*/
		if(tmp512.getMarker()[0] != 0x55 || tmp512.getMarker()[1] != 0xAA) 
		{
		    throw new OpenLVException("The image: " + imageName + " does not appear to be a disk file or bootable partition"
			    + endL
			    + "Please make sure that the image file(s) you chose is a valid disk image");
		}
		else   //we almost certainly have an mbr or a partition (not a garbage file)
		{
		    // System.err.println("MBR Signature found: almost certainly have an mbr or partition (not garbagefile)");
		}

		//sanity check on user's image file selection
		if(tmp512.isValidMBR())   //if mbr structure is a valid mbr (not first 512 bytes of a partition)
		{
		    isFullDisk = true;   //full disk 
		    //postOutput("Detected full disk image" + endL);
		}
		else 
		{
		    isFullDisk = false;   //partition only
		    //postOutput("Detected Partition Image" + endL);
		}

		if(isPhysicalDisk && !isFullDisk)   //physical partitions are not handled (eg mounting partition in PDE)
		{
		    throw new OpenLVException("OpenLV cannot boot physical partitions. If you are using mounting software, make sure to mount the full disk image." + endL);
		}

		if(!isPhysicalDisk)
		{
		    /* check if any files in the image are not readonly */
		    boolean writable = false;
		    for(int i = 0; i < imgFiles.length; i++)
		    {
			if(imgFiles[i].canWrite())
			{
			    writable = true;
			    // System.err.println("Writable File: " + imgFiles[i]);
			}
		    }

		    if(writable)   //if any file is not read-only, prompt to make them read only
		    {
			Object[] options = {"Yes", "No"};   //button titles
			int answer = JOptionPane.showOptionDialog(frame, 
				"Making your image file read-only will provide an extra layer of\n protection against accidental modification of evidence\n Would you like to make the image file(s) read-only?",
				"The image you have chosen is not read-only",
				JOptionPane.YES_NO_OPTION,
				JOptionPane.WARNING_MESSAGE,
				null,              //no custom icon
				options,           //the titles of buttons
				options[0]);       //default button title

			if(answer == JOptionPane.YES_OPTION)   //user wants to make file(s) read-only
			{
			    for(int i = 0; i < imgFiles.length; i++)   //set image imgFiles to read-only
				imgFiles[i].setReadOnly();
			    //postOutput("Making image file(s) read-only at user's request" + endL);
			}      
			//else
			    // System.err.println("User chose not to make image files read-only");
		    }
		}

		                        String fsType = null;      //type of filesystem for this image (NTFS, FAT, etc)
                        if(!isFullDisk) //partition only
                        {
                            //read in and modify generic mbr
                            long sizeOfPartition = 0;      //size of the partition in bytes
                            for(int i = 0; i < imgFiles.length; i++)
                            {
                                sizeOfPartition += imgFiles[i].length();
                            }
                            System.err.println("Size of partition (bytes): " + sizeOfPartition);

                            //we cannot autodetect the os for partition images (because there is no MBR)
			    /*
                            if(guestOSTypeText.equals("auto"))
                            {
                                throw new OpenLVException("OpenLV cannot auto detect the OS for partition images. Please select the image OS and try again.");
                            }

                            if(guestOSTypeText.equals("linux"))
                            {
                                throw new OpenLVException("OpenLV does not currently support linux partition images. If you have access to the full disk image, please select that and try again.");
                            }

                            //depending on the OS, choose the correct 'generic' mbr for the partition
                            if(guestOSTypeText.equals("win98") || guestOSTypeText.equals("winMe"))   //win 98 or Me
                            {
                                genericMBR = new File(InternalConfigStrings.getString("OpenLVLauncher.GenericMBRLocationW98Me"));
                                System.err.println("Using Generic Windows MBR for 98/Me");   
                            }
                            else                     //non-win98/Me  //TODO linux???
                            {
                                genericMBR = new File(InternalConfigStrings.getString("OpenLVLauncher.GenericMBRLocation"));
                                System.err.println("Using Generic Windows MBR for non-win98/me");
                            }
*/
                            //mbr file based on the image file name with .mbr appended to it
                            customMBR = new File(outDir.getAbsolutePath().trim() + 
                                    System.getProperty("file.separator") + 
                                    imgFiles[0].getName() + 
                                    ".mbr");

                            //note, this does not write the nt driver serial number (bytes 440-443 of mbr)
                            //this is because we need the vmdk to mount the disk to check the registry for this
                            //serial number but the vmdk is not created at this point. The serial number is added after the vmdk is created

                            //if(startFromScratch)//only modify mbr if user wants to start from scratch (otherwise it was previously created)
                            //    modifyGenericMBR(sizeOfPartition, 255, genericMBR, customMBR);      //creates customMBR
                            mbr = new MasterBootRecord(customMBR);   //use modified generic mbr             
                        }
                        else   //full disk
                        {
                            if(!isPhysicalDisk)
                                mbr = new MasterBootRecord(imgFiles[0]);   //use mbr from image
                            else
                                mbr = tmp512;
                            //check mbr flag for bootable partition and check if fat or ntfs
                            if(mbr.getBootablePartition().isFAT())
                                fsType = "FAT";
                            else if(mbr.getBootablePartition().isNTFS())
                                fsType = "NTFS";
                            else
                                fsType = "OTHER";
                        }
		//det OS, FS type, etc for future MBR population TODO XXXX
		return retVal;
	}

	public void createVMDKfile(File outputDir) throws OpenLVException
	{
		String outFileVMDKName          = getImageName() + ".vmdk";
		File customMBR = null;

		//build name for new file with full path to output file
		final String fullOutVMDKPath    = outputDir.getAbsolutePath().trim() + System.getProperty("file.separator") + outFileVMDKName;   //vmdk config
		filename = fullOutVMDKPath;

		File outVMDKFile             = new File(fullOutVMDKPath);         //vmdk file

		int answerContStartOver;      //user response to continue or start from scratch
		final boolean startFromScratch;   

		    //check if the vmdk output file exists
		    if(!outVMDKFile.exists())
		    {
			try   //no, so create the file
			{  
			    outVMDKFile.createNewFile();   //create the file
			    //System.err.println("Created: " + outVMDKFile.getAbsolutePath());
			}
			catch(IOException ioe)
			{
			    throw new OpenLVException("Could not create file: " + outFileVMDKName + " in " + outputDir 
				    + endL
				    + ioe.getMessage());  
			}
		    }
		    else if(!outVMDKFile.canWrite()) //check if the file is writable
		    {
			throw new OpenLVException(outFileVMDKName + " in " + outputDir+ " is not writable."
				+ endL
				+ "Please make the file writable or choose a new one.");  
		    }

		    //vmdk file exists and is writable, so write it
		    PrintWriter vmdkWriter = null;
		    try
		    {
			StringBuffer vmdkBuffer = new StringBuffer();
			//postOutput("Generating vmdk file..." + endL);
			vmdkWriter = new PrintWriter(new BufferedWriter( new FileWriter(outVMDKFile)));
			vmdkBuffer.append("# Disk Descriptor File" + endL);
			vmdkBuffer.append("version=1" + endL);
			vmdkBuffer.append("CID=fffffffe" + endL);
			vmdkBuffer.append("parentCID=ffffffff" + endL);

			if(!isPhysicalDisk)   //image file(s)
			    vmdkBuffer.append("createType=\"monolithicFlat\"" + endL);
			else   //physical disk
			    vmdkBuffer.append("createType=\"fullDevice\"" + endL);

			vmdkBuffer.append(endL);

			vmdkBuffer.append("# Extent description" + endL);

			long unallocatedSpace;
			final String    diskSize         = PARENT_DISK_SIZE_GB;
			long totalSectorsOnParentDisk = 0;         //sectors on disk image
			totalSectorsOnParentDisk = (long)(Float.parseFloat(diskSize) * BYTES_PER_GIG) / 512;
			if(!isFullDisk)   //just a partition image
			{
			    if(customMBR != null)
			    {
				vmdkBuffer.append("RW 63 FLAT \"" + customMBR + "\" 0" + endL);   //add reference to mbr so we can boot partition
			    }
			    else
				throw new OpenLVException("Custom MBR not found");

			    if(isChunked)   //chunked partition image
			    {
				long sectorsInChunk;
				for(int i = 0; i < imgFiles.length; i++)
				{
				    sectorsInChunk = imgFiles[i].length() / 512;   //number of sectors in current chunk
				    vmdkBuffer.append("RW " + (sectorsInChunk) + " FLAT " + "\"" + imgFiles[i].getCanonicalPath() + "\"" + " 0" + endL);   //one line for each chunk for extent description
				}

				unallocatedSpace = totalSectorsOnParentDisk - mbr.totalSectorsFromPartitions() - 63;   
			    }
			    else   //not a chunked partition image
			    {
				vmdkBuffer.append("RW " + (mbr.totalSectorsFromPartitions()) + " FLAT " + "\"" + imgFiles[0] + "\"" + " 0" + endL);   //just need one extent line pointing to whole image file
				unallocatedSpace = totalSectorsOnParentDisk - mbr.totalSectorsFromPartitions() - 63;   
			    }
			}
			else         //full disk image
			{
			    if(isChunked)   //chunked full disk image
			    {
				long sectorsInChunk = 0, totalSectors = 0;
				for(int i = 0; i < imgFiles.length; i++)
				{
				    sectorsInChunk = imgFiles[i].length() / 512;
				    totalSectors += sectorsInChunk;
				    vmdkBuffer.append("RW " + (sectorsInChunk) + " FLAT " + "\"" + imgFiles[i].getCanonicalPath() + "\"" + " 0" + endL);   //one line describing each chunk for the extent description
				}
				System.err.println("ts: " + totalSectors  );
				System.err.println("MBR:" + mbr.toString());
				System.err.println("tsfp " + mbr.totalSectorsFromPartitions());
				if(totalSectors >= mbr.totalSectorsFromPartitions())
				    unallocatedSpace = totalSectors - mbr.totalSectorsFromPartitions() - 63;   //standard way to get unallocated space (sectors in file - sectors in mbr)
				else
				    unallocatedSpace = mbr.totalSectorsFromPartitions() - totalSectors + 63; //added because sometimes total sectors making up file is less than total in mbr for partitions (eg nist image) so here we account for unallocated
			    }
			    else   //not a chunked full disk image
			    {
				if(isPhysicalDisk)
				{
				    vmdkBuffer.append("RW " + (mbr.totalSectorsFromPartitions() + 63) + " FLAT " + "\"" + physicalDiskName + "\"" + " 0" + endL);   //just need one extent line pointing to the physical disk
				    unallocatedSpace = mbr.totalSectorsFromPartitions()/1000;   //fudge factor - since total sectors from file shows up as 0 for physical disks
				}
				else   //full disk dd image
				{
				    vmdkBuffer.append("RW " + (mbr.totalSectorsFromPartitions() + 63) + " FLAT " + "\"" + imgFiles[0] + "\"" + " 0" + endL);   //just need one extent line pointing to whole image file
				    unallocatedSpace = mbr.totalSectorsOnDiskFromFile() - mbr.totalSectorsFromPartitions() /*- mbr.getBootablePartition().getEndSector()*/ + 63;   //add 63?
				}
			    }
			}

			if(unallocatedSpace > 0)
			    vmdkBuffer.append("RW " + unallocatedSpace + " ZERO" + endL);   //leftover 0'ed space

			vmdkBuffer.append(endL);

			vmdkBuffer.append("#DDB - Disk Data Base" + endL);
			vmdkBuffer.append("ddb.adapterType = \"ide\"" + endL);
			vmdkBuffer.append("ddb.geometry.sectors = \"" + mbr.getBootablePartition().getEndSector() + "\"" + endL);
			vmdkBuffer.append("ddb.geometry.heads = \"" + mbr.getBootablePartition().getEndHead() + "\"" + endL);
			vmdkBuffer.append("ddb.geometry.cylinders = \"" + mbr.largestCylinderValOnDisk() + "\"" + endL);
			vmdkBuffer.append("ddb.virtualHWVersion = \"3\"" + endL);

			//System.err.println(vmdkBuffer.toString());
			vmdkWriter.write(vmdkBuffer.toString());   //write the vmdk buffer to the file
		    }
		    catch(IOException ioe)
		    {
			throw new OpenLVException("Error writing vmdk file: " + outVMDKFile.getAbsolutePath());
		    }
		    finally 
		    {
			if (vmdkWriter != null) 
			    vmdkWriter.close();
		    }   

		return;
	}

	public String[] getFiles()
	{
		return this.files;
	}

	/**
	 * inspector for bootSource datamember
	 * @return bootSource string
	 */
	public String getBootSource()
	{
		return this.bootSource;
	}
	/**
	 * inspector for physicalDiskNamedatamember
	 * @return physicalDiskNamestring
	 */
	public String getPhysicalDiskName()
	{
		return this.physicalDiskName;
	}
	/**
	 * inspector for physicalDiskInfo datamember
	 * @return physicalDiskInfo string
	 */
	public String getPhysicalDiskInfo()
	{
		return this.physicalDiskInfo;
	}
	/**
	 * inspector for filename datamember
	 * @return filename string
	 */
	public String getFilename()
	{
		return this.filename;
	}

	/**
	 * mutator for bootSource datamember
	 * @param bs the value to store in the private datamember
	 */
	public void setBootSource(String bs)
	{
		this.bootSource = bs;
	}

	/**
	 * inspector for isPhysicalDisk 
	 * @return true if it is a physical disk 
	 */
	public boolean isPhysicalDisk()
	{
		return this.isPhysicalDisk;
	}

	/**
	 * mutator for physicalDiskInfo datamember
	 * @param pdi the value to store in the private datamember
	 */
	public void setPhysicalDiskInfo(String pdi)
	{
		this.physicalDiskInfo = pdi;
		isValid = true;
	}

	/**
	 * mutator for physicalDiskNamedatamember
	 * @param pdi the value to store in the private datamember
	 */
	public void setPhysicalDiskName(String pdi)
	{
		this.physicalDiskName = pdi;
	}
}
