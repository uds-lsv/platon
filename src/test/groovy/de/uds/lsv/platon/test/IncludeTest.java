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

package de.uds.lsv.platon.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import de.uds.lsv.platon.script.IncludeReader;

public class IncludeTest {
	static Charset charset = StandardCharsets.UTF_8;
	
	@Test
	public void includeTest() throws IOException {
		URL sourceUrl = IncludeTest.class.getClassLoader().getResource("test/include-simple.txt");
		try (BufferedReader reader = new BufferedReader(new IncludeReader(sourceUrl, charset))) {
			String line = reader.readLine();
			Assert.assertEquals("success!", line);
			Assert.assertNull(reader.readLine());
		}
	}
	
	@Test
	public void commentIncludeTest() throws IOException {
		URL sourceUrl = IncludeTest.class.getClassLoader().getResource("test/include-comment.txt");
		try (BufferedReader reader = new BufferedReader(new IncludeReader(sourceUrl, charset))) {
			String line = reader.readLine();
			Assert.assertEquals("success!", line);
			Assert.assertNull(reader.readLine());
		}
	}
	
	@Test
	public void recursiveIncludeTest() throws IOException {
		URL sourceUrl = IncludeTest.class.getClassLoader().getResource("test/include-recursive.txt");
		try (BufferedReader reader = new BufferedReader(new IncludeReader(sourceUrl, charset))) {
			String line = reader.readLine();
			Assert.assertEquals("success!", line);
			Assert.assertNull(reader.readLine());
		}
	}
	
	@Test
	public void lineNumberTest() throws IOException {
		List<Integer> expected = new ArrayList<>();
		
		URL sourceUrl = IncludeTest.class.getClassLoader().getResource("test/include-linenumbers-1.txt");
		try (IncludeReader includeReader = new IncludeReader(sourceUrl, charset)) {
			try (BufferedReader bufferedReader = new BufferedReader(includeReader)) {
				String line;
				while ((line = bufferedReader.readLine()) != null) {
					expected.add(Integer.parseInt(line.trim()));
				}
			}
			
			int i = 0;
			for (int expectedLineNumber : expected) {
				int translatedLineNumber = includeReader.translateLocation(i).startLine;
				Assert.assertEquals(
					expectedLineNumber,
					translatedLineNumber
				);
				
				i++;
			}
		}
	}
}
