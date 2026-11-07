package io.github.kortev.chainsawman.contract;

/** The devil contracts a player can hold: none, or the Eternity Devil's (more to come, one devil each). */
public enum Contract {
	NONE("none"),
	ETERNITY("eternity");

	public final String id;

	Contract(String id) {
		this.id = id;
	}

	public static Contract byId(String id) {
		for (Contract c : values()) {
			if (c.id.equals(id)) {
				return c;
			}
		}
		return NONE;
	}

	public static Contract byOrdinal(int ordinal) {
		return ordinal >= 0 && ordinal < values().length ? values()[ordinal] : NONE;
	}
}
