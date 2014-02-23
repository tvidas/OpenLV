/*
   SAMV_Structure.java
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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import cmu.forensics.registry.crypto.DES;
import cmu.forensics.registry.crypto.RC4;
import cmu.forensics.registry.crypto.Util;

/**
 * SAMV_Structure
 * Represents a user's SAM V registry structure (SAMV) in the SAM hive
 * All offsets are relative to the beginning of the SAMV structure
 * @author Tom Spencer
 * @version 0.7, Mar 2009
 */

public class SAMV_Structure
{
    private int unameOffset;        //0x000C
    private int unameLen;           //0x0010
    private int lmHashOffset;       //0x009C
    private int lmHashLen;          //0x00A0
    private int ntHashOffset;       //0x00A8
    private int ntHashLen;          //0x00AC

    private byte[] vData;           //Holds all of the SAM V Data

    private boolean encrypted;      // are the hashes encrypted?
    private boolean keySet;         // is the hashed boot key (hBootKey) set?
    private boolean sysKey;         // is the hashed boot key (hBootKey) set?

    private byte[] hBootKey;        // hashed boot key (derived from syskey)

    private byte[] rid;             // user RID in byte[] format
    private int irid;               // user RID in int format for convenience

    // constants
    private static final int DES_BLOCK_SIZE = 8;        // DES ECB blocks are 8 bytes long
    private static final byte[] ALMPASSWORD = new String("LMPASSWORD\0").getBytes();    // Magic LM String
    private static final byte[] ANTPASSWORD = new String("NTPASSWORD\0").getBytes();    // Magic NT String

