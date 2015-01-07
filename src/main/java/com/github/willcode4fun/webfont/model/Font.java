package com.github.willcode4fun.webfont.model;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.ImmutableList;

public class Font {
	String name;
	String prefix;
	List<Glyph> glyphs = new ArrayList<Glyph>();

	public Font(final String name, final String prefix) {
		super();
		this.name = name;
		this.prefix = prefix;
	}

	public String getName() {
		return name;
	}

	public String getPrefix() {
		return prefix;
	}

	public Font addGlyph(final Glyph glyph) {
		this.glyphs.add(glyph);
		return this;
	}

	public List<Glyph> getGlyphs() {
		return ImmutableList.copyOf(glyphs);
	}

}
