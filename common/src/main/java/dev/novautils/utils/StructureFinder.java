package dev.novautils.utils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

/**
 * Where structures can generate, worked out from the world seed alone - the same placement maths
 * the game uses, done on the client. Vanilla then also checks biome and terrain before it really
 * builds a structure, which needs the full world generator, so these are candidate spots: most
 * are real, some come out empty (wrong biome, no island under an end city...).
 */
public final class StructureFinder {
    private StructureFinder() {}

    public enum Dim { OVERWORLD, NETHER, END }

    private enum Spread { LINEAR, TRIANGULAR }

    /** A kind of structure and its vanilla placement (spacing and separation in chunks, salt). */
    public record Kind(String name, String[] aliases, Dim dim, int spacing, int separation, int salt, Spread spread,
                       float frequency, String note) {
        boolean matches(String q) {
            if (name.equals(q)) return true;
            for (String a : aliases) if (a.equals(q)) return true;
            return false;
        }
    }

    public record Result(String name, int x, int z, double distance, String note) {}

    private static Kind kind(String name, Dim dim, int spacing, int separation, int salt, Spread spread, String... aliases) {
        return new Kind(name, aliases, dim, spacing, separation, salt, spread, 1f, "");
    }

    public static final List<Kind> KINDS = List.of(
        kind("village", Dim.OVERWORLD, 34, 8, 10387312, Spread.LINEAR, "vila", "villages"),
        new Kind("outpost", new String[]{"pillager_outpost", "posto"}, Dim.OVERWORLD, 32, 8, 165745296, Spread.LINEAR, 0.2f, ""),
        kind("desert_pyramid", Dim.OVERWORLD, 32, 8, 14357617, Spread.LINEAR, "desert_temple", "pyramid", "piramide"),
        kind("jungle_temple", Dim.OVERWORLD, 32, 8, 14357619, Spread.LINEAR, "jungle_pyramid"),
        kind("igloo", Dim.OVERWORLD, 32, 8, 14357618, Spread.LINEAR, "iglu"),
        kind("swamp_hut", Dim.OVERWORLD, 32, 8, 14357620, Spread.LINEAR, "witch_hut"),
        kind("monument", Dim.OVERWORLD, 32, 5, 10387313, Spread.TRIANGULAR, "ocean_monument", "monumento"),
        kind("mansion", Dim.OVERWORLD, 80, 20, 10387319, Spread.TRIANGULAR, "woodland_mansion", "mansao"),
        kind("ruined_portal", Dim.OVERWORLD, 40, 15, 34222645, Spread.LINEAR, "portal"),
        kind("shipwreck", Dim.OVERWORLD, 24, 4, 165745295, Spread.LINEAR, "naufragio"),
        kind("ocean_ruin", Dim.OVERWORLD, 20, 8, 14357621, Spread.LINEAR, "ocean_ruins"),
        kind("ancient_city", Dim.OVERWORLD, 24, 8, 20083232, Spread.LINEAR, "city", "cidade"),
        kind("trail_ruins", Dim.OVERWORLD, 34, 8, 83469867, Spread.LINEAR),
        kind("trial_chambers", Dim.OVERWORLD, 34, 12, 94251327, Spread.LINEAR, "trial"),
        kind("fortress", Dim.NETHER, 27, 4, 30084232, Spread.LINEAR, "nether_fortress", "fortaleza"),
        kind("bastion", Dim.NETHER, 27, 4, 30084232, Spread.LINEAR, "bastion_remnant", "bastiao"),
        kind("end_city", Dim.END, 20, 11, 10387313, Spread.TRIANGULAR, "endcity", "end", "cidade_end"),
        kind("stronghold", Dim.OVERWORLD, 0, 0, 0, Spread.LINEAR, "fortress_end", "fortaleza_end", "strong")
    );

    public static Kind find(String query) {
        String q = query.toLowerCase(Locale.ROOT).replace(' ', '_').replace("minecraft:", "");
        for (Kind k : KINDS) if (k.matches(q)) return k;
        return null;
    }

    // ---------------------------------------------------------------- vanilla placement maths

    /** RandomSpreadStructurePlacement.getPotentialStructureChunk. Returns {chunkX, chunkZ}. */
    private static int[] potentialChunk(long seed, Kind k, int regionX, int regionZ) {
        Random r = new Random((long) regionX * 341873128712L + (long) regionZ * 132897987541L + seed + k.salt());
        int range = k.spacing() - k.separation();
        int ox, oz;
        if (k.spread() == Spread.TRIANGULAR) {
            ox = (r.nextInt(range) + r.nextInt(range)) / 2;
            oz = (r.nextInt(range) + r.nextInt(range)) / 2;
        } else {
            ox = r.nextInt(range);
            oz = r.nextInt(range);
        }
        return new int[]{regionX * k.spacing() + ox, regionZ * k.spacing() + oz};
    }

