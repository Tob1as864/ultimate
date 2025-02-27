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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.automata.petrinet.IPetriNet;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.Marking;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.PetriNetNot1SafeException;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.netdatastructures.Transition;
import de.uni_freiburg.informatik.ultimate.util.HashUtils;
import de.uni_freiburg.informatik.ultimate.util.datastructures.ImmutableSet;

/**
 * Event of a {@link BranchingProcess}. Each event corresponds to a {@link Transition} of a {@link IPetriNet}.
 *
 * @author Julian Jarecki (jareckij@informatik.uni-freiburg.de)
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            symbol type
 * @param <PLACE>
 *            place content type
 */
public final class Event<LETTER, PLACE> implements Serializable {
	private static final long serialVersionUID = 7162664880110047121L;

	// See https://github.com/ultimate-pa/ultimate/pull/595 for discussion
	private static final boolean USE_HASH_JENKINS = false;
	private static final int HASH_PRIME = 89;

	/**
	 * Use the optimization that is outlined in observation B17 in the following issue.
	 * https://github.com/ultimate-pa/ultimate/issues/448 Omit order check in cut-off check.
	 */
	private static final boolean BUMBLEBEE_B17_OPTIMIZAION = true;
	private int mSerialNumber = -1;
	private final int mHashCode;

	private final Set<Condition<LETTER, PLACE>> mPredecessors;
	private final Set<Condition<LETTER, PLACE>> mSuccessors;
	private final Configuration<LETTER, PLACE> mLocalConfiguration;
	private final Marking<PLACE> mMark;
	private final ConditionMarking<LETTER, PLACE> mConditionMark;

	private Event<LETTER, PLACE> mCompanion;
	private final Transition<LETTER, PLACE> mTransition;
	private final Map<PLACE, Set<PLACE>> mPlaceCorelationMap;
	private int mDepth;

	/**
	 * Creates an Event from its predecessor conditions and the transition from the net system it is mapped to by the
	 * homomorphism. Its successor conditions are automatically created. The given set not be referenced directly, but
	 * copied.
	 *
	 * @param predecessors
	 *            predecessor conditions
	 * @param transition
	 *            homomorphism transition
	 */
	// TODO Frank 2022-08-23: Providing the hashCode in the constructor does not seem like a good idea...
	public Event(final Collection<Condition<LETTER, PLACE>> predecessors, final Transition<LETTER, PLACE> transition,
			final BranchingProcess<LETTER, PLACE> bp, final int hashCode) throws PetriNetNot1SafeException {
		assert conditionToPlaceEqual(predecessors,
				transition.getPredecessors()) : "An event was created with inappropriate predecessors.\n  "
						+ "transition: " + transition.toString() + "\n  events predecessors: " + predecessors.toString()
						+ "\n  " + "transitions predecessors:" + transition.getPredecessors();
		mPredecessors = new HashSet<>(predecessors);

		mTransition = transition;
		mSuccessors = transition.getSuccessors().stream().map(p -> bp.constructCondition(this, p))
				.collect(Collectors.toSet());

		if (USE_HASH_JENKINS) {
			mHashCode = HashUtils.hashJenkins(HASH_PRIME, hashCode);
		} else {
			mHashCode = hashCode;
		}

		final Set<Condition<LETTER, PLACE>> conditionMarkSet = new HashSet<>();
		mDepth = 0;
		final Set<Event<LETTER, PLACE>> predecessorEvents =
				predecessors.stream().map(c -> c.getPredecessorEvent()).collect(Collectors.toSet());
		final Set<Event<LETTER, PLACE>> localConfigurationsEvents = new HashSet<>();
		for (final Event<LETTER, PLACE> predEvent : predecessorEvents) {
			for (final Event<LETTER, PLACE> e : predEvent.mLocalConfiguration) {
				localConfigurationsEvents.add(e);
			}
			predEvent.mConditionMark.addTo(conditionMarkSet);
			mDepth = Math.max(mDepth, predEvent.getDepth());
		}
		mDepth++;
		localConfigurationsEvents.add(this);
		mLocalConfiguration = new Configuration<>(localConfigurationsEvents, mDepth);
		for (final Event<LETTER, PLACE> a : mLocalConfiguration) {
			conditionMarkSet.removeAll(a.getPredecessorConditions());
		}
		conditionMarkSet.addAll(mSuccessors);
		mConditionMark = new ConditionMarking<>(conditionMarkSet);
		mMark = mConditionMark.getMarking();
		mPlaceCorelationMap = new HashMap<>();
		if (bp.getNewFiniteComprehensivePrefixMode()) {
			computePlaceCorelationMap(bp);
		}
	}

