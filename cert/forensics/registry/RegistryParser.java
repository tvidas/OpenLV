/*
    RegistryParser.java
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
import java.io.IOException;
import java.io.RandomAccessFile;
import java.lang.Long;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.NumberFormat;
import java.util.HashMap;

import cert.forensics.liveview.LiveViewLauncher;
import cert.forensics.liveview.LogWriter;
import cert.forensics.registry.crypto.DES;
import cert.forensics.registry.crypto.RC4;
import cert.forensics.registry.crypto.Util;

/**
 * RegistryParser
 * Utility class that parses the registry and blanks out all
 * windows logon passwords
 * @author Tim Vidas
 * @author Brian Kaplan
 * @version 0.7, Jan 2009
 */

public class RegistryParser
{
	static final int ROOTKEY_OFFSET = 0x24;
        private static String samHashes;        // pwdump formatted dump of sam passwords
        private static String mscashHashes;     // mscash (CacheDump) formatted dump of domain cached credentials

        /**
         * Main class for testing RegistryParser functions directly
         *
         * @param String[] contains paths to SYSTEM, SAM, and SECURITY
         * registry files in that order
         *
         * @returns nothing
         */
        /*public static void main(String[] args)
        {
            String[] answer = null;

            if ((answer = clearPasswords(args[0], args[1], args[2], ".", "basename")) != null) {
                System.out.println("\nCleared passwords for these accounts:");
                for (String s : answer) {
                    System.out.println("\t" + s);
                }
                System.out.println();
            }
            else {
                System.out.println("\nNo passwords cleared!");
            }
        }
        */


        /**
         * Pulls the encryption keys out of the registry files and then calls
         * clearMSCACHEPasswords to clear out the passwords for the network cached credentials 
	 * also exports the hashes to outputDir\baseFileName.MSCASHDUMP
         *
	 * @param sysLoc location of SYSTEM file
	 * @param secLoc location of SECURITY file
	 * @param outputDir directory to place exported hashes
	 * @param baseFileName basename for hashes (.MSCASHDUMP will be automatically added)
         * @returns an array of Strings containing the user names of the
         * accounts whose passwords have been blanked, or null if none
         */

        public static String[] clearDomainPasswords(String sysLoc, String secLoc, String outputDir, String baseFileName)
        {
            // Open files and hives for reading encryption keys
            File sysFile = new File(sysLoc);
            //File samFile = new File(samLoc);
            File secFile = new File(secLoc);

            Hive sysHive = new Hive(sysFile);
            //Hive samHive = new Hive(samFile);
            Hive secHive = new Hive(secFile);

            // bootkey from SYSTEM
            byte[] bootKey = getBootKey(sysHive);

            // hBootKey from SAM
            //byte[] hBootKey = getHashedBootKey(samHive, bootKey);

            // lsakey from SECURITY
            byte[] lsaKey  = getLSAKey(secHive, bootKey);

            // nlkm from SECURITY
            byte[] nlkmKey = getNLKM(secHive, lsaKey);

            // free Hives
            sysHive = null;
            //samHive = null;
            secHive = null;

            // clear passwords
            String[] MSCACHEusers = RegistryParser.clearMSCACHEPasswords(secFile, nlkmKey);

            // create lengths we know are defined
            int mscashLen = (MSCACHEusers != null)? MSCACHEusers.length : 0;

            if ( mscashLen == 0) { return null; }

            String[] allUsers = new String[mscashLen];
            if (mscashLen > 0)
            {
                System.arraycopy(MSCACHEusers, 0, allUsers, 0, mscashLen);

                try
		{
                    RandomAccessFile raf = new RandomAccessFile(outputDir + "\\" + baseFileName + ".MSCASHDUMP", "rw");
                    raf.writeBytes(mscashHashes);   //write out cached domain credential hashes
                    raf.setLength(raf.getFilePointer());
                    raf.close();
		}
		catch (IOException ioe)
		{
			System.out.println("I/O error while writing to MSCASH dump file: " + ioe.getMessage());
			return null;
		}
            }

            return allUsers;
        }

