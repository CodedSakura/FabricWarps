# FabricWarps
[![CurseForge downloads](http://cf.way2muchnoise.eu/short_433362.svg)](https://www.curseforge.com/minecraft/mc-mods/fabricwarps)
[![GitHub release version](https://img.shields.io/github/v/release/CodedSakura/FabricWarps)](https://github.com/CodedSakura/FabricWarps)  
A server-side Fabric mod that adds /warp command-set.  
Works for Minecraft 1.16.2+ (snapshots not fully tested)  
Requires [FabricAPI](https://www.curseforge.com/minecraft/mc-mods/fabric-api)  

## Commands
`/warp <destination>` - Teleports you to the destination

`/warps` - Alias for `/warps list`  
`/warps list [<dimension>]` - Lists all warps for the specified dimension or all dimensions

### OP level 2 permissions
`/warps add <name> [<x y z> [<yaw pitch> [<dimension>]]]` - creates a new warp destination
using your coordinates/rotation/dimension if they're not provided  
`/warps remove <name>` - removes a warp destination with the name  
`/warps config [<name> [<value>]]` - sets or gets a config value  

## Configuration

Can be found in `config/FabricWarps.properties`.  
Configuring through commands automatically rewrites the file.

`cooldown` - The minimum time between warping. Default: 15 (seconds)  
`bossbar` - Whether to enable the boss bar indication for standing still, if set to false will use action bar for time. Default: true  
`stand-still` - How long should the player stand still for after accepting a tpa or tpahere request. Default: 5 (seconds)  
