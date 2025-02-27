/*
 * Copyright (C) 2011-2015 Julian Jarecki (jareckij@informatik.uni-freiburg.de)
 * Copyright (C) 2011-2015 Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * Copyright (C) 2009-2015 University of Freiburg
 *
 * This file is part of the ULTIMATE Automata Library.
 *
 * The ULTIMATE Automata Library is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Automata Library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Automata Library. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Automata Library, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Automata Library grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.automata.petrinet.unfolding;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;

import de.uni_freiburg.informatik.ultimate.util.HashUtils;

/**
 * A condition.
 *
 * @author Julian Jarecki (jareckij@informatik.uni-freiburg.de)
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            symbol type
 * @param <PLACE>
 *            place content type
 **/
public class Condition<LETTER, PLACE> implements Serializable {
	private static final long serialVersionUID = -497620137647502376L;

	// See https://github.com/ultimate-pa/ultimate/pull/595 for discussion
	private static final boolean USE_HASH_JENKINS = false;

	private final Event<LETTER, PLACE> mPredecessor;
	private final Collection<Event<LETTER, PLACE>> mSuccessors;
	private final PLACE mPlace;

	private final int mSerialNumber;
	private final int mHashCode;

	/**
	 * Construct conditions only via {@link BranchingProcess}
	 */
	Condition(final Event<LETTER, PLACE> predecessor, final PLACE place, final int serialNumber) {
		mSerialNumber = serialNumber;

		mPredecessor = predecessor;
		mSuccessors = new HashSet<>();
		mPlace = place;

		if (USE_HASH_JENKINS) {
			// Serial numbers should not be used verbatim for hash codes,
			// because this would cause frequent hash collisions for e.g. sets or lists of conditions.
			mHashCode = HashUtils.hashJenkins(17, mSerialNumber);
		} else {
			mHashCode = mSerialNumber;
		}
	}

	/**
	 * Adds successors of an event.
	 *
	 * @param event
	 *            event
	 */
	public void addSuccesssor(final Event<LETTER, PLACE> event) {
		mSuccessors.add(event);
	}

	public Collection<Event<LETTER, PLACE>> getSuccessorEvents() {
		return mSuccessors;
	}

	public Event<LETTER, PLACE> getPredecessorEvent() {
		return mPredecessor;
	}

	public PLACE getPlace() {
		return mPlace;
	}

	@Override
	public String toString() {
		return "c" + mSerialNumber + ":CorrespPlace: " + mPlace.toString();
	}

	@Override
	public int hashCode() {
		return mHashCode;
	}

	@Override
	public boolean equals(final Object obj) {
		// We intentionally use reference equality here:
		// - An efficient equality check is crucial for unfolding performance.
		// - The unfolding should never create two instances representing "equal" conditions.
		return this == obj;
	}
}
