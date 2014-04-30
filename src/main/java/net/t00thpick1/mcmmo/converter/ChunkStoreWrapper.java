package net.t00thpick1.mcmmo.converter;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import com.gmail.nossr50.util.blockmeta.chunkmeta.ChunkStore;
import com.gmail.nossr50.util.blockmeta.chunkmeta.McMMOSimpleRegionFile;
import com.gmail.nossr50.util.blockmeta.chunkmeta.PrimitiveChunkStore;

class ChunkStoreWrapper implements Runnable {
    private final AtomicInteger reads;
    public final int regionX;
    public final int regionZ;
    private final File file;
    private final File directory;
    private final Map<List<Integer>, ChunkStoreWrapper> wrappers;

    ChunkStoreWrapper(File directory, int x, int z, Map<List<Integer>, ChunkStoreWrapper> wrappers) {
        this.regionX = x;
        this.regionZ = z;
        this.wrappers = wrappers;

        this.directory = directory;
        this.file = new File(this.directory, "mcmmo_" + regionX + "_" + regionZ + "_.mcm");
        this.reads = new AtomicInteger((regionX < 0) && (regionZ < 0) ? 4 : 2);
    }

    void decrementUses() {
        if (reads.decrementAndGet() == 0 && file.exists()) {
            if (!file.delete()) {
                System.err.println("[mcMMO] Failed to delete `" + file + "'");
            }
        }
    }

