# Draw = Items

Fabric Minecraft **26.1.2** mod that adds a drawing canvas and an on-the-fly
image recognizer: sketch something → press **Submit** → the game figures out
what you drew and gives it to you.

## How it works

Run `/draw` to open the drawing screen. Draw on the 64×64 canvas using the
palette, brushes (1px → 12px), eraser, fill bucket, and color picker. Undo /
redo with `Ctrl+Z` / `Ctrl+Y`.

When you press **Submit**, the server extracts features from the drawing
(dominant color bucket, bounding-box aspect ratio, fill density, symmetry) and
matches them against a table of templates. The best match is granted to you
with a confidence percentage.

Known templates include: torch, apple, blaze rod, fire charge, oak leaves,
melon, emerald, water bucket, diamond, gold ingot, wheat, egg, oak planks,
stick, iron ingot, cobblestone, coal, ink sac, amethyst shard, ender pearl,
poppy, rose bush, bone, bundle, plus every dye as a color-based fallback so
you always get **something**.

## Installation

1. Fabric Loader ≥ 0.19.2
2. Fabric API 0.146.1+26.1.2
3. Drop the mod jar in `mods/`.

## Commands

- `/draw` — opens the drawing screen for the calling player.
