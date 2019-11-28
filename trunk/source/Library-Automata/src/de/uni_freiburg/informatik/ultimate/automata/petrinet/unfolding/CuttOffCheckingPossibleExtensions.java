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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import de.uni_freiburg.informatik.ultimate.automata.petrinet.IPetriNet;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.ITransition;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.Marking;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.PetriNetNot1SafeException;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.netdatastructures.ISuccessorTransitionProvider;
import de.uni_freiburg.informatik.ultimate.automata.petrinet.netdatastructures.SimpleSuccessorTransitionProvider;
import de.uni_freiburg.informatik.ultimate.util.datastructures.DataStructureUtils;
import de.uni_freiburg.informatik.ultimate.util.datastructures.relation.HashRelation;

/**
 * Implementation of a possible extension.
 *
 * @author Julian Jarecki (jareckij@informatik.uni-freiburg.de)
 * @author Matthias Heizmann (heizmann@informatik.uni-freiburg.de)
 * @param <LETTER>
 *            symbol type
 * @param <PLACE>
 *            place content type
 */
public class CuttOffCheckingPossibleExtensions<LETTER, PLACE> implements IPossibleExtensions<LETTER, PLACE> {

	/**
	 * Use the optimization that is outlined in observation B07 in the following
	 * issue. https://github.com/ultimate-pa/ultimate/issues/448
	 */
	private static final boolean BUMBLEBEE_B07_OPTIMIZAION = true;
	private final PriorityQueue<Event<LETTER, PLACE>> mPePq;
	private final TreeSet<Event<LETTER, PLACE>> mPeTreeSet;
	private int mMaximalSize = 0;
	private static final boolean USE_PQ = true;
	/**
	 * If {@link Event} is known to be cut-off event we can move it immediately
	 * to front because it will not create descendants. This optimization keeps
	 * the queue smaller. TODO 2019-10-16 Matthias: Mehdi found out that this
	 * ArrayDeque is currently unused because the cut-off detection is only done
	 * later. We could to an additional cut-off check earlier but we have doubts
	 * that this will pay off.
	 */
	private final ArrayDeque<Event<LETTER, PLACE>> mFastpathCutoffEventList;
	private final BranchingProcess<LETTER, PLACE> mBranchingProcess;
	private final boolean mLazySuccessorComputation = true;

	/**
	 * A candidate is useful if it lead to at least one new possible extension.
	 */
	private int mUsefulExtensionCandidates = 0;
	private int mUselessExtensionCandidates = 0;
	private final Map<Marking<LETTER, PLACE>, Event<LETTER, PLACE>> mMarkingEventMap = new HashMap<>();
	Comparator<Event<LETTER, PLACE>> mOrder;

	public CuttOffCheckingPossibleExtensions(final BranchingProcess<LETTER, PLACE> branchingProcess, final Comparator<Event<LETTER, PLACE>> order) {
		mBranchingProcess = branchingProcess;
		mPePq = new PriorityQueue<>(order);;
		mPeTreeSet = new TreeSet<>(order);
		mFastpathCutoffEventList = new ArrayDeque<>();
		mOrder = order;
		mMarkingEventMap.put(mBranchingProcess.getDummyRoot().getMark(), mBranchingProcess.getDummyRoot());
	}

	@Override
	public Event<LETTER, PLACE> remove() {
		if (mFastpathCutoffEventList.isEmpty()) {
			if (USE_PQ) {
				return mPePq.remove();
			} else {
				return mPeTreeSet.pollFirst();
			}
		} else {
			return mFastpathCutoffEventList.removeFirst();
		}
	}

	@Override
	public void update(final Event<LETTER, PLACE> event) throws PetriNetNot1SafeException {
		final Collection<Candidate<LETTER, PLACE>> candidates = computeCandidates(event);
		for (final Candidate<LETTER, PLACE> candidate : candidates) {
			if (candidate.getInstantiated().isEmpty()) {
				throw new AssertionError("at least one place has to be instantiated");
			}
			final int possibleExtensionsBefore = size();
			evolveCandidate(candidate);
			if (size() > possibleExtensionsBefore) {
				mUsefulExtensionCandidates++;
			} else {
				mUselessExtensionCandidates++;
			}
		}
	}

