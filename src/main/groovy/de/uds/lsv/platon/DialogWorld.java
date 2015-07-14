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

package de.uds.lsv.platon;

import java.io.Closeable;
import java.util.Map;

import de.uds.lsv.platon.exception.DialogWorldException;
import de.uds.lsv.platon.session.DialogEngine;
import de.uds.lsv.platon.session.DialogSession;

public interface DialogWorld extends Closeable {
	/**
	 * Called to associate a WorldServer instance with a DialogSession
	 * and to perform initialization tasks (e.g. pushing initial world
	 * state).
	 * 
	 * @param dialogSession
	 */
	void init(DialogSession dialogSession);
	
	/**
	 * Start a transaction of object change requests.
	 * Either all of them succeed atomically, or none of them must be
	 * performed.
	 *
	 * @return
	 *   -1 if transactions are not supported, or
	 *   a strictly positive unique identifier for the transaction that
	 *   can be used in the change request methods. A transaction
	 *   identifier can be used only in the session for which it was
	 *   returned.
	 */
	int beginTransaction();
	
	/**
	 * End the specified transaction and attempt to perform the queued
	 * requests. 
	 *
	 * @param transaction
	 *   The transaction identifier (returned by beginTransaction).
	 *   Non-positive values shall be ignored.
	 */
	void endTransaction(int transactionId) throws DialogWorldException;
	
	/**
	 * DE requests from GS to modify an object.
	 * If the request contains acceptable and inacceptable attribute
	 * changes, the entire set of modifications in {@code obj}
	 * shall be rejected.
	 * After successful modifications a
	 * {@code changeNotificationModify} call reflecting the
	 * modifications must be made.
	 *
	 * @param transaction
	 *   A transaction identifier returned by beginTransaction,
	 *   or -1 to indicate that the request is not part of a larger
	 *   transaction.
	 * @param obj
	 *   A map representing the object. It should only include the object id
	 *   and attributes to be modified.
	 * @see DialogEngine.Iface#changeNotificationModify(int,Map)
	 */
	void changeRequestModify(
		int transactionId,
		Map<String,String> obj
	) throws DialogWorldException;
	
	/**
	 * DE requests from GS to delete an object.
	 * Deleting an object may involve additional game world actions,
	 * possibly over a longer period of time. This must be indicated
	 * in the return value.
	 * 
	 * As for {@code changeRequestModify}, a
	 * {@code changeNotificationDelete} call reflecting
	 * a successful deletion must be made.
	 * 
	 * @param transaction
	 *   A transaction identifier returned by beginTransaction,
	 *   or -1 to indicate that the request is not part of a larger
	 *   transaction.
	 * @param objectId
	 *   The identifier of the object to be deleted.
	 * @return
	 *   see {@code changeRequestModify}.
	 * @see #changeRequestModify(int,Map)
	 * @see DialogEngine.Iface#changeNotificationDelete(int,String)
	 */
	void changeRequestDelete(
		int transactionId,
		String objectId
	) throws DialogWorldException;
}
