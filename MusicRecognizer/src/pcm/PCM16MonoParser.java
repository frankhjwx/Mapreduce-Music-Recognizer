package pcm;

import org.apache.hadoop.fs.FSDataInputStream;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;

public class PCM16MonoParser {
    public static PCM16MonoData parse(FSDataInputStream f) throws IOException {

        /**
         * If the first 4 bytes do not equal ASCII text "RIFF"
         */
        f.seek(0);
        if (f.readInt() != 0x52494646) {
            throw new RuntimeException("The file is not an RIFF file");
        }

        /**
         * If the format field is not "WAVE"
         */
        f.seek(8);
        if (f.readInt() != 0x57415645) {
            throw new RuntimeException("The file is not a WAVE file");
        }

        /**
         * If the audio is not PCM
         */
        f.seek(20);
        if (f.readShort() != (short) 0x0100) {
            throw new RuntimeException("The file is not a PCM file");
        }

        /**
         * If the audio has more than one channels
         */
        f.seek(22);
        if (f.readShort() != (short) 0x0100) {
            throw new RuntimeException("The audio has >1 channels");
        }

        /**
         * If the sample rate is not 44100
         */
        f.seek(24);
        if (f.readInt() != 0x44AC0000) {
            throw new RuntimeException("The audio's sample rate is not 44100 Hz");
        }

        /**
         * If the bit depth is not 16
         */
        f.seek(34);
        if (f.readShort() != (short) 0x1000) {
            throw new RuntimeException("The audio's bit depth is not 16-bit");
        }

        /**
         * If the file has extra parameters
         */
        f.seek(36);
        if (f.readShort() != (short) 0x0000) {
            throw new RuntimeException("The audio has extra parameters in its \"fmt \" block");
        }

        /**
         * Find the data size
         */
        f.seek(42);
        int a = f.readByte() & 0xFF;
        f.seek(43);
        int b = (f.readByte() << 8) & 0xFF00;
        f.seek(44);
        int c = (f.readByte() << 16) & 0xFF0000;
        f.seek(45);
        int d = (f.readByte() << 24) & 0xFF000000;
        int size = d | c | b | a;


        /**
         * read data and generate a PCM16MonoData
         */
        PCM16MonoData data = new PCM16MonoData();
        byte[] raw = new byte[size];
        f.seek(46);
        f.readFully(raw);
        data.setRawData(raw);
        return data;
    }
}
