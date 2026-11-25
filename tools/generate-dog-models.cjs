/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
// Generate the picker from verified NPC metadata, never from guessed rig IDs.
const fs = require('node:fs');
const definitions = require('../build/dog-catalog/definitions.json');
const byId = new Map(definitions.map(d => [d.id, d]));
const classics = [
    ['STRAY_DOG', 2902, 'Stray dog'],
    ['CLEAN_STRAY_DOG', 16495, 'Stray dog - Clean'],
    ['STRAY_DOG_DARK', 3829, 'Stray dog - Dark'],
    ['CLAN_HALL_DOG', 10760, 'Stray dog - Clan hall'],
    ['DOG', 111, 'Classic dog'], ['GUARD_DOG', 114, 'Guard dog'],
    ['WILD_DOG', 112, 'Wild dog'], ['SHADOW_HOUND', 3449, 'Shadow hound'],
    ['SHEEPDOG', 2802, 'Sheepdog - Sitting'],
    ['ROSIE', 10438, 'Rosie - Sleeping sheepdog'],
    ['BLOODHOUND', 7232, 'Bloodhound'],
    ['MYTHS_GUILD_DOG', 8041, 'Myths Guild dog'],
    ['SPOOKY_DOG', 11867, 'Spooky dog'], ['SPIDEY_DOG', 12513, 'Spidey dog'],
    ['HALLOWEEN_DOG', 14112, 'Halloween stray dog'],
    ['MOLOSSUS', 12992, 'Molossus - Brown'], ['MOLOSSUS_DARK', 12993, 'Molossus - Dark'],
    ['MOLOSSUS_CABBAGE', 12999, 'Molossus - Cabbage collar'],
    ['XOLO', 12996, 'Xolo'], ['LOLA', 12998, 'Lola'],
    ['TECHICHI', 13958, 'Techichi - Brown'], ['TECHICHI_DARK', 13960, 'Techichi - Dark'],
    ['CHIRIBAYA', 13961, 'Chiribaya - Brown'], ['CHIRIBAYA_DARK', 13962, 'Chiribaya - Dark'],
    ['WOOFSON', 13966, 'Woofson'], ['ZIGGY', 14539, 'Ziggy'],
    ['SMART_DOG', 13842, 'Smart-looking dog'],
    ['CASTAWAY', 15150, 'Castaway'], ['CASTAWAY_ADRIFT', 15149, 'Castaway - Adrift'],
    ['JOE', 3907, 'Joe'], ['JOE_BALL', 4289, 'Joe - With ball'],
    ['HELLPUPPY', 3099, 'Hellpuppy'], ['HELLHOUND', 104, 'Hellhound'],
    ['SKELETON_HELLHOUND', 5054, 'Skeleton hellhound'],
    ['REANIMATED_DOG', 7025, 'Reanimated dog'],
    ['REANIMATED_HELLHOUND', 11463, 'Reanimated hellhound'],
    ['TERROR_DOG', 6474, 'Terror dog - Small'], ['TERROR_DOG_LARGE', 6473, 'Terror dog - Large']
];
const modern = definitions.filter(d => d.id >= 16361 && d.id <= 16396 || d.id >= 16433 && d.id <= 16468)
    .map(d => {
        const parts = d.symbol.replace(/_PUPPY$/, '').split('_');
        const coat = { CHOCO: 'Chocolate', BW: 'Black and white', MERLE: 'Merle', TOASTED: 'Toasted' }[parts.at(-1)]
            || parts.at(-1)[0] + parts.at(-1).slice(1).toLowerCase();
        return [d.symbol, d.id, `${d.name} - ${coat}`];
    }).sort((a, b) => a[2].localeCompare(b[2]));
const entries = [...classics, ...modern];
const constants = entries.map(([key, id, label]) => {
    const d = byId.get(id);
    if (!d || d.morph || !d.models.length) throw Error(`Missing concrete dog ${key}/${id}`);
    if (d.textures.length || d.fullRecolor) throw Error(`Add texture/full-recolor support for ${key}`);
    // Rosie is a static sleeping model; sitting dogs have no walk sequence.
    return `    ${key}(${id}, ${JSON.stringify(label)}, ${d.idle}, ${d.walk}, ${d.ambient}, ${d.contrast})`;
});
const source = `/* Copyright (c) 2026, Drew. SPDX-License-Identifier: BSD-2-Clause */
package com.ayrasdoggies;

/** Verified against the game cache; regenerate with tools/generate-dog-models.cjs. */
public enum DogModel
{
${constants.join(',\n')};

    private final int npcId;
    private final String label;
    private final int idle;
    private final int walk;
    private final int ambient;
    private final int contrast;

    DogModel(int npcId, String label, int idle, int walk, int ambient, int contrast)
    {
        this.npcId = npcId;
        this.label = label;
        this.idle = idle;
        this.walk = walk;
        this.ambient = ambient;
        this.contrast = contrast;
    }

    public int getNpcId() { return npcId; }
    public int getIdle() { return idle; }
    public int getWalk() { return walk; }
    public int getAmbient() { return ambient; }
    public int getContrast() { return contrast; }
    public int animation(boolean moving) { return moving && walk != -1 ? walk : idle; }

    public static DogModel fromNpcId(int id)
    {
        for (DogModel model : values())
        {
            if (model.npcId == id) { return model; }
        }
        return null;
    }

    @Override public String toString() { return label; }
}
`;
fs.writeFileSync('src/main/java/com/ayrasdoggies/DogModel.java', source);
console.log(`Generated ${entries.length} dog appearances, including ${modern.length} quest adult/puppy coats.`);
