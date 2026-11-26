/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.GZIPOutputStream;
import net.runelite.api.gameval.AnimationID;
import net.runelite.cache.definitions.ItemDefinition;
import net.runelite.cache.definitions.NpcDefinition;
import net.runelite.cache.definitions.ObjectDefinition;
import net.runelite.cache.definitions.loaders.ItemLoader;
import net.runelite.cache.definitions.loaders.NpcLoader;
import net.runelite.cache.definitions.loaders.ObjectLoader;
import net.runelite.cache.fs.ArchiveFiles;
import net.runelite.cache.fs.Container;
import net.runelite.cache.fs.FSFile;
import net.runelite.cache.index.ArchiveData;
import net.runelite.cache.index.FileData;
import net.runelite.cache.index.IndexData;

/** Offline metadata export. Opens every game-cache file read-only; ships no game meshes. */
public final class GeneratePropCatalog
{
	private final Path cache;
	private final IndexData configs;
	private final List<Entry> entries = new ArrayList<>();
	private final Map<Integer, List<String>> animationNames = new HashMap<>();
	private final Map<String, Integer> animations = new HashMap<>();

	private GeneratePropCatalog(Path cache) throws Exception
	{
		this.cache = cache;
		configs = index(2);
		for (Field field : AnimationID.class.getFields())
		{
			int id = field.getInt(null);
			animations.put(field.getName(), id);
			animationNames.computeIfAbsent(id, ignored -> new ArrayList<>()).add(field.getName());
		}
	}

