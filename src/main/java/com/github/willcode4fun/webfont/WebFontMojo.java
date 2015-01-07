package com.github.willcode4fun.webfont;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.io.IOUtils;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;

import com.github.willcode4fun.webfont.model.Font;
import com.github.willcode4fun.webfont.model.Glyph;
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

					final Font data = extractData(cleaned, simpleName, simpleName);

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

	private Font extractData(final File svgFile, final String simpleName, final String prefix) throws IOException {
		final Font font = new Font(simpleName, prefix);
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

			font.addGlyph(new Glyph(glyphName, glyphCode));

			nextGlyphIndex = content.indexOf("<glyph", nextGlyphIndex + 1);
		}
		return font;
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

	private void generateSample(final File outputDir, final Font font) throws IOException {
		final File base = new File(outputDir, "/font-" + font.getName() + "/sample/sample.html");
		String content = IOUtils.toString(this.getClass().getResourceAsStream("/sample.html.tpl"));
		content = content.replaceAll("##name##", font.getName());
		base.createNewFile();
		final StringBuilder sb = new StringBuilder();
		for (final Glyph glyph : font.getGlyphs()) {
			sb.append("\n<i class=\"icon-");
			sb.append(font.getPrefix());
			sb.append("-");
			sb.append(glyph.getName());
			sb.append("\"></i>");
		}
		content = content.replaceAll("##body##", sb.toString());

		Files.write(content.getBytes(), base);

	}

	private void generateCss(final File outputDir, final Font font) throws IOException {
		final String name = font.getName();
		final File base = new File(outputDir, "/font-" + name + "/css/font-" + name + ".css");

		base.createNewFile();
		String cssContent = IOUtils.toString(this.getClass().getResourceAsStream("/css.tpl"));

		cssContent = cssContent.replaceAll("##name##", name).replaceAll("##prefix##", font.getPrefix())
				.replaceAll("##family##", "Font" + name.toUpperCase().charAt(0) + name.substring(1));

		final StringBuilder sb = new StringBuilder(cssContent);
		for (final Glyph glyph : font.getGlyphs()) {
			sb.append("\n.icon-");
			sb.append(font.getPrefix());
			sb.append("-");
			sb.append(glyph.getName());
			sb.append(":before {  content: \"");
			if (glyph.getUnicode().length() > 1) {
				sb.append("\\");
			}
			sb.append(glyph.getUnicode());
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
