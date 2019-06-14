/*
 * Copyright © 2017 Miura Systems Ltd. All rights reserved.
 */
package com.miurasystems.miuralibrary.update;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;

public final class FileUtil {

    public static byte[] fileToByte(String filename, int offset, int length) {
        int size;
        byte[] w = new byte[length];
        FileInputStream in = null;
        ByteArrayOutputStream out = null;
        try {

            in = new FileInputStream(new File(filename));
            in.getChannel().position(offset);

            out = new ByteArrayOutputStream();
            size = in.read(w, 0, length);
            out.write(w, 0, size);
            out.close();

            in.close();

            return out.toByteArray();
        } catch (Exception e) {
        } finally {
            try {
                if (in != null) in.close();
                if (out != null) out.close();
            } catch (Exception e2) {
            }
        }
        return null;
    }

}
