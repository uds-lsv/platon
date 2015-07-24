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

Integer.metaClass.millisecond << {
	closure ->
	if (delegate != 1) {
		logger.error(String.format('Grammar exception! Plural expected in »%d.millisecond«! ;)', delegate));
	}
	return new de.uds.lsv.platon.script.TimeDurationWithClosure(0, 0, 0, delegate, closure);
};
Integer.metaClass.getMillisecond << {
	if (delegate != 1) {
		logger.error(String.format('Grammar exception! Plural expected in »%d.millisecond«! ;)', delegate));
	}
	return new groovy.time.TimeDuration(0, 0, 0, delegate);
};

Integer.metaClass.second << {
	closure ->
	if (delegate != 1) {
		logger.error(String.format('Grammar exception! Plural expected in »%d.second«! ;)', delegate));
	}
	return new de.uds.lsv.platon.script.TimeDurationWithClosure(0, 0, delegate, 0, closure);
};
Integer.metaClass.getSecond << {
	if (delegate != 1) {
		logger.error(String.format('Grammar exception! Plural expected in »%d.second«! ;)', delegate));
	}
	return new groovy.time.TimeDuration(0, 0, delegate, 0);
};

Integer.metaClass.minute << {
	closure ->
	if (delegate != 1) {
		logger.error(String.format('Grammar exception! Plural expected in »%d.minute«! ;)', delegate));
	}
	return new de.uds.lsv.platon.script.TimeDurationWithClosure(0, delegate, 0, 0, closure);
};
Integer.metaClass.getMinute << {
	if (delegate != 1) {
		logger.error(String.format('Grammar exception! Plural expected in »%d.minute«! ;)', delegate));
	}
	return new groovy.time.TimeDuration(0, delegate, 0, 0);
};

Integer.metaClass.hour << {
	closure ->
	return new de.uds.lsv.platon.script.TimeDurationWithClosure(delegate, 0, 0, 0, closure);
};
Integer.metaClass.getHour << {
	return new groovy.time.TimeDuration(delegate, 0, 0, 0);
};



Integer.metaClass.milliseconds << {
	closure ->
	return new de.uds.lsv.platon.script.TimeDurationWithClosure(0, 0, 0, delegate, closure);
};
Integer.metaClass.getMilliseconds << {
	return new groovy.time.TimeDuration(0, 0, 0, delegate);
};

Integer.metaClass.seconds << {
	closure ->
	return new de.uds.lsv.platon.script.TimeDurationWithClosure(0, 0, delegate, 0, closure);
};
Integer.metaClass.getSeconds << {
	return new groovy.time.TimeDuration(0, 0, delegate, 0);
};

Integer.metaClass.minutes << {
	closure ->
	return new de.uds.lsv.platon.script.TimeDurationWithClosure(0, delegate, 0, 0, closure);
};
Integer.metaClass.getMinutes << {
	return new groovy.time.TimeDuration(0, delegate, 0, 0);
};

Integer.metaClass.hours << {
	closure ->
	return new de.uds.lsv.platon.script.TimeDurationWithClosure(delegate, 0, 0, 0, closure);
};
Integer.metaClass.getHours << {
	return new groovy.time.TimeDuration(delegate, 0, 0, 0);
};