        /**
         * Pulls the encryption keys out of the registry files and then calls
         * clearSAMPasswords to clear out the local passwords
	 * also exports the hashes to outputDir\baseFileName.MSCASHDUMP
         *
	 * @param sysLoc location of SYSTEM file
	 * @param samLoc location of SAM file
	 * @param outputDir directory to place exported hashes
	 * @param baseFileName basename for hashes (.MSCASHDUMP will be automatically added)
         *
         * @returns an array of Strings containing the user names of the
         * accounts whose passwords have been blanked, or null if none
         */
        public static String[] clearLocalPasswords(String sysLoc, String samLoc, String outputDir, String baseFileName)
        {
            // Open files and hives for reading encryption keys
            File sysFile = new File(sysLoc);
            File samFile = new File(samLoc);
            //File secFile = new File(secLoc);

            Hive sysHive = new Hive(sysFile);
            Hive samHive = new Hive(samFile);
            //Hive secHive = new Hive(secFile);

            // bootkey from SYSTEM
            byte[] bootKey = getBootKey(sysHive);

            // hBootKey from SAM
            byte[] hBootKey = getHashedBootKey(samHive, bootKey);

            // lsakey from SECURITY
            //byte[] lsaKey  = getLSAKey(secHive, bootKey);

            // nlkm from SECURITY
            //byte[] nlkmKey = getNLKM(secHive, lsaKey);

            // free Hives
            sysHive = null;
            samHive = null;
            //secHive = null;

            // clear passwords
            String[] SAMusers     = RegistryParser.clearSAMPasswords(samFile, hBootKey);

            // create lengths we know are defined
            int samLen    = (SAMusers     != null)? SAMusers.length     : 0;

            if (samLen == 0) { return null; }

            String[] allUsers = new String[samLen];

            if (samLen > 0)
            {
                System.arraycopy(SAMusers, 0, allUsers, 0, samLen);

                try
		{
                    RandomAccessFile raf = new RandomAccessFile(outputDir + "\\" + baseFileName + ".SAMDUMP", "rw");
                    raf.writeBytes(samHashes);      // write out SAM hashes
                    raf.setLength(raf.getFilePointer());
                    raf.close();
		}
		catch (IOException ioe)
		{
			System.out.println("I/O error while writing to SAM dump file: " + ioe.getMessage());
			return null;
		}
            }

            return allUsers;
        }


        /**
         * Pulls the encryption keys out of the registry files and then calls
         * the clearSAMPasswords and clearMSCACHEPasswords to clear out the
         * passwords for the local and network cached credentials respectively
	 * also exports the hashes to outputDir\baseFileName.MSCASHDUMP
         *
	 * @param sysLoc location of SYSTEM file
	 * @param samLoc location of SAM file
	 * @param secLoc location of SECURITY file
	 * @param outputDir directory to place exported hashes
	 * @param baseFileName basename for hashes (.MSCASHDUMP will be automatically added)
         *
         * @returns an array of Strings containing the user names of the
         * accounts whose passwords have been blanked, or null if none
         */

	/*
        public static String[] clearPasswords(String sysLoc, String samLoc, String secLoc, String outputDir, String baseFileName)
        {
            // Open files and hives for reading encryption keys
            File sysFile = new File(sysLoc);
            File samFile = new File(samLoc);
            File secFile = new File(secLoc);

            Hive sysHive = new Hive(sysFile);
            Hive samHive = new Hive(samFile);
            Hive secHive = new Hive(secFile);

            // bootkey from SYSTEM
            byte[] bootKey = getBootKey(sysHive);

            // hBootKey from SAM
            byte[] hBootKey = getHashedBootKey(samHive, bootKey);

            // lsakey from SECURITY
            byte[] lsaKey  = getLSAKey(secHive, bootKey);

            // nlkm from SECURITY
            byte[] nlkmKey = getNLKM(secHive, lsaKey);

            // free Hives
            sysHive = null;
            samHive = null;
            secHive = null;

            // clear passwords
            String[] SAMusers     = RegistryParser.clearSAMPasswords(samFile, hBootKey);
            String[] MSCACHEusers = RegistryParser.clearMSCACHEPasswords(secFile, nlkmKey);

            // create lengths we know are defined
            int samLen    = (SAMusers     != null)? SAMusers.length     : 0;
            int mscashLen = (MSCACHEusers != null)? MSCACHEusers.length : 0;

            if ((samLen + mscashLen) == 0) { return null; }

            String[] allUsers = new String[samLen + mscashLen];

            if (samLen > 0)
            {
                System.arraycopy(SAMusers, 0, allUsers, 0, samLen);

                try
		{
                    RandomAccessFile raf = new RandomAccessFile(outputDir + "\\" + baseFileName + ".SAMDUMP", "rw");
                    raf.writeBytes(samHashes);      // write out SAM hashes
                    raf.setLength(raf.getFilePointer());
                    raf.close();
		}
		catch (IOException ioe)
		{
			System.out.println("I/O error while writing to SAM dump file: " + ioe.getMessage());
			return null;
		}
            }

            if (mscashLen > 0)
            {
                System.arraycopy(MSCACHEusers, 0, allUsers, samLen, mscashLen);

                try
		{
                    RandomAccessFile raf = new RandomAccessFile(outputDir + "\\" + baseFileName + ".MSCASHDUMP", "rw");
                    raf.writeBytes(mscashHashes);   //write out cached domain credential hashes
                    raf.setLength(raf.getFilePointer());
                    raf.close();
		}
		catch (IOException ioe)
		{
			System.out.println("I/O error while writing to MSCASH dump file: " + ioe.getMessage());
			return null;
		}
            }

            return allUsers;
        }
	*/