    /** Pillager outposts: "legacy type 1" frequency reduction (1 in 5). */
    private static boolean passesFrequency(long seed, Kind k, int chunkX, int chunkZ) {
        if (k.frequency() >= 1f) return true;
        int i = chunkX >> 4, j = chunkZ >> 4;
        Random r = new Random((long) (i ^ j << 4) ^ seed);
        r.nextInt();
        return r.nextInt((int) (1f / k.frequency())) == 0;
    }

    /** WorldgenRandom.setLargeFeatureSeed. */
    private static Random largeFeature(long seed, int chunkX, int chunkZ) {
        Random r = new Random(seed);
        long a = r.nextLong(), b = r.nextLong();
        return new Random((long) chunkX * a ^ (long) chunkZ * b ^ seed);
    }

    /** Nether complexes: fortress (weight 2) or bastion (weight 3) - the game picks with this roll. */
    private static String netherPick(long seed, int chunkX, int chunkZ) {
        return largeFeature(seed, chunkX, chunkZ).nextInt(5) < 2 ? "fortress" : "bastion";
    }

    /** Outposts are not placed within 10 chunks of a village spot. */
    private static boolean nearVillage(long seed, int chunkX, int chunkZ) {
        Kind village = KINDS.get(0);
        int rx = Math.floorDiv(chunkX, village.spacing()), rz = Math.floorDiv(chunkZ, village.spacing());
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                int[] v = potentialChunk(seed, village, rx + dx, rz + dz);
                if (Math.abs(v[0] - chunkX) <= 10 && Math.abs(v[1] - chunkZ) <= 10) return true;
            }
        }
        return false;
    }

    /** Stronghold ring positions (before the game nudges each one up to ~112 blocks toward a good biome). */
    private static List<int[]> strongholds(long seed) {
        List<int[]> out = new ArrayList<>();
        Random r = new Random(seed);
        int distance = 32, spread = 3, count = 128;
        double angle = r.nextDouble() * Math.PI * 2.0;
        int inRing = 0, ring = 0;
        for (int n = 0; n < count; n++) {
            double d = (4 * distance + distance * ring * 6) + (r.nextDouble() - 0.5) * distance * 2.5;
            int cx = (int) Math.round(Math.cos(angle) * d), cz = (int) Math.round(Math.sin(angle) * d);
            r.nextLong(); // the game forks a random here for the biome search
            out.add(new int[]{cx, cz});
            angle += Math.PI * 2.0 / spread;
            if (++inRing == spread) {
                ring++;
                inRing = 0;
                spread += 2 * spread / (ring + 1);
                spread = Math.min(spread, count - n);
                angle += r.nextDouble() * Math.PI * 2.0;
            }
        }
        return out;
    }

    /**
     * The {@code count} nearest candidate spots of {@code k} to block (x, z) in its dimension,
     * nearest first, searching up to {@code radiusChunks} chunks out.
     */
    public static List<Result> nearest(long seed, Kind k, int x, int z, int count, int radiusChunks) {
        List<Result> out = new ArrayList<>();
        int pcx = x >> 4, pcz = z >> 4;
        if (k.name().equals("stronghold")) {
            for (int[] c : strongholds(seed)) {
                int bx = (c[0] << 4) + 4, bz = (c[1] << 4) + 4;
                out.add(new Result("stronghold", bx, bz, Math.hypot(bx - x, bz - z), "about +-112 blocks"));
            }
        } else {
            int rr = radiusChunks / k.spacing() + 1;
            int rx0 = Math.floorDiv(pcx, k.spacing()), rz0 = Math.floorDiv(pcz, k.spacing());
            for (int rx = rx0 - rr; rx <= rx0 + rr; rx++) {
                for (int rz = rz0 - rr; rz <= rz0 + rr; rz++) {
                    int[] c = potentialChunk(seed, k, rx, rz);
                    if (!passesFrequency(seed, k, c[0], c[1])) continue;
                    if (k.name().equals("outpost") && nearVillage(seed, c[0], c[1])) continue;
                    String note = "";
                    if (k.dim() == Dim.NETHER) {
                        if (!netherPick(seed, c[0], c[1]).equals(k.name())) continue;
                    }
                    int bx = (c[0] << 4) + 8, bz = (c[1] << 4) + 8;
                    if (k.dim() == Dim.END && Math.hypot(bx, bz) < 1000) continue; // only the outer islands
                    out.add(new Result(k.name(), bx, bz, Math.hypot(bx - x, bz - z), note));
                }
            }
        }
        out.sort(Comparator.comparingDouble(Result::distance));
        return out.subList(0, Math.min(count, out.size()));
    }

    /** Test hook: the candidate chunk {x, z} in a region, and whether the spot survives the extra checks. */
    public static int[] candidate(long seed, Kind k, int regionX, int regionZ) {
        return potentialChunk(seed, k, regionX, regionZ);
    }

    public static boolean outpostAllowed(long seed, int chunkX, int chunkZ) {
        Kind k = find("outpost");
        return passesFrequency(seed, k, chunkX, chunkZ) && !nearVillage(seed, chunkX, chunkZ);
    }

    public static List<int[]> strongholdChunks(long seed) {
        return strongholds(seed);
    }

    /** The public 2b2t seed. */
    public static final long SEED_2B2T = -4172144997902289642L;
}
