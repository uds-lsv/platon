/*
 * Copyright 2015, Spoken Language Systems Group, Saarland University.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package de.uds.lsv.platon.script;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import de.martingropp.util.Pair;
import de.martingropp.util.PreprocessingReader;

/**
 * A reader supporting #include and //#include.
 * 
 * @author mgropp
 */
public class IncludeReader extends PreprocessingReader {
	private static final Log logger = LogFactory.getLog(IncludeReader.class.getName());
	
	/** We use this to determine the include path. */
	private static final String STDLIB = "/script-include/stdlib.groovy";
	
	public static class CodeLocation {
		public URL url;
		public int startLine;
		
		public CodeLocation(URL url, int startLine) {
			this.url = url;
			this.startLine = startLine;
		}
		
		@Override
		public String toString() {
			return String.format("%s:%d", url, startLine);
		}
	}
	
	public static class CodeSpan extends CodeLocation {
		/** exclusive */
		public int endLine;
		
		public CodeSpan(URL url, int startLine) {
			this(url, startLine, -1);
		}
		
		public CodeSpan(URL url, int startLine, int endLine) {
			super(url, startLine);
			this.endLine = endLine;
		}
		
		public boolean containsLine(int line) {
			return (
				line >= startLine &&
				(endLine < 0 || line < endLine)
			);
		}
		
		public int size() {
			if (endLine < 0) {
				throw new IllegalStateException();
			}
			
			return endLine - startLine;
		}
		
		@Override
		public String toString() {
			if (endLine >= 0) {
				return String.format("%s:%d→%d", url, startLine, endLine);
			} else {
				return super.toString();
			}
		}
	}
	
	private static class Origin extends CodeSpan {
		public Origin parent;
		public List<Origin> children = null;
		
		public Origin(URL url, int startLine) {
			this(null, url, startLine);
		}
		
		public Origin(Origin parent, URL url, int startLine) {
			super(url, startLine);
			this.parent = parent;
		}
		
		public Origin addChild(URL url, int startLine) {
			if (children == null) {
				children = new ArrayList<>();
			}
			
			Origin child = new Origin(this, url, startLine);
			children.add(child);
			
			return child;
		}
		
		public Origin end(int endLine) {
			this.endLine = endLine;
			return parent;
		}
		
		public CodeLocation translateLine(int line) {
			if (line < startLine) {
				throw new RuntimeException(
					"Line not inside span " + this + ": " + line
				);
			}
			if (endLine >= 0 && line >= endLine) {
				throw new RuntimeException(
					"Line not inside span " + this + ": " + line
				);
			}
			
			int delta = 0;
			if (children != null) {
				for (Origin child : children) {
					if (child.startLine > line) {
						break;
					}
					
					if (child.containsLine(line)) {
						return child.translateLine(line);
					}
					
					// -1: #include line
					delta += child.size() - 1;
				}
			}
			
			return new CodeLocation(this.url, line - this.startLine - delta);
		}		
	}
	
	private final URI rootUri;
	private final Set<URL> alreadyIncluded = new HashSet<>();
	private Origin rootOrigin;
	private Origin currentOrigin;
	
	/**
	 * A list of URIs whose resolve methods are called to resolve
	 * included files.
	 * (To be exact, the resolve method of IncludeReader is called.)
	 */
	private List<URI> includePath = new ArrayList<>();
	
	public IncludeReader(URL sourceUrl) throws IOException {
		this(sourceUrl, StandardCharsets.UTF_8);
	}
	
	public IncludeReader(URL sourceUrl, Charset charset) throws IOException {
		super(new BufferedReader(new InputStreamReader(sourceUrl.openStream(), charset)));
		try {
			this.rootUri = sourceUrl.toURI();
		}
		catch (URISyntaxException e) {
			throw new RuntimeException(e);
		}
		rootOrigin = new Origin(sourceUrl, getLine());
		currentOrigin = rootOrigin;
		alreadyIncluded.add(sourceUrl);
		
		setupIncludePath();
	}
	