	/**
         * Clears the passwords for every account in the given SAM hive file
	 *
         * @param samFile File pointing to the SAM file
         * @param hBootKey contains the hashed system boot key needed to decrypt the sam hashes
         * @return String[] of usernames found on system for success, null on failure
	 */
	private static String[] clearSAMPasswords(File samFile, byte[] hBootKey)
	{
                StringBuffer samHashBuffer = new StringBuffer();
                Hive hive = new Hive(samFile);

                HashMap nameRIDMap = getUserRIDMap(hive);	//user account names mapped to RID values

		if(nameRIDMap == null)	//did the mapping fail
			return null;

		long curRID = -1;
		String curUserName = null;
		Object[] temp = nameRIDMap.keySet().toArray();
		String[] userNames = new String[temp.length];
		for(int j = 0; j < temp.length; j++)	//make array of usernames
			userNames[j] = (String)temp[j];

		long[] offsetToVStruct = new long[userNames.length];	//array of each username's offset to their V structure

		for(int i = 0; i < userNames.length; i++)
		{
			curUserName = userNames[i];
			curRID = ((Long)nameRIDMap.get(curUserName)).longValue();

			String hexRID = Long.toHexString(curRID);	//8 byte hex RID of current user without leading 0's

			if(hexRID.length() < 8)	//pad with leading zero's to make length 8
			{
				int padding = 8 - hexRID.length();
				StringBuffer sbuf = new StringBuffer(hexRID);
				for(int x = 0; x < padding; x++)
				{
					sbuf.insert(0, '0');	//insert leading zero
				}

				hexRID = sbuf.toString();
			}

			//build path to current user's V structure
			String path = "\\SAM\\Domains\\Account\\Users\\" + hexRID + "\\V";

			int vOffset = traversePath(hive, 0, path, 1);	//offset into vk structure for V struct
			VK_Structure finalVK = new VK_Structure(hive.getBuffer(), vOffset);
			offsetToVStruct[i] = finalVK.getOfs_data() + 0x1004;		//add the actual offset to the V structure to the array

                        SAMV_Structure samv = new SAMV_Structure(hive.getBuffer(), (int) offsetToVStruct[i], (int)finalVK.getLen_data());

                        samv.decrypt(hBootKey, (int)curRID);

                        System.out.println(samv.toString());

                        samHashBuffer.append(samv.getPWDUMPline() + System.getProperty("line.separator"));
		}

		//for all offsets (one for each user account), zero out the LM and NT hash lengths in SAM
		try
		{
	        RandomAccessFile raf = new RandomAccessFile(samFile, "rw");

			for(int a = 0; a < offsetToVStruct.length; a++) //for all of the V struct offsets (for each account name)
			{
				raf.seek(offsetToVStruct[a] + 0xa0);	//jump to lm hash length
				raf.write(0x00);			//zero it
				raf.seek(offsetToVStruct[a] + 0xac);	//jump to nt hash length
				raf.write(0x00);			//zero it
			}
			raf.close();
		}
		catch (IOException ioe)
		{
			System.out.println("I/O error while writing 0's to SAM File " + ioe.getMessage());
			return null;
		}

            samHashes = new String(samHashBuffer);

	    return userNames;
	}

	/**
         * Converts the system boot key into an RC4 key and then uses it to
         * decrypt the hashed boot key stored in the SAM hive.
	 *
         * @param samHive Hive pointing to the SAM registry hive
         * @param bootKey contains the system boot key
         * @return byte[] containing the hashed boot key needed to decrypt SAM hashes,
         * null if bootKey is null
	 */
        private static byte[] getHashedBootKey(Hive samHive, byte[] bootKey)
        {
            if (bootKey == null) { return null; }

            int vkOfs;
            VK_Structure vk;
            MessageDigest md5;
            RC4 rc4 = new RC4();

            byte[] aqwerty = new String("!@#$%^&*()qwertyUIOPAzxcvbnmQQQQQQQQQQQQ)(*@&%\0").getBytes();
            byte[] anum    = new String("0123456789012345678901234567890123456789\0").getBytes();

            vkOfs = traversePath(samHive, 0, "\\SAM\\Domain\\Account\\F", 1);
            vk = new VK_Structure(samHive.getBuffer(), vkOfs);

            // 16 bytes needed in MD5 hash from the \\SAM\\Domain\\Account\\F reg entry
            byte[] fKey = byteSlice(samHive.getBuffer(), (int)vk.getOfs_data() + 0x1004 + 0x70, 0x10);

            try
            {
                md5 = MessageDigest.getInstance("MD5");
            }
            catch (NoSuchAlgorithmException nsae) { return null; }

            md5.update(fKey);
            md5.update(aqwerty);
            md5.update(bootKey);
            md5.update(anum);

            rc4.setKey( md5.digest() );

            // copy out the encrypted hashed boot key
            byte[] hBootKey = byteSlice(samHive.getBuffer(), (int)vk.getOfs_data() + 0x1004 + 0x80, 0x20);

            // decrypt the hashed boot key in place using the md5 hash as the key
            rc4.decrypt(hBootKey, 0, hBootKey, 0, hBootKey.length);

            return hBootKey;
        }

