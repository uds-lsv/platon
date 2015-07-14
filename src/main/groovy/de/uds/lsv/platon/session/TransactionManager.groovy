package de.uds.lsv.platon.session

import groovy.transform.TypeChecked

import java.util.concurrent.atomic.AtomicInteger

import de.uds.lsv.platon.action.Action
import de.uds.lsv.platon.action.EnvironmentModifiedAction
import de.uds.lsv.platon.action.ObjectAddedAction
import de.uds.lsv.platon.action.ObjectDeletedAction
import de.uds.lsv.platon.action.ObjectModifiedAction

@TypeChecked
public class TransactionManager {
	// We could implement Closeable here.
	// However, there might be some debugging tools that complain if
	// we don't use try with resources or a finally to close a 
	// transaction, both of which are a bad idea.
	public static class Transaction {
		private final TransactionManager transactionManager;
		private final int transactionId;
		
		private Transaction(TransactionManager transactionManager, int transactionId) {
			this.transactionManager = transactionManager;
			this.transactionId = transactionId;
		}
		
		public void close() {
			if (!transactionManager.transactions.containsKey(transactionId)) {
				return;
			}
			
			transactionManager.session.submitAndWait({
				List<Action> transaction = transactionManager.transactions[transactionId];
				try {
					transactionManager.session.worldState.batchApply(transaction);
				}
				finally {
					synchronized (transactionManager.transactions) {
						transactionManager.transactions.remove(transactionId);
					}
				}
			});
		}
		
		/**
		 * Add an object creation to a transaction, or submit it
		 * immediately if transactionId < 0.
		 */
		public void addChangeNotificationAdd(Map<String,String> properties) {
			ObjectAddedAction change = new ObjectAddedAction(
				transactionManager.session,
				properties
			);
			transactionManager.addAction(
				transactionId,
				change
			);
		}
		
		/**
		 * Add an object modification to a transaction, or submit it
		 * immediately if transactionId < 0.
		 */
		public void addChangeNotificationModify(Map<String,String> modifications) {
			ObjectModifiedAction change = new ObjectModifiedAction(
				transactionManager.session,
				modifications
			);
			transactionManager.addAction(
				transactionId,
				change
			);
		}
		
		/**
		 * Add an object deletion to a transaction, or submit it
		 * immediately if transactionId < 0.
		 */
		public void addChangeNotificationDelete(String id) {
			ObjectDeletedAction change = new ObjectDeletedAction(
				transactionManager.session,
				id
			);
			transactionManager.addAction(
				transactionId,
				change
			);
		}
		
		/**
		 * Add an environment modification to a transaction, or submit it
		 * immediately if transactionId < 0.
		 */
		public void addEnvironmentModification(String key, String value) {
			EnvironmentModifiedAction change = new EnvironmentModifiedAction(
				transactionManager.session,
				key,
				value
			);
			transactionManager.addAction(
				transactionId,
				change
			);
		}
	}
	
	private final DialogSession session;
	private final AtomicInteger lastId = new AtomicInteger(0);
	private final Map<Integer,List<Action>> transactions = new HashMap<>();
	
	public TransactionManager(DialogSession session) {
		this.session = session;
	}
	
	public Transaction createTransaction() {
		return new Transaction(this, beginTransaction());
	}
	
	public int beginTransaction() {
		int transactionId = lastId.incrementAndGet();
		transactions.put(transactionId, new ArrayList<Action>());
		return transactionId;
	}
	
	public void endTransaction(int transactionId) {
		if (!transactions.containsKey(transactionId)) {
			throw new IllegalArgumentException("Transaction with identifier ${transactionId} does not exist or was already closed.");
		}
		
		session.submitAndWait({
			List<Action> transaction = transactions[transactionId];
			try {
				session.worldState.batchApply(transaction);
			}
			finally {
				transactions.remove(transactionId);
			}
		})
	}
	
	/**
	 * Add an object creation to a transaction, or submit it
	 * immediately if transactionId < 0.
	 */
	public void addChangeNotificationAdd(int transactionId, Map<String,String> properties) {
		ObjectAddedAction change = new ObjectAddedAction(session, properties);
		if (transactionId < 0) {
			session.submit({ session.worldState.apply(change) });
		} else {
			addAction(
				transactionId,
				change
			);
		}
	}
	
	/**
	 * Add an object modification to a transaction, or submit it
	 * immediately if transactionId < 0.
	 */
	public void addChangeNotificationModify(int transactionId, Map<String,String> modifications) {
		ObjectModifiedAction change = new ObjectModifiedAction(session, modifications);
		if (transactionId < 0) {
			session.submit({ session.worldState.apply(change) });
		} else {
			addAction(
				transactionId,
				change
			);
		}
	}
	
	/**
	 * Add an object deletion to a transaction, or submit it
	 * immediately if transactionId < 0.
	 */
	public void addChangeNotificationDelete(int transactionId, String id) {
		ObjectDeletedAction change = new ObjectDeletedAction(session, id);
		if (transactionId < 0) {
			session.submit({ session.worldState.apply(change) });
		} else {
			addAction(
				transactionId,
				change
			);
		}
	}
	
	/**
	 * Add an environment modification to a transaction, or submit it
	 * immediately if transactionId < 0.
	 */
	public void addEnvironmentModification(int transactionId, String key, String value) {
		EnvironmentModifiedAction change = new EnvironmentModifiedAction(session, key, value);
		if (transactionId < 0) {
			session.submit({ session.worldState.apply(change) });
		} else {
			addAction(
				transactionId,
				change
			);
		}
	}
	
	private synchronized void addAction(int transactionId, Action action) {
		if (!transactions.containsKey(transactionId)) {
			throw new IllegalArgumentException("Transaction with identifier ${transactionId} does not exist or was already closed.");
		}
		
		transactions[transactionId].add(action);
	}
}