	/**
	 * This constructor disables #include! 
	 * @param reader
	 */
	public IncludeReader(BufferedReader reader) {
		readers.push(reader);
		rootUri = null;
		setupIncludePath();
	}
	
	/**
	 * @param reader
	 * @param rootUri
	 *   The base path for included files.
	 */
	public IncludeReader(BufferedReader reader, URI rootUri) {
		readers.push(reader);
		this.rootUri = rootUri;
		if (rootUri != null) {
			rootOrigin = new Origin(null, getLine());
			currentOrigin = rootOrigin;
		}
		setupIncludePath();
	}
	
	private void setupIncludePath() {
		if (rootUri != null) {
			includePath.add(rootUri);
		}
		
		URL url = IncludeReader.class.getResource(STDLIB);
		if (url != null) {
			try {
				includePath.add(url.toURI());
			}
			catch (URISyntaxException e) {
				// should™ not happen anyway
				throw new RuntimeException(e);
			}
		}
	}
	
	@Override
	protected void popReader() throws IOException {
		super.popReader();
		if (currentOrigin != null) {
			currentOrigin = currentOrigin.end(getLine());
		}
	}
	
	private static URI resolve(URI rootUri, String relative) {
		// https://bugs.openjdk.java.net/browse/JDK-8020755
		if ("jar".equals(rootUri.getScheme())) {
			if (relative.startsWith("/")) {
				throw new RuntimeException("Absolute paths are not supported!");
			}
			
			String root = rootUri.toString();
			int index = root.lastIndexOf('/');
			try {
				if (index < 0) {
					return new URI(relative);
				}
				
				return new URI(
					root.substring(0, index + 1) +
					relative
				).normalize();
			}
			catch (URISyntaxException e) {
				// should™ not happen anyway
				throw new RuntimeException(e);
			}
		}
		
		return rootUri.resolve(relative);
	}
	
	private Pair<InputStream,URL> open(String relative) throws MalformedURLException {
		for (URI baseUri : includePath) {
			URL url = resolve(baseUri, relative).toURL();
			if (alreadyIncluded.contains(url)) {
				throw new RuntimeException(String.format(
					"Not including »%s«: already loaded.",
					url
				));
			}
			
			alreadyIncluded.add(url);
			
			try {
				return new Pair<>(
					url.openStream(),
					url
				);
			}
			catch (IOException e) {
				continue;
			}
		}
		
		return null;
	}
	
	@Override
	protected boolean preprocess(String line, LinkedList<String> buffer) throws MalformedURLException, IOException {
		if (
			line.trim().startsWith("#include ") ||
			line.trim().startsWith("//#include ")
		) {
			if (rootUri == null) {
				throw new RuntimeException("#include disabled (reason: anonymous Reader).");
			}
			
			line = line.trim();
			String filename = line.substring(line.indexOf("#include") + 9).trim();
			if (
				filename.length() < 2 ||
				!"\"'".contains(Character.toString(filename.charAt(0))) ||
				filename.charAt(filename.length()-1) != filename.charAt(0)
			) {
				throw new RuntimeException("Bad argument for #include: »" + filename + "«");
			}
			
			filename = filename.substring(1, filename.length()-1);
			
			Pair<InputStream,URL> resolved = open(filename);
			if (resolved == null) {
				throw new IOException("Failed to open " + filename);
			}
			
			InputStream stream = resolved.first;
			URL includeUrl = resolved.second;
			
			logger.debug(String.format(
				"Including script from %s",
				includeUrl
			));
			
			currentOrigin = currentOrigin.addChild(includeUrl, getLine());
			
			readers.push(
				new BufferedReader(new InputStreamReader(stream))
			);
			
		} else {
			buffer.add(line);
		}
		
		return true;
	}
	
	/**
	 * @param line
	 *   line number, 0-based!
	 * @return
	 */
	public CodeLocation translateLocation(int line) {
		if (rootOrigin == null) {
			return new CodeLocation(null, line);
		} else {
			return rootOrigin.translateLine(line);
		}
	}
}