	private boolean firstbornCutoffCheck(Event<LETTER, PLACE> newEvent) {
		Event<LETTER, PLACE> eventWithSameMarking = mMarkingEventMap.get(newEvent.getMark());

		if (eventWithSameMarking == null) {
			return false;
		}

		if (mOrder.compare(newEvent, eventWithSameMarking) > 0) {
			newEvent.setCompanion(eventWithSameMarking);
			return true;
		} else {
			boolean eventWithSameMarkingWasInTheMainQueu;
			if (USE_PQ) {
				eventWithSameMarkingWasInTheMainQueu = mPePq.remove(eventWithSameMarking);
			} else {
				eventWithSameMarkingWasInTheMainQueu = mPePq.remove(eventWithSameMarking);
			}
			assert(eventWithSameMarkingWasInTheMainQueu);
			mFastpathCutoffEventList.add(eventWithSameMarking);
			eventWithSameMarking.setCompanion(newEvent);
			return false;

		}
	}
	/**
	 * Evolves a {@code Candidate} for a new possible Event in all possible ways and, as a side-effect, adds valid
	 * extensions (ones whose predecessors are a co-set) to he possible extension set.
	 */
	@SuppressWarnings("squid:S1698")
	private void evolveCandidate(final Candidate<LETTER, PLACE> cand) throws PetriNetNot1SafeException {
		if (cand.isFullyInstantiated()) {
			for (final ITransition<LETTER, PLACE> trans : cand.getTransition().getTransitions()) {
				final Event<LETTER, PLACE> newEvent = new Event<>(cand.getInstantiated(), trans, mBranchingProcess);
				if (firstbornCutoffCheck(newEvent)) {
					mFastpathCutoffEventList.add(newEvent);
				} else {
					mMarkingEventMap.put(newEvent.getMark(), newEvent);
					final boolean somethingWasAdded;
					if (USE_PQ) {
						somethingWasAdded = mPePq.add(newEvent);
					} else {
						somethingWasAdded = mPeTreeSet.add(newEvent);
					}
					mMaximalSize = Integer.max(mMaximalSize, mPePq.size());
					if (!somethingWasAdded) {
						throw new AssertionError("Event was already in queue.");
					}
				}
			}
			return;
		}
		final PLACE nextUninstantiated = cand.getNextUninstantiatedPlace();
		if (BUMBLEBEE_B07_OPTIMIZAION) {
			final List<Condition<LETTER, PLACE>> yetInstantiated = cand.getInstantiated();
			// list that contains one set for each instantiated condition c
			// the set contains all conditions that are in co-relation to c and
			// whose place is 'nextUninstantiated'
			final List<Set<Condition<LETTER, PLACE>>> coRelatedWithInstantiated = new ArrayList<>();
			for (final Condition<LETTER, PLACE> instantiated : yetInstantiated) {
				final Set<Condition<LETTER, PLACE>> coRelatedToInstantiated = mBranchingProcess.getCoRelation()
						.computeCoRelatatedConditions(instantiated, nextUninstantiated);
				// 2019-10-18 Matthias Optimization: Use construction cache
				// because while backtracking same set is computed several times
				coRelatedWithInstantiated.add(coRelatedToInstantiated);
			}
			final Set<Condition<LETTER, PLACE>> inCoRelationWithAllInstantiated = DataStructureUtils
					.intersection(coRelatedWithInstantiated);
			for (final Condition<LETTER, PLACE> c : inCoRelationWithAllInstantiated) {
				assert cand.getTransition().getPredecessorPlaces().contains(c.getPlace());
				// equality intended here
				assert c.getPlace().equals(nextUninstantiated);
				assert !cand.getInstantiated().contains(c);
				if (!c.getPredecessorEvent().isCutoffEvent()) {
					cand.instantiateNext(c);
					evolveCandidate(cand);
					cand.undoOneInstantiation();
				}
			}
		} else {
			for (final Condition<LETTER, PLACE> c : mBranchingProcess.place2cond(nextUninstantiated)) {
				assert cand.getTransition().getPredecessorPlaces().contains(c.getPlace());
				// equality intended here
				assert c.getPlace().equals(nextUninstantiated);
				assert !cand.getInstantiated().contains(c);
				if (!c.getPredecessorEvent().isCutoffEvent()) {
					if (mBranchingProcess.getCoRelation().isCoset(cand.getInstantiated(), c)) {
						cand.instantiateNext(c);
						evolveCandidate(cand);
						cand.undoOneInstantiation();
					}
				}
			}
		}
	}



	/**
	 * @return All {@code Candidate}s for possible extensions that are successors of the {@code Event}.
	 */
	private Collection<Candidate<LETTER, PLACE>> computeCandidates(final Event<LETTER, PLACE> event) {
		if (mLazySuccessorComputation) {
			final Set<Condition<LETTER, PLACE>> conditions = event.getSuccessorConditions();
			final Set<PLACE> correspondingPlaces = conditions.stream().map(Condition::getPlace).collect(Collectors.toSet());
			final Collection<ISuccessorTransitionProvider<LETTER, PLACE>> successorTransitionProviders = mBranchingProcess
					.getNet().getSuccessorTransitionProviders(correspondingPlaces);
			final List<Candidate<LETTER, PLACE>> candidates = successorTransitionProviders.stream()
					.map(x -> new Candidate<LETTER, PLACE>(x, conditions)).collect(Collectors.toList());
			return candidates;
		} else {
			if (!(mBranchingProcess.getNet() instanceof IPetriNet)) {
				throw new IllegalArgumentException("non-lazy computation only available for fully constructed nets");
			}
			final IPetriNet<LETTER, PLACE> fullPetriNet = (IPetriNet<LETTER, PLACE>) mBranchingProcess.getNet();
			final Set<ITransition<LETTER, PLACE>> transitions = new HashSet<>();
			for (final Condition<LETTER, PLACE> cond : event.getSuccessorConditions()) {
				for (final ITransition<LETTER, PLACE> t : fullPetriNet.getSuccessors(cond.getPlace())) {
					transitions.add(t);
				}
			}
			final List<Candidate<LETTER, PLACE>> candidates = new ArrayList<>();
			for (final ITransition<LETTER, PLACE> transition : transitions) {
				final Candidate<LETTER, PLACE> candidate = new Candidate<>(new SimpleSuccessorTransitionProvider<>(
						Collections.singleton(transition), fullPetriNet), event.getSuccessorConditions());
				candidates.add(candidate);
			}
			return candidates;
		}
	}


	@Override
	public boolean isEmpy() {
		return mPePq.isEmpty() && mFastpathCutoffEventList.isEmpty();
	}

	@Override
	public int size() {
		return mPePq.size() + mFastpathCutoffEventList.size();
	}

	public int getUsefulExtensionCandidates() {
		return mUsefulExtensionCandidates;
	}

	public int getUselessExtensionCandidates() {
		return mUselessExtensionCandidates;
	}

	public int getMaximalSize() {
		return mMaximalSize;
	}
}