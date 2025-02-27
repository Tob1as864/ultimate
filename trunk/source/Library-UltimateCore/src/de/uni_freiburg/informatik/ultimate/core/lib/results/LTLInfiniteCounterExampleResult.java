/*
 * Copyright (C) 2014-2015 Daniel Dietsch (dietsch@informatik.uni-freiburg.de)
 * Copyright (C) 2015 University of Freiburg
 *
 * This file is part of the ULTIMATE Core.
 *
 * The ULTIMATE Core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * The ULTIMATE Core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with the ULTIMATE Core. If not, see <http://www.gnu.org/licenses/>.
 *
 * Additional permission under GNU GPL version 3 section 7:
 * If you modify the ULTIMATE Core, or any covered work, by linking
 * or combining it with Eclipse RCP (or a modified version of Eclipse RCP),
 * containing parts covered by the terms of the Eclipse Public License, the
 * licensors of the ULTIMATE Core grant you additional permission
 * to convey the resulting work.
 */
package de.uni_freiburg.informatik.ultimate.core.lib.results;

import de.uni_freiburg.informatik.ultimate.core.model.models.IElement;
import de.uni_freiburg.informatik.ultimate.core.model.services.IBacktranslationService;
import de.uni_freiburg.informatik.ultimate.core.model.translation.IProgramExecution;
import de.uni_freiburg.informatik.ultimate.util.CoreUtil;

/**
 * Result that reports that a counter example for an LTL property was found and that it is infinite.
 *
 * @author dietsch@informatik.uni-freiburg.de
 *
 */
public class LTLInfiniteCounterExampleResult<ELEM extends IElement, TE extends IElement, E>
		extends NonterminatingLassoResult<ELEM, TE, E> {

	private final String mLTLProperty;

	public LTLInfiniteCounterExampleResult(final ELEM position, final String plugin,
			final IBacktranslationService translatorSequence, final IProgramExecution<TE, E> stem,
			final IProgramExecution<TE, E> loop, final String ltlproperty) {
		super(position, plugin, translatorSequence, stem, loop);
		mLTLProperty = ltlproperty;
	}

	@Override
	public String getShortDescription() {
		return "Violation of LTL property " + mLTLProperty;
	}

	@Override
	public String getLongDescription() {
		final StringBuilder sb = new StringBuilder();
		sb.append("Found an infinite, lasso-shaped execution that violates the LTL property ");
		sb.append(mLTLProperty);
		sb.append(".");
		sb.append(CoreUtil.getPlatformLineSeparator());
		sb.append("Stem:");
		sb.append(CoreUtil.getPlatformLineSeparator());
		sb.append(mTranslatorSequence.translateProgramExecution(mStem));
		sb.append("Loop:");
		sb.append(CoreUtil.getPlatformLineSeparator());
		sb.append(mTranslatorSequence.translateProgramExecution(mLoop));
		sb.append("End of lasso representation.");
		return sb.toString();
	}

}
