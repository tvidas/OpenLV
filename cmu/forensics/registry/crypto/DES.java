/* Jarapac DCE/RPC Framework
 * Copyright (C) 2003  Eric Glass
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

//package rpc.security.ntlm;
package cmu.forensics.registry.crypto;

import java.security.Key;

import javax.crypto.Cipher;

import javax.crypto.spec.SecretKeySpec;

public class DES {

    private final Cipher cipher;
    private Key pkey;

    public DES(byte[] key) {
        cipher = createCipher();
        pkey = new SecretKeySpec(key, "DES");
    }

    public void decrypt(byte[] data, int offset, byte[] output, int index, int length) {
        try {
            cipher.init(Cipher.DECRYPT_MODE, pkey);
            cipher.doFinal(data, offset, length, output, index);
        } catch (Exception ex) {
            throw new IllegalStateException();
        }
    }

    public void encrypt(byte[] data, int offset, byte[] output, int index, int length) {
        try {
            cipher.init(Cipher.ENCRYPT_MODE, pkey);
            cipher.doFinal(data, offset, length, output, index);
        } catch (Exception ex) {
            throw new IllegalStateException();
        }
    }

    private static Cipher createCipher() {
        try {
            Cipher cipher = Cipher.getInstance("DES/ECB/NoPadding");
            return cipher;
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to create cipher: " + ex);
        }
    }

    public static byte[] createKey(byte[] bytes, int offset) {
        byte[] material = new byte[8];
        material[0] = bytes[offset];
        material[1] = (byte) (bytes[offset++] << 7 |
                (bytes[offset] & 0xff) >>> 1);
        material[2] = (byte) (bytes[offset++] << 6 |
                (bytes[offset] & 0xff) >>> 2);
        material[3] = (byte) (bytes[offset++] << 5 |
                (bytes[offset] & 0xff) >>> 3);
        material[4] = (byte) (bytes[offset++] << 4 |
                (bytes[offset] & 0xff) >>> 4);
        material[5] = (byte) (bytes[offset++] << 3 |
                (bytes[offset] & 0xff) >>> 5);
        material[6] = (byte) (bytes[offset++] << 2 |
                (bytes[offset] & 0xff) >>> 6);
        material[7] = (byte) (bytes[offset] << 1);
        // adjust odd-parity
        for (int i = 0; i < 8; i++) {
            byte b = material[i];
            if ((((b >>> 7) ^ (b >>> 6) ^ (b >>> 5) ^ (b >>> 4) ^ (b >>> 3) ^
                    (b >>> 2) ^ (b >>> 1)) & 0x01) == 0) {
                material[i] |= (byte) 0x01;
            } else {
                material[i] &= (byte) 0xfe;
            }
        }
        return material;
    }

}
