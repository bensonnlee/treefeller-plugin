package com.attic.treechopper; // Change if needed

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.Tag; // Important for log/leaf matching
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class TreeListener implements Listener {

    private final TreeChopper plugin;

    // Define faces including diagonals
    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
            BlockFace.NORTH_EAST, BlockFace.NORTH_WEST, BlockFace.SOUTH_EAST, BlockFace.SOUTH_WEST,
    };

    public TreeListener(TreeChopper plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block brokenBlock = event.getBlock();
        Material brokenType = brokenBlock.getType();

        // 1. Check if the broken block is a log
        if (!Tag.LOGS.isTagged(brokenType)) { // Check both wood and log tags, covers most tree types
            plugin.logDebug("Broken block is not a log: " + brokenType);
            return;
        }

        // 2. Check if the player is using an axe (if required)
        ItemStack itemInHand = player.getInventory().getItemInMainHand();
        boolean usingAxe = Tag.ITEMS_AXES.isTagged(itemInHand.getType());

        if (plugin.getConfig().getBoolean("require-axe", true) && !usingAxe) {
            plugin.logDebug("Axe required, but player is not using one.");
            return;
        }

        // Optional: Add sneak requirement check
        // if (plugin.getConfig().getBoolean("require-sneaking", false) && !player.isSneaking()) {
        //    plugin.logDebug("Sneaking required, but player is not sneaking.");
        //    return;
        // }

        // Prevent creative mode players from triggering to avoid accidental large-scale breaks
        if (player.getGameMode() == GameMode.CREATIVE) {
            plugin.logDebug("Player is in creative mode.");
            return;
        }

        // --- Core Logic ---
        plugin.logDebug("Potential tree chop detected for: " + brokenType);

        int maxTreeSize = plugin.getConfig().getInt("max-tree-size", 500);
        int logSearchRadius = plugin.getConfig().getInt("log-search-radius", 15);
        boolean checkForLeaves = plugin.getConfig().getBoolean("check-for-leaves", true);

        Set<Block> logsToBreak = new HashSet<>();
        Queue<Block> queue = new LinkedList<>();
        Set<Block> visitedLogs = new HashSet<>(); // Track visited to prevent infinite loops

        logsToBreak.add(brokenBlock); // Add the initial block
        queue.add(brokenBlock);
        visitedLogs.add(brokenBlock);

        boolean foundLeavesNearby = false; // Flag for natural tree check

        // 3. Find all connected logs of the same type (BFS)
        while (!queue.isEmpty()) {
            if (logsToBreak.size() > maxTreeSize) {
                plugin.logDebug("Tree exceeds max size (" + maxTreeSize + "). Aborting chop.");
                // player.sendMessage(ChatColor.RED + "This tree is too large to chop down at once!"); // Optional feedback
                return; // Stop processing if tree is too big
            }

            Block currentLog = queue.poll();

            // Check neighbors (including diagonals)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue; // Skip self

                        Block neighbor = currentLog.getRelative(dx, dy, dz);

                        // Optimization: Check distance limit early
                        if (manhattanDistance(brokenBlock.getX(), brokenBlock.getY(), brokenBlock.getZ(), neighbor.getX(), neighbor.getY(), neighbor.getZ()) > logSearchRadius) {
                            plugin.logDebug("Neighbor log exceeds search radius: " + neighbor.getLocation());
                            continue;
                        }

                        // Check if the neighbor is the same log type and not already visited
                        if (!visitedLogs.contains(neighbor) && neighbor.getType() == brokenType) {
                            visitedLogs.add(neighbor);
                            logsToBreak.add(neighbor);
                            queue.add(neighbor);
                            plugin.logDebug("Found connected log: " + neighbor.getLocation());
                        }
                        // Check for nearby leaves if required for the 'natural' check
                        else if (checkForLeaves && !foundLeavesNearby && Tag.LEAVES.isTagged(neighbor.getType())) {
                            plugin.logDebug("Found adjacent leaves, likely a natural tree.");
                            foundLeavesNearby = true;
                            // We only need to find one leaf connection to confirm, but keep searching logs
                        }
                    }
                }
            }
        }

        // If checking for leaves is enabled and none were found connected to the log structure, assume it's player-placed
        if(checkForLeaves && !foundLeavesNearby && logsToBreak.size() > 1) {
            // We re-check one more time directly around all found logs, just in case the initial block wasn't touching leaves
            boolean hasLeafConnection = false;
            for(Block log : logsToBreak) {
                for (int dx = -1; dx <= 1; dx++) {
                    for (int dy = -1; dy <= 1; dy++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (dx == 0 && dy == 0 && dz == 0) continue;
                            if(Tag.LEAVES.isTagged(log.getRelative(dx,dy,dz).getType())) {
                                hasLeafConnection = true;
                                plugin.logDebug("Leaf check passed during secondary scan.");
                                break;
                            }
                        }
                        if(hasLeafConnection) break;
                    }
                    if(hasLeafConnection) break;
                }
            }
            if (!hasLeafConnection) {
                plugin.logDebug("No leaves found connected to the log structure. Assuming player-placed, cancelling chop.");
                return; // Do not break the tree
            }
        }


        // 4. If it's just the single block the player broke, do nothing extra
        if (logsToBreak.size() <= 1 && !foundLeavesNearby) { // Only proceed if it's a tree structure or we found leaves
            plugin.logDebug("Only one log found or no leaves nearby (and check enabled). Normal break.");
            return;
        }


        plugin.logDebug("Proceeding to break " + logsToBreak.size() + " logs.");

        // --- Prevent the original event from dropping the item, we'll handle drops manually ---
        event.setDropItems(false); // We will handle drops via breakNaturally


        // 5. Break the logs & handle durability/drops
        int logsBrokenCount = 0;
        boolean toolBroken = false;

        for (Block log : logsToBreak) {
            if (log.equals(brokenBlock)) continue; // Skip original block, event handles it conceptually

            // Check if tool broke mid-chop
            if(toolBroken) break;

            // Use breakNaturally to handle drops (like potential saplings) and enchantments correctly
            if (log.breakNaturally(itemInHand)) { // Returns true if block broke successfully
                logsBrokenCount++;

                // Damage the tool for each *extra* block broken
                if (usingAxe && itemInHand.getItemMeta() instanceof Damageable) {
                    Damageable damageable = (Damageable) itemInHand.getItemMeta();
                    int unbreakingLevel = itemInHand.getEnchantmentLevel(Enchantment.UNBREAKING);
                    Random random = new Random();

                    // Standard durability calculation check (1 / (Unbreaking Level + 1)) chance to *not* take damage
                    if (unbreakingLevel == 0 || random.nextInt(unbreakingLevel + 1) == 0) {
                        damageable.setDamage(damageable.getDamage() + 1);
                        itemInHand.setItemMeta(damageable); // Apply the changes

                        if (damageable.getDamage() >= itemInHand.getType().getMaxDurability()) {
                            plugin.logDebug("Player's axe broke!");
                            player.getInventory().setItemInMainHand(null); // Remove broken tool
                            player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                            toolBroken = true;
                            // Stop breaking more logs if tool breaks
                        }
                    }
                }
            } else {
                plugin.logDebug("Failed to break log at: " + log.getLocation());
                // Maybe another plugin cancelled the break internally? Or protection?
            }
        }

        // Break the original block last using breakNaturally to ensure consistency
        if (!toolBroken) { // Only break original if tool didn't break on others
            if(brokenBlock.breakNaturally(itemInHand)) {
                logsBrokenCount++; // Count the original block too for messages/stats if needed
                plugin.logDebug("Broke original block: " + brokenBlock.getLocation());
            } else {
                plugin.logDebug("Failed to break original block: " + brokenBlock.getLocation());
            }
        } else {
            // If tool broke, we need to make sure the original block still breaks (without tool effects)
            brokenBlock.setType(Material.AIR);
            logsBrokenCount++; // Still count it as broken
            plugin.logDebug("Original block broken without tool due to prior tool break.");
        }

        plugin.logDebug("Finished breaking logs. Total broken: " + logsBrokenCount);


        // 6. Find and decay nearby leaves
        findAndDecayLeaves(logsToBreak);
    }

    private void findAndDecayLeaves(Set<Block> brokenLogs) {
        int leafSearchRadius = plugin.getConfig().getInt("leaf-search-radius", 7);
        Set<Block> leavesToDecay = new HashSet<>();
        Queue<Block> leafQueue = new LinkedList<>();
        Set<Block> visitedLeaves = new HashSet<>(); // Prevent checking same leaf block multiple times

        // Initial population: Find leaves adjacent to *any* of the broken logs
        for (Block log : brokenLogs) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block neighbor = log.getRelative(dx, dy, dz);
                        if (Tag.LEAVES.isTagged(neighbor.getType()) && !visitedLeaves.contains(neighbor)) {
                            // Check distance from *any* broken log initially
                            // This check might be overly simple, a better check is distance from original base maybe?
                            // For now, adjacency is good enough to start the BFS
                            visitedLeaves.add(neighbor);
                            leafQueue.add(neighbor);
                            leavesToDecay.add(neighbor);
                            plugin.logDebug("Added initial leaf: " + neighbor.getLocation());
                        }
                    }
                }
            }
        }

        // BFS for leaves connected to the initial set, within the radius from the logs
        while (!leafQueue.isEmpty()) {
            Block currentLeaf = leafQueue.poll();

            // Check neighbors (orthogonally is usually sufficient for leaf decay spread, but let's be thorough)
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;

                        Block neighbor = currentLeaf.getRelative(dx, dy, dz);

                        if (Tag.LEAVES.isTagged(neighbor.getType()) && !visitedLeaves.contains(neighbor)) {
                            // Check if this leaf is reasonably close to *any* of the original logs
                            boolean closeToLog = false;
                            for(Block log : brokenLogs) {
                                if (manhattanDistance(log.getX(), log.getY(), log.getZ(), neighbor.getX(), neighbor.getY(), neighbor.getZ()) <= leafSearchRadius) {
                                    closeToLog = true;
                                    break;
                                }
                            }

                            if(closeToLog) {
                                visitedLeaves.add(neighbor);
                                leafQueue.add(neighbor);
                                leavesToDecay.add(neighbor);
                                plugin.logDebug("Added connected leaf: " + neighbor.getLocation());
                            } else {
                                plugin.logDebug("Leaf too far from logs: " + neighbor.getLocation());
                            }
                        }
                    }
                }
            }
        }

        plugin.logDebug("Found " + leavesToDecay.size() + " leaves to decay.");

        // Schedule the decay update slightly later to allow log breaks to fully process
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            int decayCount = 0;
            for (Block leafBlock : leavesToDecay) {
                // Re-check if it's still a leaf block when the task runs
                if (Tag.LEAVES.isTagged(leafBlock.getType()) && leafBlock.getBlockData() instanceof Leaves) {
                    Leaves leafData = (Leaves) leafBlock.getBlockData();
                    if (leafData.isPersistent()) {
                        leafData.setPersistent(false); // Allow natural decay
                        // Optionally force distance check sooner, but persistent=false is often enough
                        leafData.setDistance(7); // Set max distance to encourage decay check
                        leafBlock.setBlockData(leafData, true); // Update with physics
                        decayCount++;
                    } else if (leafData.getDistance() < 7) {
                        // If not persistent but still close, force distance update
                        leafData.setDistance(7);
                        leafBlock.setBlockData(leafData, true); // Update with physics
                        decayCount++;
                    }
                }
            }
            plugin.logDebug("Marked " + decayCount + " leaves for faster decay.");
        }, 1L); // Run 1 tick later
    }

    // Helper method for distance calculation (Manhattan distance is simpler/faster than Euclidean for grid checks)
    private int manhattanDistance(int x1, int y1, int z1, int x2, int y2, int z2) {
        return Math.abs(x1 - x2) + Math.abs(y1 - y2) + Math.abs(z1 - z2);
    }
}