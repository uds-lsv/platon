package de.uds.lsv.platon.world;

import org.apache.commons.logging.Log
import org.apache.commons.logging.LogFactory

import de.uds.lsv.platon.world.WorldState.ModifyListener

/**
 * Unlike a normal listener, allows you to subscribe to a specific
 * state or a specific state transition in the properties of an
 * object.
 */
class SubscriptionManager implements ModifyListener {
	private static final Log logger = LogFactory.getLog(SubscriptionManager.class.getName());
	
	public class Subscription {
		private final Closure fromFilter;
		private final Closure toFilter;
		private final Closure action;
		
		private Subscription(Closure fromFilter, Closure toFilter, Closure action) {
			this.fromFilter = fromFilter;
			this.toFilter = toFilter;
			this.action = action;
		}
		
		public void cancel() {
			cancelSubscription(this);
		}
	}
	
	private final WorldState worldState;
	private final Map<Closure,Map<Closure,List<Closure>>> subscriptions = new LinkedHashMap<>();
	
	public SubscriptionManager(WorldState worldState) {
		this.worldState = worldState;
		worldState.addModifyListener(this);
	}
	
	public Subscription subscribe(Closure filter, Closure action) {
		logger.debug(String.format(
			"Adding world state subscription %s => %s",
			filter.toString(), action.toString()
		));
		return subscribe(null, filter, action);
	}
	
	public synchronized Subscription subscribe(Closure fromFilter, Closure toFilter, Closure action) {
		logger.debug(String.format(
			"Adding world state subscription %s -> %s => %s",
			fromFilter.toString(), toFilter.toString(),
			action.toString()
		));
	
		if (!subscriptions.containsKey(fromFilter)) {
			subscriptions.put(fromFilter, new LinkedHashMap<>());
		}
		def inner = subscriptions[fromFilter];
		if (!inner.containsKey(toFilter)) {
			inner[toFilter] = new ArrayList<>();
		}
		
		inner[toFilter].add(action);
		
		return new Subscription(fromFilter, toFilter, action);
	}
	
	public synchronized void cancelSubscription(Subscription subscription) {
		logger.debug(String.format(
			"Cancelling world state subscription %s->%s => %s",
			subscription.fromFilter.toString(),
			subscription.toFilter.toString(),
			subscription.action.toString()
		));
	
		List<Closure> actions = subscriptions.get(subscription.fromFilter)?.get(subscription.toFilter);
		if (actions == null) {
			return;
		}
		
		actions.remove(subscription.action);
		if (actions.isEmpty()) {
			subscriptions[subscription.fromFilter].remove(subscription.toFilter);
		}
		
		if (subscriptions[subscription.fromFilter].isEmpty()) {
			subscriptions.remove(subscription.fromFilter);
		}
	}

	@Override
	public void objectModified(WorldObject object, Map<String,Object> oldProperties) {
		Map<String,Object> newProperties = object.getProperties();
		
		logger.debug("World object modified: " + newProperties);
		
		def subscriptionsCopy = new LinkedHashMap<>(subscriptions);
		for (fromFilter in subscriptionsCopy.keySet()) {
			if (fromFilter == null || fromFilter(oldProperties)) {
				if (fromFilter != null) {
					logger.debug("fromFilter matches: " + fromFilter);
				}
				
				def subscriptionsFrom = new LinkedHashMap<>(subscriptionsCopy[fromFilter]);
				for (toFilter in subscriptionsFrom.keySet()) {
					if (toFilter(newProperties)) {
						logger.debug(String.format(
							"Filter matches: %s->%s",
							fromFilter.toString(),
							toFilter.toString()
						));
						
						def subscriptionsFromTo = new ArrayList<>(subscriptionsFrom[toFilter]); 
						for (action in subscriptionsFromTo) {
							action(object);
						}
					}
				}
			}
		}
	}
}