	/**
         * Clears the passwords for every cached network login in the given
         * SECURITY registry file
	 *
         * @param secFile File pointing to the SECURITY file
         * @param nlkmKey byte[] containing nlkmKey
         * @return String[] of accounts whose passwords have been blanked, null on failure
	 */
	private static String[] clearMSCACHEPasswords(File secFile, byte[] nlkmKey)
	{
                int vkOfs;
                VK_Structure vk;
                DCC_Structure dcc;
                StringBuffer userNameBuffer = new StringBuffer();
                StringBuffer mscashHashBuffer = new StringBuffer();

                // no nlkmKey means we failed to decrypt LSA Secrets properly, which almost certainly means unsupported guest
                if (nlkmKey == null) {
                    LiveViewLauncher.postOutput("Domain Cached Credentials will not be cleared " +
                                                "(only supported for Windows NT4 (SP4 with syskey), 2000, XP, and 2003)"
                                                + LiveViewLauncher.endL);
                    return null;
                }

                // should this be a user checked option?
                boolean enableUsers = true;

                Hive secHive = new Hive(secFile);

                for (int i = 1; (vkOfs = traversePath(secHive, 0, "\\Cache\\NL$"+i, 1)) > 0; i++) {
                    vk = new VK_Structure(secHive.getBuffer(), vkOfs);

                    // offset to the cached credential data (ccdata)
                    int ccdata = (int)vk.getOfs_data() + 0x1004;

                    if (ccdata == 0x1004 || getWord(secHive.getBuffer(), ccdata) == 0) {
                        continue;
                    }

                    // try to create and populate a new DCC structure
                    try {
                        dcc = new DCC_Structure(secHive.getBuffer(), ccdata, (int)vk.getLen_data(), enableUsers);
                    } catch (Exception e) {
                        // unable to process successfully.
                        continue;
                    }

                    try {
                        // decrypt dce portion using nlkmKey
                        dcc.decrypt(nlkmKey);
                    } catch (Exception e) {
                        // error in decrypt
                        LiveViewLauncher.logError(e.toString());
                        continue;
                    }

                    if (userNameBuffer.length() > 0) {
                        userNameBuffer.append(":");
                    }

                    // get username in user@example.com form
                    userNameBuffer.append(dcc.getFullUserName());

                    // get MSCASH formatted line of curent hash value for use with john or other crackers
                    mscashHashBuffer.append(dcc.getMSCASHline() + System.getProperty("line.separator"));

                    // blank password
                    dcc.clearUserPassword();

                    // change cipherKey if desired (not necessary, and probably better not to from a least change perspective)
                    // dcc.setRandomCipherKey();

                    // re-encrypt dce with new blank password
                    dcc.encrypt();

                    // pull the entire DCC as multiple parts of it may have been changed
                    byte[] dccEntry     = dcc.getDCC();

                    try
                    {
                        RandomAccessFile raf = new RandomAccessFile(secFile, "rw");
                        raf.seek(ccdata);           // jump to DCC
                        raf.write(dccEntry);        // overwrite DCC
			raf.close();
                    }
                    catch (IOException ioe)
                    {
			System.out.println("I/O error while writing to the SECURITY File " + ioe.getMessage());
			return null;
                    }
                }

                mscashHashes = new String(mscashHashBuffer);

                return (userNameBuffer.length() > 0)? (new String(userNameBuffer)).split(":") : null;
        }

	/**
         * Assembles and descrambles the boot key from the SYSTEM registry hive
	 *
         * @param sysHive Hive containing the SYSTEM registry hive
         * @return byte[] containing the boot key (also called syskey), null if no bootkey
	 */
        private static byte[] getBootKey(Hive sysHive)
        {
            // descrambling matrix
            int p[] = { 0x8, 0x5, 0x4, 0x2, 0xb, 0x9, 0xd, 0x3, 0x0, 0x6, 0x1, 0xc, 0xe, 0xa, 0xf, 0x7 };

            // Find the right ControlSet
            int vkOfs = traversePath(sysHive, 0, "\\Select\\Default", 1);
            VK_Structure vk = new VK_Structure(sysHive.getBuffer(), vkOfs);

            NumberFormat nf = NumberFormat.getInstance();
            nf.setMinimumIntegerDigits(3);
            String controlSet = "\\ControlSet" + nf.format(vk.getOfs_data());

            String lsaRoot = controlSet + "\\Control\\Lsa\\";

            // Reg keys containing parts of the boot key
            String [] keys = {"JD", "Skew1", "GBG", "Data"};

            byte[] bootUnsorted = new byte [16];

            for (int i = 0; i < keys.length; i++) {
                int nkOfs = traversePath(sysHive, 0, lsaRoot + keys[i], 0);

                if (nkOfs == 0) { return null; } // syskey is not enabled

                NK_Structure nk = new NK_Structure(sysHive.getBuffer(), nkOfs+4);

                /* as an additional obfuscation, the boot key data is stored in the classnam
                 * in UTF-16LE ascii chars representing the actual hex values.  i.e.
                 * 'a', '0', '5', '0', '7', '0', 'b', '0' ... becomes 0xa5, 0x7b ... */
                byte[] utfkey = byteSlice(sysHive.getBuffer(), (int)nk.getOfs_classnam() + 0x1004, 0x10);

                /* convert { 'a', '0', '5', '0', '7', '0', 'b', '0' } to { 0xa5, 0x7b}
                 * skip all odd entries, convert 'a' and '5' into a string 'a5' which can then
                 * be parsed by parseInt as a hexstring, finally convert to byte */
                for (int j = 0; j < utfkey.length; j += 4)
                {
                    bootUnsorted[(i*4) + (j/4)] = (byte)( Integer.parseInt(new String(new byte[]{utfkey[j],utfkey[j+2]}), 16 ) );
                }

            }

            byte[] bootKey = new byte [bootUnsorted.length];

            // descramble
            for (int i = 0; i < bootKey.length; i++) {
                bootKey[i] = bootUnsorted[p[i]];
            }

            return bootKey;
        }

