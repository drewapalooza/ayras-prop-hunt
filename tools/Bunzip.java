/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.tools.bzip2.CBZip2InputStream;

// Build-time helper: the cache omits the BZh1 header. Apache Ant ships with Gradle.
public final class Bunzip
{
    public static void main(String[] args) throws Exception
    {
        byte[] compressed = Files.readAllBytes(Path.of(args[0]));
        byte[] input = new byte[compressed.length + 2];
        input[0] = 'h';
        input[1] = '1';
        System.arraycopy(compressed, 0, input, 2, compressed.length);
        try (CBZip2InputStream stream = new CBZip2InputStream(new ByteArrayInputStream(input)))
        {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] block = new byte[8192];
            int count;
            while ((count = stream.read(block, 0, block.length)) != -1)
            {
                output.write(block, 0, count);
            }
            Files.write(Path.of(args[1]), output.toByteArray());
        }
    }
}
