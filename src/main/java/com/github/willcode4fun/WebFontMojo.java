package com.github.willcode4fun;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import com.google.common.io.Files;

/**
 * Goal which touches a timestamp file.
 *
 * @goal touch
 *
 * @phase process-sources
 */
public class WebFontMojo extends AbstractMojo {
	private static final String GLYPH_NAME = "glyph-name=\"";
	private static final String UNICODE = "unicode=\"";
	/**
	 * Location of the file.
	 *
	 * @parameter default-value="${project.build.directory}"
	 * @required
	 */
	private File outputDirectory;

	/**
	 * Location of the file.
	 *
	 * @parameter default-value="${basedir}/src/main/svg/"
	 * @required
	 */
	private File inputDirectory;

	/**
	 * Location of the file.
	 *
	 * @parameter alias="fontforge.binary"
	 * @required
	 */
	private String fontforgeBinary;

	/**
	 * Location of the file.
	 *
	 * @parameter default-value="false"
	 * @required
	 */
	private boolean generateSample;

	public void execute() throws MojoExecutionException {
		final File dir = inputDirectory;
		getLog().info("Looking for files in : " + dir.getName());
		for (final File file : dir.listFiles()) {
			if (file.getName().endsWith(".svg")) {

				final String name = file.getName();
				final String simpleName = name.replace(".svg", "").toLowerCase();
				getLog().info("File to transform : " + name);

				try {
					final File cleaned = cleanSvgFile(file);
					// final File cleaned = file;
					getLog().info("cleaned file: " + cleaned.getAbsolutePath());

					final FontData data = extractData(cleaned, simpleName, simpleName);

					generateDirs(outputDirectory, simpleName);
					if (generateSample) {
						generateSample(outputDirectory, data);
					}
					generateCss(outputDirectory, data);
					generateFonts(cleaned.getAbsolutePath(), outputDirectory.getAbsolutePath() + "/font-" + simpleName
							+ "/font/" + name);
				} catch (final IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}

	private FontData extractData(final File svgFile, final String simpleName, final String prefix) throws IOException {
		final FontData data = new FontData();
		data.name = simpleName;
		data.prefix = prefix;
		final String content = Files.toString(svgFile, Charset.forName("UTF-8"));
		int nextGlyphIndex = content.indexOf("<glyph", 0);
		while (nextGlyphIndex > 0) {
			final int nameStart = content.indexOf(GLYPH_NAME, nextGlyphIndex) + GLYPH_NAME.length();
			final int nameEnd = content.indexOf("\"", nameStart);

			final int unicodeStart = content.indexOf(UNICODE, nextGlyphIndex) + UNICODE.length();
			final int unicodeEnd = content.indexOf("\"", unicodeStart);

			final String glyphName = content.substring(nameStart, nameEnd);
			final String glyphCode = content.substring(unicodeStart, unicodeEnd).replaceAll("&#x", "")
					.replaceAll(";", "");
			getLog().info("Glyph name : " + glyphName + " unicode =" + glyphCode);

			final GlyphData glyph = new GlyphData();
			glyph.name = glyphName;
			glyph.unicode = glyphCode;
			data.glyphs.add(glyph);

			nextGlyphIndex = content.indexOf("<glyph", nextGlyphIndex + 1);
		}
		return data;
	}

	private class FontData {
		String name;
		String prefix;
		List<GlyphData> glyphs = new ArrayList<GlyphData>();
	}

	private class GlyphData {
		String name;
		String unicode;
	}

	private File cleanSvgFile(final File file) {
		try {
			final String content = Files.toString(file, Charset.forName("UTF-8"));
			// remove everything after defs
			final String onlydefs = content.substring(0, content.indexOf("</defs>")) + "</defs></svg>";
			// replace glyphs unicodes

			final Pattern p = Pattern.compile("unicode=\\\"(.)\\\"");
			final Matcher m = p.matcher(onlydefs); // get a matcher object
			final StringBuffer sb = new StringBuffer();
			int index = 0xf000;

			while (m.find()) {
				m.appendReplacement(sb, "unicode=\"&#x" + Integer.toHexString(index) + ";\"");
				index++;
			}
			m.appendTail(sb);
			sb.toString();// outputDirectory
			final File res = File.createTempFile("temp", ".svg", outputDirectory);
			Files.write(sb.toString().getBytes(), res);
			return res;
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	private void generateSample(final File outputDir, final FontData data) throws IOException {
		final File base = new File(outputDir, "/font-" + data.name + "/sample/sample.html");
		String content = IOUtils.toString(this.getClass().getResourceAsStream("/sample.html.tpl"));
		content = content.replaceAll("##name##", data.name);
		base.createNewFile();
		final StringBuilder sb = new StringBuilder();
		for (final GlyphData glyph : data.glyphs) {
			sb.append("\n<i class=\"icon-");
			sb.append(data.prefix);
			sb.append("-");
			sb.append(glyph.name);
			sb.append("\"></i>");
		}
		content = content.replaceAll("##body##", sb.toString());

		Files.write(content.getBytes(), base);

	}

	private void generateCss(final File outputDir, final FontData data) throws IOException {
		final File base = new File(outputDir, "/font-" + data.name + "/css/font-" + data.name + ".css");

		base.createNewFile();
		String cssContent = IOUtils.toString(this.getClass().getResourceAsStream("/css.tpl"));

		cssContent = cssContent.replaceAll("##name##", data.name).replaceAll("##prefix##", data.prefix)
				.replaceAll("##family##", "Font" + data.name.toUpperCase().charAt(0) + data.name.substring(1));

		final StringBuilder sb = new StringBuilder(cssContent);
		for (final GlyphData glyph : data.glyphs) {
			sb.append("\n.icon-");
			sb.append(data.prefix);
			sb.append("-");
			sb.append(glyph.name);
			sb.append(":before {  content: \"");
			if (glyph.unicode.length() > 1) {
				sb.append("\\");
			}
			sb.append(glyph.unicode);
			sb.append("\" }");
		}

		Files.write(sb.toString().getBytes(), base);

	}

	private void generateDirs(final File outputDir, final String name) {

		final File base = new File(outputDir, "font-" + name);
		base.mkdir();
		final File css = new File(base, "css");
		css.mkdir();
		final File font = new File(base, "font");
		font.mkdir();
		if (generateSample) {
			final File sample = new File(base, "sample");
			sample.mkdir();
		}
	}

	private void generateFonts(final String srcFile, final String dstFile) {
		final Runtime runtime = Runtime.getRuntime();

		try {
			final File script = File.createTempFile("fontforgescript", "sh");
			IOUtils.write("Open($1)\n" + "Generate($2:r + \".ttf\")\n" + "Generate($2:r + \".otf\")\n"
					+ "Generate($2:r + \".woff\")\n" + "Generate($2:r + \".svg\")\n", new FileOutputStream(script));
			final Process process = runtime.exec(new String[] { fontforgeBinary, "-script", script.getAbsolutePath(),
					srcFile, dstFile });
			// Consommation de la sortie standard de l'application externe dans un Thread separe
			new Thread() {
				@Override
				public void run() {
					try {
						final BufferedReader reader = new BufferedReader(
								new InputStreamReader(process.getInputStream()));
						String line = "";
						try {
							while ((line = reader.readLine()) != null) {
								getLog().info(line);
							}
						} finally {
							reader.close();
						}
					} catch (final IOException ioe) {
						ioe.printStackTrace();
					}
				}
			}.start();

			// Consommation de la sortie d'erreur de l'application externe dans un Thread separe
			new Thread() {
				@Override
				public void run() {
					try {
						final BufferedReader reader = new BufferedReader(
								new InputStreamReader(process.getErrorStream()));
						String line = "";
						try {
							while ((line = reader.readLine()) != null) {
								getLog().error(line);
							}
						} finally {
							reader.close();
						}
					} catch (final IOException ioe) {
						ioe.printStackTrace();
					}
				}
			}.start();
			process.waitFor();
		} catch (final IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (final InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
