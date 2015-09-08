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

import org.codehaus.groovy.jsr223.GroovyScriptEngineFactory
import org.codehaus.groovy.jsr223.GroovyScriptEngineImpl
import org.junit.Assert
import org.junit.Test

import TestEnvironment.TestDoor
import de.uds.lsv.platon.world.WorldObject
import de.uds.lsv.platon.world.WorldState

class WorldStateTest {
	private static def objects = [
		[
			(WorldObject.FIELD_ID): "1-${TestDoor.TYPE}" as String, 
			(WorldObject.FIELD_TYPE): TestDoor.TYPE,
			"isOpen": "true",
			"isLocked": "false"
		],
		[
			(WorldObject.FIELD_ID): "2-${TestDoor.TYPE}" as String,
			(WorldObject.FIELD_TYPE): TestDoor.TYPE,
			"isOpen": "false",
			"isLocked": "false"
		],
		[
			(WorldObject.FIELD_ID): "3-${TestDoor.TYPE}" as String,
			(WorldObject.FIELD_TYPE): TestDoor.TYPE,
			"isOpen": "false",
			"isLocked": "true"
		]
	];
	
	private static Random random = new Random();
	
	private static WorldState createWorldState() {
		WorldState worldState = new WorldState(null);
		
		GroovyScriptEngineFactory factory = new GroovyScriptEngineFactory();
		GroovyScriptEngineImpl scriptEngine = factory.getScriptEngine();
		scriptEngine.put("registerType", {
			typeClass ->
			def typeId = typeClass.getField("TYPE").get();
			worldState.worldMaker.registerType(
				typeId,
				typeClass
			);
		});
	
		Reader reader = new InputStreamReader(WorldStateTest.class.getResourceAsStream("/test/testobjects.groovy"));
		try {
			scriptEngine.eval(reader);
			worldState.worldMaker.registerTypes(scriptEngine.getClassLoader());
		}
		finally {
			reader.close();
		}
		
		return worldState;
	}
	
	@Test
	public void testAdd() {
		WorldState worldState = createWorldState();
		
		for (localObject in objects) {
			worldState.changeNotificationAdd(localObject);
			
			def worldObject = worldState.getObjects()[localObject[WorldObject.FIELD_ID]];
			def worldObjectProperties = worldObject.getProperties();
			for (entry in localObject) {
				if (entry.key in [ WorldObject.FIELD_ID, WorldObject.FIELD_TYPE ]) {
					continue;
				}
				
				def value = worldObjectProperties[entry.key] as String;
				Assert.assertEquals(entry.value, value);
			}
		}
	}
	
	@Test
	public void testAddNotification() {
		WorldState worldState = createWorldState();
		
		final WorldObject[] notification = [ null ];
		worldState.addAddListener(
			new WorldState.AddListener() {
				@Override
				public void objectAdded(WorldObject object) {
					notification[0] = object;
				}
			}
		);
		
		for (localObject in objects) {
			notification[0] = null;
			worldState.changeNotificationAdd(localObject);
			
			def worldObject = worldState.getObjects()[localObject[WorldObject.FIELD_ID]];
			Assert.assertEquals(worldObject, notification[0]);
		}
	}
	
	@Test
	public void testModify() {
		WorldState worldState = createWorldState();
		
		for (localObject in objects) {
			worldState.changeNotificationAdd(localObject);
		}
		
		for (int i = 0; i < 100; i++) {
			def object = objects[random.nextInt(objects.size())];
			def worldObject = worldState.getObjects()[object.id];
			
			def compatible = objects.findAll { it.type == object.type && it.id != object.id };
			compatible = compatible[random.nextInt(compatible.size())]
			def property = compatible.keySet().findAll { it != WorldObject.FIELD_ID && it != WorldObject.FIELD_TYPE };
			property = property[random.nextInt(property.size())];
			
			worldState.changeNotificationModify([
				(WorldObject.FIELD_ID): object.id,
				(property): compatible[property]
			]);
			
			Assert.assertEquals(compatible[property], worldObject.getProperties()[property] as String);
		}
	}
	
	@Test
	public void testModifyMultiple() {
		WorldState worldState = createWorldState();
		
		for (localObject in objects) {
			worldState.changeNotificationAdd(localObject);
		}
		
		for (int i = 0; i < 100; i++) {
			def object = objects[random.nextInt(objects.size())];
			def worldObject = worldState.getObjects()[object.id];
			
			def compatible = objects.findAll { it.type == object.type && it.id != object.id };
			compatible = compatible[random.nextInt(compatible.size())]
			def properties = compatible.findAll { it.key != WorldObject.FIELD_ID && it.key != WorldObject.FIELD_TYPE };
			def propertyKeys = new ArrayList(properties.keySet());
			Collections.shuffle(propertyKeys);
			propertyKeys = propertyKeys[0..random.nextInt(properties.size())];
			properties = propertyKeys.inject([:]) { map, key -> map << [ (key): properties[key]] };
			properties[WorldObject.FIELD_ID] = object.id;
			
			worldState.changeNotificationModify(properties);
			
			for (entry in properties) {
				if (entry.key in [ WorldObject.FIELD_ID, WorldObject.FIELD_TYPE ]) {
					continue;
				}
				
				Assert.assertEquals(entry.value, worldObject.getProperties()[entry.key] as String);
			}
		}
	}
	
