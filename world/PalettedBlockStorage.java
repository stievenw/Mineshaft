// src/main/java/com/mineshaft/world/PalettedBlockStorage.java
package com.mineshaft.world;

import java.io.*;
import java.util.*;

/**
 * Minecraft-style palette-based block storage
 * 
 * Instead of storing "minecraft:stone" for every block,
 * creates a palette per chunk section:
 * 
 * Palette:
 * 0 = air
 * 1 = stone
 * 2 = dirt
 * 3 = grass_block
 * 
 * Then stores indices: [1, 1, 1, 2, 2, 3, 0, 0, ...]
 * 
 * For a section with only 4 block types, we only need 2 bits per block!
 * 16×16×384 = 98,304 blocks × 2 bits = 24,576 bytes (24 KB) before deflate
 * vs storing strings = ~600-800 KB
 * 
 * Compression ratio: 95%+ reduction!
 */
public class PalettedBlockStorage {
    // ✅ CRITICAL FIX: Full chunk height (16×16×384) not just one section!
    // OLD: 4096 (16×16×16) - THIS WAS THE BUG!
    // NEW: 98304 (16×16×384) - Full chunk from -64 to +320
    private static final int BLOCKS_PER_CHUNK = 98304; // 16×16×384

    private List<String> palette;
    private int[] indices;
    private int bitsPerEntry;

    public PalettedBlockStorage() {
        this.palette = new ArrayList<>();
        this.indices = new int[BLOCKS_PER_CHUNK]; // ✅ Fixed!
        this.bitsPerEntry = 4; // Start with 4 bits (supports up to 16 block types)
    }

    /**
     * Set block at index
     */
    public void setBlock(int index, String blockId) {
        if (index < 0 || index >= BLOCKS_PER_CHUNK) {
            return;
        }

        // Get or add to palette
        int paletteIndex = palette.indexOf(blockId);
        if (paletteIndex == -1) {
            paletteIndex = palette.size();
            palette.add(blockId);

            // Expand bits per entry if needed
            updateBitsPerEntry();
        }

        indices[index] = paletteIndex;
    }

    /**
     * Get block at index
     */
    public String getBlock(int index) {
        if (index < 0 || index >= BLOCKS_PER_CHUNK) {
            return "minecraft:air";
        }

        int paletteIndex = indices[index];
        if (paletteIndex >= 0 && paletteIndex < palette.size()) {
            return palette.get(paletteIndex);
        }

        return "minecraft:air";
    }

    /**
     * Update bits per entry based on palette size
     */
    private void updateBitsPerEntry() {
        int paletteSize = palette.size();

        if (paletteSize <= 2) {
            bitsPerEntry = 1;
        } else if (paletteSize <= 4) {
            bitsPerEntry = 2;
        } else if (paletteSize <= 16) {
            bitsPerEntry = 4;
        } else if (paletteSize <= 256) {
            bitsPerEntry = 8;
        } else {
            bitsPerEntry = 16; // Fallback for very diverse sections
        }
    }

    /**
     * Serialize to bytes (compressed format)
     */
    public byte[] serialize() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);

        // Write palette size
        dos.writeShort(palette.size());

        // Write palette entries
        for (String blockId : palette) {
            dos.writeUTF(blockId);
        }

        // Write bits per entry
        dos.writeByte(bitsPerEntry);

        // Write packed indices
        packIndices(dos);

        dos.close();
        return baos.toByteArray();
    }

    /**
     * Deserialize from bytes
     */
    public static PalettedBlockStorage deserialize(byte[] data) throws IOException {
        PalettedBlockStorage storage = new PalettedBlockStorage();
        DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data));

        // Read palette
        int paletteSize = dis.readShort();
        storage.palette = new ArrayList<>(paletteSize);
        for (int i = 0; i < paletteSize; i++) {
            storage.palette.add(dis.readUTF());
        }

        // Read bits per entry
        storage.bitsPerEntry = dis.readByte();

        // Read packed indices
        storage.unpackIndices(dis);

        dis.close();
        return storage;
    }

    /**
     * Pack indices into bit-packed format
     */
    private void packIndices(DataOutputStream dos) throws IOException {
        int bitBuffer = 0;
        int bitCount = 0;

        for (int index : indices) {
            // Add bits to buffer
            bitBuffer |= (index << bitCount);
            bitCount += bitsPerEntry;

            // Write bytes when buffer is full
            while (bitCount >= 8) {
                dos.writeByte(bitBuffer & 0xFF);
                bitBuffer >>>= 8;
                bitCount -= 8;
            }
        }

        // Write remaining bits
        if (bitCount > 0) {
            dos.writeByte(bitBuffer & 0xFF);
        }
    }

    /**
     * Unpack indices from bit-packed format
     */
    private void unpackIndices(DataInputStream dis) throws IOException {
        int bitBuffer = 0;
        int bitCount = 0;
        int index = 0;

        while (index < BLOCKS_PER_CHUNK) {
            // Read more bytes if needed
            while (bitCount < bitsPerEntry && dis.available() > 0) {
                int nextByte = dis.readUnsignedByte();
                bitBuffer |= (nextByte << bitCount);
                bitCount += 8;
            }

            // Extract value
            if (bitCount >= bitsPerEntry) {
                int mask = (1 << bitsPerEntry) - 1;
                indices[index++] = bitBuffer & mask;
                bitBuffer >>>= bitsPerEntry;
                bitCount -= bitsPerEntry;
            } else {
                break; // Not enough bits
            }
        }
    }

    /**
     * Check if this storage is empty (all air)
     */
    public boolean isEmpty() {
        return palette.size() <= 1 && (palette.isEmpty() || palette.get(0).equals("minecraft:air"));
    }

    /**
     * Get palette size (for debugging)
     */
    public int getPaletteSize() {
        return palette.size();
    }

    /**
     * Get estimated compressed size in bytes
     */
    public int getEstimatedSize() {
        // Palette: 2 bytes (size) + sum of string lengths
        int paletteBytes = 2;
        for (String id : palette) {
            paletteBytes += 2 + id.length(); // UTF string format
        }

        // Bits per entry: 1 byte
        int bitsBytes = 1;

        // Packed indices
        int indexBytes = (BLOCKS_PER_CHUNK * bitsPerEntry + 7) / 8;

        return paletteBytes + bitsBytes + indexBytes;
    }

    @Override
    public String toString() {
        return String.format("PalettedStorage[palette=%d types, bits=%d, size=%d bytes]",
                palette.size(), bitsPerEntry, getEstimatedSize());
    }
}
