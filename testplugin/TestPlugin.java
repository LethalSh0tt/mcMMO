import java.util.Random;

import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import com.gmail.nossr50.mcMMO;
import com.gmail.nossr50.util.blockmeta.chunkmeta.ChunkManager;

public class TestPlugin extends JavaPlugin {

    @Override
    public boolean onCommand(final CommandSender sender, final Command command, final String commandLabel, final String[] args) {
        ChunkManager m = mcMMO.getPlaceStore();
        Player player = (Player) sender;
        World world = player.getWorld();
        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("set")) {
                long seed = Long.valueOf(args[1]);
                Random rand = new Random(seed);
                for (int i = 0; i < 10000; i++) {
                    int x = rand.nextInt(10000) - 5000;
                    int y = rand.nextInt(255);
                    int z = rand.nextInt(10000) - 5000;
                    m.setTrue(x, y, z, world);
                }
            }
            if (args[0].equalsIgnoreCase("check")) {
                long seed = Long.valueOf(args[1]);
                Random rand = new Random(seed);
                int err = 0;
                for (int i = 0; i < 10000; i++) {
                    int x = rand.nextInt(10000) - 5000;
                    int y = rand.nextInt(255);
                    int z = rand.nextInt(10000) - 5000;
                    if (!m.isTrue(x, y, z, world)) {
                        getLogger().info(String.format("Missing %d %d %d", x, y, z));
                        err++;
                    }
                }
                player.sendMessage(String.format("We lost %d blocks", err));
            }
        }
        return true;
    }
}
