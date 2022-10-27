/**
 *******************************************************************************************
 **
 **  @filename       Utils.java
 **  @brief
 **
 **  @copyright      (c) Core|Vision B.V.,
 **                  Cereslaan 10b,
 **                  5384 VT  Heesch,
 **                  The Netherlands,
 **                  All Rights Reserved
 **
 **  @author         tom
 **  @svnversion     $Date: 2021-12-18 15:34:55 +0100 (Sat, 18 Dec 2021) $
 **                  $Revision: 50048 $
 **
 *******************************************************************************************
 */


package nl.das.svnactions;

import java.nio.ByteBuffer;
import java.util.UUID;

/**
 *
 */
public class Utils {

	private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();

    public static String generateGUID() {
    	UUID uuid = UUID.randomUUID();
        ByteBuffer bb = ByteBuffer.wrap(new byte[16]);
        bb.putLong(uuid.getMostSignificantBits());
        bb.putLong(uuid.getLeastSignificantBits());
        byte[] bar = bb.array();
    	StringBuilder hex = new StringBuilder();
    	for (int i = 0; i < bar.length; i += 2) {
	        char[] hexDigits = new char[2];
	        hexDigits[0] = Character.forDigit((bar[i] >> 4) & 0xF, 16);
	        hexDigits[1] = Character.forDigit((bar[i + 1] & 0xF), 16);
	        hex.append(hexDigits);
    	}
        return hex.toString();
    }

    public static String bytesToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int j = 0; j < bytes.length; j++) {
            int v = bytes[j] & 0xFF;
            hexChars[j * 2] = HEX_ARRAY[v >>> 4];
            hexChars[(j * 2) + 1] = HEX_ARRAY[v & 0x0F];
        }
        return new String(hexChars);
    }
}
