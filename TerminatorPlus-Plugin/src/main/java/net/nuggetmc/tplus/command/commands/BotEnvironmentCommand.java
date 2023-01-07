package net.nuggetmc.tplus.command.commands;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.nuggetmc.tplus.api.agent.legacyagent.LegacyAgent;
import net.nuggetmc.tplus.api.agent.legacyagent.LegacyMats;
import net.nuggetmc.tplus.api.utils.ChatUtils;
import net.nuggetmc.tplus.command.CommandHandler;
import net.nuggetmc.tplus.command.CommandInstance;
import net.nuggetmc.tplus.command.annotation.Arg;
import net.nuggetmc.tplus.command.annotation.Command;
import net.nuggetmc.tplus.command.annotation.OptArg;

public class BotEnvironmentCommand extends CommandInstance {

    public BotEnvironmentCommand(CommandHandler handler, String name, String description, String... aliases) {
        super(handler, name, description, aliases);
    }
    
    @Command
    public void root(CommandSender sender, List<String> args) {
        commandHandler.sendRootInfo(this, sender);
    }
    
    @Command(
        name = "help",
        desc = "Help for /botenvironment."
    )
    public void help(CommandSender sender) {
    	sender.sendMessage("If you are running this plugin in a Magma server, keep in mind that blocks added by mods are not considered solid.");
    	sender.sendMessage("You must manually add solid blocks added by mods with " + ChatColor.YELLOW + "/botenvironment addSolid <material>" + ChatColor.RESET);
    	sender.sendMessage("The material name is the mod ID and the block name concatted with a \"_\", converted to uppercase.");
    	sender.sendMessage("For example, if mod \"examplemod\" adds a block \"custom_block\", the material name is EXAMPLEMOD_CUSTOM_BLOCK.");
    	sender.sendMessage("Additionally, you may use " + ChatColor.YELLOW + "/bot addSolidGroup <modid>" + ChatColor.RESET + ", where modid is the uppercase MODID plus a \"_\"");
    }
    