        /**
         * Decrypts the LSA key from the SECURITY hive
         *
         * @param secHive Security hive containing the encrypted LSA key
         * @param bootKey System bootkey needed to decrypt the LSA key
         * @return a byte[] containing the LSA key, null if bootKey is null
         */
        private static byte[] getLSAKey(Hive secHive, byte[] bootKey)
        {
            if (bootKey == null) { return null; }

            MessageDigest md5;
            RC4 rc4 = new RC4();

            int vkOfs = traversePath(secHive, 0, "\\Policy\\PolSecretEncryptionKey\\@", 1);

            // Key does not exist for Vista+
            if (vkOfs == 0) {
                return null;
            }

            VK_Structure vk = new VK_Structure(secHive.getBuffer(), vkOfs);

            // copy out the Policy Secret Encryption Key default value
            byte[] PSEK = byteSlice(secHive.getBuffer(), (int)vk.getOfs_data() + 0x1004, (int)vk.getLen_data());

            try
            {
                md5 = MessageDigest.getInstance("MD5");
            }
            catch (NoSuchAlgorithmException nsae) { return null; }

            // the bootKey is the part of the data to be hashed
            md5.update(bootKey);

            // pull out a portion of the PSEK needed to compute the decryption key
            byte[] psekmd5 = byteSlice(PSEK, 60, 0x10);

            // add 1000 concatenations of the key pulled from PSEK to the MD5 data
            for (int i = 0; i < 1000; i++) {
                md5.update(psekmd5);
            }

            // set RC4 key to the md5 output
            rc4.setKey( md5.digest() );

            // decrypt the LSA key in place
            rc4.decrypt(PSEK, 12, PSEK, 12, 0x30);

            // return the 16 byte LSA key starting from offset 28
            return byteSlice(PSEK, 28, 0x10);
        }


        /**
         * Retrieves the NL$KM LSA Secret needed to decrypt the cached
         * domain credentials
         *
         * @param secHive Security hive containing the encrypted LSA secrets
         * @param lsaKey byte[] containing the lsa key needed to decrypt LSA secrets
         * @return byte[] containing the decrypted NL$KM secret, null if lsaKey is null
         */
        private static byte[] getNLKM(Hive secHive, byte[] lsaKey)
        {
            if (lsaKey == null) { return null; }

            // NL$KM is just like any other LSA secret, just call getLSASecret
            return getLSASecret(secHive, "NL$KM", lsaKey);
        }

        /**
         * Generic class to decrypt any LSA secret from the SECURITY hive
         * by name.  Retrieves LSA secret for given secretName and calls
         * decryptSecret to do actual decryption.
         *
         * @param secHive Security hive containing the encrypted LSA key
         * @param secretName Name of the LSA secret to decrypt
         * @param lsaKey byte[] containing the lsa key needed to decrypt LSA secrets
         * @return a byte[] containing the decrypted LSA secret
         */
        private static byte[] getLSASecret(Hive secHive, String secretName, byte[] lsaKey)
        {
            int vkOfs = traversePath(secHive, 0, "\\Policy\\Secrets\\" + secretName + "\\CurrVal\\@", 1);
            VK_Structure vk = new VK_Structure(secHive.getBuffer(), vkOfs);

            //secret starts 12 bytes in
            byte[] secret = byteSlice(secHive.getBuffer(), (int)vk.getOfs_data() + 0x1004 + 12, (int)vk.getLen_data() - 12);

            // call decrypySecret to do the actual decryption
            return decryptSecret(secret, lsaKey);
        }

