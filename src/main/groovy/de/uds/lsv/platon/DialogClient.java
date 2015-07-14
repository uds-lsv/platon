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

import de.uds.lsv.platon.action.IOType;
import de.uds.lsv.platon.session.User;

public interface DialogClient extends Closeable {
		/**
		 * Starts the output of {@code text}.
		 * 
		 * @param user
		 *   The identifier of the user to be addressed.
		 *   Set to 0 to address all users in the session.
		 * @param text
		 *   The text to be output.
		 *   May contain additional markup depending on the output
		 *   type! (e.g. emotional speech or bold text; TODO: to be
		 *   specified in detail).
		 * @param details
		 *   A map that may contain additional information like
		 *   output types.
		 * @return
		 *   An identifier to refer to the output process later.
		 *   Must be unique within the session.
		 */
		int outputStart(User user, IOType ioType, String text, Map<String,Object> details);
		
		/**
		 * Aborts the output process with id {@code id}.
		 * Must be followed by an {@code outputEnded} notification.
		 * If the output is already complete, this shall be indicated in
		 * the {@code outputEnded} notification.
		 *
		 * @param id
		 *   The identifier of the output process returned by
		 *   {@code outputStart}.
		 * @param user
		 *   For outputs directed at multiple users (see
		 *   {@code outputStart}): abort the output process only for the
		 *   specified user. Set to null for "all users".
		 */
		void outputAbort(int outputId, User user);
}
