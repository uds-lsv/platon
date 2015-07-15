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

import org.junit.Assert
import org.junit.Test

import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.action.ModifyObjectAction
import de.uds.lsv.platon.script.WorldObjectWrapper
import de.uds.lsv.platon.world.DefaultWorldObject
import de.uds.lsv.platon.world.WorldClass
import de.uds.lsv.platon.world.WorldField
import de.uds.lsv.platon.world.WorldMethod
import de.uds.lsv.platon.world.WorldObject

public class WorldObjectWrapperTest {
	@WorldClass("test.TestObject")
	static class TestObject extends WorldObject {
		private static String TYPE = "test.TestObject";
		
		@WorldField
		public int propertyA = 17;
		
		@WorldField
		public String propertyB = "foo";
		
		@WorldField
		boolean propertyC = false;
		
		@WorldField(writable=false)
		String propertyD = null;
		
		Object[] propertyE = null;
		
		@WorldMethod
		public List<Action> methodC() {
			propertyC = true;
			return [ new ModifyObjectAction(null, null) ];
		}
		
		@WorldMethod
		public List<Action> methodD(String arg) {
			propertyD = arg;
			return [ new ModifyObjectAction(null, null) ];
		}
		
		@WorldMethod
		public List<Action> methodE(Object... args) {
			propertyE = args;
			return [ new ModifyObjectAction(null, null) ];
		}
		
		@Override
		public String getType() {
			return TYPE;
		}
	}
	
	@Test
	public void testGetProperties() {
		TestObject object = new TestObject();
		object.init(null, [ "id": "test" ]);
		WorldObjectWrapper wrapper = new WorldObjectWrapper(object, null);
		
		Assert.assertEquals(object.propertyA, wrapper.propertyA);
		Assert.assertEquals(object.propertyB, wrapper.propertyB);
		Assert.assertEquals(object.propertyC, wrapper.propertyC);
		Assert.assertEquals(object.propertyD, wrapper.propertyD);
		
		object.propertyA = 42;
		Assert.assertEquals(object.propertyA, wrapper.propertyA);
		Assert.assertEquals(object.propertyB, wrapper.propertyB);
		
		object.propertyB = "bar";
		Assert.assertEquals(object.propertyA, wrapper.propertyA);
		Assert.assertEquals(object.propertyB, wrapper.propertyB);
		
		object.propertyB = null;
		Assert.assertEquals(object.propertyA, wrapper.propertyA);
		Assert.assertEquals(object.propertyB, wrapper.propertyB);
	}
	
	@Test
	public void testSetPropertiesException() {
		TestObject object = new TestObject();
		object.init(null, [ "id": "test" ]);
		WorldObjectWrapper wrapper = new WorldObjectWrapper(object, null);
		
		while (true) {
			try {
				wrapper.propertyD = 42;
			}
			catch (IllegalAccessException e) {
				break;
			}
			
			Assert.fail("Expected IllegalAccessException!");
		}
	}
	
	@Test
	public void testMethodInvocationPlain() {
		TestObject object = new TestObject();
		object.init(null, [ "id": "test" ]);
		WorldObjectWrapper wrapper = new WorldObjectWrapper(object, null);
		
		object.propertyC = false;
		wrapper.methodC();
		Assert.assertTrue(object.propertyC);
	}
	
	@Test
	public void testMethodInvocationWithArguments() {
		TestObject object = new TestObject();
		object.init(null, [ "id": "test" ]);
		WorldObjectWrapper wrapper = new WorldObjectWrapper(object, null);
	
		object.propertyD = null;
		wrapper.methodD("bar");
		Assert.assertEquals("bar", object.propertyD);
		
		wrapper.methodE("foo", "bar", 42);
		Assert.assertArrayEquals(
			[ "foo", "bar", 42 ] as Object[],
			object.propertyE
		);
	
		wrapper.methodE();
		Assert.assertArrayEquals(
			[] as Object[],
			object.propertyE
		);
	}
	
	@Test
	public void testDefaultWorldObject() {
		WorldObject object = new DefaultWorldObject("type");
		object.init(null, [ "id": "test", "foo": "bar", "bar": "baz" ]);
		WorldObjectWrapper wrapper = new WorldObjectWrapper(object, null);
		
		Assert.assertEquals("type", wrapper.type);
		Assert.assertEquals("test", wrapper.id);
		Assert.assertEquals("bar", wrapper.foo);
		Assert.assertEquals("baz", wrapper.bar);
		try {
			def x = wrapper.baz
		}
		catch (MissingPropertyException e) {
			return;
		}
		Assert.fail("MissingPropertyException not thrown.");
		
	}
}