        /**
         * Generic class to decrypt an LSA secret
         *
         * @param secret byte[] containing the encrypted LSA secret
         * @param lsaKey byte[] containing the lsa key needed to decrypt the LSA secret
         * @return a byte[] containing the decrypted LSA secret
         */
        private static byte[] decryptSecret(byte[] secret, byte[] lsaKey)
        {
            DES des;
            int lsaKeyOffset = 0;
            byte[] dest = new byte[secret.length];

            // decrypt secret in 8 byte DES chunks
            for(int i = 0; i < secret.length; i += 8) {
                int length = (secret.length - i < 8)? secret.length - i : 8;

                // use shifting parts of the lsaKey to decode the blocks
                byte[] key_block = byteSlice(lsaKey, lsaKeyOffset, 7);

                // convert the 7 byte key into an 8 byte (w/parity) DES key
                des = new DES( DES.createKey(key_block,0) );
                des.decrypt(secret, i , dest, i, length);

                // shift 7 bytes forward into the lsaKey
                lsaKeyOffset += 7;

                // rotate lsaKeyOffset back into array bounds
                if ((lsaKey.length - lsaKeyOffset) < 7) {
                    lsaKeyOffset = lsaKey.length - lsaKeyOffset;
                }
            }

            // first dword of decrypted secret is secret length
            int secretLen = getDWord(dest, 0);

            // decrypted secret starts 8 bytes in
            return byteSlice(dest, 8, secretLen);
        }

        /**
         * Creates a mapping between user account names and their RID values
         *
         * @param hive the hive that contains usernames
         * @return a hashmap of names and RIDs
         */
	public static HashMap getUserRIDMap(Hive hive)
	{
		HashMap<String, Long> nameRIDMap = new HashMap<String, Long>();

		//find offset to NK structure for this path
		int nkOfs = traversePath(hive, 0, "\\SAM\\Domains\\Account\\Users\\Names",0);

		if(nkOfs <= 0)	//did we find it?
		{
			System.out.println("Cannot find usernames in registry");
			return null;
		}

		int count = 0, countri = 0;
		int exNextN = -2;
		int newNKOfs;

		int sptr_nkoffs;
		NK_Structure sptr_nk;
		String sptr_name;

		while(exNextN > 0 || exNextN == -2)	//-2 is starting case
		{
			//get value of ex_next_n
			NK_Structure nk = new NK_Structure(hive.getBuffer(), nkOfs + 4);

			if(nk.getId() != 0x6b6e)
			{
				System.out.println("Not an nk node....error");
				return null;
			}

			long lfOffset = nk.getOfs_lf() + 0x1004;
			int lfKeyID = getWord(hive.getBuffer(), (int)lfOffset);
			long lfKeyOfsNK = getDWord(hive.getBuffer(), (int)lfOffset + 0x0004);

			RI_Structure ri = new RI_Structure(hive.getBuffer(), (int)nk.getOfs_lf() + 0x1004);

			if(ri.getId() == 0x6972)
			{
				if(countri < 0 || countri >= ri.getNo_lis())
				{
					exNextN = 0;
					continue;
				}

				/* get li of lf struct thats current based on countri */
				LI_Structure li = new LI_Structure(hive.getBuffer(), (int)ri.getOfs_li_array()[countri] + 0x1004);

				if(li.getId() == 0x696c)
				{
					newNKOfs = (int)li.getOfs_nk_array()[count] + 0x1000;
				}
				else
				{
					lfOffset = ri.getOfs_li_array()[countri] + 0x1004;
					lfKeyID = getWord(hive.getBuffer(), (int)lfOffset);
					lfKeyOfsNK = getDWord(hive.getBuffer(), (int)(lfOffset + 0x0004 + (count * 0x0008)) );
					newNKOfs = (int)lfKeyOfsNK + 0x1000;
				}

				if(count >= li.getNo_keys() - 1)
				{
					countri++;	//bump up ri so we take next ri entry next time
					count = -1;	//reset li traverse counter for next round, not used later here
				}
			}
			else	//plain handler
			{
				if(nk.getNo_subkeys() <=0 || count >= nk.getNo_subkeys())
				{
					exNextN = -1;
					continue;
				}

				if(lfKeyID == 0x696c)	//is it 3.x li instead?
				{
					LI_Structure li = new LI_Structure(hive.getBuffer(), (int)nk.getOfs_lf() + 0x1004);
					newNKOfs = (int)li.getOfs_nk_array()[count] + 0x1000;
				}
				else
				{
					lfKeyOfsNK = getDWord(hive.getBuffer(), (int)(lfOffset + 0x0004 + (count * 0x0008)) );
					newNKOfs = (int)lfKeyOfsNK + 0x1000;
				}
			}
			sptr_nkoffs = newNKOfs;
			NK_Structure new_nk_key = new NK_Structure(hive.getBuffer(), newNKOfs + 4);

			sptr_nk = new_nk_key;

			if(new_nk_key.getId() != 0x6b6e)
			{
				System.out.println("ex_next: error not nk node at " + newNKOfs);
				exNextN = 0;
				continue;
			}
			else
			{
				if(new_nk_key.getLen_name() <= 0)
				{
					System.out.println("ex_next nk has no name!");
					return null;
				}
				else
				{
					sptr_name = new_nk_key.getKeynameStr();	//TODO change to actual max len
//					System.out.println("Name: sptr_name: " + sptr_name);

					//find rid for this username
					String ridString = "\\SAM\\Domains\\Account\\Users\\Names\\" + sptr_name + "\\@";
					int offsetRIDVK = traversePath(hive, 0, ridString, 1);
//					System.out.println("Offset RID: " + offsetRIDVK);

					VK_Structure vk_rid = new VK_Structure(hive.getBuffer(), offsetRIDVK);
					long rid;
					if(vk_rid.getLen_data() == 0x80000000)	//special inline case where data is in val type field
					{
//						System.out.println("RID = " + vk_rid.getVal_type());
						rid = vk_rid.getVal_type();
					}
					else
					{
						System.out.println("**********ERROR - CANT FIND RID");
						rid = -1;
					}
					nameRIDMap.put(sptr_name, new Long(rid));
				}
			}
			count++;
		}
//		System.out.println("***GOT HASHMAP OF NAME TO RID PAIRS");
		return nameRIDMap;
	}

