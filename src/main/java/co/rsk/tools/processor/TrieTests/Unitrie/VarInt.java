package co.rsk.tools.processor.TrieTests.Unitrie;


public class VarInt {
    public final long value;
    private final int originallyEncodedSize;

    public VarInt(long value) {
        this.value = value;
        this.originallyEncodedSize = this.getSizeInBytes();
    }

    public VarInt(byte[] buf, int offset) {
        int first = 255 & buf[offset];
        if (first < 253) {
            this.value = (long)first;
            this.originallyEncodedSize = 1;
        } else if (first == 253) {
            this.value = (long)(255 & buf[offset + 1] | (255 & buf[offset + 2]) << 8);
            this.originallyEncodedSize = 3;
        } else if (first == 254) {
            this.value = Utils.readUint32(buf, offset + 1);
            this.originallyEncodedSize = 5;
        } else {
            this.value = Utils.readInt64(buf, offset + 1);
            this.originallyEncodedSize = 9;
        }

    }

    public int getOriginalSizeInBytes() {
        return this.originallyEncodedSize;
    }

    public final int getSizeInBytes() {
        return sizeOf(this.value);
    }

    public static int sizeOf(long value) {
        if (value < 0L) {
            return 9;
        } else if (value < 253L) {
            return 1;
        } else if (value <= 65535L) {
            return 3;
        } else {
            return value <= 4294967295L ? 5 : 9;
        }
    }

    public byte[] encode() {
        byte[] bytes;
        switch(sizeOf(this.value)) {
            case 1:
                return new byte[]{(byte)((int)this.value)};
            case 2:
            case 4:
            default:
                bytes = new byte[9];
                bytes[0] = -1;
                Utils.uint64ToByteArrayLE(this.value, bytes, 1);
                return bytes;
            case 3:
                return new byte[]{-3, (byte)((int)this.value), (byte)((int)(this.value >> 8))};
            case 5:
                bytes = new byte[5];
                bytes[0] = -2;
                Utils.uint32ToByteArrayLE(this.value, bytes, 1);
                return bytes;
        }
    }
}