    @Command(
        name = "getMaterial",
        desc = "Prints out the current material at the specified location.",
        aliases = {"getmat", "getMat", "getmaterial"}
    )
    public void getMaterial(Player sender, @Arg("x") String x, @Arg("y") String y, @Arg("z") String z) {
    	Location loc = sender.getLocation().clone();
    	try {
        	loc.setX(parseDoubleOrRelative(x, loc, 0));
        	loc.setY(parseDoubleOrRelative(y, loc, 1));
        	loc.setZ(parseDoubleOrRelative(z, loc, 2));
        } catch (NumberFormatException e) {
        	sender.sendMessage("A valid location must be provided!");
        	return;
        }
    	if (!isLocationLoaded(loc)) {
    		sender.sendMessage(String.format("The location at " + ChatColor.BLUE + "(%d, %d, %d)" + ChatColor.RESET + " is not loaded.",
    			loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    		return;
    	}
    	if (Math.abs(loc.getX() - sender.getLocation().getX()) > 250 || Math.abs(loc.getZ() - sender.getLocation().getZ()) > 250) {
    		sender.sendMessage(String.format("The location at " + ChatColor.BLUE + "(%d, %d, %d)" + ChatColor.RESET + " is too far away.",
    			loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
    		return;    		
    	}
    	Material mat = loc.getBlock().getType();
		sender.sendMessage(String.format("Material at " + ChatColor.BLUE + "(%d, %d, %d)" + ChatColor.RESET + ": " + ChatColor.GREEN + "%s",
			loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), mat.name()));
    }
    
    @Command(
        name = "addSolidGroup",
        desc = "Adds every block starting with a prefix to the list of solid materials.",
        aliases = {"addsolidgroup"}
    )
    public void addSolidGroup(CommandSender sender, @Arg("prefix") String prefix, @OptArg("includeNonSolid") boolean includeNonSolid) {
    	try {
    		Field byName = Material.class.getDeclaredField("BY_NAME");
    		byName.setAccessible(true);
			Field materialField = BlockBehaviour.class.getDeclaredField("f_60442_"); //material
			materialField.setAccessible(true);
    		Map<String, Material> map = (Map<String, Material>) byName.get(null);
    		Map<Material, Block> materialsToBlocks = new HashMap<>();
			if (!includeNonSolid) {
				// Build material -> block map using ForgeRegistries
				Object blocksRegistry = Class.forName("net.minecraftforge.registries.ForgeRegistries").getDeclaredField("BLOCKS").get(null);
				Set<Entry<ResourceKey<Block>, Block>> blockSet = (Set<Entry<ResourceKey<Block>, Block>>) Class.forName("net.minecraftforge.registries.IForgeRegistry").getMethod("getEntries").invoke(blocksRegistry);
				
				for (Entry<ResourceKey<Block>, Block> entry : blockSet) {
		            String result = (String) Class.forName("org.magmafoundation.magma.util.ResourceLocationUtil").getMethod("standardize", ResourceLocation.class).invoke(null, entry.getKey().location());
		            Material material = Material.getMaterial(result);
		            if (material != null)
		            	materialsToBlocks.put(material, entry.getValue());
				}
			}
			int added = 0;
    		for (Entry<String, Material> entry : map.entrySet()) {
    			boolean valid = entry.getValue().isBlock() && entry.getKey().startsWith(prefix);
    			if (valid && !includeNonSolid)
    				if (!materialsToBlocks.containsKey(entry.getValue())) {
    					sender.sendMessage("Warning: The material " + ChatColor.GREEN + entry.getValue().name() + ChatColor.RESET
    						+ " was not found in the Forge registries, this should not happen!");
    				} else {
    					// Check if block is solid
    					Block block = materialsToBlocks.get(entry.getValue());
    					net.minecraft.world.level.material.Material mat = (net.minecraft.world.level.material.Material) materialField.get(block);
    					valid = mat.blocksMotion();
    				}
    			if (valid && LegacyMats.SOLID_MATERIALS.add(entry.getValue()))
    				added++;
    		}
    		sender.sendMessage("Successfully added " + ChatColor.BLUE + added + ChatColor.RESET + " materials with prefix " + ChatColor.GREEN + prefix + ChatColor.RESET);
    	} catch(ReflectiveOperationException e) {
    		sender.sendMessage("This command only works on Magma servers!");
    	}
    }
    
    @Command(
        name = "addSolid",
        desc = "Adds a material to the list of solid materials.",
        aliases = {"addsolid"}
    )
    public void addSolid(CommandSender sender, List<String> args) {
    	Material mat;
    	if (args.size() == 1)
    		mat = Material.getMaterial(args.get(0));
    	else if (args.size() == 3) {
    		if (!(sender instanceof Player)) {
    			sender.sendMessage("You must be a player to specify coordinates!");
    			return;
    		}
    		Location loc = ((Player)sender).getLocation().clone();
	    	try {
	    		loc.setX(parseDoubleOrRelative(args.get(0), loc, 0));
	    		loc.setY(parseDoubleOrRelative(args.get(1), loc, 1));
	    		loc.setZ(parseDoubleOrRelative(args.get(2), loc, 2));
	        } catch (NumberFormatException e) {
	        	sender.sendMessage("A valid location must be provided! " + ChatColor.YELLOW + "/bot addSolid <x> <y> <z>" + ChatColor.RESET);
	        	return;
	        }
	    	if (!isLocationLoaded(loc)) {
	    		sender.sendMessage(String.format("The location at " + ChatColor.BLUE + "(%d, %d, %d)" + ChatColor.RESET + " is not loaded.",
	    			loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
	    		return;
	    	}
	    	if (Math.abs(loc.getX() - ((Player)sender).getLocation().getX()) > 250 || Math.abs(loc.getZ() - ((Player)sender).getLocation().getZ()) > 250) {
	    		sender.sendMessage(String.format("The location at " + ChatColor.BLUE + "(%d, %d, %d)" + ChatColor.RESET + " is too far away.",
	    			loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
	    		return;    		
	    	}
	    	mat = loc.getBlock().getType();
    	} else {
    		sender.sendMessage("Invalid syntax!");
    		sender.sendMessage("To specify a material: " + ChatColor.YELLOW + "/bot addSolid <material>" + ChatColor.RESET);
    		sender.sendMessage("To specify a location containing a material: " + ChatColor.YELLOW + "/bot addSolid <x> <y> <z>" + ChatColor.RESET);
    		return;
    	}
    	if (mat == null) {
    		sender.sendMessage("The material you specified does not exist!");
    		return;
    	}
		if (LegacyMats.SOLID_MATERIALS.add(mat))
			sender.sendMessage("Successfully added " + ChatColor.BLUE + mat.name() + ChatColor.RESET + " to the list.");
		else
			sender.sendMessage(ChatColor.BLUE + mat.name() + ChatColor.RESET + " already exists in the list!");
    }
    
    @Command(
        name = "removeSolid",
        desc = "Removes a material from the list of solid materials.",
        aliases = {"removesolid"}
    )
    public void removeSolid(CommandSender sender, List<String> args) {
    	Material mat;
    	if (args.size() == 1)
    		mat = Material.getMaterial(args.get(0));
    	else if (args.size() == 3) {
    		if (!(sender instanceof Player)) {
    			sender.sendMessage("You must be a player to specify coordinates!");
    			return;
    		}
    		Location loc = ((Player)sender).getLocation().clone();
	    	try {
	    		loc.setX(parseDoubleOrRelative(args.get(0), loc, 0));
	    		loc.setY(parseDoubleOrRelative(args.get(1), loc, 1));
	    		loc.setZ(parseDoubleOrRelative(args.get(2), loc, 2));
	        } catch (NumberFormatException e) {
	        	sender.sendMessage("A valid location must be provided! " + ChatColor.YELLOW + "/bot removeSolid <x> <y> <z>" + ChatColor.RESET);
	        	return;
	        }
	    	if (!isLocationLoaded(loc)) {
	    		sender.sendMessage(String.format("The location at " + ChatColor.BLUE + "(%d, %d, %d)" + ChatColor.RESET + " is not loaded.",
	    			loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
	    		return;
	    	}
	    	if (Math.abs(loc.getX() - ((Player)sender).getLocation().getX()) > 250 || Math.abs(loc.getZ() - ((Player)sender).getLocation().getZ()) > 250) {
	    		sender.sendMessage(String.format("The location at " + ChatColor.BLUE + "(%d, %d, %d)" + ChatColor.RESET + " is too far away.",
	    			loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()));
	    		return;    		
	    	}
	    	mat = loc.getBlock().getType();
    	} else {
    		sender.sendMessage("Invalid syntax!");
    		sender.sendMessage("To specify a material: " + ChatColor.YELLOW + "/bot removeSolid <material>" + ChatColor.RESET);
    		sender.sendMessage("To specify a location containing a material: " + ChatColor.YELLOW + "/bot removeSolid <x> <y> <z>" + ChatColor.RESET);
    		return;
    	}
    	if (mat == null) {
    		sender.sendMessage("The material you specified does not exist!");
    		return;
    	}
		if (LegacyMats.SOLID_MATERIALS.remove(mat))
			sender.sendMessage("Successfully removed " + ChatColor.BLUE + mat.name() + ChatColor.RESET + " from the list.");
		else
			sender.sendMessage(ChatColor.BLUE + mat.name() + ChatColor.RESET + " does not exist in the list!");
    }

    @Command(
        name = "listSolids",
        desc = "Displays the list of solid materials manually added.",
        aliases = {"listsolids"}
    )
    public void listSolids(CommandSender sender) {
    	sender.sendMessage(ChatUtils.LINE);
    	for (Material mat : LegacyMats.SOLID_MATERIALS)
    		sender.sendMessage(ChatColor.GREEN + mat.name() + ChatColor.RESET);
    	sender.sendMessage("Total items: " + ChatColor.BLUE + LegacyMats.SOLID_MATERIALS.size() + ChatColor.RESET);
    	sender.sendMessage(ChatUtils.LINE);
    }
    
    @Command(
        name = "clearSolids",
        desc = "Clears the list of solid materials manually added.",
        aliases = {"clearsolids"}
    )
    public void clearSolids(CommandSender sender) {
    	int size = LegacyMats.SOLID_MATERIALS.size();
    	LegacyMats.SOLID_MATERIALS.clear();
    	sender.sendMessage("Removed all " + ChatColor.BLUE + size + ChatColor.RESET + " item(s) from the list.");
    }
    
    @Command(
        name = "addHostileMob",
        desc = "Adds a mob type to the list of hostile mobs.",
        aliases = {"addhostilemob"}
    )
    public void addHostileMob(CommandSender sender, @Arg("mobName") String mobName) {
    	EntityType type = EntityType.fromName(mobName);
    	if (type == null) {
    		sender.sendMessage("The entity type you specified does not exist!");
    		return;
    	}
    	if (LegacyAgent.HOSTILE_MOBS.add(type))
    		sender.sendMessage("Successfully added " + ChatColor.BLUE + type.name() + ChatColor.RESET + " to the list.");
    	else
    		sender.sendMessage(ChatColor.BLUE + type.name() + ChatColor.RESET + " already exists in the list!");
    }
    
    @Command(
        name = "removeHostileMob",
        desc = "Removes a mob type to the list of hostile mobs.",
        aliases = {"removehostilemob"}
    )
    public void removeHostileMob(CommandSender sender, @Arg("mobName") String mobName) {
    	EntityType type = EntityType.fromName(mobName);
    	if (type == null) {
    		sender.sendMessage("The entity type you specified does not exist!");
    		return;
    	}
    	if (LegacyAgent.HOSTILE_MOBS.remove(type))
    		sender.sendMessage("Successfully removed " + ChatColor.BLUE + type.name() + ChatColor.RESET + " from the list.");
    	else
    		sender.sendMessage(ChatColor.BLUE + type.name() + ChatColor.RESET + " does not exist in the list!");
    }
    
    @Command(
        name = "listHostileMobs",
        desc = "Displays the list of hostile mobs manually added.",
        aliases = {"listhostilemobs"}
    )
    public void listHostileMobs(CommandSender sender) {
    	sender.sendMessage(ChatUtils.LINE);
    	for (EntityType type : LegacyAgent.HOSTILE_MOBS)
    		sender.sendMessage(ChatColor.GREEN + type.name() + ChatColor.RESET);
    	sender.sendMessage("Total items: " + ChatColor.BLUE + LegacyAgent.HOSTILE_MOBS.size() + ChatColor.RESET);
    	sender.sendMessage(ChatUtils.LINE);
    }
    
    @Command(
        name = "removeHostileMobs",
        desc = "Clears the list of hostile mobs manually added.",
        aliases = {"removehostilemobs"}
    )
    public void removeHostileMobs(CommandSender sender) {
    	int size = LegacyAgent.HOSTILE_MOBS.size();
    	LegacyAgent.HOSTILE_MOBS.clear();
    	sender.sendMessage("Removed all " + ChatColor.BLUE + size + ChatColor.RESET + " item(s) from the list.");
    }
    
    private double parseDoubleOrRelative(String pos, Location loc, int type) {
		if (loc == null || pos.length() == 0 || pos.charAt(0) != '~')
			return Double.parseDouble(pos);
		double relative = pos.length() == 1 ? 0 : Double.parseDouble(pos.substring(1));
    	switch (type) {
    		case 0:
    			return relative + Math.round(loc.getX() * 1000) / 1000D;
    		case 1:
    			return relative + Math.round(loc.getY() * 1000) / 1000D;
    		case 2:
    			return relative + Math.round(loc.getZ() * 1000) / 1000D;
    		default:
    			return 0;
    	}
    }
    
    private boolean isLocationLoaded(Location loc) {
    	return loc.getWorld().isChunkLoaded(Location.locToBlock(loc.getX()) >> 4, Location.locToBlock(loc.getZ()) >> 4);
    }
}
