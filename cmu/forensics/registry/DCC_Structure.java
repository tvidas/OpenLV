/*
   DCC_Structure.java
   Copyright (C) 2009

   Tom Spencer <tom at synlapse d0t com>


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

import java.util.Arrays;
import java.util.Random;
import java.util.zip.DataFormatException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import cmu.forensics.registry.crypto.MD4;
import cmu.forensics.registry.crypto.RC4;
import cmu.forensics.registry.crypto.Util;

/**
 * DCC_Structure
 * Represents a Domain Cached Credential (DCC) registry structure in the SECURITY hive
 * Offsets are relative to the beginning of the DCC structure
 * @author Tom Spencer
 * @version 0.7, Mar 2009
 */

public class DCC_Structure
{
    private int unameLen;           // 0x0000
    private int domainLen;          // 0x0002
    private int pre2kUnameLen;      // 0x0004
    private int unameFullLen;       // 0x0006
    private int logonScriptLen;     // 0x0008
    private int profilePathLen;     // 0x000A
    private int homeDirLen;         // 0x000C
    private int homeDriveLen;       // 0x000E
    private int userRID;            // 0x0010
    private int groupRID;           // 0x0014
    // 0x0018 - 0x001D unknown 6 bytes
    //private int logonDomainLen;   // 0x001E
    private long lastAccessTime;    // 0x0020
    private int dccVersion;         // 0x0028
    // 0x002C - 0x002F unknown 4 bytes
    private int userEnabled;        // 0x0030
    // 0x0032 - 0x003B unknown 14 bytes
    private int domainFullLen;      // 0x003C
    private int logonFullLen;       // 0x003E

    private byte[] dccData;         // Holds all of the dccData

    private int dccDataSize;        // Convenient DCE size reference (dccData.length - dceRootOffset)
    private int dceDataSize;        // Convenient DCE size reference (dccData.length - dceRootOffset)

    private SecretKeySpec nlkmKey;  // decryption key in a SecretKeySpec object

    private boolean encrypted       = true;     // is the DCE encrypted?
    private boolean keySet          = false;    // is the decryption key set?

    private int cipherKeyOffset     = 0x40;     // offset of Cipher Key from the DCC root
    private int hmacOffset          = 0x50;     // offset of the HMAC from the DCC root
    private int dceRootOffset       = 0x60;     // offset of the DCE from the DCC root

    // Offset measured from dceRootOffset
    private int hashOffset          = 0x0;      // offset of hash from dceRootOffset
    private int userOffset          = 0x48;     // offset of user from dceRootOffset
    private int domainOffset;                   // variable offset, depends on previous lengths
    private int fullDomainOffset;               // variable offset, depends on previous lengths


    // Constants
    private static final int DCCVER_NT3_0 = 0x10000; // WinNT 3.0
    private static final int DCCVER_NT3_5 = 0x10002; // WinNT 3.5
    private static final int DCCVER_NT4_0 = 0x10003; // WinNT 4.0 SP4
    private static final int DCCVER_NT5_0 = 0x10004; // Windows 2000/XP/2003

    private boolean encryptedDCE        = true;

