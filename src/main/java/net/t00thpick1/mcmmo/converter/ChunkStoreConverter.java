package net.t00thpick1.mcmmo.converter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectOutputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import net.t00thpick1.mcmmo.com.wolvereness.overmapped.lib.MultiProcessor;

@SuppressWarnings("javadoc")
public class ChunkStoreConverter {
    public static File directory;
    public static int threadCount = 5;

    public static int convertChunkStoreFlatFiles(int nThreads) throws IOException, InterruptedException {
        threadCount = nThreads;
        return StartConversion();
    }

    public static int convertChunkStoreFlatFiles() throws IOException, InterruptedException {
        return StartConversion();
    }

    /***
     * Iterates through worldsToConvert.yml and looks for entries, if any exist they are converted to the new format
     * and finally worldsToConvert.yml is destroyed if no failures were detected
     * 
     * @return Returns the number of worlds successfully converted
     * @throws IOException
     * @throws InterruptedException
     */
    private static int StartConversion() throws IOException, InterruptedException {
        String workingPath = System.getProperty("user.dir") + File.separator;
        File toConvertFile = new File(workingPath, "worldsToConvert.yml");

        int conversionCount = 0;
        int failureCount = 0;

        if (!toConvertFile.exists()) {
            System.out.println("[mcMMO] worldsToConvert.yml was not found!");
            return conversionCount;
        }

        InputStream inputStream = new FileInputStream(toConvertFile);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream, Charset.forName("UTF-8")));
        String worldName;

        while ((worldName = bufferedReader.readLine()) != null) {
            directory = new File(workingPath, worldName + File.separator + "mcmmo_regions");

            if (!directory.isDirectory()) {
                System.out.println("[mcMMO] Folder path invalid: " + directory.toString());
                System.out.println("[mcMMO] Invalid mcmmo_regions directory for world: '" + worldName + "' make sure the directory in worldsToConvert.yml is a valid system path");
                System.out.println("[mcMMO] Conversion Process failed for: " + worldName);
                failureCount++;
                continue;
            }

            System.out.println("[mcMMO] Upgrading ChunkStore format for world '" + worldName + "' using " + threadCount + " threads");
            processChunkStoreDirectory();

            conversionCount++;
        }

        bufferedReader.close();

        if (failureCount == 0)
            toConvertFile.delete(); // Don't need worldsToConvert.yml anymore
        else
            System.out.println("[mcMMO] Failed to convert " + failureCount + " worlds, please edit or delete worldsToConvert.yml and try again");

        return conversionCount;
    }

    /***
     * Runs through each ChunkStore file in directory and copies the data into a new file,
     * the old file is then destroyed.
     * 
     * @throws IOException
     */
    private static void processChunkStoreDirectory() throws IOException {
        File[] files = directory.listFiles(new ChunkStoreFilter());
        final Map<List<Integer>, ChunkStoreWrapper> toConvert = new LinkedHashMap<List<Integer>, ChunkStoreWrapper>();
        for (File file : files) {
            final int[] coords = parseFile(file);
            if (coords == null) {
                continue;
            }
            int x = coords[0];
            int z = coords[1];
            toConvert.put(Arrays.asList(x, z), null);
            if (x < 0) {
                toConvert.put(Arrays.asList(x - 1, z), null);
            }
            if (z < 0) {
                toConvert.put(Arrays.asList(x, z - 1), null);
            }
            if (x < 0 && z < 0) {
                toConvert.put(Arrays.asList(x - 1, z - 1), null);
            }
        }

        Object[] sorted = toConvert.keySet().toArray();
        Arrays.sort(sorted, new Comparator<Object>() {
            public int compare(List<Integer> o1, List<Integer> o2) {
                final int x1 = o1.get(0), z1 = o1.get(1);
                final int x2 = o2.get(0), z2 = o2.get(1);

                return (Math.abs(x1) + Math.abs(z1)) - (Math.abs(x2) + Math.abs(z2));
            }

            public int compare(Object o1, Object o2) {
                return compare((List<Integer>) o1, (List<Integer>) o2);
            }
        });

        toConvert.clear();

        for (Object object : sorted) {
            List<Integer> item = (List<Integer>) object;
            int x = item.get(0);
            int z = item.get(1);
            toConvert.put(item, new ChunkStoreWrapper(directory, x, z, toConvert));
        }

        MultiProcessor processor = MultiProcessor.newMultiProcessor(threadCount, new ThreadFactory() {
            final AtomicInteger i = new AtomicInteger();

            public Thread newThread(Runnable r) {
                return new Thread(ChunkStoreConverter.class.getName() + "-Processor-" + i.incrementAndGet());
            }
        });

        List<Future<ChunkStoreWrapper>> tasks = new ArrayList<Future<ChunkStoreWrapper>>();
        for (final ChunkStoreWrapper wrapper : toConvert.values()) {
            final Callable<ChunkStoreWrapper> callable = new Callable<ChunkStoreWrapper>() {
                public ChunkStoreWrapper call() throws Exception {
                    wrapper.run();
                    return wrapper;
                }
            };
            tasks.add(processor.submit(callable));
        }

        for (int i = 0, l = tasks.size(); i < l; i++) {
            try {
                while (true) {
                    try {
                        ChunkStoreWrapper wrapper = tasks.get(i).get();
                        System.out.println("[mcMMO] " + (i + 1) + " / " + l + ", (" + wrapper.regionX + "," + wrapper.regionZ + ")");
                        break;
                    }
                    catch (InterruptedException ex) {}
                }
            }
            catch (Throwable t) {
                t.printStackTrace();
            }
        }

        processor.shutdown();
        File file = new File(directory, "mcMMO.format");
        if (!file.isFile()) {
            file.createNewFile();
        }
        ObjectOutputStream objectStream = new ObjectOutputStream(new FileOutputStream(file));
        objectStream.writeObject(1);
        objectStream.flush();
        objectStream.close();
        System.out.println("[mcMMO] Conversion done for world: " + directory.getParentFile().getName());
    }

    private static int[] parseFile(File file) {
        // Parse coordinates
        String coordString = file.getName().substring(6);
        coordString = coordString.substring(0, coordString.length() - 5);
        String[] coords = coordString.split("_");
        int rx = Integer.valueOf(coords[0]);
        int rz = Integer.valueOf(coords[1]);
        if (rx >= 0 && rz >= 0) { // Only chunks with negative coords are messed up, so we can just rename positive ones
            file.renameTo(new File(directory, "mcmmo_" + rx + "_" + rz + "_.v1.mcm"));
            file.delete();
            return null;
        }

        return new int[] { rx, rz };
    }
}
