/******************************************************************************
 *
 * Copyright (c) 1998-2000 by Mindbright Technology AB, Stockholm, Sweden.
 *                 www.mindbright.se, info@mindbright.se
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
 *****************************************************************************
 * $Author: nallen $
 * $Date: 2001/11/12 16:31:15 $
 * $Name:  $
 *****************************************************************************/
//package mindbright.security;
package cert.forensics.registry.crypto;

public abstract class MessageDigest {
    public static boolean useNative = false;

    public static MessageDigest getInstance(String algorithm)
	throws InstantiationException, IllegalAccessException, ClassNotFoundException
    {
	if(useNative) {
	    try {
		Class c1, c2;
		c1 = Class.forName("java.security.MessageDigest");
		c2 = Class.forName("mindbright.security.NativeHashState");
		MessageDigest md = (MessageDigest)c2.newInstance();
		md.init(algorithm);
		return md;
	    } catch (Throwable t) {
		// !!! Oh well, we're not too worried, the pure java
		// versions are pretty quick, we don't need no
		// steenking native code anyway... :-)
	    }
	}
	Class c;
	c = Class.forName("mindbright.security." + algorithm);
	return (MessageDigest)c.newInstance();
    }

    protected void init(String algorithm) throws Exception {
    }

    public abstract String getName();
    public abstract void reset();
    public abstract void update(byte[] buf, int offset, int length);
    public abstract byte[] digest();
    public abstract int blockSize();
    public abstract int hashSize();

    public final void update(byte[] buf) {
        update(buf, 0, buf.length);
    }

    public int digestInto(byte[] dest, int destOff) {
	byte[] dig = digest();
	System.arraycopy(dig, 0, dest, destOff, dig.length);
	return dig.length;
    }

    public Object clone() throws CloneNotSupportedException {
	if(this instanceof Cloneable) {
	    return super.clone();
	} else {
	    throw new CloneNotSupportedException();
	}
    }
}
