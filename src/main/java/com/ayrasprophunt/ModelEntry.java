/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Locale;

/** Immutable, bundled metadata. Meshes themselves are loaded from the game cache. */
public final class ModelEntry
{
	final ModelKind kind;
	final int id;
	final int variant;
	final String name;
	final String key;
	final String search;
	final int[] models;
	final short[] colors, colorTo, textures, textureTo;
	final int idle, walk, run, attack;
	final int sx, sy, sz, ox, oy, oz, ambient, contrast, fullColor;
	final boolean mirrored;

	private ModelEntry(DataInputStream in) throws IOException
	{
		int type = in.readUnsignedByte();
		if (type >= ModelKind.values().length) { throw new IOException("Unknown catalog model kind"); }
		kind = ModelKind.values()[type];
		id = in.readInt();
		variant = in.readUnsignedByte();
		String label = in.readUTF();
		name = label.isBlank() ? kind + " " + id : label;
		key = kind.name() + ":" + id + ":" + variant;
		search = (name + " " + kind + " " + id).toLowerCase(Locale.ROOT);
		int count = in.readUnsignedShort();
		if (count < 1 || count > 256) { throw new IOException("Invalid model-part count"); }
		models = new int[count];
		for (int i = 0; i < count; i++) { models[i] = in.readInt(); }
		short[][] pair = pairs(in);
		colors = pair[0]; colorTo = pair[1];
		pair = pairs(in);
		textures = pair[0]; textureTo = pair[1];
		idle = in.readInt(); walk = in.readInt(); run = in.readInt(); attack = in.readInt();
		sx = in.readInt(); sy = in.readInt(); sz = in.readInt();
		ox = in.readInt(); oy = in.readInt(); oz = in.readInt();
		ambient = in.readInt(); contrast = in.readInt(); fullColor = in.readInt();
		mirrored = in.readBoolean();
	}

	static ModelEntry read(DataInputStream input) throws IOException { return new ModelEntry(input); }

	private static short[][] pairs(DataInputStream in) throws IOException
	{
		int count = in.readUnsignedShort();
		if (count > 4096) { throw new IOException("Invalid color/texture count"); }
		short[][] pair = new short[2][count];
		for (int i = 0; i < count; i++) { pair[0][i] = in.readShort(); pair[1][i] = in.readShort(); }
		return pair;
	}

	public String getKey() { return key; }
	public String getName() { return name; }
	public int getId() { return id; }
	public ModelKind getKind() { return kind; }
	public boolean isAnimated() { return idle >= 0 || walk >= 0 || run >= 0 || attack >= 0; }
	public String detail() { return kind + "  #" + id + (kind == ModelKind.OBJECT ? " / shape " + variant : "") + "  ·  " + (isAnimated() ? "Animated" : "Static"); }
	int motionAnimation(boolean moving, boolean running)
	{
		if (moving && running && run >= 0) { return run; }
		return moving && walk >= 0 ? walk : idle;
	}
	@Override public String toString() { return name + " — " + detail(); }
}