	@Deprecated
	public void setSerialNumber(final int serialNumber) {
		mSerialNumber = serialNumber;
	}

	public int getDepth() {
		return mDepth;
	}

	/**
	 * Creates a dummy event. Used as the root of a {@link BranchingProcess}.
	 *
	 * @param net
	 *            Petri net
	 */
	public Event(final BranchingProcess<LETTER, PLACE> bp) {
		mTransition = null;
		mLocalConfiguration = new Configuration<>(new HashSet<Event<LETTER, PLACE>>(), 0);
		mMark = new Marking<>(ImmutableSet.of(bp.getNet().getInitialPlaces()));

		final Set<Condition<LETTER, PLACE>> conditionMarkSet = new HashSet<>();
		mConditionMark = new ConditionMarking<>(conditionMarkSet);
		mPredecessors = new HashSet<>();
		mSuccessors = mMark.stream().map(p -> bp.constructCondition(this, p)).collect(Collectors.toSet());
		conditionMarkSet.addAll(mSuccessors);
		mHashCode = HashUtils.hashJenkins(HASH_PRIME, 0);
		mPlaceCorelationMap = new HashMap<>();
		if (bp.getNewFiniteComprehensivePrefixMode()) {
			computePlaceCorelationMap(bp);
		}
		mDepth = 0;
	}

	/**
	 * @return The Set of all successor events of all successor conditions of the event.
	 */
	public Set<Event<LETTER, PLACE>> getSuccessorEvents() {
		final HashSet<Event<LETTER, PLACE>> result = new HashSet<>();
		for (final Condition<LETTER, PLACE> c : getSuccessorConditions()) {
			result.addAll(c.getSuccessorEvents());
		}
		return result;
	}

	/**
	 * @return The Set of all predecessor events of all predecessor conditions of the event.
	 */
	public Set<Event<LETTER, PLACE>> getPredecessorEvents() {
		final HashSet<Event<LETTER, PLACE>> result = new HashSet<>();
		for (final Condition<LETTER, PLACE> c : getPredecessorConditions()) {
			result.add(c.getPredecessorEvent());
		}
		return result;
	}

	/**
	 * returns true, if the homomorphism h of the corresponding branching process reduced to conditions and places is a
	 * well defined isomorphism. this is a helper method used only for assertions.
	 */
	private boolean conditionToPlaceEqual(final Collection<Condition<LETTER, PLACE>> conditions,
			final Collection<PLACE> placesIn) {
		final Collection<PLACE> places = new HashSet<>(placesIn);
		for (final Condition<LETTER, PLACE> c : conditions) {
			if (!places.remove(c.getPlace())) {
				return false;
			}
		}
		return places.isEmpty();
	}

	public ConditionMarking<LETTER, PLACE> getConditionMark() {
		return mConditionMark;
	}

	public Set<Condition<LETTER, PLACE>> getSuccessorConditions() {
		return mSuccessors;
	}

	public Set<Condition<LETTER, PLACE>> getPredecessorConditions() {
		return mPredecessors;
	}

	public Map<PLACE, Set<PLACE>> getPlaceCorelationMap() {
		return mPlaceCorelationMap;
	}

	/**
	 * @return marking of the local configuration of this.
	 */
	public Marking<PLACE> getMark() {
		return mMark;
	}