	public static void main(String[] args) throws Exception
	{
		GeneratePropCatalog generator = new GeneratePropCatalog(Path.of(args[0]));
		generator.npcs();
		generator.objects();
		generator.items();
		for (ArchiveData archive : generator.index(7).getArchives())
		{
			Entry entry = new Entry(3, archive.getId(), 0, "Raw model " + archive.getId(), new int[]{archive.getId()});
			generator.entries.add(entry);
		}
		Path destination = Path.of(args[1]);
		Files.createDirectories(destination.toAbsolutePath().getParent());
		try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new GZIPOutputStream(Files.newOutputStream(destination)))))
		{
			out.writeInt(0x50525031);
			out.writeInt(generator.entries.size());
			for (Entry entry : generator.entries) { entry.write(out); }
		}
		Map<Integer, Integer> counts = new TreeMap<>();
		for (Entry entry : generator.entries) { counts.merge(entry.kind, 1, Integer::sum); }
		System.out.println("Catalog: " + generator.entries.size() + " entries; NPC/scenery/item/raw counts " + counts);
		System.out.println("Compressed metadata bytes: " + Files.size(destination));
		for (Entry entry : generator.entries)
		{
			if (entry.name.equalsIgnoreCase("TzKal-Zuk") || entry.name.equalsIgnoreCase("Chicken") && entry.id == 1173
				|| entry.name.equalsIgnoreCase("Bench") && entry.id < 2000)
			{
				System.out.println(entry.kind + ":" + entry.id + ":" + entry.variant + " " + entry.name
					+ " models=" + Arrays.toString(entry.models) + " idle/walk/run/attack=" + entry.idle + "/" + entry.walk + "/" + entry.run + "/" + entry.attack);
			}
		}
	}

	private IndexData index(int id) throws Exception
	{
		IndexData data = new IndexData();
		data.load(archive(255, id));
		return data;
	}

	private byte[] archive(int index, int id) throws Exception
	{
		try (RandomAccessFile idx = new RandomAccessFile(cache.resolve("main_file_cache.idx" + index).toFile(), "r");
			RandomAccessFile dat = new RandomAccessFile(cache.resolve("main_file_cache.dat2").toFile(), "r"))
		{
			idx.seek(id * 6L);
			int length = medium(idx);
			int sector = medium(idx);
			if (length <= 0 || sector <= 0) { throw new IllegalStateException("Missing cache archive " + index + "/" + id); }
			ByteArrayOutputStream bytes = new ByteArrayOutputStream(length);
			int chunk = 0;
			while (bytes.size() < length)
			{
				dat.seek(sector * 520L);
				int archiveId = id > 65535 ? dat.readInt() : dat.readUnsignedShort();
				int part = dat.readUnsignedShort();
				sector = medium(dat);
				int foundIndex = dat.readUnsignedByte();
				if (archiveId != id || part != chunk++ || foundIndex != index) { throw new IllegalStateException("Invalid cache sector"); }
				byte[] block = new byte[Math.min(id > 65535 ? 510 : 512, length - bytes.size())];
				dat.readFully(block);
				bytes.write(block);
			}
			return Container.decompress(bytes.toByteArray(), null).data;
		}
	}

	private static int medium(RandomAccessFile file) throws Exception
	{
		return file.readUnsignedByte() << 16 | file.readUnsignedByte() << 8 | file.readUnsignedByte();
	}

	private Iterable<FSFile> definitions(int id) throws Exception
	{
		ArchiveData metadata = Arrays.stream(configs.getArchives()).filter(a -> a.getId() == id).findFirst().orElseThrow();
		ArchiveFiles files = new ArchiveFiles();
		for (FileData file : metadata.getFiles()) { files.addFile(new FSFile(file.getId())); }
		files.loadContents(archive(2, id));
		return files.getFiles();
	}

	private void npcs() throws Exception
	{
		NpcLoader loader = new NpcLoader();
		Map<Integer, NpcDefinition> all = new LinkedHashMap<>();
		for (FSFile file : definitions(9)) { all.put(file.getFileId(), loader.load(file.getFileId(), file.getContents())); }
		for (NpcDefinition original : all.values())
		{
			NpcDefinition def = original;
			for (int depth = 0; !hasModels(def.models) && def.configs != null && depth < 16; depth++)
			{
				NpcDefinition next = Arrays.stream(def.configs).filter(id -> id >= 0).mapToObj(all::get).filter(d -> d != null && hasModels(d.models)).findFirst().orElse(null);
				if (next == null || next == def) { break; }
				def = next;
			}
			if (!hasModels(def.models)) { continue; }
			Entry e = new Entry(0, original.id, 0, name(original.name, def.name, "NPC", original.id), def.models);
			e.idle = def.standingAnimation;
			e.walk = def.walkingAnimation;
			e.run = def.runAnimation;
			e.attack = attack(def);
			e.colors = def.recolorToFind; e.colorTo = def.recolorToReplace;
			e.textures = def.retextureToFind; e.textureTo = def.retextureToReplace;
			e.fullColor = def.fullRecolor;
			e.sx = def.widthScale; e.sy = def.heightScale; e.sz = def.widthScale;
			e.ambient = 64 + def.ambient; e.contrast = 850 + def.contrast * 5;
			entries.add(e);
		}
	}

	private int attack(NpcDefinition def)
	{
		for (int sequence : new int[]{def.standingAnimation, def.walkingAnimation})
		{
			for (String name : animationNames.getOrDefault(sequence, List.of()))
			{
				String prefix = name.replaceFirst("_(READY|IDLE|WALK|STAND)(?:[0-9_].*)?$", "");
				if (prefix.equals(name)) { continue; }
				for (String suffix : new String[]{"_ATTACK", "_ATTACK1", "_ATTACK_01", "_ATTACK_MELEE", "_MELEE_ATTACK", "_ATTACK_UNARMED", "_ATTACKUNARMED"})
				{
					Integer match = animations.get(prefix + suffix);
					if (match != null) { return match; }
				}
			}
		}
		return def.standingAnimation == 808 && def.walkingAnimation == 819 ? AnimationID.HUMAN_UNARMEDPUNCH : -1;
	}

	private void objects() throws Exception
	{
		ObjectLoader loader = new ObjectLoader();
		for (FSFile file : definitions(6))
		{
			ObjectDefinition def = loader.load(file.getFileId(), file.getContents());
			if (!hasModels(def.getObjectModels())) { continue; }
			int[] types = def.getObjectTypes();
			int[] variants = types == null ? new int[]{10} : Arrays.stream(types).distinct().sorted().toArray();
			for (int variant : variants)
			{
				int[] ids = def.getObjectModels();
				if (types != null)
				{
					List<Integer> selected = new ArrayList<>();
					for (int i = 0; i < types.length; i++) { if (types[i] == variant) { selected.add(ids[i]); } }
					ids = selected.stream().mapToInt(Integer::intValue).toArray();
				}
				Entry e = new Entry(1, def.getId(), variant, name(def.getName(), null, "Unnamed scenery", def.getId()), ids);
				e.idle = def.getAnimationID();
				e.colors = def.getRecolorToFind(); e.colorTo = def.getRecolorToReplace();
				e.textures = def.getRetextureToFind(); e.textureTo = def.getTextureToReplace();
				e.fullColor = def.getFullRecolor();
				e.sx = def.getModelSizeX(); e.sy = def.getModelSizeHeight(); e.sz = def.getModelSizeY();
				e.ox = def.getOffsetX(); e.oy = def.getOffsetHeight(); e.oz = def.getOffsetY();
				e.mirrored = def.isRotated();
				e.ambient = 64 + def.getAmbient(); e.contrast = 768 + def.getContrast();
				entries.add(e);
			}
		}
	}

	private void items() throws Exception
	{
		ItemLoader loader = new ItemLoader();
		for (FSFile file : definitions(10))
		{
			ItemDefinition def = loader.load(file.getFileId(), file.getContents());
			if (def.inventoryModel <= 0) { continue; }
			Entry e = new Entry(2, def.id, 0, name(def.name, null, "Unnamed item", def.id), new int[]{def.inventoryModel});
			e.colors = def.colorFind; e.colorTo = def.colorReplace;
			e.textures = def.textureFind; e.textureTo = def.textureReplace;
			e.fullColor = def.fullRecolor;
			e.sx = def.resizeX; e.sy = def.resizeY; e.sz = def.resizeZ;
			e.ambient = 64 + def.ambient; e.contrast = 768 + def.contrast;
			entries.add(e);
		}
	}

	private static boolean hasModels(int[] models) { return models != null && models.length > 0; }
	private static String name(String preferred, String fallback, String type, int id)
	{
		String name = preferred != null && !preferred.isBlank() && !preferred.equalsIgnoreCase("null") ? preferred : fallback;
		if (name == null || name.isBlank() || name.equalsIgnoreCase("null")) { return type + " " + id; }
		return name.replaceAll("<[^>]*>", "").replaceAll("\\p{Cntrl}", "").trim();
	}

	private static final class Entry
	{
		final int kind, id, variant;
		final String name;
		final int[] models;
		int idle = -1, walk = -1, run = -1, attack = -1;
		int sx = 128, sy = 128, sz = 128, ox, oy, oz, ambient = 64, contrast = 768, fullColor = -1;
		boolean mirrored;
		short[] colors, colorTo, textures, textureTo;
		Entry(int kind, int id, int variant, String name, int[] models)
		{
			this.kind = kind; this.id = id; this.variant = variant; this.name = name; this.models = models;
		}
		void write(DataOutputStream out) throws Exception
		{
			out.writeByte(kind); out.writeInt(id); out.writeByte(variant); out.writeUTF(name);
			out.writeShort(models.length);
			for (int model : models) { out.writeInt(model); }
			pairs(out, colors, colorTo); pairs(out, textures, textureTo);
			for (int value : new int[]{idle, walk, run, attack, sx, sy, sz, ox, oy, oz, ambient, contrast, fullColor}) { out.writeInt(value); }
			out.writeBoolean(mirrored);
		}
		private static void pairs(DataOutputStream out, short[] from, short[] to) throws Exception
		{
			int length = from == null || to == null ? 0 : Math.min(from.length, to.length);
			out.writeShort(length);
			for (int i = 0; i < length; i++) { out.writeShort(from[i]); out.writeShort(to[i]); }
		}
	}
}