    public void run() {
        try {
            writeNewRegionFile(regionX, regionZ, wrappers);
        }
        catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private void writeChunkStore(McMMOSimpleRegionFile file, int x, int z, ChunkStore data) {
        try {
            ObjectOutputStream objectStream = new ObjectOutputStream(file.getOutputStream(x, z));
            objectStream.writeObject(data);
            objectStream.flush();
            objectStream.close();
            data.setDirty(false);
        }
        catch (IOException e) {
            throw new RuntimeException("Unable to write chunk meta data for " + x + ", " + z, e);
        }
    }

    private PrimitiveChunkStore getChunkStore(McMMOSimpleRegionFile rf, int x, int z) throws IOException {
        InputStream in = rf.getInputStream(x, z);
        if (in == null) {
            return null;
        }
        ObjectInputStream objectStream = new ObjectInputStream(in);
        try {
            Object o = objectStream.readObject();
            if (o instanceof PrimitiveChunkStore) {
                return (PrimitiveChunkStore) o;
            }

            throw new RuntimeException("Wrong class type read for chunk meta data for " + x + ", " + z);
        }
        catch (IOException e) {
            return null;
        }
        catch (ClassNotFoundException e) {
            return null;
        }
        finally {
            objectStream.close();
        }
    }

    private void writeNewRegionFile(int regionX, int regionZ, Map<List<Integer>, ChunkStoreWrapper> wrappers) throws IOException {
        McMMOSimpleRegionFile newFile = new McMMOSimpleRegionFile(new File(directory, "mcmmo_" + regionX + "_" + regionZ + "_.v1.mcm"), regionX, regionZ);
        File bulkRegion = new File(directory, "mcmmo_" + regionX + "_" + regionZ + "_.mcm");
        File leftRegion = new File(directory, "mcmmo_" + (regionX + 1) + "_" + regionZ + "_.mcm");
        File lowerRegion = new File(directory, "mcmmo_" + regionX + "_" + (regionZ + 1) + "_.mcm");
        File lowerLeftRegion = new File(directory, "mcmmo_" + (regionX + 1) + "_" + (regionZ + 1) + "_.mcm");
        if (bulkRegion.isFile()) {
            // Grab matching region
            int oldRegionX = regionX;
            int oldRegionZ = regionZ;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(bulkRegion, oldRegionX, oldRegionZ, true);

            for (int chunkX = oldRegionX << 5; chunkX < (oldRegionX << 5) + 32; chunkX++) {
                for (int chunkZ = oldRegionZ << 5; chunkZ < (oldRegionZ << 5) + 32; chunkZ++) {
                    PrimitiveChunkStore chunk = getChunkStore(original, chunkX, chunkZ);
                    if (chunk == null) {
                        continue;
                    }
                    int newChunkX = chunkX;
                    // Decrement all those below 0
                    if (chunkX < 0) {
                        newChunkX--;
                    }
                    int newChunkZ = chunkZ;
                    // Decrement all those below 0
                    if (chunkZ < 0) {
                        newChunkZ--;
                    }
                    if (newChunkX >> 5 != regionX || newChunkZ >> 5 != regionZ) {
                        continue;
                    }
                    chunk.convertCoordinatesToVersionOne(newChunkX, newChunkZ);
                    writeChunkStore(newFile, newChunkX, newChunkZ, chunk);
                }
            }

            original.close();
            wrappers.get(Arrays.asList(oldRegionX, oldRegionZ)).decrementUses();
        }
        if (leftRegion.isFile() && regionX < 0) {
            // Grab region to the left
            int oldRegionX = regionX + 1;
            int oldRegionZ = regionZ;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(leftRegion, oldRegionX, oldRegionZ, true);

            int chunkX = (oldRegionX << 5);
            for (int chunkZ = oldRegionZ << 5; chunkZ < (oldRegionZ << 5) + 32; chunkZ++) {
                PrimitiveChunkStore chunk = getChunkStore(original, chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int newChunkX = chunkX;
                // Actual region coord is negative, so we want all 0 and below to decrement
                if (chunkX <= 0) {
                    newChunkX--;
                }
                int newChunkZ = chunkZ;
                // Decrement all those below 0
                if (chunkZ < 0) {
                    newChunkZ--;
                }
                if (newChunkX >> 5 != regionX || newChunkZ >> 5 != regionZ) {
                    continue;
                }
                chunk.convertCoordinatesToVersionOne(newChunkX, newChunkZ);
                writeChunkStore(newFile, newChunkX, newChunkZ, chunk);
            }

            original.close();
            wrappers.get(Arrays.asList(oldRegionX, oldRegionZ)).decrementUses();
        }
        if (lowerRegion.isFile() && regionZ < 0) {
            // Grab region below
            int oldRegionX = regionX;
            int oldRegionZ = regionZ + 1;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(lowerRegion, oldRegionX, oldRegionZ, true);

            int chunkZ = oldRegionZ << 5;
            for (int chunkX = oldRegionX << 5; chunkX < (oldRegionX << 5) + 32; chunkX++) {
                PrimitiveChunkStore chunk = getChunkStore(original, chunkX, chunkZ);
                if (chunk == null) {
                    continue;
                }
                int newChunkX = chunkX;
                // Decrement all those below 0
                if (chunkX < 0) {
                    newChunkX--;
                }
                int newChunkZ = chunkZ;
                // Actual region coord is negative, so we want all 0 and below to decrement
                if (chunkZ <= 0) {
                    newChunkZ--;
                }
                if (newChunkX >> 5 != regionX || newChunkZ >> 5 != regionZ) {
                    continue;
                }
                chunk.convertCoordinatesToVersionOne(newChunkX, newChunkZ);
                writeChunkStore(newFile, newChunkX, newChunkZ, chunk);
            }

            original.close();
            wrappers.get(Arrays.asList(oldRegionX, oldRegionZ)).decrementUses();
        }
        if (lowerLeftRegion.isFile() && regionX < 0 && regionZ < 0) {
            // Grab region to the left and below
            int oldRegionX = regionX + 1;
            int oldRegionZ = regionZ + 1;
            McMMOSimpleRegionFile original = new McMMOSimpleRegionFile(lowerLeftRegion, oldRegionX, oldRegionZ, true);

            int chunkZ = (oldRegionZ << 5);
            int chunkX = (oldRegionX << 5);
            PrimitiveChunkStore chunk = getChunkStore(original, chunkX, chunkZ);
            if (chunk != null) {
                int newChunkX = chunkX;
                // Actual region coord is negative, so we want all 0 and below to decrement
                if (chunkX <= 0) {
                    newChunkX--;
                }
                int newChunkZ = chunkZ;
                // Actual region coord is negative, so we want all 0 and below to decrement
                if (chunkZ <= 0) {
                    newChunkZ--;
                }
                if (newChunkX >> 5 == regionX && newChunkZ >> 5 == regionZ) {
                    chunk.convertCoordinatesToVersionOne(newChunkX, newChunkZ);
                    writeChunkStore(newFile, newChunkX, newChunkZ, chunk);
                }
            }

            original.close();
            wrappers.get(Arrays.asList(oldRegionX, oldRegionZ)).decrementUses();
        }
        newFile.close();
    }
}