    /**
     *  SAMV Struct constructor
     *
     *  @param hiveBuf the hive to populate the struct with
     *  @param offsetInHive offset into the hive for the NK_structure
     *  @param size of V data structure
     */
    public SAMV_Structure(int[] hiveBuf, int offsetInHive, int size)
    {
        unameOffset     = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x0C) + 0xCC;
        unameLen        = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x10);
        lmHashOffset    = RegistryParser.getDWord(hiveBuf, offsetInHive + 0x9C) + 0xCC;
        lmHashLen       = RegistryParser.getDWord(hiveBuf, offsetInHive + 0xA0);
        ntHashOffset    = RegistryParser.getDWord(hiveBuf, offsetInHive + 0xA8) + 0xCC;
        ntHashLen       = RegistryParser.getDWord(hiveBuf, offsetInHive + 0xAC);

        //copy entire SAMV entry to vData
        vData           = RegistryParser.byteSlice(hiveBuf, offsetInHive, size);

        sysKey = false;

        if (lmHashLen == 20 || ntHashLen == 20) {
            sysKey = true;

            // SYSKEY hash offsets have a 4 byte counter before the actual hash, we need to skip this counter
            lmHashOffset += 4;
            ntHashOffset += 4;

            // hashes are 16 bytes, if length is not 20 this hash is not set and should be considered to be 0
            if (lmHashLen == 20) { lmHashLen = 16; }
            if (ntHashLen == 20) { ntHashLen = 16; }
        }

        encrypted  = true;
    }

    /**
     * generic toString method that assembles datamembers
     * @return a formated string
     */
    public String toString()
    {
        StringBuffer sb = new StringBuffer();
        String newL = System.getProperty("line.separator");
        sb.append("unameOffset\t 0x" + Integer.toHexString(unameOffset) + newL);
        sb.append("unameLen\t 0x" + Integer.toHexString(unameLen) + newL);
        sb.append("lmHashOffset\t 0x" + Integer.toHexString(lmHashOffset) + newL);
        sb.append("lmHashLen\t 0x" + Integer.toHexString(lmHashLen) + newL);
        sb.append("ntHashOffset\t 0x" + Integer.toHexString(ntHashOffset) + newL);
        sb.append("ntHashLen\t 0x" + Integer.toHexString(ntHashLen) + newL);
        sb.append("sysKey\t\t " + ((sysKey)?"true":"false") + newL);

        if (!encrypted) {
            sb.append("username\t " + getUserS() + newL);
            sb.append("rid\t\t " + irid + newL);
            sb.append("lmhash\t\t " + getLMHashS() + newL);
            sb.append("nthash\t\t " + getNTHashS() + newL);
        }

        return sb.toString();
    }

    /**
     * sid_to_key1 - adapted from pwdump, converts user's rid to a des key
     * @return byte[] containing key for the first des encrypted hash block
     */
    private byte[] sid_to_key1()
    {
        byte[] s = new byte[7];

        s[0] = rid[0];
        s[1] = rid[1];
        s[2] = rid[2];
        s[3] = rid[3];
        s[4] = s[0];
        s[5] = s[1];
        s[6] = s[2];

        return s;
    }

    /**
     * sid_to_key2 - adapted from pwdump, converts user's rid to a des key
     * @return byte[] containing key for the second des encrypted hash block
     */
    private byte[] sid_to_key2()
    {
        byte[] s = new byte[7];

        s[0] = rid[3];
        s[1] = rid[0];
        s[2] = rid[1];
        s[3] = rid[2];
        s[4] = s[0];
        s[5] = s[1];
        s[6] = s[2];

        return s;
    }

    /**
     * Convenience function to set the key and user's rid and then decrypt the
     * password hashes using those values
     * @param hashedBootKey key to use to decrypt the password hashes in NT5+, null if pre NT5
     * @param userRid user's rid, used to create des keys to decrypt individual hash blocks
     */
    public void decrypt(byte[] hashedBootKey, int userRid)
    {
        setHBootKey(hashedBootKey);
        setRid(userRid);
        decrypt();
    }

    /**
     * Decrypts the password hashes inplace in the vData structure
     */
    public void decrypt()
    {
        if (!encrypted || (sysKey && !keySet)) { return; }

        // set key1, this is a function of the users rid
        DES des1 = new DES( DES.createKey(sid_to_key1(), 0) );

        // set key2, this is a function of the users rid
        DES des2 = new DES( DES.createKey(sid_to_key2(), 0) );

        // decrypt LM hash
        decryptHash(vData, lmHashOffset, lmHashLen, ALMPASSWORD, des1, des2);

        // decrypt NT hash
        decryptHash(vData, ntHashOffset, ntHashLen, ANTPASSWORD, des1, des2);

        // hashes are now decrypted (or properly recognized as absent)
        encrypted = false;
    }

    /**
     * Handles the actual inplace decryption of a single password hash
     * @param data byte[] containing password hash
     * @param hashOffset hashes offset into data
     * @param hashLen length of hash
     * @param aLmNtPassword byte representation of magic string needed to create RC4 Key
     * @param des1 DES instance set with key1 from sid_to_key1
     * @param des2 DES instance set with key2 from sid_to_key2
     */
    private void decryptHash(byte[] data, int hashOffset, int hashLen, byte[] aLmNtPassword, DES des1, DES des2)
    {
        // hashLen must be 16 or hash is not set
        if (hashLen != 16) { return; }

        // hBootKey needed for SYSKEY systems
        if (sysKey) {
            RC4 rc4 = new RC4();

            // get RC4 key, this is an MD5 of bootkey, user rid, and ALMPASSWORD
            rc4.setKey( getRC4Key(aLmNtPassword) );
            rc4.decrypt(data, hashOffset, data, hashOffset, hashLen);
        }

        // decrypt first hash block inplace with key1
        des1.decrypt(data, hashOffset, data, hashOffset, DES_BLOCK_SIZE);
        // decrypt second hash block inplace with key2
        des2.decrypt(data, hashOffset + DES_BLOCK_SIZE, data, hashOffset + DES_BLOCK_SIZE, DES_BLOCK_SIZE);
    }