	/**
	 * Recursevely follows the given path string and returns the offset for the
	 * last nk or vk structure
	 *
         * @param hive the registry hive
         * @param offsetToStartNode offset to the start node
         * @param path reg path
         * @param structureType the stucture type to find, 0 for nk, 1 for vk
         * @return the offset of the last nk or vk structure
	 */
	public static int traversePath(Hive hive, int offsetToStartNode, String path, int structureType)
	{
		NK_Structure key, newnkkey;
		long vlistofs;

		if(path.startsWith("\\"))	//if we are at the root
		{
			path = path.substring(1,path.length());	//chop off slash
//			System.out.println("new path: " + path);
			offsetToStartNode = hive.getRootOffset() + 4;
		}

		key = new NK_Structure(hive.getBuffer(), offsetToStartNode);

		if(key.getId() != 0x6b6e)
		{
			System.out.println("Error: Not an nk node");
			return 0;
		}

		//find \ delimiter or end of string
		String[] branches = path.trim().split("\\\\");
		String part = branches[0];
//		System.out.println("Searching for: " + part);

		if(part.trim().equals(""))
		{
//			System.out.println("End of string");
			return offsetToStartNode - 4;
		}
		else
		{
			StringBuffer sb = new StringBuffer();
			for(int i = 1; i < branches.length; i++)
			{
				sb.append(branches[i]);
				if (i < branches.length -1)
					sb.append("\\");
			}

			path = sb.toString();
		}

		//last name in path, we want vk, and nk has values
		if(branches.length == 1 && structureType == 1 && key.getNo_values() > 0)
		{
//			System.out.println("VK name match for " + path);
			vlistofs = key.getOfs_vallist() + 0x1004;

			//vlist_find
			int indexIntoTable = -1;
			for(int i = 0; i < key.getNo_values(); i++)
			{
				long vkofs = vlistofs + (i * 0x4); //+ 0x1004;
				int offset = getDWord(hive.getBuffer(), (int)vkofs);
				VK_Structure vkkey = new VK_Structure(hive.getBuffer(), offset + 0x1004);
//				System.out.println(vkkey);

				String compareStr = new String(vkkey.getKeynameStr());

			    if(vkkey.getLen_name() == 0 && part.compareTo("@") == 0) //@ is alias for nameless val
			    {
			        indexIntoTable = i;
			        return offset + 0x1004;
			    }

				if(compareStr.startsWith(part.toLowerCase()))
				{
					indexIntoTable = i;
					return offset + 0x1004; //(int)(getDWord(hdesc.getBuffer(), offset) + 0x1000);
				}
			}
		}

		int subs = -1, riCount, r, newNKOfs;

		if(key.getNo_subkeys() > 0)	//if has subkeys, loop through hash
		{
			long lfOffset = key.getOfs_lf() + 0x1004;
			int lfKeyID = getWord(hive.getBuffer(), (int)lfOffset);
			long lfKeyOfsNK = getDWord(hive.getBuffer(), (int)lfOffset + 0x0004);

			if(lfKeyID == 0x6972)	//ri struct needs special parsing
			{
				System.out.println("ri key needs special parsing -- not handled");
				//make rikey
				riCount = 0; //ricnt = rikey.getNo_lis()
				r = 0;
				//make likey
				//subs = likey.getNo_keys();
				//if likey id != 0x696c
					//make new lfkey
					//likey = null
			}
			else
			{
				if(lfKeyID == 0x696c)	//li?
				{
					System.out.println("li key");
					//likey
				}
				else
				{
//					System.out.println("likey = null");
					//likey == null
				}
				subs = (int)key.getNo_subkeys();
				riCount = 0;
				r = 0;
			}

			do
			{
				for(int i = 0; i < subs; i++)
				{
					//if(likey != null)
					//	newnkofs = likey.hash[i].ofs_nk + 0x1004;
					//else

					lfKeyOfsNK = getDWord(hive.getBuffer(), (int)(lfOffset + 0x0004 + (i * 0x0008)) );
					newNKOfs = (int)lfKeyOfsNK + 0x1004;

					//make new nk key
					newnkkey = new NK_Structure(hive.getBuffer(), newNKOfs);

					//check new nk key id
					if(newnkkey.getId() != 0x6b6e)
					{
						System.out.println("not nk node! strange?");
					}
					else
					{
						if(newnkkey.getLen_name() <= 0)
						{
							System.out.println("[No name]");
						}
						else
						{
							if(newnkkey.getKeynameStr().toLowerCase().startsWith(part.toLowerCase()))
							{
//								System.out.println(" Key at " + newnkofs + " matches, recursing with path: " + path);
//								System.out.println(newnkkey.getKeynameStr().toLowerCase() + " ~== " + part.toLowerCase());
								return traversePath(hive, newNKOfs, path, structureType);
							}
							else
							{
//								System.out.println("Name does not match: " + newnkkey.getKeynameStr().toLowerCase() + " != " + part.toLowerCase());
							}
						}
					}//if id okay

				}//hash loop

				r++;
//				if(ricnt > 0 && r < ricnt)
//				{
//
//				}
			} while(r < riCount && riCount > 0);
		}
		return 0;
	}