	/**
	 * @param event
	 *            event
	 * @param order
	 *            order
	 * @param sameTransitionCutOff
	 *            If true, additionally we require
	 *            <ul>
	 *            <li>e and e' belong to the same transition</li>
	 *            </ul>
	 *            which will produce fewer cut-off events and a bigger prefix hence. However, we assume the blowup is
	 *            not so big TODO: check this claim. (maybe linear? with respect to what?)
	 * @return If the event is a companion of this
	 *         <ul>
	 *         <li>return true and calls {@link Event#setCompanion(Event)}.</li>
	 *         <li>otherwise return false.</li>
	 *         </ul>
	 *         <b>Definition:</b> e is a companion of e' iff
	 *         <ul>
	 *         <li>e < e' with respect to order</li>
	 *         <li>Mark([e]) == Mark([e'])</li>
	 *         </ul>
	 *         So far this definition corresponds to cut-off events as defined in the 1996 TACAS Paper.
	 */
	public boolean checkCutOffAndSetCompanion(final Event<LETTER, PLACE> event,
			final Comparator<Event<LETTER, PLACE>> order, final boolean sameTransitionCutOff) {
		// additional requirement for cut-off events.
		// TODO: tests to compare prefix sizes.
		if (sameTransitionCutOff && !getTransition().equals(event.getTransition())) {
			return false;
		}
		if (!getMark().equals(event.getMark())) {
			return false;
		}
		if (!BUMBLEBEE_B17_OPTIMIZAION) {
			if (order.compare(event, this) >= 0) {
				return false;
			}
		}
		setCompanion(event);
		return true;
	}

	/**
	 * #Backfolding
	 */
	public boolean checkCutOffAndSetCompanionForComprehensivePrefix(final Event<LETTER, PLACE> companionCandidate,
			final Comparator<Event<LETTER, PLACE>> order, final boolean sameTransitionCutOff) {
		// by comparing the hashmaps we check simultaneously if they have the same marking (set of keys of the map)
		if (sameTransitionCutOff && !getTransition().equals(companionCandidate.getTransition())) {
			return false;
		}

		if (order.compare(companionCandidate, this) >= 0) {
			return false;
		}

		if (!companionCandidate.getPlaceCorelationMap().equals(getPlaceCorelationMap())) {
			return false;
		}

		setCompanion(companionCandidate);
		return true;
	}

	/**
	 * #Backfolding
	 * <p>
	 * Map m such that for each {@link Condition} c in the local configuration of this {@link Event} the map contains
	 * the pair (c.getPlace(),bp.getCoRelatedPlaces(c)). </ p> TODO Find a nice name for this map or find a view that is
	 * easy to understand
	 */
	public void computePlaceCorelationMap(final BranchingProcess<LETTER, PLACE> bp) {
		for (final Condition<LETTER, PLACE> c : getConditionMark()) {
			mPlaceCorelationMap.put(c.getPlace(), bp.computeCoRelatedPlaces(c));
		}
	}

	/**
	 * set this.companion to e, or, if e is a cut-off event itself to the companion of e.
	 */
	public void setCompanion(final Event<LETTER, PLACE> event) {
		assert mCompanion == null;
		if (event.getCompanion() == null) {
			mCompanion = event;
		} else {
			setCompanion(event.getCompanion());
		}
	}

	/**
	 * @return {@code true} iff the event is a cutoff event. requires, that
	 *         {@link #checkCutOffSetCompanion(Event, Comparator)} was called.
	 *         <p>
	 *         In the implementation of the construction of the unfolding, it is called after being added to a the
	 *         Branchinprocess.
	 * @see BranchingProcess#isCutoffEvent(Event, Comparator)
	 */
	public boolean isCutoffEvent() {
		return mCompanion != null;
	}

	/**
	 * @return The size of the local configuration, that is the number of ancestor events.
	 */
	public int getAncestors() {
		return mLocalConfiguration.size();
	}

	public Configuration<LETTER, PLACE> getLocalConfiguration() {
		return mLocalConfiguration;
	}

	public boolean conditionMarkContains(final Condition<LETTER, PLACE> c) {
		return mConditionMark.contains(c);
	}

	public Event<LETTER, PLACE> getCompanion() {
		return mCompanion;
	}

	public Transition<LETTER, PLACE> getTransition() {
		return mTransition;
	}

	public int getSerialNumber() {
		return mSerialNumber;
	}

	public int getTotalOrderId() {
		return mTransition.getTotalOrderId();
	}

	@Override
	public String toString() {
		if (mSerialNumber == 0) {
			return "Dummy event whose successors are the initial conditions of the branching process";
		} else {
			return mSerialNumber + ":" + +mLocalConfiguration.size() + "A:" + getTransition().toString();
		}
	}

	@Override
	public int hashCode() {
		return mHashCode;
	}

	@Override
	public boolean equals(final Object obj) {
		// We intentionally use reference equality here:
		// - An efficient equality check is crucial for unfolding performance; comparing sets of conditions is too slow.
		// - The unfolding should never create two instances representing "equal" events.
		return this == obj;
	}
}