/*
    Encrypt not implemented, any reason to?
    public void encrypt()
    {
        if (encrypted || !keySet) { return; }

        encrypted = true;
    }
*/
    /**
     * set hBootKey - this is needed for hash decryption from NT5+
     * @param hashedBootKey byte[] containing the hashed boot key, only first 0x10 bytes will be used
     */
    public void setHBootKey(byte[] hashedBootKey)
    {
        // hashedBootKey less than 16 bytes, treat this as pre NT5 that doesn't need hBootKey
        if (hashedBootKey == null || hashedBootKey.length < 0x10) { return; }

        // Store hashed Bootkey
        hBootKey = RegistryParser.byteSlice(hashedBootKey, 0, 0x10);
        keySet = true;
    }

    /**
     * set user's rid - this is needed for hash decryption
     * @param intRid user's rid in int format
     */
    public void setRid(int intRid)
    {
        // store rid as an int for convenience
        irid = intRid;

        // store rid as a byte[] for crypto stuff
        rid = new byte[4];

        // int to Little Endian byte[]
        for (int i = 0; i < 4; i++) {
            rid[i] = (byte)( intRid >> ((i) * 8) );
        }
    }

    /**
     * get the RC4 key needed for hash decryption in NT5.0+.  This requires the hashed boot key
     * and the user rid be set.
     * @param lmntString byte[] containing a magic string needed to create the RC4 key
     */
    public byte[] getRC4Key(byte[] lmntString)
    {
        if (!keySet) { return null; }

        MessageDigest md5;

        try
        {
            md5 = MessageDigest.getInstance("MD5");
        }
        catch (NoSuchAlgorithmException nsae)
        {
            //Error
            return null;
        }

        md5.update(hBootKey);
        md5.update(rid);
        md5.update(lmntString);

        return md5.digest();
    }

/*
    setUserPassword and helper function clearUserPassword
    not implemented.  Clearing can be done more simply and
    more universally by just changing LM/NT hash sizes to 0
    If we need to add a password for someone who currently
    has no hash we will make the SAMV entry longer, this
    will be quite difficult to translate back to the reg file
    without significant effort

    public void setUserPassword(String password)
    {

    }

    public void clearUserPassword()
    {
        setUserPassword(""); // set a blank password
    }
*/
    /**
     * get the user's current password in PWDump format (user:rid:lmhash:nthash:::)
     * @return ANSI String of user's current password in PWDump format
     */
    public String getPWDUMPline()
    {
        return new String(getUserS() + ":" + irid + ":" + getLMHashS() + ":" + getNTHashS() + ":::");
    }

    /**
     * Retrieves user's LM hash or the blank LM hash if no hash exists
     * @return byte[] containing user's LM hash
     */
    public byte[] getLMHash()
    {
        if (lmHashLen == 16)
        {
            return RegistryParser.byteSlice(vData, lmHashOffset, lmHashLen);
        }
        else
        {
            // return blank LM hash : aad3b435b51404eeaad3b435b51404ee
            return new byte[] { (byte)0xaa, (byte)0xd3, (byte)0xb4, (byte)0x35,
                                (byte)0xb5, (byte)0x14, (byte)0x04, (byte)0xee,
                                (byte)0xaa, (byte)0xd3, (byte)0xb4, (byte)0x35,
                                (byte)0xb5, (byte)0x14, (byte)0x04, (byte)0xee
                              };
        }
    }

    /**
     * Retrieves user's NT hash or the blank NT hash if no hash exists
     * @return byte[] containing user's NT hash
     */
    public byte[] getNTHash()
    {
        if (ntHashLen == 16)
        {
            return RegistryParser.byteSlice(vData, ntHashOffset, ntHashLen);
        }
        else
        {
            // return blank NT hash : 31d6cfe0d16ae931b73c59d7e0c089c0
            return new byte[] { (byte)0x31, (byte)0xd6, (byte)0xcf, (byte)0xe0,
                                (byte)0xd1, (byte)0x6a, (byte)0xe9, (byte)0x31,
                                (byte)0xb7, (byte)0x3c, (byte)0x59, (byte)0xd7,
                                (byte)0xe0, (byte)0xc0, (byte)0x89, (byte)0xc0
                              };
        }
    }

    /**
     * Retrieves the user name
     * @return byte[] containing user name
     */
    public byte[] getUser()
    {
        return RegistryParser.byteSlice(vData, unameOffset, unameLen);
    }

    /**
     * Retrieves the LM hash as a String
     * @return ANSI String of user's LM hash
     */
    public String getLMHashS()
    {
        return RegistryParser.hexToString(getLMHash());
    }

    /**
     * Retrieves the NT hash as a String
     * @return ANSI String of user's NT hash
     */
    public String getNTHashS()
    {
        return RegistryParser.hexToString(getNTHash());
    }

    /**
     * Retrieves the user name as a String
     * @return ANSI String of user name
     */
    public String getUserS()
    {
        return RegistryParser.utfToString(getUser(), false);
    }
}

