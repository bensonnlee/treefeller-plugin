# TreeChopper for PaperMC

![Java Version](https://img.shields.io/badge/Java-17+-blue.svg)
![Minecraft Version](https://img.shields.io/badge/Minecraft-1.21.x-green.svg)
![Server Software](https://img.shields.io/badge/Server-PaperMC-orange.svg)

A simple PaperMC plugin for Minecraft 1.21+ that allows players to chop down entire trees quickly by breaking just one log block, followed by accelerated leaf decay. Designed to be efficient and configurable.

## Features

*   **Whole Tree Felling:** Break a single log block of a tree with an axe (configurable) to chop down all connected logs of the same type.
*   **Fast Leaf Decay:** Leaves connected to the felled tree decay much faster than vanilla mechanics.
*   **Player-Placed Log Protection:** By default, the plugin attempts to identify "natural" trees by checking for nearby leaves, preventing accidental destruction of log structures.
*   **Diagonal Connections:** Correctly detects logs connected diagonally as part of the same tree.
*   **Tool Durability:** Applies appropriate durability damage to the axe used for each log broken.
*   **Configurable:** Control various aspects like axe requirement, max tree size, leaf checking, and decay behavior via `config.yml`.
*   **Performance Conscious:** Includes settings like max tree size and batched leaf processing to prevent server lag.

## Requirements

*   **Minecraft Version:** 1.21.x
*   **Server Software:** [PaperMC](https://papermc.io/) (Might work on forks like Folia, but requires testing, especially regarding scheduler usage).

## Installation

1.  Download the latest `.jar` file from the [Releases page]([Link to Releases]).
2.  Place the downloaded `TreeChopper-x.x.x.jar` file into your server's `plugins` folder.
3.  Restart your server completely (a `/reload` is not recommended for installing new plugins).

## Usage

1.  Ensure you are in Survival or Adventure mode (Creative mode is ignored).
2.  Find a naturally generated tree.
3.  Break any log block of the tree using a standard Minecraft axe (unless `require-axe` is set to `false` in the config).
4.  The entire connected structure of the same log type should break, dropping items as normal.
5.  Connected leaves will begin to decay rapidly.

## Configuration

The configuration file `config.yml` is generated in the `/plugins/TreeChopper/` directory after the first run.

```yaml
# TreeChopper Configuration

# Set to true to require the player to use an axe. Set to false to allow any item (or fist).
require-axe: true

# Maximum number of log blocks that can be broken in one go. Prevents server lag from huge trees.
max-tree-size: 500

# If true, only logs connected (directly or indirectly within the search) to leaf blocks will be considered part of a 'natural' tree.
# This helps prevent breaking player-built log structures that don't resemble trees.
check-for-leaves: true

# Maximum distance (Manhattan distance) a leaf block can be from *any* broken log block to be included in the fast decay search.
leaf-search-radius: 7

# Maximum distance (Manhattan distance) a log block can be from the *initially broken* log to be considered part of the same tree.
# Helps limit the search scope, especially with diagonal checks.
log-search-radius: 15

# If true, leaves placed by players (persistent=true) will be ignored by the fast decay/break logic.
# Set to true to force-break even player-placed leaves connected to the tree.
force-break-persistent-leaves: false

# Debug mode - prints extra information to console. Useful for troubleshooting.
# Recommended to leave false during normal operation.
debug-mode: false
