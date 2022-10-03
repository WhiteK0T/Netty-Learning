package org.tehlab.whitek0t.netty02.bytebuf;

import io.netty.buffer.ByteBuf;

import java.util.Random;

import static io.netty.buffer.Unpooled.*;

public class ByteBufUsage {

    public static void main(String[] args) {
        //Create Byte Buf
        ByteBuf heapBuffer    = buffer(128);
        ByteBuf directBuffer  = directBuffer(256);
        ByteBuf wrappedBuffer = wrappedBuffer(new byte[128], new byte[256]);

        //random access
        ByteBuf buffer = heapBuffer;
        for (int i = 0; i < buffer.capacity(); i ++) {
            byte b = buffer.getByte(i);
            System.out.println((char) b);
        }

        // Iterates the readable bytes of a buffer.
        while (directBuffer.isReadable()) {
            System.out.println(directBuffer.readByte());
        }

        // Fills the writable bytes of a buffer with random integers.
        while (wrappedBuffer.maxWritableBytes() >= 4) {
            wrappedBuffer.writeInt(new Random().nextInt());
        }
    }
}