/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasprophunt;

public enum ModelKind
{
	NPC("NPCs"), OBJECT("Scenery"), ITEM("Items"), RAW("Raw meshes");

	private final String label;
	ModelKind(String label) { this.label = label; }
	@Override public String toString() { return label; }
}
