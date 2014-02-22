package cert.forensics.mbr;
/*
 * Represents one of the four partition entries in an MBR
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

public class PartitionEntry
{
	private static final int PARTITION_ENTRY_SIZE = 16; 
	private int[] entryBytes;
	
	//known type signatures to sanity check validity of the partition entry and mbr
	private int[] validPartitionTypes = {0x00, 0x10, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08,  0x09, 0x0a, 0x0b, 0x0c, 0x0e, 0x0f, 0x10, 0x11, 0x12, 0x14, 0x16, 0x17, 0x18, 0x1b, 0x1c,
										 0x1e, 0x24, 0x39, 0x3c, 0x40, 0x41, 0x42, 0x4d, 0x4e, 0x4f, 0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x5c, 0x61, 0x63, 0x64, 0x65, 0x70, 0x75,
										 0x80, 0x81, 0x82, 0x83, 0x84, 0x85, 0x86, 0x87, 0x88, 0x8e, 0x93, 0x94, 0x9f, 0xa0, 0xa5, 0xa6, 0xa7, 0xa8, 0xa9, 0xab, 0xb7, 0xb8, 0xbb,
										 0xbe, 0xbf, 0xc1, 0xc4, 0xc6, 0xc7, 0xda, 0xdb, 0xde, 0xdf, 0xe1, 0xe3, 0xe4, 0xeb, 0xee, 0xef, 0xf0, 0xf1, 0xf4, 0xf2, 0xfd, 0xfe, 0xff};
	
	//structure of parititon table
	private int 	state;						//byte 1
	private int 	beginHead;					//byte 2
	private int 	beginCylinderSector;		//byte 3+4
	private int		partitionType;				//byte 5
	private int		endHead;					//byte 6
	private int		endCylinderSector;			//byte 7+8
	private long	relativeSector;				//byte 9+10+11+12 (sectors between mbr and 1st sector of parition)
	private long	numSectors;					//byte 13+14+15+16
	
	public PartitionEntry(int[] pe)
	{
		if(pe.length == PARTITION_ENTRY_SIZE)
			entryBytes = pe;
		else
			entryBytes = new int[PARTITION_ENTRY_SIZE];
		
		state = entryBytes[0];
		beginHead = entryBytes[1];
		beginCylinderSector = ((entryBytes[3] << 8) | entryBytes[2]); 	//combine bytes 3 + 2 (backwards)
		partitionType = entryBytes[4];
		endHead = entryBytes[5];
		endCylinderSector =((entryBytes[7] << 8) | entryBytes[6]); 		//combine bytes 7 + 6 (backwards)
		
		relativeSector = 	((entryBytes[11] 	& 0xFF) << 24)			//combine bytes 8-11  (backwards)
						| 	((entryBytes[10] 	& 0xFF) << 16)
						| 	((entryBytes[9] 	& 0xFF) << 8)
						| 	(entryBytes[8] 		& 0xFF);
		
		numSectors = 	((entryBytes[15] 	& 0xFF) << 24)				//combine bytes 12-15 (backwards)
					| 	((entryBytes[14] 	& 0xFF) << 16)
					| 	((entryBytes[13] 	& 0xFF) << 8)
					| 	(entryBytes[12] 	& 0xFF);
		
		
	}

	/* Extract 6 bit sector from Cylinder/Sector 16bit structure  */
	private static int getSector(int cylSectStructure)
	{		
		return (cylSectStructure & 63);
	}
	
	/* Extract 10bit cylinder from Cylinder/Sector 16bit structure  
	 * */
	private static int getCylinder(int cylSectStructure)
	{		
		int bits8To15 = ((cylSectStructure & 65280) >> 8);
		
		int cylinderVal = bits8To15;
		
		if((cylSectStructure & 64) != 0) //if the 6th bit of the 16 bit structure is not zero
			cylinderVal += 256;	//set bit 8 of the 10 bit cylinder value
		
		if((cylSectStructure & 128) != 0) //if the 7th bit of the 16 bit structure is not zero
			cylinderVal += 512; //set bit 9 of the 10 bit cylinder value

		return cylinderVal;
	}

	public int getBeginCylinder()
	{
		return getCylinder(beginCylinderSector);
	}
	
	public int getBeginSector()
	{
		return getSector(beginCylinderSector);
	}

	public int getBeginHead()
	{
		return beginHead;
	}

	public long getEndCylinder()
	{
		return getCylinder(endCylinderSector);
	}
	
	public long getEndSector()
	{
		return getSector(endCylinderSector);
	}

	public int getEndHead()
	{
		return endHead;
	}

	public int[] getEntryBytes()
	{
		return entryBytes;
	}

	public long getNumSectors()
	{
		return numSectors;
	}

	public int getPartitionType()
	{
		return partitionType;
	}

	public long getRelativeSector()
	{
		return relativeSector;
	}

	public int getState()
	{
		return state;
	}
	
	public boolean isBootable()
	{
		return state == 0x80;	//flag 0x80 means partition is bootable
	}
	
	/* checks if the partition type is a "known" value so it is really
	 * most useful for checking if something is not valid */
	public boolean isValidPartition()
	{
		for(int i = 0; i < validPartitionTypes.length; i++)
		{
			if(validPartitionTypes[i] == partitionType)
				return true;
		}
		return false;
	}
	
	/* Really only used to check bootable partitions, so not all obscure windows partition type flags are checked */
	public boolean isNotWindowsBased()
	{
		if(		partitionType == 0x07 ||		//NTFS
				partitionType == 0x0b || 		//FAT32 CHS
				partitionType == 0x0c ||		//FAT32 LBA
				partitionType == 0x06 	 		//FAT16
	
		)
			return false;	//it may be windows based
		else
			return true;	//can't be windows based
	}
	
	public boolean isFAT()
	{
		return (partitionType == 0x0b || partitionType == 0x0c || partitionType == 0x06);		
	}
	
	public boolean isNTFS()
	{
		return (partitionType == 0x07);
	}
}