    /**
     *  DCC Struct constructor
     *
     *  @param hiveBuf the hive to populate the struct with
     *  @param offsetInHive offset into the hive for the DCC_Structure
     *  @param dccSize size of the DCC Structure
     */
    public DCC_Structure(int[] hiveBuf, int offsetInHive, int dccSize, boolean enableUsers) throws DataFormatException
    {
        unameLen        = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0000);
        domainLen       = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0002);
        pre2kUnameLen   = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0004);
        unameFullLen    = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0006);
        logonScriptLen  = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0008);
        profilePathLen  = RegistryParser.getWord(hiveBuf, offsetInHive + 0x000A);
        homeDirLen      = RegistryParser.getWord(hiveBuf, offsetInHive + 0x000C);
        homeDriveLen    = RegistryParser.getWord(hiveBuf, offsetInHive + 0x000E);
        userRID         = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0010);
        groupRID        = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0014);
        // 0x0018 - 0x001D unknown 6 bytes
        //logonDomainLen  = RegistryParser.getWord(hiveBuf, offsetInHive + 0x001E);
        lastAccessTime  = ((long)RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0024) << 32)
                         | (long)RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0020);
        dccVersion      = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0028);
        // 0x002C - 0x002F unknown 4 bytes
        userEnabled     = RegistryParser.getWord(hiveBuf, offsetInHive + 0x0030);
        // 0x0032 - 0x003B unknown 12 bytes
        domainFullLen   = RegistryParser.getWord(hiveBuf, offsetInHive + 0x003C);
        logonFullLen    = RegistryParser.getWord(hiveBuf, offsetInHive + 0x003E);

        if (dccVersion != DCCVER_NT5_0) {
            throw new DataFormatException("Only NT5 style domain cached credentials are currently supported");
        }

        //copy entire DCC entry to dccData
        dccData         = RegistryParser.byteSlice(hiveBuf, offsetInHive, dccSize);
        dccDataSize     = dccSize;
        dceDataSize     = dccDataSize - dceRootOffset;

        if (userEnabled == 0) {
            if (enableUsers == false) {
                throw new DataFormatException("User is not enabled and enabling users is disabled!");
            }

            // disabled accounts have decrypted DCEs
            encrypted = false;

            // enable user account
            enableUser();
        }

        // pad User and Domain out to 4 bytes
        int lenUserPad   = (unameLen  % 4 == 0)? 0 : 4 - (unameLen  % 4);
        int lenDomainPad = (domainLen % 4 == 0)? 0 : 4 - (domainLen % 4);

        // set the domain and full domain offset
        domainOffset     = userOffset   + unameLen  + lenUserPad;
        fullDomainOffset = domainOffset + domainLen + lenDomainPad;
    }

    /**
     * generic toString method that assembles datamembers
     * @return a formated string
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        String newL = System.getProperty("line.separator");
        sb.append("dccVersion\t 0x" + Integer.toHexString(dccVersion) + newL);
        sb.append("unameLen\t 0x" + Integer.toHexString(unameLen) + newL);
        sb.append("domainLen\t 0x" + Integer.toHexString(domainLen) + newL);
        sb.append("domainFullLen\t 0x" + Integer.toHexString(domainFullLen) + newL);
        sb.append("CipherKey\t 0x" + getCipherKeyS() + newL);
        sb.append("HMAC\t 0x" + getHMACS() + newL);
        sb.append("hash\t 0x" + getHashS() + newL);

        return sb.toString();
    }

    /**
     * Calculates a new HMAC for the current DCE using the RC4 key as the HMAC key
     * @param data byte[] containing the DCE data, usually this would be the dccData structure
     * @param offset offset into the data structure where the DCE structure starts
     * @param key key to use for the HMAC
     * @return HMAC byte[] of the DCE entry
     */
    public byte[] calcHmac(byte[] data, int offset, byte[] key)
    {
        byte[] dataToHMAC = RegistryParser.byteSlice(data, offset, data.length - offset);

        SecretKey sk = new SecretKeySpec(key, "HmacMD5");

        Mac mac;

        try {
            mac = Mac.getInstance("HmacMD5");
            mac.init(sk);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        return mac.doFinal(dataToHMAC);
    }

    /**
     * Convenience function to set the key and then decrypt the DCE using that key
     * @param nlkmByteKey key to use to decrypt the DCE
     */
    public void decrypt(byte[] nlkmByteKey) throws DataFormatException
    {
        setKey(nlkmByteKey);

        try {
            decrypt();
        } catch (DataFormatException e) {
            throw e;
        }
    }

    /**
     * Decrypts the DCE inplace in the dccData structure
     */
    public void decrypt() throws DataFormatException
    {
        if (!keySet) { return; }

        // get the RC4 Key, this is an HMAC of the DCC's CipherKey using the nlkmKey
        byte[] rc4Key = getRC4Key();

        // the DCE is only encrypted if the user is enabled
        if (encrypted)
        {
            // decrypt DCE in place
            RC4 rc4 = new RC4();
            rc4.setKey(rc4Key);
            rc4.decrypt(dccData, dceRootOffset, dccData, dceRootOffset, dceDataSize);
        }

        // compute the HMAC for the decrypted DCE
        byte[] hmac = calcHmac(dccData, dceRootOffset, rc4Key);

        // compare the computed HMAC to the DCC's stored HMAC, they should match
        if (!Arrays.equals(getHMAC(), hmac) )
        {
            // didn't decompress properly, output warning
            throw new DataFormatException("DCC decrypt: HMAC validation failed!");
        }

        // DCE is now decrypted
        encrypted = false;
    }

    /**
     * Encrypts the DCE inplace in the dccData structure
     */
    public void encrypt()
    {
        if (encrypted || !keySet) { return; }

        // get the RC4 Key, this is an HMAC of the DCC's Cipher Key using the nlkmKey
        // this is computed again in case the Cipher Key has been changed
        byte[] rc4Key = getRC4Key();

        // Calculate the HMAC for the DCE and store it
        setHMAC(calcHmac(dccData, dceRootOffset, rc4Key));

        RC4 rc4 = new RC4();
        rc4.setKey(rc4Key);

        // encrypt DCE in place
        rc4.encrypt(dccData, dceRootOffset, dccData, dceRootOffset, dceDataSize);

        // DCE is now encrypted again
        encrypted = true;
    }

    /**
     * Convert the byte[] NL$KM into the usable SecretKeySpec HmacMD5 object
     * @param nlkmByteKey byte[] containing the NL$KM key
     */
    public void setKey(byte[] nlkmByteKey)
    {
        if (nlkmByteKey == null) { return; }

        // Generate secret key for HMAC-MD5
        nlkmKey = new SecretKeySpec(nlkmByteKey, "HmacMD5");

        // nlkm key has been set
        keySet = true;
    }

    /**
     * Compute the RC4 key from the HMAC-MD5 of the nlkmKey and the CipherKey
     * @return RC4 key to encrypt/decrypt the DCE
     */
    public byte[] getRC4Key()
    {
        // Get instance of Mac object implementing HMAC-MD5, and
        // initialize it with the secret key

        Mac mac;

        try {
            mac = Mac.getInstance("HmacMD5");
            mac.init(nlkmKey);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        return mac.doFinal(getCipherKey());
    }

    /**
     * Sets password to the passed ANSI String for the current DCC entry
     * @param password ANSI String containing the new password or an empty string for no password
     */
    public void setUserPassword(String password)
    {
        byte[] utfPass;
        byte[] uNameLowerCase;

        try {
            utfPass = password.getBytes("UTF-16LE");    // password needs to be in UTF-16LE form
            uNameLowerCase = new String(getUser(), "UTF-16LE").toLowerCase().getBytes("UTF-16LE");
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }

        MD4 md4 = new MD4();
        md4.update(utfPass);
        md4.update(md4.digest());       // MD4 password and add the hash to the next MD4 iteration
        md4.update(uNameLowerCase);     // concatenate username as a salt to the hashed password

        byte[] hash = md4.digest();

        /*
         * For OSs beyond win2k3 a multi-thousand round SHA1 based PBKDF2 is used
         * the number of iterations is stored somewhere in the DCC (maybe in the DCE?), default is 10240 (0x2800) rounds
         * but where does the salt come from??
        if (getDccVersion() >= win2k3)
        {
            PBKDF2 kdf = new PBKDF2();
            hash = kdf.deriveKey(salt, (long) getPBKDF2iterations(), hash); where does salt come from?
        }
        */

        setHash( hash );  // hash again and update the DCE password hash
    }

    /**
     * Sets the current DCC entry password to nothing
     */
    public void clearUserPassword()
    {
        setUserPassword(""); // set a blank password
    }

    /**
     * Sets the "user enabled" flag in the DCC
     */
    public void enableUser()
    {
        // enable user
        byte[] enableBytes = new byte[] { (byte)0x01, (byte)0x00 };
        System.arraycopy(enableBytes, 0, dccData, 0x30, 2);
    }

    /**
     * Retrieves the mscash (CacheDump) formatted line for cracking
     * @return ANSI mscash line
     */
    public String getMSCASHline()
    {
        return new String(getUserS() + ":" + getHashS() + ":" + getDomainS() + ":" + getFullDomainS());
    }

    /**
     * Retrieves the full user name
     * @return ANSI full user name
     */
    public String getFullUserName()
    {
        return new String(getUserS() + "@" + getFullDomainS());
    }

    /**
     * Retrieves the DCC cipher key
     * @return Current DCC Cipher Key
     */
    public byte[] getCipherKey()
    {
        return RegistryParser.byteSlice(dccData, cipherKeyOffset, 0x10);
    }

    /**
     * overwrite the Cipher Key in the DCC.  This is not required to reset the password.
     * @param newCipherKey byte[] containing the new Cipher Key to set, only first 0x10 bytes used
     */
    public void setCipherKey(byte[] newCipherKey)
    {
        System.arraycopy(newCipherKey, 0, dccData, cipherKeyOffset, 0x10);
    }

    /**
     * Create a new random Cipher Key.  Windows does this for every DCE change, although
     * it is isn't strictly required or enforced.  This function is provided for completeness only.
     * @effect a new random Cipher Key will be set, this will change the RC4 key used for encryption/decryption
     */
    public void setRandomCipherKey()
    {
        byte[] newCipherKey = new byte[0x10];
        new Random().nextBytes(newCipherKey);
        setCipherKey(newCipherKey);
    }

    /**
     * Retrieves the DCC HMAC
     * @return Current DCC HMAC
     */
    public byte[] getHMAC()
    {
        return RegistryParser.byteSlice(dccData, hmacOffset, 0x10);
    }

    /**
     * overwrite the HMAC in the DCC
     * @param newHmac byte[] containing the new HMAC to set, only first 0x10 bytes will be used
     */
    public void setHMAC(byte[] newHmac)
    {
        System.arraycopy(newHmac, 0, dccData, hmacOffset, 0x10);
    }

    /**
     * Retrieves the DCE password hash in its current encrypted or decrypted form
     * @return Current DCE password hash
     */
    public byte[] getHash()
    {
        return RegistryParser.byteSlice(dccData, dceRootOffset + hashOffset, 0x10);
    }

    /**
     * overwrite the hash in the DCE
     * @param newHash byte[] containing the new hash to set, only first 0x10 bytes will be used
     */
    public void setHash(byte[] newHash)
    {
        System.arraycopy(newHash, 0, dccData, dceRootOffset, 0x10);
    }

    /**
     * inspector function for dccVersion
     * @return the dccVersion datamember
     */
    public int getDccVersion()
    {
        return dccVersion;
    }

    /**
     * inspector function for the user name
     * @return the unicode user name from the DCE in its current decrypted or encrypted form
     */
    public byte[] getUser()
    {
        return RegistryParser.byteSlice(dccData, dceRootOffset + userOffset, unameLen);
    }

    /**
     * inspector function for the short domain name
     * @return the unicode short domain name from the DCE in its current decrypted or encrypted form
     */
    public byte[] getDomain()
    {
        return RegistryParser.byteSlice(dccData, dceRootOffset + domainOffset, domainLen);
    }

    /**
     * inspector function for the full domain name
     * @return the unicode full domain name from the DCE in its current decrypted or encrypted form
     */
    public byte[] getFullDomain()
    {
        return RegistryParser.byteSlice(dccData, dceRootOffset + fullDomainOffset, domainFullLen);
    }

    /**
     * inspector function for the DCE
     * @return the entire DCE in its current decrypted or encrypted form
     */
    public byte[] getDCE()
    {
        return RegistryParser.byteSlice(dccData, dceRootOffset, dceDataSize);
    }

    /**
     * inspector function for the DCC
     * @return the entire DCC
     */
    public byte[] getDCC()
    {
        return RegistryParser.byteSlice(dccData, 0, dccDataSize);
    }

    /**
     * inspector function for the Cipher Key String
     * @return the Cipher Key hex in ansi string form
     */
    public String getCipherKeyS()
    {
        return RegistryParser.hexToString(getCipherKey());
    }

    /**
     * inspector function for the HMAC String
     * @return the HMAC hex in ansi string form
     */
    public String getHMACS()
    {
        return RegistryParser.hexToString(getHMAC());
    }

    /**
     * inspector function for the Hash String
     * @return the Hash hex in ansi string form
     */
    public String getHashS()
    {
        return RegistryParser.hexToString(getHash());
    }

    /**
     * inspector function for the Username String
     * @return the Username in ansi string form
     */
    public String getUserS()
    {
        return RegistryParser.utfToString(getUser(), false);
    }

    /**
     * inspector function for the short Domain name String
     * @return the short Domain name in ansi string form
     */
    public String getDomainS()
    {
        return RegistryParser.utfToString(getDomain(), true);
    }

    /**
     * inspector function for the full Domain name String
     * @return the full Domain name in ansi string form
     */
    public String getFullDomainS()
    {
        return RegistryParser.utfToString(getFullDomain(), true);
    }
}

