import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.action.ModifyObjectAction
import de.uds.lsv.platon.world.WorldClass
import de.uds.lsv.platon.world.WorldField
import de.uds.lsv.platon.world.WorldMethod
import de.uds.lsv.platon.world.WorldObject

@WorldClass("Mipumi.Common.Adventure.AirlockRecord")
class Airlock extends WorldObject {
	public static final String TYPE = "Mipumi.Common.Adventure.AirlockRecord";
	
	@WorldField
	public boolean isOn = false;
	
	@WorldField
	public boolean isOpen = false;
	
	@WorldField
	public String name = "airlock";
	
	@WorldField
	public String roomId = null;
	
	@WorldMethod
	public List<Action> switchOn() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOn": "true" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> switchOff() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOn": "false" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> toggle() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOn": isOn ? "false" : "true" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> open() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOpen": "true" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> close() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOpen": "false" ]
			)
		];
	}
}

@WorldClass("Mipumi.Common.Adventure.DoorRecord")
class Door extends WorldObject {
	public static final String TYPE = "Mipumi.Common.Adventure.DoorRecord";
	
	@WorldField
	public boolean isOpen = false;
	
	@WorldField
	public boolean isLocked = false;
	
	@WorldField
	public String name = "door";
	
	@WorldField
	public String roomId = null;
	
	@WorldMethod
	public List<Action> open() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOpen": "true" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> close() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOpen": "false" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> lock() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isLocked": "true" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> unlock() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isLocked": "false" ]
			)
		];
	}
}

@WorldClass("Mipumi.Common.Adventure.NotificationRecord")
class Notification extends WorldObject {
	public static final String TYPE = "Mipumi.Common.Adventure.NotificationRecord";
	
	@WorldField
	public String text = "";
}

@WorldClass("Mipumi.Common.Adventure.PlayerRecord")
class Player extends WorldObject {
	public static final String TYPE = "Mipumi.Common.Adventure.PlayerRecord";
	
	@WorldField
	public String userId = "nameless user";
	
	@WorldField
	public String roomId = null;
	
	@WorldMethod
	public List<Action> moveTo(String roomId) {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "roomId": roomId ]
			)
		];
	}
}

@WorldClass("Mipumi.Common.Adventure.RoomRecord")
class Room extends WorldObject {
	public static final String TYPE = "Mipumi.Common.Adventure.RoomRecord";
	
	@WorldField
	public String name = "room";
	
	@WorldField
	public int oxygen = 100;
	
	@WorldField
	public String stationId = null;
}

@WorldClass("Mipumi.Common.Adventure.StationRecord")
class Station extends WorldObject {
	public static final String TYPE = "Mipumi.Common.Adventure.StationRecord";
	
	@WorldField
	public int energy = 100;
}

@WorldClass("Mipumi.Common.Adventure.SwitchRecord")
class Switch extends WorldObject {
	public static final String TYPE = "Mipumi.Common.Adventure.SwitchRecord";
	
	@WorldField
	public boolean isOn = false;
	
	@WorldField
	public String name = "switch";
	
	@WorldField
	public String roomId = null;
	
	@WorldMethod
	public List<Action> switchOn() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOn": "true" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> switchOff() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOn": "false" ]
			)
		];
	}
	
	@WorldMethod
	public List<Action> toggle() {
		return [
			new ModifyObjectAction(
				session,
				[ (FIELD_ID): id, "isOn": isOn ? "false" : "true" ]
			)
		];
	}
}