        /**
         * function to get a word from a buffer, doesn't modify buf
         * @param buf buffer to extract from
         * @param offset offset to start extraction
         * @return 2 bytes from buf at offset
         */
	public static int getWord(int[] buf, int offset)
	{
		return (( (buf[offset + 1] & 0xFF) << 8) | buf[offset]);
	}

        /**
         * function to get a word from a buffer, doesn't modify buf
         * @param buf buffer to extract from
         * @param offset offset to start extraction
         * @return 2 bytes from buf at offset
         */
	public static int getWord(byte[] buf, int offset)
	{
		return ((buf[offset + 1] << 8) | buf[offset]);
	}

	/**
         * function to get a dword from a buffer, doesn't modify buf
         * @param buf buffer to extract from
         * @param offset offset to start extraction
         * @return 4 bytes from buf at offset
         */
	public static int getDWord(int[] buf, int offset)
	{
		return			 	(	( buf[offset + 3] 	& 0xFF) << 24)
						| 	((buf[offset + 2] 	& 0xFF) << 16)
						| 	((buf[offset + 1] 	& 0xFF) << 8)
						| 	 (buf[offset + 0] 	& 0xFF);
	}

        /**
         * function to get a dword from a buffer, doesn't modify buf
         * @param buf buffer to extract from
         * @param offset offset to start extraction
         * @return 4 bytes from buf at offset
         */
	public static int getDWord(byte[] buf, int offset)
	{
		return			 	        ( buf[offset + 3] << 24)
						| 	( buf[offset + 2] << 16)
						| 	( buf[offset + 1] << 8)
						| 	( buf[offset + 0]);
	}



        /**
         * hexidecimal to int utility function
         * @param hexStr the hex to convert
         * @return the integer version
         */
	public static int hexToInt(String hexStr)
	{
		return Integer.decode(hexStr).intValue();
	}

        /**
         * simple hex dump function (prints)
         * @param msg prefex on the print string
         * @param val the val to print in hex (int, long, or int[])
         */
	public static void printHex(String msg, int val)
	{
		System.out.println(msg + " 0x" + Integer.toHexString(val));
	}

	public static void printHex(String msg, long val)
	{
		System.out.println(msg + " 0x" + Long.toHexString(val));
	}

	public static void printHex(String msg, int[] val)
	{
		StringBuffer buf = new StringBuffer();
		buf.append(msg + " 0x");
		for(int i = 0; i < val.length; i++)
			buf.append(Integer.toHexString(val[i]));
		System.out.println(buf.toString());
	}

        public static String hexToString(byte[] hex)
        {
            try {
                return Util.toString(hex, 0, hex.length).toLowerCase();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        public static void arrayCopy(int[] a, int aOffset, int[] b, int bOffset, int len)
        {
            System.arraycopy(a, aOffset, b, bOffset, len);
        }

        public static void arrayCopy(byte[] a, int aOffset, byte[] b, int bOffset, int len)
        {
            System.arraycopy(a, aOffset, b, bOffset, len);
        }

        public static void arrayCopy(int[] a, int aOffset, byte[] b, int bOffset, int len)
        {
            for (int i = 0; i < len; i++) {
                b[bOffset + i] = (byte)a[aOffset + i];
            }
        }

        public static void arrayCopy(byte[] a, int aOffset, int[] b, int bOffset, int len)
        {
            for (int i = 0; i < len; i++) {
                b[bOffset + i] = (int)a[aOffset + i];
            }
        }

        public static byte[] byteSlice(byte[] array, int offset, int length)
        {
            byte[] slice = new byte[length];
            arrayCopy(array, offset, slice, 0, length);
            return slice;
        }

        public static byte[] byteSlice(int[] array, int offset, int length)
        {
            byte[] slice = new byte[length];
            arrayCopy(array, offset, slice, 0, length);
            return slice;
        }

        public static String utfToString(byte[] utf, boolean lowerCase)
        {
            String retString;
            try {
                retString = new String(utf, "UTF-16LE");
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }

            if (lowerCase) {
                retString = retString.toLowerCase();
            }

            return retString;
        }
}