	@Test
	public void testModifyNotification() {
		WorldState worldState = createWorldState();
		
		for (localObject in objects) {
			worldState.changeNotificationAdd(localObject);
		}
		
		for (int i = 0; i < 100; i++) {
			def object = objects[random.nextInt(objects.size())];
			def worldObject = worldState.getObjects()[object.id];
			
			def compatible = objects.findAll { it.type == object.type && it.id != object.id };
			compatible = compatible[random.nextInt(compatible.size())]
			def properties = compatible.findAll { it.key != WorldObject.FIELD_ID && it.key != WorldObject.FIELD_TYPE };
			def propertyKeys = new ArrayList(properties.keySet());
			Collections.shuffle(propertyKeys);
			propertyKeys = propertyKeys[0..random.nextInt(properties.size())];
			properties = propertyKeys.inject([:]) { map, key -> map << [ (key): properties[key]] };
			properties[WorldObject.FIELD_ID] = object.id;
			
			final def notification = [ null, null ];
			worldState.addModifyListener(
				new WorldState.ModifyListener() {
					@Override
					public void objectModified(WorldObject obj, Map<String,Object> oldProperties) {
						notification[0] = obj.getProperties();
						notification[1] = oldProperties;
					}
				}
			);

			def oldProperties = worldObject.getPropertiesWithInternalNames();
			worldState.changeNotificationModify(properties);
			
			Assert.assertEquals(worldObject.getProperties(), notification[0]);
			Assert.assertEquals(oldProperties, notification[1]);
		}
	}
	
	@Test
	public void testDelete() {
		for (int i = 0; i < 30; i++) {
			WorldState worldState = createWorldState();
			for (localObject in objects) {
				worldState.changeNotificationAdd(localObject);
			}
			
			int index;
			if (i < worldState.getObjects().size()) {
				index = i;
			} else {
				random.nextInt(worldState.getObjects().size());
			}
			
			while (!worldState.getObjects().isEmpty()) {
				def objectId = new ArrayList<>(worldState.getObjects().keySet())[index];
				
				int sizeBefore = worldState.getObjects().size();
				worldState.changeNotificationDelete(objectId);
				
				int sizeAfter = worldState.getObjects().size();
				Assert.assertEquals(sizeAfter, sizeBefore-1);
				Assert.assertFalse(worldState.getObjects().keySet().contains(objectId));
				
				if (worldState.getObjects().isEmpty()) {
					break;
				} else {
					index = random.nextInt(worldState.getObjects().size());
				}
			}
		}
	}
	
	@Test
	public void testDeleteNotification() {
		for (int i = 0; i < 30; i++) {
			WorldState worldState = createWorldState();
			
			final WorldObject[] notification = [ null ];
			worldState.addDeleteListener(
				new WorldState.DeleteListener() {
					@Override
					public void objectDeleted(WorldObject object) {
						notification[0] = object;
					}
				}
			);
			
			for (localObject in objects) {
				worldState.changeNotificationAdd(localObject);
			}
			
			int index;
			if (i < worldState.getObjects().size()) {
				index = i;
			} else {
				random.nextInt(worldState.getObjects().size());
			}
			
			while (!worldState.getObjects().isEmpty()) {
				def objectId = new ArrayList<>(worldState.getObjects().keySet())[index];
				
				int sizeBefore = worldState.getObjects().size();
				notification[0] = null;
				worldState.changeNotificationDelete(objectId);
				
				Assert.assertEquals(objectId, notification[0][WorldObject.FIELD_ID]);
				
				int sizeAfter = worldState.getObjects().size();
				Assert.assertEquals(sizeAfter, sizeBefore-1);
				Assert.assertFalse(worldState.getObjects().keySet().contains(objectId));
				
				if (worldState.getObjects().isEmpty()) {
					break;
				} else {
					index = random.nextInt(worldState.getObjects().size());
				}
			}
		}
	}
	
	/*
	@Test
	public void testCreateTypes() {
		List<Map<String,String>> objects = [
			[
				(WorldObject.FIELD_TYPE): Airlock.TYPE,
				(WorldObject.FIELD_ID): "airlock1",
				"isOn": "true",
				"isOpen": "false",
				"name": "airlock",
				"roomId": "room1"
			],
			[
				(WorldObject.FIELD_TYPE): Door.TYPE,
				(WorldObject.FIELD_ID): "door1",
				"isLocked": "true",
				"isOpen": "false",
				"name": "door",
				"roomId": "room1"
			],
			[
				(WorldObject.FIELD_TYPE): Notification.TYPE,
				(WorldObject.FIELD_ID): "notification1",
				"text": "foo bar baz"
			],
			[
				(WorldObject.FIELD_TYPE): Player.TYPE,
				(WorldObject.FIELD_ID): "player1",
				"userId": "some user",
				"roomId": "room1"
			],
			[
				(WorldObject.FIELD_TYPE): Room.TYPE,
				(WorldObject.FIELD_ID): "room1",
				"name": "room",
				"oxygen": "50",
				"stationId": "station1"
			],
			[
				(WorldObject.FIELD_TYPE): Station.TYPE,
				(WorldObject.FIELD_ID): "station1",
				"energy": "10"
			],
			[
				(WorldObject.FIELD_TYPE): Switch.TYPE,
				(WorldObject.FIELD_ID): "switch1",
				"isOn": "true",
				"name": "switch",
				"roomId": "room1"
			]
		];
		
		for (object in objects) {
			WorldState worldState = createWorldState();
			def worldObject = worldState.worldMaker.create(null, object);
			
			Assert.assertNotNull(worldObject);
			
			Assert.assertEquals(
				object[WorldObject.FIELD_TYPE],
				worldObject.getType()
			);
		
			for (property in object) {
				def key = (property.key == WorldObject.FIELD_TYPE) ? "type" : property.key;
				Assert.assertEquals(property.value, worldObject[key] as String);
			}
		}
	}
	*/
}
