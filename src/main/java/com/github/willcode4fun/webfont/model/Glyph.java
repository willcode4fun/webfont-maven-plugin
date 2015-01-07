package com.github.willcode4fun.webfont.model;

public class Glyph {

	String name;
	String unicode;

	public Glyph(final String name, final String unicode) {
		super();
		this.name = name;
		this.unicode = unicode;
	}

	public String getName() {
		return name;
	}

	public String getUnicode() {
		return unicode;
	}

}
