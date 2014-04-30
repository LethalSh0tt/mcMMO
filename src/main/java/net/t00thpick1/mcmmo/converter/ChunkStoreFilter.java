package net.t00thpick1.mcmmo.converter;

import java.io.File;
import java.io.FilenameFilter;

public class ChunkStoreFilter implements FilenameFilter {
    @Override
    public boolean accept(File dir, String name) {
        return name.endsWith(".mcm") && !name.endsWith(".v1.mcm"); // Grab unconverted files only
    }
}
